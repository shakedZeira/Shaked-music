package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [DownloadPlaylistUseCase]: the summary overload delegates to
 * [DownloadRepository.enqueuePlaylist], and the id overload enqueues one entry per track while
 * preserving the playlist association.
 */
class DownloadPlaylistUseCaseTest {

    private val downloads = mockk<DownloadRepository>()
    private val useCase = DownloadPlaylistUseCase(downloads)

    @Test
    fun `summary overload enqueues the whole playlist in order`() = runTest {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        coEvery { downloads.enqueuePlaylist(any(), any()) } returns ids

        val result = useCase(summary("p1"), listOf(track("t1"), track("t2")))

        assertEquals(ids, result)
        coVerify(exactly = 1) {
            downloads.enqueuePlaylist(summary("p1"), listOf(track("t1"), track("t2")))
        }
    }

    @Test
    fun `id overload enqueues every track with the playlist id`() = runTest {
        coEvery { downloads.enqueue(any(), any()) } returns UUID.randomUUID()
        val tracks = listOf(track("t1"), track("t2"), track("t3"))

        useCase("p1", tracks)

        tracks.forEach { track ->
            coVerify(exactly = 1) { downloads.enqueue(track, "p1") }
        }
        coVerify(exactly = tracks.size) { downloads.enqueue(any(), "p1") }
    }

    private fun summary(id: String) = PlaylistSummary(
        id = id,
        name = "Playlist $id",
        ownerName = "Owner",
        artworkUrl = null,
        trackCount = 2,
    )

    private fun track(id: String) = Track(
        id = id,
        title = "Title $id",
        artists = listOf("Artist"),
        album = "Album",
        artworkUrl = null,
        durationMs = 180_000L,
        isrc = null,
        spotifyUri = null,
    )
}
