package com.example.spotifyreplica.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import com.example.spotifyreplica.domain.usecase.SyncPlaylistsUseCase
import com.example.spotifyreplica.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the library screen renders.
 *
 * [localPlaylists] is the Room-cached copy of [playlists] (available offline); [downloads] are the
 * tracks that can be played locally, and [statusByTrackId] maps a Spotify track id to its live
 * download-queue status (absent when the track was never enqueued).
 */
data class LibraryUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val localPlaylists: List<PlaylistEntity> = emptyList(),
    val downloads: List<LibraryTrack> = emptyList(),
    val statusByTrackId: Map<String, DownloadStatus> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

/**
 * Drives the library screen: the user's Spotify playlists, the local downloads, and the login CTA.
 *
 * Fetched playlist summaries and Room-backed flows are combined into a single [uiState]. The Room
 * flows (downloaded tracks + queue) keep updating after the screen has loaded, so download progress
 * and the "downloaded" checkmarks stay live without re-fetching anything.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val syncPlaylists: SyncPlaylistsUseCase,
    private val catalog: MusicCatalogRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val playback: PlaybackConnection,
    private val settings: SettingsManager,
) : ViewModel() {

    private val playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val fetchedState: Flow<FetchedState> =
        combine(playlists, isLoading, error) { cached, loading, message ->
            FetchedState(playlists = cached, isLoading = loading, error = message)
        }

    private val roomState: Flow<RoomState> = combine(
        library.observeLibraryTracks(),
        downloads.observeQueue(),
        library.observePlaylists(),
    ) { libraryTracks, queue, cachedPlaylists ->
        RoomState(
            downloads = libraryTracks,
            statusByTrackId = queue.associate { it.spotifyId to it.status },
            localPlaylists = cachedPlaylists,
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        fetchedState,
        roomState,
        settings.spotifyLoggedIn,
    ) { fetched, room, loggedIn ->
        LibraryUiState(
            playlists = fetched.playlists,
            localPlaylists = room.localPlaylists,
            downloads = room.downloads,
            statusByTrackId = room.statusByTrackId,
            isLoading = fetched.isLoading,
            error = fetched.error,
            loggedIn = loggedIn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LibraryUiState(),
    )

    init {
        refresh()
    }

    /**
     * Re-reads the user's playlists from Spotify, shows them immediately, and caches them in Room.
     *
     * The single round-trip both "loads" (display list) and "syncs" (Room write via
     * [SyncPlaylistsUseCase.cache]), so we never hit `/me/playlists` twice on one refresh.
     */
    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            catalog.myPlaylists()
                .onSuccess { fetched ->
                    playlists.value = fetched
                    runCatching { syncPlaylists.cache(fetched) }
                }
                .onFailure { error.value = it.readableMessage() }
            isLoading.value = false
        }
    }

    /** Clears the stored Spotify session so the login CTA reappears. */
    fun logout() {
        viewModelScope.launch {
            settings.clearSpotifyTokens()
            playlists.value = emptyList()
        }
    }

    /** Queues every downloaded track and starts playback from the top. */
    fun playAll() {
        val tracks = uiState.value.downloads
        if (tracks.isNotEmpty()) playback.setQueue(tracks, startIndex = 0)
    }

    /** Plays a single downloaded [track]. */
    fun play(track: LibraryTrack) {
        playback.playTrack(track)
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Could not load your library"

    private data class FetchedState(
        val playlists: List<PlaylistSummary>,
        val isLoading: Boolean,
        val error: String?,
    )

    private data class RoomState(
        val downloads: List<LibraryTrack>,
        val statusByTrackId: Map<String, DownloadStatus>,
        val localPlaylists: List<PlaylistEntity>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
