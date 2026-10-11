package com.example.spotifyreplica.data.download

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.LibraryRepository
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues and cancels track downloads.
 *
 * One **unique** work per track (`download_<spotifyId>`, `ExistingWorkPolicy.KEEP`) guarantees a
 * track is never downloaded twice concurrently, and makes cancel/retry map cleanly. Each enqueue
 * also writes a `QUEUED` row to Room, so the downloads UI observes progress from the database
 * rather than polling `WorkInfo`.
 *
 * Work requires a connected network and backs off exponentially (10s, 20s, 40s, …) on transient
 * failures. The URL itself is resolved *inside* the worker, because resolved URLs expire.
 */
@Singleton
class DownloadQueue @Inject constructor(
    private val workManager: WorkManager,
    private val libraryRepository: LibraryRepository,
) {

    /** Enqueues (or keeps an existing) download for [track], returning the WorkRequest id. */
    suspend fun enqueue(track: Track, playlistId: String?): UUID {
        val downloadId = defaultDownloadId(track.id)
        libraryRepository.cacheTrack(track)
        libraryRepository.upsertDownload(
            DownloadItem(
                id = downloadId,
                spotifyId = track.id,
                playlistId = playlistId,
                title = track.title,
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = null,
                error = null
            )
        )

        val request = buildRequest(track, playlistId, downloadId)
        workManager.enqueueUniqueWork(uniqueDownloadWorkName(track.id), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    /** Enqueues every track of [playlist]; returns the WorkRequest ids in order. */
    suspend fun enqueuePlaylist(playlist: PlaylistSummary, tracks: List<Track>): List<UUID> =
        tracks.map { track -> enqueue(track, playlist.id) }

    /** Cancels the unique work for [spotifyId]; an in-flight `.part` is cleaned up by the worker. */
    fun cancel(spotifyId: String) {
        workManager.cancelUniqueWork(uniqueDownloadWorkName(spotifyId))
    }

    private fun buildRequest(track: Track, playlistId: String?, downloadId: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<TrackDownloadWorker>()
            .setInputData(track.toDownloadInputData(playlistId, downloadId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(DOWNLOAD_TAG)
            .build()

    companion object {
        /** Tag applied to every download work request. */
        const val DOWNLOAD_TAG = "download"

        private const val BACKOFF_SECONDS = 10L
    }
}
