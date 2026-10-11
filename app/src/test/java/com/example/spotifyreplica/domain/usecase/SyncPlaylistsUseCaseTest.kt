package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [SyncPlaylistsUseCase]: a successful fetch is cached (with a sync timestamp)
 * and reports the count; a repository failure is propagated without writing anything.
 */
class SyncPlaylistsUseCaseTest {

    private val catalog = mockk<MusicCatalogRepository>()
    private val library = mockk<LibraryRepository>()
    private val useCase = SyncPlaylistsUseCase(catalog, library)

    @Test
    fun `success caches every playlist and returns the count`() = runTest {
        coEvery { catalog.myPlaylists() } returns Result.success(
            listOf(summary("p1"), summary("p2")),
        )
        coEvery { library.upsertPlaylists(any()) } returns Unit

        val result = useCase()

        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) {
            library.upsertPlaylists(
                match { entities ->
                    entities.map { it.spotifyId } == listOf("p1", "p2") &&
                        entities.all { it.name.isNotBlank() && it.syncedAtMs != null }
                },
            )
        }
    }

    @Test
    fun `repository failures propagate untouched and cache nothing`() = runTest {
        val failure = IllegalStateException("Spotify error 401")
        coEvery { catalog.myPlaylists() } returns Result.failure(failure)

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
        coVerify(exactly = 0) { library.upsertPlaylists(any()) }
    }

    @Test
    fun `an empty library caches zero playlists`() = runTest {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        coEvery { library.upsertPlaylists(any()) } returns Unit

        val result = useCase()

        assertEquals(0, result.getOrThrow())
        coVerify(exactly = 1) { library.upsertPlaylists(emptyList()) }
    }

    private fun summary(id: String) = PlaylistSummary(
        id = id,
        name = "Playlist $id",
        ownerName = "Owner",
        artworkUrl = null,
        trackCount = 3,
    )
}
