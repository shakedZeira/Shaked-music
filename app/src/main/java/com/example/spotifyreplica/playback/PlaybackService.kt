package com.example.spotifyreplica.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.spotifyreplica.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground playback service wrapping an [ExoPlayer] and a [MediaSession].
 *
 * Media3 owns the media notification and lock-screen controls: it enters the foreground (with
 * `foregroundServiceType="mediaPlayback"`) only while something is playing, and reuses the
 * `MediaSession` for hardware transport buttons. Audio focus and "becoming noisy" (headphone
 * unplug) are delegated to the player.
 *
 * Task-removal policy: if playback is in progress the service keeps playing in the background;
 * otherwise it stops itself so the process can be reclaimed. Playback is therefore not tied to the
 * activity lifecycle, which is what "background playback" requires.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            /* requestCode = */ 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()

        this.exoPlayer = exoPlayer
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = exoPlayer
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            // Nothing is playing: let the service die with the task.
            stopSelf()
        }
        // Otherwise keep playing in the background (documented behaviour).
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
