package com.example.spotifyreplica.data.remote.spotify

import com.squareup.moshi.Json

/**
 * Moshi DTOs for the Spotify Web API, aligned with the **Feb/March 2026 dev-mode migration**:
 *
 * - The playlist contents endpoint is `GET /playlists/{id}/items` and the playlist field is
 *   **`items`** (`items.items[].item`) — `/tracks` and a `tracks` field are gone.
 * - `GET /search?type=track` caps `limit` at **10**.
 * - `popularity`/`available_markets` were removed; `external_ids.isrc` is still present.
 * - A playlist's `items` object may be **ABSENT** for playlists the user neither owns nor
 *   collaborates on, so every collection field is optional/defaulted and unknown keys are ignored.
 *
 * Every field has a default so Moshi's reflective adapter can tolerate **missing** keys. Note that a
 * default does **not** protect against an explicit JSON `null`: Moshi's reflective `KotlinJsonAdapter`
 * only falls back to a default when the key is absent, and throws `JsonDataException` when a key is
 * present with `null`. Spotify sends `"images": null` for some playlists (and `"id": null` for local
 * files), so every field Spotify may null out is declared **nullable** and coalesced in mapping.
 */

/** `POST https://accounts.spotify.com/api/token` (PKCE: no client secret). */
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String? = null,
    val scope: String? = null,
    @Json(name = "expires_in") val expiresIn: Long = 0L,
    @Json(name = "refresh_token") val refreshToken: String? = null,
)

/** `GET /me`. `country`/`email`/`product`/`followers` are removed in dev mode. */
data class SpotifyUserDto(
    val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
)

data class SpotifyImageDto(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

data class SpotifyArtistDto(
    val id: String? = null,
    val name: String = "",
)

data class SpotifyAlbumDto(
    val id: String? = null,
    val name: String = "",
    /** Nullable: Spotify may send `"images": null`, which Moshi treats as an error for a non-null list. */
    val images: List<SpotifyImageDto>? = null,
)

data class SpotifyExternalIdsDto(
    val isrc: String? = null,
)

/** A Spotify track object. `popularity`/`available_markets` are never read. */
data class SpotifyTrackDto(
    /** Nullable: local files (`is_local: true`) carry `"id": null`. */
    val id: String? = null,
    val name: String = "",
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @Json(name = "duration_ms") val durationMs: Long = 0L,
    @Json(name = "external_ids") val externalIds: SpotifyExternalIdsDto? = null,
    val uri: String? = null,
    @Json(name = "is_local") val isLocal: Boolean = false,
)

/** One element of a playlist's `items` array; `item` is null for local files / episodes. */
data class PlaylistItemDto(
    val item: SpotifyTrackDto? = null,
)

/**
 * The paging object returned by `GET /playlists/{id}/items`. The array field is `items` and may be
 * absent entirely for playlists the user cannot read.
 */
data class PlaylistItemsPage(
    val items: List<PlaylistItemDto> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val next: String? = null,
)

/** A Spotify playlist object. In 2026 the contents summary field is `items` (`tracks` is gone). */
data class SpotifyPlaylistDto(
    val id: String = "",
    val name: String = "",
    val owner: SpotifyOwnerDto? = null,
    /** Nullable: Spotify returns `"images": null` for some (e.g. empty/system) playlists. */
    val images: List<SpotifyImageDto>? = null,
    val items: PlaylistItemsPage? = null,
)

data class SpotifyOwnerDto(
    val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
)

/** `GET /me/playlists` paging envelope. */
data class PlaylistsPage(
    val items: List<SpotifyPlaylistDto> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val next: String? = null,
)

/** `GET /search?type=track` paging envelope for the nested `tracks` object. */
data class SearchTracksPage(
    val items: List<SpotifyTrackDto> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val next: String? = null,
)

/** `GET /search` response. Other keys (`albums`, `artists`, ...) are ignored. */
data class SearchResponse(
    val tracks: SearchTracksPage? = null,
)
