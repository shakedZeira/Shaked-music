package com.example.spotifyreplica.data.download

import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the worker input-data helpers in [TrackDownloadWorker] (pure JVM: `androidx.work.Data`
 * is a plain value holder, so no Robolectric is required).
 */
class DownloadInputDataTest {

    @Test
    fun `round trips a fully populated track`() {
        val track = sampleTrack(
            id = "track-1",
            artists = listOf("The Weeknd"),
            artworkUrl = "https://example.com/art.jpg",
        )

        val data = track.toDownloadInputData(playlistId = "playlist-1", downloadId = "download_track-1")
        val restored = inputDataToTrack(data)

        assertEquals(track, restored)
        assertEquals("download_track-1", data.getString(KEY_DOWNLOAD_ID))
        assertEquals("playlist-1", data.getString(KEY_PLAYLIST_ID))
    }

    @Test
    fun `keeps multiple artists as a single delimited field`() {
        val track = sampleTrack(artists = listOf("Artist A", "Artist, B", "Artist C"))

        val restored = inputDataToTrack(
            track.toDownloadInputData(playlistId = null, downloadId = "download_track-1"),
        )

        assertEquals(track.artists, restored?.artists)
    }

    @Test
    fun `a standalone download has no playlist id`() {
        val track = sampleTrack()

        val data = track.toDownloadInputData(playlistId = null, downloadId = defaultDownloadId(track.id))

        assertNull(data.getString(KEY_PLAYLIST_ID))
        assertEquals(track, inputDataToTrack(data))
    }

    @Test
    fun `missing spotify id yields null`() {
        val data = workDataOf(KEY_TITLE to "Orphan")
        assertNull(inputDataToTrack(data))
    }

    @Test
    fun `missing title yields null`() {
        val data = workDataOf(KEY_SPOTIFY_ID to "no-title")
        assertNull(inputDataToTrack(data))
    }

    @Test
    fun `unique work name matches default download id`() {
        assertEquals("download_abc123", uniqueDownloadWorkName("abc123"))
        assertEquals(uniqueDownloadWorkName("abc123"), defaultDownloadId("abc123"))
    }
}
