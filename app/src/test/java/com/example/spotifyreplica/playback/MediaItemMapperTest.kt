package com.example.spotifyreplica.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import com.example.spotifyreplica.domain.model.LibraryTrack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed tests for the offline → Media3 mapping. [android.net.Uri] is an Android class,
 * so a Robolectric runtime is required (see `app/src/test/resources/robolectric.properties`).
 *
 * File-URI assertions are built from the same [java.io.File] instance the mapper resolves, so the
 * tests stay valid regardless of the host OS path separator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaItemMapperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `maps spotify id title artist and album into the media item`() {
        val track = sampleTrack(
            spotifyId = "4cOdK2wGLETKBW3PvgPWqT",
            title = "Blinding Lights",
            artists = listOf("The Weeknd"),
            album = "After Hours",
        )

        val item = MediaItemMapper.toMediaItem(track)

        assertEquals("4cOdK2wGLETKBW3PvgPWqT", item.mediaId)
        assertEquals("Blinding Lights", item.mediaMetadata.title?.toString())
        assertEquals("The Weeknd", item.mediaMetadata.artist?.toString())
        assertEquals("After Hours", item.mediaMetadata.albumTitle?.toString())
        assertEquals(true, item.mediaMetadata.isPlayable)
    }

    @Test
    fun `joins multiple artists with a comma separator`() {
        val item = MediaItemMapper.toMediaItem(
            sampleTrack(artists = listOf("Artist A", "Artist B")),
        )

        assertEquals("Artist A, Artist B", item.mediaMetadata.artist?.toString())
    }

    @Test
    fun `maps an artwork url into the artwork uri`() {
        val item = MediaItemMapper.toMediaItem(
            sampleTrack(artworkUrl = "https://i.scdn.co/image/abc"),
        )

        assertEquals("https://i.scdn.co/image/abc", item.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun `null artwork url produces no artwork uri`() {
        val item = MediaItemMapper.toMediaItem(sampleTrack(artworkUrl = null))

        assertNull(item.mediaMetadata.artworkUri)
    }

    @Test
    fun `maps a downloaded file path to a file uri`() {
        val file = tempFolder.newFile("song.opus")

        val item = MediaItemMapper.toMediaItem(sampleTrack(localPath = file.absolutePath))

        val uri = item.localConfiguration?.uri
        assertEquals("file", uri?.scheme)
        assertEquals(Uri.fromFile(file), uri)
    }

    @Test
    fun `keeps an existing content uri untouched`() {
        val item = MediaItemMapper.toMediaItem(
            sampleTrack(localPath = "content://media/audio/1"),
        )

        val uri = item.localConfiguration?.uri
        assertEquals("content", uri?.scheme)
        assertEquals("content://media/audio/1", uri?.toString())
    }

    @Test
    fun `localUri wraps a bare filesystem path in a file uri`() {
        val file = tempFolder.newFile("song.mp3")

        val uri = MediaItemMapper.localUri(file.absolutePath)

        assertEquals("file", uri.scheme)
        assertEquals(Uri.fromFile(file), uri)
    }

    @Test
    fun `localUri leaves an already-schemed uri untouched`() {
        val uri = MediaItemMapper.localUri("content://downloads/1")

        assertEquals("content", uri.scheme)
        assertEquals("content://downloads/1", uri.toString())
    }

    private fun sampleTrack(
        spotifyId: String = "track-id",
        title: String = "Song",
        artists: List<String> = listOf("Artist"),
        album: String = "Album",
        artworkUrl: String? = null,
        localPath: String = "/data/user/0/app/files/song.opus",
        sizeBytes: Long? = 1_024L,
        durationMs: Long = 200_000L,
        container: String = "OGG",
        bitrateKbps: Int? = 320,
    ) = LibraryTrack(
        spotifyId = spotifyId,
        title = title,
        artists = artists,
        album = album,
        artworkUrl = artworkUrl,
        localPath = localPath,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        container = container,
        bitrateKbps = bitrateKbps,
    )
}
