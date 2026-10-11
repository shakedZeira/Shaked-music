package com.example.spotifyreplica.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.spotifyreplica.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the low-importance notifications the download engine shows while a track is being
 * written. The channel is created lazily; on API 33+ the user may still block the notification,
 * which is harmless — the foreground service is only an optimization, not a requirement.
 */
@Singleton
class Notifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Creates the downloads channel if it does not exist yet. Safe to call repeatedly. */
    fun ensureDownloadChannel() {
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * A determinate (or indeterminate, when [totalBytes] is unknown) download-progress notification
     * suitable for [androidx.work.ForegroundInfo].
     */
    fun downloadProgressNotification(
        title: String,
        bytesWritten: Long,
        totalBytes: Long?,
    ): Notification {
        ensureDownloadChannel()
        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (totalBytes != null && totalBytes > 0L) {
            val progress = ((bytesWritten * MAX_PROGRESS) / totalBytes).toInt()
            builder.setProgress(MAX_PROGRESS, progress.coerceIn(0, MAX_PROGRESS), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "downloads"

        /** Fixed notification id for the single active download foreground notification. */
        const val DOWNLOAD_NOTIFICATION_ID = 0x5D0

        private const val CHANNEL_NAME = "Downloads"
        private const val CHANNEL_DESCRIPTION = "Shows progress while audio downloads"
        private const val MAX_PROGRESS = 100
    }
}
