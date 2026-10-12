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

        /** Relative jump applied by the rewind/fast-forward transport buttons, in milliseconds. */
        const val SEEK_STEP_MS = 10_000L

        /**
         * Computes the absolute seek position for a relative [deltaMs] jump from [positionMs].
         *
         * Pure and side-effect free so the clamping rules can be unit-tested on a plain JVM: the
         * result is never negative, and when [durationMs] is greater than zero it is clamped into
         * `0..durationMs`. An unknown duration (`0`) only clamps the lower bound, so a forward jump
         * is not silently rewound to the start.
         *
         * @param positionMs current playback position; a negative value is treated as 0.
         * @param durationMs known track duration, or 0 when unknown.
         * @param deltaMs signed jump to apply (negative to rewind, positive to fast-forward).
         */
        fun seekTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
            val target = positionMs.coerceAtLeast(0L) + deltaMs
            return if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
        }
    }
}
