package com.example.spotifyreplica.domain.model

/**
 * A Spotify track, mapped into a clean domain type so DTOs never leak to the UI.
 *
 * [isrc] and [spotifyUri] are optional because the 2026 Web API omits `external_ids`/`uri` for
 * some track objects, and Removed-In-Dev-Mode fields such as `popularity` are never read.
 */
data class Track(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val isrc: String?,
    val spotifyUri: String?,
)
