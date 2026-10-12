package com.example.spotifyreplica.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.playback.PlaybackConnection
import com.example.spotifyreplica.playback.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the app-wide playback state and transport controls to the player UI. The underlying
 * [PlaybackConnection] is a singleton, so the mini player and the full player screen observe the
 * exact same session.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnection: PlaybackConnection,
    libraryRepository: LibraryRepository,
) : ViewModel() {

    /** Live playback state (current track, position, queue, shuffle/repeat). */
    val state: StateFlow<PlayerState> = playbackConnection.state

    /** The downloaded library, used to start playback from the player screen. */
    val library: StateFlow<List<LibraryTrack>> = libraryRepository.observeLibraryTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    init {
        playbackConnection.connect()
    }

    /** Plays [track] on its own. */
    fun playTrack(track: LibraryTrack) = playbackConnection.playTrack(track)

    /** Replaces the queue with [tracks] and starts at [startIndex]. */
    fun playQueue(tracks: List<LibraryTrack>, startIndex: Int = 0) =
        playbackConnection.setQueue(tracks, startIndex)

    fun togglePlayPause() = playbackConnection.togglePlayPause()

    fun next() = playbackConnection.next()

    fun previous() = playbackConnection.previous()

    fun seekTo(positionMs: Long) = playbackConnection.seekTo(positionMs)

    /** Jumps [PlayerState.SEEK_STEP_MS] back from the current position, clamped at 0. */
    fun rewind() = playbackConnection.seekBy(-PlayerState.SEEK_STEP_MS)

    /** Jumps [PlayerState.SEEK_STEP_MS] ahead from the current position, clamped at the duration. */
    fun fastForward() = playbackConnection.seekBy(PlayerState.SEEK_STEP_MS)

    /** Flips shuffle on/off. */
    fun toggleShuffle() = playbackConnection.setShuffle(!state.value.shuffleEnabled)

    /** Cycles repeat OFF -> ALL -> ONE -> OFF. */
    fun cycleRepeatMode() {
        val next = when (state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playbackConnection.setRepeatMode(next)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
