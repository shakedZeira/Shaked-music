package com.example.spotifyreplica.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.usecase.SearchTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the search screen renders.
 *
 * [downloadsByTrackId] maps a Spotify track id to the status of its download-queue entry (absent
 * when the track was never downloaded), so a row can show queued/downloading/downloaded/failed
 * without the screen having to know about Room.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    val downloadsByTrackId: Map<String, DownloadStatus> = emptyMap(),
)

/**
 * Drives Spotify search + add-to-downloads.
 *
 * Typing debounces (see [DEBOUNCE_MS]) so a burst of keystrokes produces a single `/search` call.
 * Only the response for the most recent query is applied, so a slow earlier request can never
 * overwrite newer results. Download state is pushed from [DownloadRepository], which is backed by
 * Room, so progress keeps updating even after the worker moves the row through its lifecycle.
 *
 * Playback is intentionally absent here — Phase 6 owns it.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTracks: SearchTracksUseCase,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    /** Field that changes on every keystroke but does not trigger I/O on its own. */
    private val content = MutableStateFlow(SearchContent())

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    /** The query the currently displayed results belong to; used to drop stale responses. */
    private var activeQuery: String = ""

    private val downloadsByTrackId: StateFlow<Map<String, DownloadStatus>> =
        downloadRepository.observeQueue()
            .map { items -> items.associate { it.spotifyId to it.status } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyMap(),
            )

    val uiState: StateFlow<SearchUiState> = combine(content, downloadsByTrackId) { content, downloads ->
        SearchUiState(
            query = content.query,
            results = content.results,
            isLoading = content.isLoading,
            isLoadingMore = content.isLoadingMore,
            error = content.error,
            endReached = content.endReached,
            downloadsByTrackId = downloads,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SearchUiState(),
    )

    /** Called on every keystroke; debounces into a fresh search from offset 0. */
    fun onQueryChange(query: String) {
        content.update { it.copy(query = query) }
        searchJob?.cancel()
        loadMoreJob?.cancel()

        if (query.isBlank()) {
            activeQuery = ""
            content.update { it.copy(results = emptyList(), error = null, endReached = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            performSearch(query.trim())
        }
    }

    /** Runs a search immediately (keyboard "search" action, or retry after an error). */
    fun search() {
        val query = content.value.query.trim()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (query.isEmpty()) {
            activeQuery = ""
            content.update {
                it.copy(results = emptyList(), isLoading = false, isLoadingMore = false, error = null, endReached = false)
            }
            return
        }
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    /** Appends the next page when the list nears its end and no request is in flight. */
    fun loadMore() {
        val state = content.value
        if (state.isLoading || state.isLoadingMore || state.endReached) return
        val query = activeQuery
        if (query.isEmpty()) return
        loadMoreJob = viewModelScope.launch {
            content.update { it.copy(isLoadingMore = true, error = null) }
            val offset = content.value.results.size
            searchTracks(query, offset)
                .onSuccess { page ->
                    if (activeQuery != query) return@onSuccess
                    content.update { current ->
                        val merged = (current.results + page.items).distinctBy { it.id }
                        current.copy(
                            results = merged,
                            isLoadingMore = false,
                            endReached = !page.hasMore || page.items.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (activeQuery != query) return@onFailure
                    content.update { it.copy(isLoadingMore = false, error = error.readableMessage()) }
                }
        }
    }

    /** Enqueues [track] onto the shared download queue (search downloads belong to no playlist). */
    fun onDownload(track: Track) {
        viewModelScope.launch { downloadRepository.enqueue(track, playlistId = null) }
    }

    /** Retries/settles a cancelled search error by re-running the current query. */
    fun retry() {
        search()
    }

    private suspend fun performSearch(query: String) {
        activeQuery = query
        content.update {
            it.copy(results = emptyList(), isLoading = true, isLoadingMore = false, error = null, endReached = false)
        }
        searchTracks(query, offset = 0)
            .onSuccess { page ->
                if (activeQuery != query) return@onSuccess
                content.update {
                    it.copy(
                        results = page.items,
                        isLoading = false,
                        error = null,
                        endReached = !page.hasMore,
                    )
                }
            }
            .onFailure { error ->
                if (activeQuery != query) return@onFailure
                content.update {
                    it.copy(isLoading = false, error = error.readableMessage())
                }
            }
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Search failed"

    /** Mutable, non-observed slice of the screen state. */
    private data class SearchContent(
        val query: String = "",
        val results: List<Track> = emptyList(),
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val endReached: Boolean = false,
    )

    companion object {
        /** Keystroke settling window before a `/search` call is issued. */
        const val DEBOUNCE_MS = 300L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
