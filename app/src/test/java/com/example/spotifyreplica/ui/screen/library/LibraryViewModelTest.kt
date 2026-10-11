package com.example.spotifyreplica.ui.screen.library

import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import com.example.spotifyreplica.domain.usecase.SyncPlaylistsUseCase
import com.example.spotifyreplica.playback.PlaybackConnection
import com.example.spotifyreplica.ui.screen.search.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Unit tests for [LibraryViewModel]: mapping the Room-backed download/queue flows into [LibraryUiState],
 * plus the refresh/logout/play actions. The Spotify catalog is mocked, so no network is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val syncPlaylists: SyncPlaylistsUseCase = mockk()
    private val catalog: MusicCatalogRepository = mockk()
    private val library: LibraryRepository = mockk()
    private val downloads: DownloadRepository = mockk()
    private val playback: PlaybackConnection = mockk()
    private val settings: SettingsManager = mockk()

    private fun createViewModel(
        loggedIn: Boolean = true,
        queue: List<DownloadItem> = emptyList(),
        libraryTracks: List<LibraryTrack> = emptyList(),
    ): LibraryViewModel {
        every { downloads.observeQueue() } returns flowOf(queue)
        every { library.observeLibraryTracks() } returns flowOf(libraryTracks)
        every { library.observePlaylists() } returns flowOf(emptyList())
        every { settings.spotifyLoggedIn } returns flowOf(loggedIn)
        return LibraryViewModel(syncPlaylists, catalog, library, downloads, playback, settings)
    }

    @Test
    fun `download statuses are mapped by spotify id`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        val viewModel = createViewModel(
            queue = listOf(
                downloadItem("t1", DownloadStatus.RUNNING),
                downloadItem("t2", DownloadStatus.COMPLETED),
            ),
        )
        subscribe(viewModel)

        advanceUntilIdle()

        val map = viewModel.uiState.value.statusByTrackId
        assertEquals(DownloadStatus.RUNNING, map["t1"])
        assertEquals(DownloadStatus.COMPLETED, map["t2"])
    }

    @Test
    fun `downloaded library tracks are exposed for playback`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
            val viewModel = createViewModel(libraryTracks = listOf(libraryTrack("t1")))
            subscribe(viewModel)

            advanceUntilIdle()

            assertEquals(listOf("t1"), viewModel.uiState.value.downloads.map { it.spotifyId })
        }

    @Test
    fun `refresh loads and caches the fetched playlists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val summaries = listOf(summary("p1"), summary("p2"))
            coEvery { catalog.myPlaylists() } returns Result.success(summaries)
            coEvery { syncPlaylists.cache(any()) } returns summaries.size
            val viewModel = createViewModel()
            subscribe(viewModel)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf("p1", "p2"), viewModel.uiState.value.playlists.map { it.id })
            coVerify(atLeast = 1) { syncPlaylists.cache(summaries) }
        }

    @Test
    fun `refresh failure surfaces an error and caches nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { catalog.myPlaylists() } returns Result.failure(
                IllegalStateException("Spotify error 429"),
            )
            val viewModel = createViewModel()
            subscribe(viewModel)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals("Spotify error 429", viewModel.uiState.value.error)
            coVerify(exactly = 0) { syncPlaylists.cache(any()) }
        }

    @Test
    fun `logged-out state is surfaced to the screen`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        val viewModel = createViewModel(loggedIn = false)
        subscribe(viewModel)

        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.loggedIn)
    }

    @Test
    fun `logout clears the stored spotify session`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        coEvery { settings.clearSpotifyTokens() } returns Unit
        val viewModel = createViewModel()
        subscribe(viewModel)

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { settings.clearSpotifyTokens() }
    }

    @Test
    fun `play delegates to the playback connection`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        every { playback.playTrack(any()) } returns Unit
        val track = libraryTrack("t1")
        val viewModel = createViewModel()
        subscribe(viewModel)

        viewModel.play(track)

        verify(exactly = 1) { playback.playTrack(track) }
    }

    @Test
    fun `playAll queues every downloaded track`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { catalog.myPlaylists() } returns Result.success(emptyList())
        every { playback.setQueue(any(), any()) } returns Unit
        val tracks = listOf(libraryTrack("t1"), libraryTrack("t2"))
        val viewModel = createViewModel(libraryTracks = tracks)
        subscribe(viewModel)
        advanceUntilIdle()

        viewModel.playAll()

        verify(exactly = 1) { playback.setQueue(listOf(libraryTrack("t1"), libraryTrack("t2")), 0) }
    }

    private fun TestScope.subscribe(viewModel: LibraryViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

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

    private fun summary(id: String) = PlaylistSummary(
        id = id,
        name = "Playlist $id",
        ownerName = "Owner",
        artworkUrl = null,
        trackCount = 2,
    )
}
