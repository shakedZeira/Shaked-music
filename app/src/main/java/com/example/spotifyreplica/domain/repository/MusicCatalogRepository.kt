package com.example.spotifyreplica.domain.repository

import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track

/**
 * Read-only Spotify catalog access (playlists + tracks). Mirrors the interface locked in the plan's
 * §7. Every call is wrapped in [Result] so HTTP/IO failures surface as readable messages instead of
 * crashing the UI, and a fresh access token is ensured before each request.
 *
 * [searchTracks]'s [limit] is coerced to the 2026 hard cap of 10 by the implementation.
 */
interface MusicCatalogRepository {
    suspend fun myPlaylists(): Result<List<PlaylistSummary>>
    suspend fun playlistTracks(playlistId: String, offset: Int, limit: Int): Result<Page<Track>>
    suspend fun searchTracks(query: String, offset: Int, limit: Int): Result<Page<Track>>
}
