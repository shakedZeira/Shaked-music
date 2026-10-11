package com.example.spotifyreplica.data.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.spotifyreplica.domain.model.Track
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes

/**
 * Writes title/artist/album and cover art into a downloaded audio file using jaudiotagger.
 *
 * This step is deliberately **best-effort**: [stampMetadata] never throws. A download is only
 * "lost" if the audio bytes themselves fail — a tagging failure (unsupported container, missing
 * artwork, malformed frame data) simply leaves the file untagged, which is always preferable to
 * discarding the audio the user waited for.
 *
 * A missing tag is created and installed on the file, because jaudiotagger's writer rejects a file
 * whose `AudioFile.getTag()` is null. For MP3 that default is an ID3v2.3 tag (ID3v1 cannot hold
 * pictures); other containers use their own default tag.
 */
@Singleton
class TagWriter @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Best-effort tag write. Returns true when jaudiotagger committed the tag, false when the file
     * was untaggable or tagging failed for any reason. Never throws.
     */
    suspend fun stampMetadata(file: File, track: Track, artworkUrl: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { writeTags(file, track, artworkUrl) }.getOrDefault(false)
        }

    private fun writeTags(file: File, track: Track, artworkUrl: String?): Boolean {
        if (!file.isFile) return false

        val audioFile = AudioFileIO.read(file)
        val tag = tagFor(audioFile)

        if (track.title.isNotBlank()) tag.setField(FieldKey.TITLE, track.title)
        if (track.artists.isNotEmpty()) {
            tag.setField(FieldKey.ARTIST, track.artists.joinToString(ARTIST_SEPARATOR))
        }
        if (track.album.isNotBlank()) tag.setField(FieldKey.ALBUM, track.album)

        artworkBytes(artworkUrl)?.let { bytes ->
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = bytes
            artwork.mimeType = mimeTypeFor(bytes)
            artwork.pictureType = PictureTypes.DEFAULT_ID
            tag.setField(artwork)
        }

        AudioFileIO.write(audioFile)
        return true
    }

    /**
     * Returns a tag capable of holding artwork. Existing tags are reused; otherwise a default tag is
     * created (ID3v2.3 for MP3, which can store pictures — ID3v1 cannot).
     *
     * The tag must be installed via [AudioFile.getTagOrCreateAndSetDefault] rather than
     * `MP3File.setID3v2Tag`: the latter only fills the ID3v2 slot and leaves [AudioFile.getTag]
     * null, which makes [AudioFileIO.write] reject the file.
     */
    private fun tagFor(audioFile: AudioFile): Tag =
        audioFile.tag ?: audioFile.getTagOrCreateAndSetDefault()

    /** Downloads, then downscales, the cover; returns null when there is no usable artwork. */
    private fun artworkBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        val raw = fetch(url)?.takeIf { it.isNotEmpty() } ?: return null
        return downscale(raw) ?: raw
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_ARTWORK_BYTES) return null
            body.byteStream().use { stream -> readAtMost(stream, MAX_ARTWORK_BYTES) }
        }
    }.getOrNull()

    /** Reads at most [max] bytes; returns null if the stream holds more than [max]. */
    private fun readAtMost(stream: InputStream, max: Int): ByteArray? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > max) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Downscales oversized artwork to keep the embedded cover small (plan risk R8). Returns null
     * when no downscale is needed (the caller then keeps the original bytes) or when it fails.
     */
    private fun downscale(raw: ByteArray): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width <= MAX_ARTWORK_DIMENSION && height <= MAX_ARTWORK_DIMENSION) return null

        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_ARTWORK_DIMENSION &&
            height / (sampleSize * 2) >= MAX_ARTWORK_DIMENSION
        ) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun mimeTypeFor(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte() -> "image/png"

        else -> "image/jpeg"
    }

    private companion object {
        const val ARTIST_SEPARATOR = ", "
        const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        const val MAX_ARTWORK_DIMENSION = 640
        const val JPEG_QUALITY = 85
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
