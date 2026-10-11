package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [SearchTracksUseCase]: blank-query short-circuit, the 2026 `limit<=10` cap and
 * query/offset forwarding. [MusicCatalogRepository] is mocked with MockK — no network involved.
 */
class SearchTracksUseCaseTest {

    private val repository = mockk<MusicCatalogRepository>()
    private val useCase = SearchTracksUseCase(repository)

    @Test
    fun `blank query returns an empty page without touching the repository`() = runTest {
        val result = useCase("   ", offset = 0)

        val page = result.getOrThrow()
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertTrue(page.total == 0)
        assertTrue(page.limit == SearchTracksUseCase.PAGE_SIZE)
        coVerify(exactly = 0) { repository.searchTracks(any(), any(), any()) }
    }

    @Test
    fun `empty query short-circuits too`() = runTest {
        val page = useCase("", offset = 0).getOrThrow()
        assertTrue(page.items.isEmpty())
        coVerify(exactly = 0) { repository.searchTracks(any(), any(), any()) }
    }

    @Test
    fun `limit is always the dev-mode cap of 10`() = runTest {
        coEvery { repository.searchTracks(any(), any(), any()) } returns Result.success(page())

        useCase("daft punk", offset = 0)

        coVerify(exactly = 1) { repository.searchTracks("daft punk", 0, 10) }
        assertTrue(SearchTracksUseCase.PAGE_SIZE == 10)
    }

    @Test
    fun `trims the query and forwards the offset unchanged`() = runTest {
        coEvery { repository.searchTracks(any(), any(), any()) } returns Result.success(page(offset = 20))

        useCase("  the weeknd  ", offset = 20)

        coVerify(exactly = 1) { repository.searchTracks("the weeknd", 20, 10) }
    }

    @Test
    fun `negative offsets are clamped to zero`() = runTest {
        coEvery { repository.searchTracks(any(), any(), any()) } returns Result.success(page())

        useCase("lofi", offset = -5)

        coVerify(exactly = 1) { repository.searchTracks("lofi", 0, 10) }
    }

    @Test
    fun `repository failures are propagated untouched`() = runTest {
        val failure = IllegalStateException("boom")
        coEvery { repository.searchTracks(any(), any(), any()) } returns Result.failure(failure)

        val result = useCase("kanye", offset = 0)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }

    private fun page(offset: Int = 0) = Page(
        items = listOf(track("id-1")),
        offset = offset,
        limit = SearchTracksUseCase.PAGE_SIZE,
        total = 1,
        hasMore = false,
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
