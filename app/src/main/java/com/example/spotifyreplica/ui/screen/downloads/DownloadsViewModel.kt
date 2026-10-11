package com.example.spotifyreplica.ui.screen.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the download queue screen. */
data class DownloadsUiState(
    val items: List<DownloadItem> = emptyList(),
)

/**
 * Observes the Room-backed download queue and exposes cancel/retry actions. Progress arrives via
 * the database (written by the worker), so the screen keeps updating even if the app was killed
 * and restarted mid-download.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadRepository.observeQueue()
        .map { downloads -> DownloadsUiState(items = downloads) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DownloadsUiState()
        )

    /** Cancels the active download for [spotifyId] and removes its queue row. */
    fun cancel(spotifyId: String) {
        viewModelScope.launch { downloadRepository.cancel(spotifyId) }
    }

    /** Re-enqueues a failed/cancelled item. */
    fun retry(item: DownloadItem) {
        viewModelScope.launch { downloadRepository.retry(item) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
