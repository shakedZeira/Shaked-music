package com.example.spotifyreplica.domain.model

/**
 * A Spotify playlist as shown in the library. [trackCount] comes from the playlist's `items.total`
 * (the field renamed from `tracks` in the Feb 2026 migration) and is 0 when the API does not expose
 * the contents for playlists the user neither owns nor collaborates on.
 */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val ownerName: String,
    val artworkUrl: String?,
    val trackCount: Int,
)
