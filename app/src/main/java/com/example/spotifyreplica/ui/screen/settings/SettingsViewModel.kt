package com.example.spotifyreplica.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.spotifyreplica.data.download.StoragePaths
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the settings screen renders.
 *
 * [loggedIn] / [loginMessage] / [audioSource] come straight from [SettingsManager]; the storage
 * figures are derived from the Room-backed downloaded library, so they stay live as downloads
 * complete or are cleared.
 */
data class SettingsUiState(
    val loggedIn: Boolean = false,
    val loginMessage: String? = null,
    val audioSource: String = SettingsManager.AUDIO_SOURCE_LOCAL,
    val downloadedTrackCount: Int = 0,
    val downloadedBytes: Long = 0L,
    val clearing: Boolean = false,
)

/**
 * Drives the settings screen: account state + logout, the audio-source selector, storage accounting
 * and "clear downloads".
 *
 * Clearing downloads cancels every queued/running [WorkManager] job, deletes the on-disk music tree
 * (via [StoragePaths]) and empties the Room library, so files, rows and work are all removed
 * together.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val libraryRepository: LibraryRepository,
    private val storagePaths: StoragePaths,
    private val workManager: WorkManager,
) : ViewModel() {

    private val clearing = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.spotifyLoggedIn,
        settingsManager.spotifyLoginMessage,
        settingsManager.audioSource,
        libraryRepository.observeLibraryTracks(),
        clearing,
    ) { loggedIn, loginMessage, audioSource, tracks, isClearing ->
        SettingsUiState(
            loggedIn = loggedIn,
            loginMessage = loginMessage,
            audioSource = audioSource,
            downloadedTrackCount = tracks.size,
            downloadedBytes = tracks.sumOf { it.sizeBytes ?: 0L },
            clearing = isClearing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /** Persists the chosen audio source (`"local"` or `"youtube"`). */
    fun setAudioSource(source: String) {
        viewModelScope.launch { settingsManager.setAudioSource(source) }
    }

    /** Clears the stored Spotify tokens and flips the logged-in flag off. */
    fun logout() {
        viewModelScope.launch {
            settingsManager.clearSpotifyTokens()
            settingsManager.setSpotifyLoggedIn(false)
        }
    }

    /**
     * Cancels all pending work, deletes every downloaded file and empties the local library.
     * Guards against re-entry while a clear is already in flight.
     */
    fun clearDownloads() {
        if (clearing.value) return
        viewModelScope.launch {
            clearing.value = true
            try {
                workManager.cancelAllWork()
                storagePaths.musicRoot().deleteRecursively()
                libraryRepository.clearAll()
            } finally {
                clearing.value = false
            }
        }
    }

    /** Dismisses the last login message. */
    fun clearLoginMessage() {
        viewModelScope.launch { settingsManager.clearSpotifyLoginMessage() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
