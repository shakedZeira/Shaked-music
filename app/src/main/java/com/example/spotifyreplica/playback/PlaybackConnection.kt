package com.example.spotifyreplica.playback

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single app-wide bridge between Compose and the [PlaybackService].
 *
 * The controller is built lazily the first time [connect] is called and talks to the session over
 * the standard Media3 [SessionToken] handshake. Every mutating call is marshalled onto the main
 * thread (where the controller lives) and mirrored into a [StateFlow] so any number of screens and
 * the mini player can observe the same playback state.
 *
 * If a command arrives before the controller has finished connecting it is buffered and replayed
 * as soon as the session is ready, so early taps from the UI are never dropped.
 */
class PlaybackConnection(
    private val context: Context,
    private val sessionToken: SessionToken,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Maps a Media3 `mediaId` (the Spotify id) back to its rich library model. */
    private val trackRegistry = LinkedHashMap<String, LibraryTrack>()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var positionJob: Job? = null

    private val mainExecutor: Executor
        get() = ContextCompat.getMainExecutor(context)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    /** Connects to the playback service. Safe to call multiple times. */
    fun connect() {
        scope.launch { connectInternal() }
    }

    /** Replaces the queue and starts playing at [startIndex]. */
    fun setQueue(tracks: List<LibraryTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackRegistry[it.spotifyId] = it }
        val items = tracks.map(MediaItemMapper::toMediaItem)
        val index = startIndex.coerceIn(0, items.lastIndex)
        withController { controller ->
            controller.setMediaItems(items, index, /* startPositionMs = */ 0L)
            controller.prepare()
            controller.play()
        }
    }

    /** Plays a single library track. */
    fun playTrack(track: LibraryTrack) {
        trackRegistry[track.spotifyId] = track
        val item = MediaItemMapper.toMediaItem(track)
        withController { controller ->
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        }
    }

    /** Toggles play/pause based on the current player state. */
    fun togglePlayPause() = withController { controller ->
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun play() = withController { it.play() }

    fun pause() = withController { it.pause() }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPreviousMediaItem() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    fun setRepeatMode(mode: Int) = withController { it.repeatMode = mode }

    /** Releases the controller and stops the position ticker (used on process teardown/tests). */
    fun release() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    private fun connectInternal() {
        if (controller != null || controllerFuture != null) return
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                this.controller = controller
                controller.addListener(listener)
                sync(controller)
                startPositionUpdates()
                pendingAction?.let { action ->
                    pendingAction = null
                    action(controller)
                    sync(controller)
                }
            } catch (t: Throwable) {
                controllerFuture = null
            }
        }, mainExecutor)
    }

    /** Runs [action] on the main thread, queueing it until the controller is connected. */
    private fun withController(action: (MediaController) -> Unit) {
        scope.launch {
            val controller = controller
            if (controller != null) {
                action(controller)
                sync(controller)
            } else {
                pendingAction = action
                connectInternal()
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val controller = controller ?: break
                sync(controller)
                delay(POSITION_POLL_MS)
            }
        }
    }

    /** Projects the player's current state into an immutable [PlayerState]. */
    private fun sync(player: Player) {
        val currentItem = player.currentMediaItem
        val track = currentItem?.mediaId?.let { trackRegistry[it] }
        val queue = buildList {
            for (i in 0 until player.mediaItemCount) {
                trackRegistry[player.getMediaItemAt(i).mediaId]?.let(::add)
            }
        }
        val duration = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: track?.durationMs
            ?: 0L
        _state.value = PlayerState(
            currentTrack = track,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            queue = queue,
            currentIndex = if (track == null) -1 else player.currentMediaItemIndex,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
