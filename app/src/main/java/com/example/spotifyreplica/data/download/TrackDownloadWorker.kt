package com.example.spotifyreplica.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

/**
 * Downloads one track in the background.
 *
 * The worker owns all Room status transitions (`RUNNING` → `COMPLETED`/`FAILED`) and re-emits
 * progress both to the download queue (Room) and to WorkManager (`setProgress`), while
 * [TrackDownloaderCore] owns the actual bytes. Unique work is created by [DownloadQueue] with
 * `ExistingWorkPolicy.KEEP`, so a track is never downloaded twice concurrently.
 *
 * The foreground service is an optimization only: on Android 12+ the OS can refuse to start one
 * (`ForegroundServiceStartNotAllowedException`), and Android 15 caps `dataSync` to 6h/24h. We
 * therefore enter the foreground "best-effort" and keep downloading regardless.
 */
@HiltWorker
class TrackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val core: TrackDownloaderCore,
    private val libraryRepository: LibraryRepository,
    private val notifications: Notifications,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val spotifyId = inputData.getString(KEY_SPOTIFY_ID) ?: return Result.failure()
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: defaultDownloadId(spotifyId)
        val playlistId = inputData.getString(KEY_PLAYLIST_ID)?.takeIf { it.isNotBlank() }
        val track = inputDataToTrack(inputData) ?: return Result.failure()

        libraryRepository.updateDownloadStatus(downloadId, DownloadStatus.RUNNING)

        val title = track.title.ifBlank { spotifyId }
        enterForeground(title, bytesWritten = 0L, totalBytes = null)

        val outcome = core.download(track, playlistId) { bytesWritten, totalBytes ->
            libraryRepository.updateDownloadProgress(downloadId, bytesWritten, totalBytes)
            setProgress(
                workDataOf(
                    KEY_PROGRESS_BYTES to bytesWritten,
                    KEY_PROGRESS_TOTAL to (totalBytes ?: UNKNOWN_TOTAL),
                )
            )
            enterForeground(title, bytesWritten, totalBytes)
        }

        return when (outcome) {
            is TrackOutcome.Success -> {
                libraryRepository.updateDownloadProgress(downloadId, outcome.sizeBytes, outcome.sizeBytes)
                libraryRepository.updateDownloadStatus(downloadId, DownloadStatus.COMPLETED)
                Result.success()
            }

            TrackOutcome.NoSource -> {
                libraryRepository.updateDownloadStatus(
                    downloadId,
                    DownloadStatus.FAILED,
                    error = "No audio source found for this track"
                )
                Result.failure()
            }

            is TrackOutcome.Permanent -> {
                libraryRepository.updateDownloadStatus(downloadId, DownloadStatus.FAILED, error = outcome.error)
                Result.failure()
            }

            is TrackOutcome.Retryable -> {
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    libraryRepository.updateDownloadStatus(
                        downloadId,
                        DownloadStatus.FAILED,
                        error = "${outcome.error} (gave up after $MAX_ATTEMPTS attempts)"
                    )
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }

    /**
     * Best-effort foreground promotion. Any failure (foreground-start not allowed, missing service
     * type on API 34+, exhausted dataSync budget on Android 15+) is swallowed: the download must
     * still proceed in the background.
     */
    private suspend fun enterForeground(title: String, bytesWritten: Long, totalBytes: Long?) {
        runCatching {
            val notification = notifications.downloadProgressNotification(title, bytesWritten, totalBytes)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    Notifications.DOWNLOAD_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(Notifications.DOWNLOAD_NOTIFICATION_ID, notification)
            }
            setForeground(info)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val UNKNOWN_TOTAL = -1L
    }
}

// --- Input/output data keys + mapping (kept free of Worker state so they are unit-testable) ----

internal const val KEY_SPOTIFY_ID = "spotifyId"
internal const val KEY_TITLE = "title"
internal const val KEY_ARTISTS = "artists"
internal const val KEY_ALBUM = "album"
internal const val KEY_ARTWORK_URL = "artworkUrl"
internal const val KEY_DURATION_MS = "durationMs"
internal const val KEY_ISRC = "isrc"
internal const val KEY_SPOTIFY_URI = "spotifyUri"
internal const val KEY_PLAYLIST_ID = "playlistId"
internal const val KEY_DOWNLOAD_ID = "downloadId"

internal const val KEY_PROGRESS_BYTES = "progressBytes"
internal const val KEY_PROGRESS_TOTAL = "progressTotal"

/** Stable unique-work name and deterministic queue-row id for a track. */
internal fun uniqueDownloadWorkName(spotifyId: String): String = "download_$spotifyId"

/** Default queue-row id (matches the unique-work name so cancel/retry map cleanly). */
internal fun defaultDownloadId(spotifyId: String): String = uniqueDownloadWorkName(spotifyId)

/** Serializes a [Track] into worker input data (artist list uses a unit-separator delimiter). */
internal fun Track.toDownloadInputData(playlistId: String?, downloadId: String): Data =
    workDataOf(
        KEY_SPOTIFY_ID to id,
        KEY_TITLE to title,
        KEY_ARTISTS to artists.joinToString(ARTIST_DELIMITER),
        KEY_ALBUM to album,
        KEY_ARTWORK_URL to artworkUrl,
        KEY_DURATION_MS to durationMs,
        KEY_ISRC to isrc,
        KEY_SPOTIFY_URI to spotifyUri,
        KEY_PLAYLIST_ID to playlistId,
        KEY_DOWNLOAD_ID to downloadId,
    )

/** Rebuilds the [Track] previously written by [toDownloadInputData]; null when the id is missing. */
internal fun inputDataToTrack(data: Data): Track? {
    val id = data.getString(KEY_SPOTIFY_ID) ?: return null
    val title = data.getString(KEY_TITLE) ?: return null
    val artists = data.getString(KEY_ARTISTS)
        ?.split(ARTIST_DELIMITER)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    return Track(
        id = id,
        title = title,
        artists = artists,
        album = data.getString(KEY_ALBUM).orEmpty(),
        artworkUrl = data.getString(KEY_ARTWORK_URL),
        durationMs = data.getLong(KEY_DURATION_MS, 0L),
        isrc = data.getString(KEY_ISRC),
        spotifyUri = data.getString(KEY_SPOTIFY_URI),
    )
}

/** Unit-separator is illegal in Spotify track/artist names, so it is a safe list delimiter. */
private const val ARTIST_DELIMITER = "\u001F"
