package com.example.spotifyreplica.domain.repository

import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import kotlinx.coroutines.flow.Flow

/**
 * Offline library: downloaded tracks, synced playlists, and the download queue.
 *
 * All `observe*` functions return cold [Flow]s backed by Room, so collectors are re-emitted whenever
 * the underlying tables change.
 */
interface LibraryRepository {

    /** The download queue, newest activity first. */
    fun observeDownloads(): Flow<List<DownloadItem>>

    /** Tracks that have finished downloading and can be played offline. */
    fun observeLibraryTracks(): Flow<List<LibraryTrack>>

    /** Cached playlists. */
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    /** Insert or replace a queue entry (e.g. when a download is enqueued). */
    suspend fun upsertDownload(item: DownloadItem)

    /** Update byte progress for an entry. */
    suspend fun updateDownloadProgress(id: String, bytesDownloaded: Long, totalBytes: Long?)

    /** Update the status (and optionally an error message) for an entry. */
    suspend fun updateDownloadStatus(id: String, status: DownloadStatus, error: String? = null)

    /** Remove a queue entry. */
    suspend fun removeDownload(id: String)

    /** Stamp a cached track as downloaded to [localPath]. */
    suspend fun markTrackDownloaded(
        spotifyId: String,
        localPath: String,
        sizeBytes: Long?,
        container: String,
        bitrateKbps: Int?,
        downloadedAtMs: Long
    )

    /** Remove every entry from the download queue. */
    suspend fun clearAll()
}
