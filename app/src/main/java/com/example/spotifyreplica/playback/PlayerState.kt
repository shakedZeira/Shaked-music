package com.example.spotifyreplica.playback

import com.example.spotifyreplica.domain.model.LibraryTrack

/**
 * Immutable snapshot of the media session's playback state, exposed to Compose as a
 * `StateFlow<PlayerState>`.
 *
 * This is a deliberately *pure* value type (no Media3/Android dependencies) so its derived helpers
 * can be unit-tested on a plain JVM. [repeatMode] values mirror
 * `androidx.media3.common.Player.REPEAT_MODE_*`.
 */
data class PlayerState(
    /** The track currently loaded in the player, or null when nothing has been queued. */
    val currentTrack: LibraryTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** The current queue in playback order (reflecting shuffle), if known. */
    val queue: List<LibraryTrack> = emptyList(),
    /** Index of [currentTrack] within [queue], or -1 when unknown. */
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = REPEAT_MODE_OFF,
) {

    /** True when something is loaded and can be played. */
    val hasMedia: Boolean
        get() = currentTrack != null

    /** Whether a "next" action is meaningful given the queue and repeat mode. */
    val hasNext: Boolean
        get() = when {
            currentIndex < 0 -> false
            repeatMode == REPEAT_MODE_ALL -> queue.isNotEmpty()
            else -> currentIndex < queue.lastIndex
        }

    /** Whether a "previous" action is meaningful given the queue and repeat mode. */
    val hasPrevious: Boolean
        get() = when {
            currentIndex < 0 -> false
            repeatMode == REPEAT_MODE_ALL -> queue.isNotEmpty()
            else -> currentIndex > 0
        }

    /** Playback progress in `[0, 1]`, or 0 when the duration is unknown. */
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

    companion object {
        /** Matches `androidx.media3.common.Player.REPEAT_MODE_OFF`. */
        const val REPEAT_MODE_OFF = 0

        /** Matches `androidx.media3.common.Player.REPEAT_MODE_ONE`. */
        const val REPEAT_MODE_ONE = 1

        /** Matches `androidx.media3.common.Player.REPEAT_MODE_ALL`. */
        const val REPEAT_MODE_ALL = 2
    }
}
