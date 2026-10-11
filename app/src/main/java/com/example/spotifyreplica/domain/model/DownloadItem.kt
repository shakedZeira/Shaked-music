package com.example.spotifyreplica.domain.model

/**
 * Domain view of one entry in the download queue.
 *
 * [title] is best-effort: it is enriched from the cached [com.example.spotifyreplica.data.db.entity.TrackEntity]
 * when available and is null for tracks we have not cached yet.
 */
data class DownloadItem(
    val id: String,
    val spotifyId: String,
    val playlistId: String?,
    val title: String?,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val error: String?
)
