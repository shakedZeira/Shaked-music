package com.example.spotifyreplica.data.download

import android.content.Context
import android.os.Environment
import com.example.spotifyreplica.domain.source.AudioContainer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where downloaded audio lives on disk.
 *
 * Files are written under the app-specific external music directory
 * (`getExternalFilesDir(DIRECTORY_MUSIC)`), so they are private to the app, are cleaned up when the
 * app is uninstalled, and need no runtime storage permission. Layout:
 *
 * ```
 * <externalMusic>/<playlistId>/<spotifyId>.<ext>   // a downloaded track
 * <externalMusic>/standalone/<spotifyId>.<ext>     // a track downloaded outside a playlist
 * <externalMusic>/<playlistId>/<spotifyId>.part.<ext>  // in-flight download
 * ```
 *
 * The `.part` file is streamed to first and only atomically renamed to the final name once the
 * bytes are complete and tagged, so a crashed/killed download never leaves a half-written file at
 * the final path (the next attempt simply overwrites/removes the stale `.part`).
 */
@Singleton
class StoragePaths @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Root of the on-disk music library. Created lazily by callers via [fileFor]. */
    fun musicRoot(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return external ?: File(context.filesDir, FALLBACK_MUSIC_DIR)
    }

    /**
     * Final destination for a downloaded track. [playlistId] may be null (a standalone download);
     * the extension is chosen from [container], or derived from [sourceUrl] for local files.
     */
    fun fileFor(
        playlistId: String?,
        spotifyId: String,
        container: AudioContainer,
        sourceUrl: String? = null,
    ): File = fileFor(playlistId, spotifyId, extensionFor(container, sourceUrl))

    /** Final destination for a downloaded track with an explicit [extension] (e.g. `m4a`). */
    fun fileFor(playlistId: String?, spotifyId: String, extension: String): File {
        val dir = directoryFor(playlistId)
        return File(dir, "${sanitize(spotifyId)}.$extension")
    }

    /**
     * In-flight companion for the final file ([target] or a computed destination). The `.part`
     * marker is inserted *before* the extension (`x.mp3` → `x.part.mp3`) rather than appended, so
     * metadata tools (jaudiotagger) can still detect the audio format from the file name.
     */
    fun partFileFor(target: File): File = File(target.parentFile, partName(target.name))

    /** In-flight `.part` destination for a track whose final path is derived from [container]. */
    fun partFileFor(
        playlistId: String?,
        spotifyId: String,
        container: AudioContainer,
        sourceUrl: String? = null,
    ): File = partFileFor(fileFor(playlistId, spotifyId, container, sourceUrl))

    /** The file extension (without a dot) used for [container]; local files keep their own. */
    fun extensionFor(container: AudioContainer, sourceUrl: String? = null): String = when (container) {
        AudioContainer.MP3 -> EXT_MP3
        AudioContainer.M4A -> EXT_M4A
        AudioContainer.WEBM_OPUS -> EXT_WEBM
        AudioContainer.LOCAL -> extensionFromSource(sourceUrl) ?: EXT_M4A
    }

    /** Total size, in bytes, of every file under [base] (recursively). */
    fun totalBytes(base: File = musicRoot()): Long {
        if (!base.exists()) return 0L
        if (base.isFile) return base.length()
        return base.listFiles()?.sumOf { child ->
            if (child.isDirectory) totalBytes(child) else child.length()
        } ?: 0L
    }

    private fun directoryFor(playlistId: String?): File {
        val dir = playlistId?.takeIf { it.isNotBlank() }
            ?.let { File(musicRoot(), sanitize(it)) }
            ?: File(musicRoot(), STANDALONE_DIR)
        dir.mkdirs()
        return dir
    }

    /** Inserts [PART_SUFFIX] before the extension so the audio format stays recognizable. */
    private fun partName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) {
            fileName.substring(0, dot) + PART_SUFFIX + fileName.substring(dot)
        } else {
            fileName + PART_SUFFIX
        }
    }

    private fun extensionFromSource(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return null
        val ext = name.substringAfterLast('.').lowercase()
        return ext.takeIf { it in KNOWN_AUDIO_EXTENSIONS }
    }

    /** Keeps a path segment to `[A-Za-z0-9._-]` so it can never escape the music root. */
    private fun sanitize(raw: String): String {
        val cleaned = raw.trim().map { ch ->
            if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') ch else '_'
        }.joinToString("")
        return cleaned.ifBlank { "unnamed" }
    }

    companion object {
        /** Directory for tracks downloaded outside of any playlist. */
        const val STANDALONE_DIR = "standalone"

        /** Suffix used for in-flight downloads before the atomic rename. */
        const val PART_SUFFIX = ".part"

        private const val FALLBACK_MUSIC_DIR = "music"

        private const val EXT_M4A = "m4a"
        private const val EXT_MP3 = "mp3"
        private const val EXT_WEBM = "webm"

        private val KNOWN_AUDIO_EXTENSIONS =
            setOf("m4a", "mp4", "aac", "mp3", "opus", "ogg", "oga", "webm", "wav", "flac")
    }
}
