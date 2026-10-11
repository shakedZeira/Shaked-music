package com.example.spotifyreplica.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single entry in the download queue. [status] stores a
 * `com.example.spotifyreplica.domain.model.DownloadStatus` name
 * (`QUEUED|RUNNING|COMPLETED|FAILED|CANCELLED`).
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val spotifyId: String,
    val playlistId: String?,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val error: String?,
    val updatedAtMs: Long
)
