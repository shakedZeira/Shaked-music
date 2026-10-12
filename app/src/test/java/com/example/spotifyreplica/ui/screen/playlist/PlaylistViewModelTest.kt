package com.example.spotifyreplica.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import com.example.spotifyreplica.domain.usecase.DownloadPlaylistUseCase
import com.example.spotifyreplica.playback.PlaybackConnection
import com.example.spotifyreplica.ui.navigation.PLAYLIST_ID_ARG
import com.example.spotifyreplica.ui.screen.search.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [PlaylistViewModel]: offset paging, download status/downloaded joining and the
 * download-all / play actions. All repositories are mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val catalog: MusicCatalogRepository = mockk()
    private val downloads: DownloadRepository = mockk()
    private val library: LibraryRepository = mockk()
    private val downloadPlaylist: DownloadPlaylistUseCase = mockk()
    private val playback: PlaybackConnection = mockk()

    private fun createViewModel(
        playlistId: String = "p1",
        downloadedTracks: List<LibraryTrack> = emptyList(),
        cachedPlaylists: List<PlaylistEntity> = emptyList(),
        queue: List<DownloadItem> = emptyList(),
    ): PlaylistViewModel {
        every { downloads.observeQueue() } returns flowOf(queue)
        every { library.observeLibraryTracks() } returns flowOf(downloadedTracks)
        every { library.observePlaylists() } returns flowOf(cachedPlaylists)
        val handle = SavedStateHandle(mapOf(PLAYLIST_ID_ARG to playlistId))
        return PlaylistViewModel(handle, catalog, downloads, library, downloadPlaylist, playback)
    }

    @Test
    fun `loads the first page and maps download statuses`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1"), track("t2")), offset = 0, hasMore = false),
            )
            val viewModel = createViewModel(
                queue = listOf(downloadItem("t1", DownloadStatus.RUNNING)),
            )
            subscribe(viewModel)

            advanceUntilIdle()

            assertEquals(listOf("t1", "t2"), viewModel.uiState.value.tracks.map { it.id })
            assertEquals(DownloadStatus.RUNNING, viewModel.uiState.value.statusByTrackId["t1"])
            assertTrue(viewModel.uiState.value.endReached)
        }

    @Test
    fun `exposes downloaded ids and the cached header`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1")), offset = 0, hasMore = false),
            )
            val viewModel = createViewModel(
                downloadedTracks = listOf(libraryTrack("t1")),
                cachedPlaylists = listOf(playlistEntity("p1", name = "Chill", owner = "Owner", count = 7)),
            )
            subscribe(viewModel)

            advanceUntilIdle()

            assertEquals("Chill", viewModel.uiState.value.name)
            assertEquals("Owner", viewModel.uiState.value.ownerName)
            assertEquals(7, viewModel.uiState.value.trackCount)
            assertTrue(viewModel.uiState.value.downloadedIds.contains("t1"))
        }

    @Test
    fun `loadMore appends the next page and stops at the end`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = (1..PAGE_SIZE).map { track("t$it") }, offset = 0, hasMore = true),
            )
            coEvery { catalog.playlistTracks("p1", PAGE_SIZE, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t${PAGE_SIZE + 1}")), offset = PAGE_SIZE, hasMore = false),
            )
            val viewModel = createViewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            assertEquals(PAGE_SIZE, viewModel.uiState.value.tracks.size)

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(PAGE_SIZE + 1, viewModel.uiState.value.tracks.size)
            coVerify(exactly = 1) { catalog.playlistTracks("p1", PAGE_SIZE, PAGE_SIZE) }
            assertTrue(viewModel.uiState.value.endReached)
        }

    @Test
    fun `loadMore is a no-op once the end is reached`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1")), offset = 0, hasMore = false),
            )
            val viewModel = createViewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            coVerify(exactly = 0) { catalog.playlistTracks("p1", 1, PAGE_SIZE) }
        }

    @Test
    fun `a failed load surfaces an error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.failure(
            IllegalStateException("Spotify error 404"),
        )
        val viewModel = createViewModel()
        subscribe(viewModel)

        advanceUntilIdle()

        assertEquals("Spotify error 404", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.tracks.isEmpty())
    }

    @Test
    fun `downloadTrack enqueues with the playlist id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1")), offset = 0, hasMore = false),
            )
            coEvery { downloads.enqueue(any(), any()) } returns UUID.randomUUID()
            val viewModel = createViewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.downloadTrack(track("t1"))
            advanceUntilIdle()

            coVerify(exactly = 1) { downloads.enqueue(track("t1"), "p1") }
        }

    @Test
    fun `downloadAll enqueues every loaded track`() = runTest(mainDispatcherRule.testDispatcher) {
        val tracks = listOf(track("t1"), track("t2"))
        coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
            page(items = tracks, offset = 0, hasMore = false),
        )
        coEvery { downloadPlaylist.invoke("p1", any<List<Track>>()) } returns emptyList()
        val viewModel = createViewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        viewModel.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadPlaylist.invoke("p1", listOf(track("t1"), track("t2"))) }
    }

    @Test
    fun `playDownloaded queues only the downloaded tracks`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1"), track("t2")), offset = 0, hasMore = false),
            )
            every { playback.setQueue(any(), any()) } returns Unit
            val viewModel = createViewModel(downloadedTracks = listOf(libraryTrack("t2")))
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.playDownloaded()

            verify(exactly = 1) { playback.setQueue(listOf(libraryTrack("t2")), 0) }
        }

    @Test
    fun `playRandom queues the downloaded tracks in random order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1"), track("t2")), offset = 0, hasMore = false),
            )
            every { playback.setQueue(any(), any()) } returns Unit
            val viewModel = createViewModel(
                downloadedTracks = listOf(libraryTrack("t1"), libraryTrack("t2")),
            )
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.playRandom()

            val listSlot = slot<List<LibraryTrack>>()
            verify(exactly = 1) { playback.setQueue(capture(listSlot), 0) }
            assertEquals(2, listSlot.captured.size)
            assertEquals(setOf(libraryTrack("t1"), libraryTrack("t2")), listSlot.captured.toSet())
        }

    @Test
    fun `playRandom is a no-op when nothing is downloaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.playlistTracks("p1", 0, PAGE_SIZE) } returns Result.success(
                page(items = listOf(track("t1"), track("t2")), offset = 0, hasMore = false),
            )
            val viewModel = createViewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.playRandom()

            verify(exactly = 0) { playback.setQueue(any(), any()) }
        }

    private fun TestScope.subscribe(viewModel: PlaylistViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun page(items: List<Track>, offset: Int, hasMore: Boolean) = Page(
        items = items,
        offset = offset,
        limit = PAGE_SIZE,
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

    private fun libraryTrack(id: String) = LibraryTrack(
        spotifyId = id,
        title = "Title $id",
        artists = listOf("Artist"),
        album = "Album",
        artworkUrl = null,
        localPath = "/data/$id.m4a",
        sizeBytes = 1024L,
        durationMs = 180_000L,
        container = "m4a",
        bitrateKbps = 256,
    )

    private fun downloadItem(spotifyId: String, status: DownloadStatus) =
        DownloadItem(
            id = "download_$spotifyId",
            spotifyId = spotifyId,
            playlistId = "p1",
            title = "Title $spotifyId",
            status = status,
            bytesDownloaded = 0L,
            totalBytes = null,
            error = null,
        )

    private fun playlistEntity(id: String, name: String, owner: String, count: Int) =
        PlaylistEntity(
            spotifyId = id,
            name = name,
            ownerName = owner,
            artworkUrl = null,
            trackCount = count,
            syncedAtMs = 1L,
        )

    private companion object {
        const val PAGE_SIZE = 50
    }
}
