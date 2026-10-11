package com.example.spotifyreplica.domain.model

/** A track that has been downloaded to local storage and is playable offline. */
data class LibraryTrack(
    val spotifyId: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val artworkUrl: String?,
    val localPath: String,
    val sizeBytes: Long?,
    val durationMs: Long,
    val container: String,
    val bitrateKbps: Int?
)
