package com.example.spotifyreplica.ui.screen.settings

import androidx.work.Operation
import androidx.work.WorkManager
import com.example.spotifyreplica.data.download.StoragePaths
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.ui.screen.search.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Unit tests for [SettingsViewModel] — state mapping and the logout / clear-downloads actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings: SettingsManager = mockk()
    private val library: LibraryRepository = mockk()
    private val storagePaths: StoragePaths = mockk()
    private val workManager: WorkManager = mockk()

    private fun createViewModel(
        loggedIn: Boolean = false,
        loginMessage: String? = null,
        audioSource: String = SettingsManager.AUDIO_SOURCE_LOCAL,
        tracks: List<LibraryTrack> = emptyList(),
    ): SettingsViewModel {
        every { settings.spotifyLoggedIn } returns flowOf(loggedIn)
        every { settings.spotifyLoginMessage } returns flowOf(loginMessage)
        every { settings.audioSource } returns flowOf(audioSource)
        every { library.observeLibraryTracks() } returns flowOf(tracks)
        return SettingsViewModel(settings, library, storagePaths, workManager)
    }

    @Test
    fun `audio source is reflected from the settings flow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel(audioSource = SettingsManager.AUDIO_SOURCE_YOUTUBE)
            subscribe(viewModel)
            advanceUntilIdle()

            assertEquals(
                SettingsManager.AUDIO_SOURCE_YOUTUBE,
                viewModel.uiState.value.audioSource,
            )
        }

    @Test
    fun `logged-in state and login message are surfaced`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel(loggedIn = true, loginMessage = "Session expired")
            subscribe(viewModel)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loggedIn)
            assertEquals("Session expired", viewModel.uiState.value.loginMessage)
        }

    @Test
    fun `downloaded count and bytes map from the library flow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val tracks = listOf(
                libraryTrack("t1", 1024L),
                libraryTrack("t2", 2048L),
            )
            val viewModel = createViewModel(tracks = tracks)
            subscribe(viewModel)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.downloadedTrackCount)
            assertEquals(3072L, viewModel.uiState.value.downloadedBytes)
        }

    @Test
    fun `setAudioSource persists the selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { settings.setAudioSource(any()) } returns Unit
            val viewModel = createViewModel()

            viewModel.setAudioSource(SettingsManager.AUDIO_SOURCE_YOUTUBE)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                settings.setAudioSource(SettingsManager.AUDIO_SOURCE_YOUTUBE)
            }
        }

    @Test
    fun `logout clears tokens and flips the logged-in flag off`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { settings.clearSpotifyTokens() } returns Unit
            coEvery { settings.setSpotifyLoggedIn(any()) } returns Unit
            val viewModel = createViewModel(loggedIn = true)

            viewModel.logout()
            advanceUntilIdle()

            coVerify(exactly = 1) { settings.clearSpotifyTokens() }
            coVerify(exactly = 1) { settings.setSpotifyLoggedIn(false) }
        }

    @Test
    fun `clearDownloads cancels work, deletes the music tree and empties the library`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val musicRoot = java.nio.file.Files
                .createTempDirectory("spotify-settings-test")
                .toFile()
            java.io.File(musicRoot, "track.m4a").writeText("audio")

            every { storagePaths.musicRoot() } returns musicRoot
            every { workManager.cancelAllWork() } returns mockk(relaxed = true)
            coEvery { library.clearAll() } returns Unit

            val viewModel = createViewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            viewModel.clearDownloads()
            advanceUntilIdle()

            verify(exactly = 1) { workManager.cancelAllWork() }
            coVerify(exactly = 1) { library.clearAll() }
            assertTrue(!musicRoot.exists())
            assertTrue(!viewModel.uiState.value.clearing)
        }

    @Test
    fun `clearLoginMessage delegates to settings`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { settings.clearSpotifyLoginMessage() } returns Unit
            val viewModel = createViewModel()

            viewModel.clearLoginMessage()
            advanceUntilIdle()

            coVerify(exactly = 1) { settings.clearSpotifyLoginMessage() }
        }

    /** Keeps the `WhileSubscribed` state flow hot so `uiState.value` reflects emissions. */
    private fun TestScope.subscribe(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun libraryTrack(id: String, sizeBytes: Long) = LibraryTrack(
        spotifyId = id,
        title = "Title $id",
        artists = listOf("Artist"),
        album = "Album",
        artworkUrl = null,
        localPath = "/data/$id.m4a",
        sizeBytes = sizeBytes,
        durationMs = 180_000L,
        container = "m4a",
        bitrateKbps = 256,
    )
}
