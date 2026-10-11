package com.example.spotifyreplica.ui.screen.search

import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import com.example.spotifyreplica.domain.usecase.SearchTracksUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Unit tests for [SearchViewModel] — debounce, paging, stale-response handling and downloads. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val catalog: MusicCatalogRepository = mockk()
    private val downloads: DownloadRepository = mockk()

    private lateinit var viewModel: SearchViewModel

    /** Constructed after the mocks are stubbed, since `observeQueue()` is read eagerly. */
    private fun createViewModel() {
        viewModel = SearchViewModel(SearchTracksUseCase(catalog), downloads)
    }

    @Test
    fun `typing debounces, then issues a single search at offset 0`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            coEvery { catalog.searchTracks("daft punk", 0, 10) } returns Result.success(
                page(items = listOf(track("t1")), offset = 0, hasMore = false),
            )
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("daft")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 50)
            viewModel.onQueryChange("daft punk")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1)
            coVerify(exactly = 0) { catalog.searchTracks(any(), any(), any()) }

            advanceTimeBy(1)
            advanceUntilIdle()

            coVerify(exactly = 1) { catalog.searchTracks("daft punk", 0, 10) }
            assertEquals(listOf("t1"), viewModel.uiState.value.results.map { it.id })
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.endReached)
        }

    @Test
    fun `blank input clears results and never calls the catalog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("   ")
            advanceUntilIdle()

            coVerify(exactly = 0) { catalog.searchTracks(any(), any(), any()) }
            assertTrue(viewModel.uiState.value.results.isEmpty())
        }

    @Test
    fun `loadMore appends the next page and honours endReached`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            coEvery { catalog.searchTracks("q", 0, 10) } returns Result.success(
                page(items = (1..10).map { track("t$it") }, offset = 0, hasMore = true),
            )
            coEvery { catalog.searchTracks("q", 10, 10) } returns Result.success(
                page(items = listOf(track("t11")), offset = 10, hasMore = false),
            )
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("q")
            advanceUntilIdle()
            assertEquals(10, viewModel.uiState.value.results.size)
            assertFalse(viewModel.uiState.value.endReached)

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(11, viewModel.uiState.value.results.size)
            coVerify(exactly = 1) { catalog.searchTracks("q", 10, 10) }
            assertTrue(viewModel.uiState.value.endReached)
        }

    @Test
    fun `loadMore is a no-op once the end is reached`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            coEvery { catalog.searchTracks("q", 0, 10) } returns Result.success(
                page(items = listOf(track("t1")), offset = 0, hasMore = false),
            )
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("q")
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            coVerify(exactly = 0) { catalog.searchTracks("q", 1, 10) }
            assertEquals(1, viewModel.uiState.value.results.size)
        }

    @Test
    fun `an in-flight stale response never overwrites the newest query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            val gate = CompletableDeferred<Unit>()
            coEvery { catalog.searchTracks("old", 0, 10) } coAnswers {
                gate.await()
                Result.success(page(items = listOf(track("stale")), offset = 0, hasMore = false))
            }
            coEvery { catalog.searchTracks("new", 0, 10) } returns Result.success(
                page(items = listOf(track("fresh")), offset = 0, hasMore = false),
            )
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("old")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1) // "old" starts and suspends on the gate.
            viewModel.onQueryChange("new")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            advanceUntilIdle()

            // Release the stale response late; it must not clobber the newer query's results.
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("fresh"), viewModel.uiState.value.results.map { it.id })
        }

    @Test
    fun `onDownload enqueues with a null playlist id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            coEvery { downloads.enqueue(any(), any()) } returns UUID.randomUUID()
            createViewModel()

            viewModel.onDownload(track("t1"))
            advanceUntilIdle()

            coVerify(exactly = 1) { downloads.enqueue(track("t1"), null) }
        }

    @Test
    fun `download statuses are mapped by spotify id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { downloads.observeQueue() } returns flowOf(
                listOf(
                    downloadItem(spotifyId = "t1", status = DownloadStatus.RUNNING),
                    downloadItem(spotifyId = "t2", status = DownloadStatus.COMPLETED),
                ),
            )
            createViewModel()
            collectUiState()

            advanceUntilIdle()

            val map = viewModel.uiState.value.downloadsByTrackId
            assertEquals(DownloadStatus.RUNNING, map["t1"])
            assertEquals(DownloadStatus.COMPLETED, map["t2"])
        }

    @Test
    fun `a failed search surfaces an error message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubQueue()
            coEvery { catalog.searchTracks("oops", 0, 10) } returns Result.failure(
                IllegalStateException("Spotify error 429"),
            )
            createViewModel()
            collectUiState()

            viewModel.onQueryChange("oops")
            advanceUntilIdle()

            assertEquals("Spotify error 429", viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.results.isEmpty())
        }

    // --- helpers ------------------------------------------------------------------------------

    private fun stubQueue() {
        every { downloads.observeQueue() } returns flowOf(emptyList())
    }

    /** Keeps the `WhileSubscribed` state flow hot so `uiState.value` reflects emissions. */
    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun page(items: List<Track>, offset: Int, hasMore: Boolean) = Page(
        items = items,
        offset = offset,
        limit = SearchTracksUseCase.PAGE_SIZE,
        total = offset + items.size,
        hasMore = hasMore,
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

    private fun downloadItem(spotifyId: String, status: DownloadStatus) = DownloadItem(
        id = "download_$spotifyId",
        spotifyId = spotifyId,
        playlistId = null,
        title = "Title $spotifyId",
        status = status,
        bytesDownloaded = 0L,
        totalBytes = null,
        error = null,
    )
}
