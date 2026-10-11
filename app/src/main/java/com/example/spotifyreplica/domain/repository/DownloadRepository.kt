package com.example.spotifyreplica.domain.repository

import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The download queue, as seen by the UI and use cases.
 *
 * Implementations delegate to `DownloadQueue` (WorkManager) and the offline
 * [LibraryRepository]; the queue itself is observed from Room so progress survives process death.
 */
interface DownloadRepository {

    /** The download queue, newest activity first (Room-backed). */
    fun observeQueue(): Flow<List<DownloadItem>>

    /** Enqueues (or keeps an existing) download for [track]; returns the WorkRequest id. */
    suspend fun enqueue(track: Track, playlistId: String?): UUID

    /** Enqueues every track of [playlist]; returns the WorkRequest ids in order. */
    suspend fun enqueuePlaylist(playlist: PlaylistSummary, tracks: List<Track>): List<UUID>

    /** Cancels the unique work for [spotifyId] and removes its queue row. */
    suspend fun cancel(spotifyId: String)

    /** Re-enqueues a failed/cancelled [item] using its cached track metadata. */
    suspend fun retry(item: DownloadItem)
}
