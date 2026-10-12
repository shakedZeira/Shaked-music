package com.example.spotifyreplica.ui.screen.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import com.example.spotifyreplica.domain.usecase.DownloadPlaylistUseCase
import com.example.spotifyreplica.playback.PlaybackConnection
import com.example.spotifyreplica.ui.navigation.PLAYLIST_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the playlist screen renders.
 *
 * [statusByTrackId] is the live download-queue state per track; [downloadedIds] /
 * [downloadedTracksByTrackId] come from Room and tell which tracks already exist on disk (and how to
 * play them offline).
 */
data class PlaylistUiState(
    val playlistId: String = "",
    val name: String = "Playlist",
    val ownerName: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val statusByTrackId: Map<String, DownloadStatus> = emptyMap(),
    val downloadedIds: Set<String> = emptySet(),
    val downloadedTracksByTrackId: Map<String, LibraryTrack> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
) {
    /** True when at least one loaded track is already on disk. */
    val hasDownloads: Boolean
        get() = downloadedIds.isNotEmpty()
}

/**
 * Drives a single Spotify playlist: paged track loading (`offset` steps of [PAGE_SIZE]), per-track
 * download state joined from Room, and "download all".
 *
 * The playlist id is read from the nav argument [PLAYLIST_ID_ARG] in [SavedStateHandle], so
 * `AppNavHost` only needs to pass an `onBack` lambda. Header metadata (name/owner/artwork/count)
 * comes from the Room playlist cache, which the library screen keeps in sync.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: MusicCatalogRepository,
    private val downloads: DownloadRepository,
    private val library: LibraryRepository,
    private val downloadPlaylist: DownloadPlaylistUseCase,
    private val playback: PlaybackConnection,
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>(PLAYLIST_ID_ARG).orEmpty()

    private val content = MutableStateFlow(PlaylistContent())

    private val statuses: Flow<Map<String, DownloadStatus>> = downloads.observeQueue()
        .map { items -> items.associate { it.spotifyId to it.status } }

    private val header: Flow<PlaylistEntity?> = library.observePlaylists()
        .map { playlists -> playlists.firstOrNull { it.spotifyId == playlistId } }

    private val roomState: Flow<RoomState> = combine(
        statuses,
        library.observeLibraryTracks(),
        header,
    ) { statusByTrackId, downloadedTracks, cachedHeader ->
        RoomState(
            statusByTrackId = statusByTrackId,
            downloadedTracksByTrackId = downloadedTracks.associateBy { it.spotifyId },
            header = cachedHeader,
        )
    }

    val uiState: StateFlow<PlaylistUiState> = combine(content, roomState) { current, room ->
        PlaylistUiState(
            playlistId = playlistId,
            name = room.header?.name ?: DEFAULT_NAME,
            ownerName = room.header?.ownerName.orEmpty(),
            artworkUrl = room.header?.artworkUrl ?: current.tracks.firstOrNull()?.artworkUrl,
            trackCount = room.header?.trackCount ?: current.total,
            tracks = current.tracks,
            statusByTrackId = room.statusByTrackId,
            downloadedIds = room.downloadedTracksByTrackId.keys,
            downloadedTracksByTrackId = room.downloadedTracksByTrackId,
            isLoading = current.isLoading,
            isLoadingMore = current.isLoadingMore,
            error = current.error,
            endReached = current.endReached,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlaylistUiState(playlistId = playlistId),
    )

    init {
        loadPage(offset = 0)
    }

    /** Appends the next page when the list nears its end and no request is in flight. */
    fun loadMore() {
        val current = content.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        loadPage(offset = current.tracks.size)
    }

    /** Retries the failed page (first page when nothing loaded yet). */
    fun retry() {
        loadPage(offset = content.value.tracks.size)
    }

    /** Enqueues a single [track] onto the shared queue, associated with this playlist. */
    fun downloadTrack(track: Track) {
        viewModelScope.launch { downloads.enqueue(track, playlistId) }
    }

    /** Enqueues every loaded track of the playlist. */
    fun downloadAll() {
        val tracks = content.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch { downloadPlaylist(playlistId, tracks) }
    }

    /** Queues the loaded tracks that are already downloaded and starts playback. */
    fun playDownloaded() {
        val downloaded = uiState.value.tracks
            .mapNotNull { uiState.value.downloadedTracksByTrackId[it.id] }
        if (downloaded.isNotEmpty()) playback.setQueue(downloaded, startIndex = 0)
    }

    /** Starts playback of the playlist's downloaded tracks in random order. */
    fun playRandom() {
        val downloaded = uiState.value.tracks
            .mapNotNull { uiState.value.downloadedTracksByTrackId[it.id] }
        if (downloaded.isEmpty()) return
        playback.setQueue(downloaded.shuffled(), startIndex = 0)
    }

    /** Plays a single loaded [track] when it is already downloaded; otherwise a no-op. */
    fun play(track: Track) {
        val local = uiState.value.downloadedTracksByTrackId[track.id] ?: return
        playback.playTrack(local)
    }

    private fun loadPage(offset: Int) {
        viewModelScope.launch {
            val firstPage = offset == 0
            content.update {
                it.copy(
                    isLoading = firstPage,
                    isLoadingMore = !firstPage,
                    error = null,
                )
            }
            catalog.playlistTracks(playlistId, offset, PAGE_SIZE)
                .onSuccess { page ->
                    content.update { current ->
                        val merged = if (firstPage) {
                            page.items
                        } else {
                            (current.tracks + page.items).distinctBy { it.id }
                        }
                        current.copy(
                            tracks = merged,
                            total = page.total,
                            isLoading = false,
                            isLoadingMore = false,
                            endReached = !page.hasMore || page.items.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    content.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Could not load this playlist"

    private data class PlaylistContent(
        val tracks: List<Track> = emptyList(),
        val total: Int = 0,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val endReached: Boolean = false,
    )

    private data class RoomState(
        val statusByTrackId: Map<String, DownloadStatus>,
        val downloadedTracksByTrackId: Map<String, LibraryTrack>,
        val header: PlaylistEntity?,
    )

    companion object {
        /** 2026 paging limit used for `/playlists/{id}/items` requests. */
        const val PAGE_SIZE = 50

        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_NAME = "Playlist"
    }
}
