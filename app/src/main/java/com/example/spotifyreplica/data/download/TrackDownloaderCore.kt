package com.example.spotifyreplica.data.download

import android.content.Context
import android.net.Uri
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.source.AudioContainer
import com.example.spotifyreplica.domain.source.AudioSourceResolver
import com.example.spotifyreplica.domain.source.ResolvedAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Progress callback: bytes written so far and the total size when known. */
typealias ProgressCallback = suspend (bytesWritten: Long, totalBytes: Long?) -> Unit

/** The terminal state of one [TrackDownloaderCore.download] attempt. */
sealed interface TrackOutcome {

    /** Audio was written, tagged (best-effort) and persisted to the local library. */
    data class Success(
        val localPath: String,
        val sizeBytes: Long,
        val container: AudioContainer,
        val bitrateKbps: Int?,
    ) : TrackOutcome

    /** No audio source could be found for the track; retrying will not help. */
    data object NoSource : TrackOutcome

    /** A transient failure (network/5xx/expired URL) worth retrying with backoff. */
    data class Retryable(val error: String) : TrackOutcome

    /** A permanent failure (404/other 4xx/unusable file) that must not be retried. */
    data class Permanent(val error: String) : TrackOutcome
}

/**
 * The actual download mechanism, independent of WorkManager so it can be unit-tested directly.
 *
 * Flow per attempt:
 *  1. Resolve the audio URL **inside** the call (YouTube URLs expire — never cache them).
 *  2. Stream bytes to a `<name>.part.<ext>` file, reporting throttled progress.
 *  3. Stamp title/artist/album/artwork (best-effort; never fatal).
 *  4. Atomically rename `.part` → final path.
 *  5. Upsert the fully-tagged track into the offline library.
 *
 * Any failure deletes the `.part` file so a retry starts clean and no orphan partials remain.
 */
@Singleton
class TrackDownloaderCore @Inject constructor(
    private val audioSourceResolver: AudioSourceResolver,
    private val okHttpClient: OkHttpClient,
    private val libraryRepository: LibraryRepository,
    private val tagWriter: TagWriter,
    private val storagePaths: StoragePaths,
    @ApplicationContext private val context: Context,
) {

    suspend fun download(
        track: Track,
        playlistId: String?,
        onProgress: ProgressCallback? = null,
    ): TrackOutcome = withContext(Dispatchers.IO) {
        val resolved = try {
            audioSourceResolver.resolve(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext TrackOutcome.Retryable(
                "Could not resolve an audio source: ${e.message ?: e.javaClass.simpleName}"
            )
        } ?: return@withContext TrackOutcome.NoSource

        val extension = storagePaths.extensionFor(resolved.container, resolved.url)
        val target = storagePaths.fileFor(playlistId, track.id, extension)
        val part = storagePaths.partFileFor(target)
        part.deleteQuietly()

        try {
            val streamed = streamToFile(resolved, part, onProgress)
            tagWriter.stampMetadata(part, track, track.artworkUrl)
            moveAtomically(part, target)

            val finalSize = target.length().takeIf { it > 0 } ?: streamed
            libraryRepository.upsertTrack(
                track = track,
                localPath = target.absolutePath,
                sizeBytes = finalSize,
                container = extension,
                bitrateKbps = resolved.bitrateKbps,
                downloadedAtMs = System.currentTimeMillis()
            )
            TrackOutcome.Success(
                localPath = target.absolutePath,
                sizeBytes = finalSize,
                container = resolved.container,
                bitrateKbps = resolved.bitrateKbps
            )
        } catch (e: CancellationException) {
            part.deleteQuietly()
            throw e
        } catch (e: SourceHttpException) {
            part.deleteQuietly()
            val message = "Server returned HTTP ${e.code}"
            if (isRetryableHttpCode(e.code)) {
                TrackOutcome.Retryable(message)
            } else {
                TrackOutcome.Permanent(message)
            }
        } catch (e: IOException) {
            part.deleteQuietly()
            TrackOutcome.Retryable(e.message ?: "Network error")
        } catch (e: Exception) {
            part.deleteQuietly()
            TrackOutcome.Permanent(e.message ?: "Download failed")
        }
    }

    private suspend fun streamToFile(
        resolved: ResolvedAudio,
        destination: File,
        onProgress: ProgressCallback?,
    ): Long {
        destination.parentFile?.mkdirs()
        val url = resolved.url
        return when {
            isHttpUrl(url) -> streamHttp(url, destination, onProgress)

            url.startsWith("content://") -> {
                val input = openContentStream(url)
                    ?: throw IOException("Cannot open content URI: $url")
                input.use { copyStream(it, destination, null, onProgress) }
            }

            else -> {
                val source = sourceFile(url)
                if (!source.isFile) throw IOException("Source file not found: $url")
                source.inputStream().use { copyStream(it, destination, source.length(), onProgress) }
            }
        }
    }

    private suspend fun streamHttp(
        url: String,
        destination: File,
        onProgress: ProgressCallback?,
    ): Long {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SourceHttpException(response.code)
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input -> return copyStream(input, destination, total, onProgress) }
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        destination: File,
        totalBytes: Long?,
        onProgress: ProgressCallback?,
    ): Long {
        var written = 0L
        var lastReported = 0L
        destination.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                written += read
                if (written - lastReported >= PROGRESS_INTERVAL_BYTES) {
                    lastReported = written
                    onProgress?.invoke(written, totalBytes)
                }
            }
            output.flush()
        }
        onProgress?.invoke(written, totalBytes)
        return written
    }

    /** Atomic `.part` → final rename, falling back to a streamed copy on filesystems without it. */
    private fun moveAtomically(source: File, target: File) {
        target.parentFile?.mkdirs()
        val atomic = runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.isSuccess
        if (atomic) return
        if (source.renameTo(target)) return
        source.copyTo(target, overwrite = true)
        source.deleteQuietly()
    }

    private fun openContentStream(url: String): InputStream? =
        runCatching { context.contentResolver.openInputStream(Uri.parse(url)) }.getOrNull()

    private fun sourceFile(url: String): File {
        val path = when {
            url.startsWith("file:///") -> {
                // file:///C:/dir/x.mp3 -> C:/dir/x.mp3 ; file:///home/x -> /home/x
                val rest = url.removePrefix("file:///")
                if (rest.length >= 2 && rest[1] == ':') rest else "/$rest"
            }

            url.startsWith("file://") -> url.removePrefix("file://")
            url.startsWith("file:") -> url.removePrefix("file:")
            else -> url
        }
        return File(path)
    }

    private fun File.deleteQuietly() {
        runCatching { if (exists()) delete() }
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_BYTES = 512L * 1024L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

/** Thrown by [TrackDownloaderCore.streamHttp] for a non-2xx HTTP response. */
internal class SourceHttpException(val code: Int) : IOException("HTTP $code")

/** 403/410 (expired URL), 408 (timeout), 429 (throttle) and 5xx are worth another attempt. */
internal fun isRetryableHttpCode(code: Int): Boolean =
    code == 403 || code == 408 || code == 410 || code == 429 || code in 500..599
