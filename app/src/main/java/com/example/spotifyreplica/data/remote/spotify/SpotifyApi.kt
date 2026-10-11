package com.example.spotifyreplica.data.remote.spotify

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API (2026 dev mode). Base URL is `BuildConfig.BASE_URL_SPOTIFY`.
 *
 * Removed-in-dev-mode endpoints (batch `GET /tracks?ids=`, `/albums?ids=`, `/artists?ids=`,
 * browse, artist top-tracks) are deliberately absent.
 */
interface SpotifyApi {

    /** Signed-in user profile (used only for the display name). */
    @GET("me")
    suspend fun me(): SpotifyUserDto

    /** Paged list of the user's playlists. */
    @GET("me/playlists")
    suspend fun myPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PlaylistsPage

    /**
     * Paged playlist contents. NOTE: the 2026 endpoint is **`items`**, not `tracks`, and the
     * response field is also `items` (`items.items[].item`).
     */
    @GET("playlists/{id}/items")
    suspend fun playlistItems(
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PlaylistItemsPage

    /** Track search. Callers must already have coerced [limit] to the hard cap of 10. */
    @GET("search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
    ): SearchResponse
}
