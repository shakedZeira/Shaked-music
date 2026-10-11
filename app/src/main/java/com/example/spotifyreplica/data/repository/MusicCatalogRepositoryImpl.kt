package com.example.spotifyreplica.data.repository

import com.example.spotifyreplica.data.remote.spotify.PlaylistItemsPage
import com.example.spotifyreplica.data.remote.spotify.SearchResponse
import com.example.spotifyreplica.data.remote.spotify.SpotifyApi
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthManager
import com.example.spotifyreplica.data.remote.spotify.SpotifyImageDto
import com.example.spotifyreplica.data.remote.spotify.SpotifyPlaylistDto
import com.example.spotifyreplica.data.remote.spotify.SpotifyTrackDto
import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Spotify-backed catalog repository. A fresh access token is ensured before every call and all
 * transport failures are mapped to [Result.failure] with a readable message.
 *
 * Playlist contents that the user cannot read (HTTP 403, or an absent `items` array) degrade to an
 * empty page instead of a hard failure, per the 2026 dev-mode visibility rules.
 */
@Singleton
class MusicCatalogRepositoryImpl @Inject constructor(
    private val authManager: SpotifyAuthManager,
    private val api: SpotifyApi,
) : MusicCatalogRepository {

    override suspend fun myPlaylists(): Result<List<PlaylistSummary>> = runApi("load playlists") {
        val playlists = mutableListOf<PlaylistSummary>()
        var offset = 0
        // Follow paging so accounts with more than one page of playlists are fully listed.
        repeat(MAX_PLAYLIST_PAGES) {
            val page = api.myPlaylists(limit = PLAYLISTS_PAGE_SIZE, offset = offset)
            playlists += page.items.map { it.toSummary() }
            val fetched = offset + page.items.size
            if (page.items.isEmpty() || fetched >= page.total) return@runApi playlists
            offset = fetched
        }
        playlists
    }

    override suspend fun playlistTracks(
        playlistId: String,
        offset: Int,
        limit: Int,
    ): Result<Page<Track>> = runApi("load playlist tracks") {
        try {
            api.playlistItems(playlistId = playlistId, limit = limit, offset = offset).toTrackPage()
        } catch (e: HttpException) {
            // The contents of playlists the user does not own/collaborate on are not readable.
            if (e.code() == FORBIDDEN) emptyPage(offset, limit) else throw e
        }
    }

    override suspend fun searchTracks(
        query: String,
        offset: Int,
        limit: Int,
    ): Result<Page<Track>> = runApi("search tracks") {
        // 2026: GET /search hard-caps limit at 10 — enforce it here so callers cannot exceed it.
        val safeLimit = coerceSearchLimit(limit)
        api.searchTracks(query = query, limit = safeLimit, offset = offset)
            .toTrackPage(offset = offset, limit = safeLimit)
    }

    private suspend fun <T> runApi(action: String, block: suspend () -> T): Result<T> {
        if (!authManager.ensureFreshToken()) {
            return Result.failure(
                IllegalStateException("Not signed in to Spotify — connect an account first"),
            )
        }
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            Result.failure(catalogError(action, "Spotify error ${e.code()}", e))
        } catch (e: IOException) {
            Result.failure(catalogError(action, "Network error (${e.message ?: "unreachable"})", e))
        } catch (e: Exception) {
            Result.failure(catalogError(action, e.message ?: e::class.java.simpleName, e))
        }
    }

    private fun catalogError(action: String, detail: String, cause: Throwable): Exception =
        IllegalStateException("Failed to $action: $detail", cause)

    companion object {
        internal const val SEARCH_LIMIT_MAX = 10
        internal const val PLAYLISTS_PAGE_SIZE = 50
        internal const val MAX_PLAYLIST_PAGES = 20
        private const val FORBIDDEN = 403
    }
}

// ---------------------------------------------------------------------------------------------
// DTO -> domain mapping. Kept here (and `internal`) so unit tests can assert the 2026 JSON shapes
// without a network stack.
// ---------------------------------------------------------------------------------------------

/** Coerces a requested search page size into the 2026 hard cap of 1..[MusicCatalogRepositoryImpl.SEARCH_LIMIT_MAX]. */
internal fun coerceSearchLimit(limit: Int): Int = limit.coerceIn(1, MusicCatalogRepositoryImpl.SEARCH_LIMIT_MAX)

internal fun emptyPage(offset: Int, limit: Int): Page<Track> =
    Page(items = emptyList(), offset = offset, limit = limit, total = 0, hasMore = false)

internal fun SpotifyTrackDto.toDomain(): Track = Track(
    id = id,
    title = name,
    artists = artists.map { it.name }.filter { it.isNotBlank() },
    album = album?.name.orEmpty(),
    artworkUrl = album?.images?.bestArtworkUrl(),
    durationMs = durationMs,
    isrc = externalIds?.isrc,
    spotifyUri = uri,
)

internal fun SpotifyPlaylistDto.toSummary(): PlaylistSummary = PlaylistSummary(
    id = id,
    name = name,
    ownerName = owner?.displayName?.takeIf { it.isNotBlank() } ?: owner?.id.orEmpty(),
    artworkUrl = images.bestArtworkUrl(),
    trackCount = items?.total ?: 0,
)

/** Maps `GET /playlists/{id}/items` (2026 field name `items`) to a [Page] of [Track]. */
internal fun PlaylistItemsPage.toTrackPage(): Page<Track> = pageOf(
    mapped = items.mapNotNull { it.item?.toDomain() },
    offset = offset,
    limit = limit,
    total = total,
    next = next,
)

/** Maps `GET /search?type=track` to a [Page] of [Track]. */
internal fun SearchResponse.toTrackPage(offset: Int, limit: Int): Page<Track> {
    val tracks = tracks ?: return emptyPage(offset, limit)
    return pageOf(
        mapped = tracks.items.map { it.toDomain() },
        offset = tracks.offset.takeIf { it != 0 } ?: offset,
        limit = tracks.limit.takeIf { it != 0 } ?: limit,
        total = tracks.total,
        next = tracks.next,
    )
}

private fun pageOf(
    mapped: List<Track>,
    offset: Int,
    limit: Int,
    total: Int,
    next: String?,
): Page<Track> {
    val consumed = offset + mapped.size
    val hasMore = next != null || (total > 0 && consumed < total)
    return Page(items = mapped, offset = offset, limit = limit, total = total, hasMore = hasMore)
}

private fun List<SpotifyImageDto>.bestArtworkUrl(): String? {
    if (isEmpty()) return null
    return firstNotNullOfOrNull { image ->
        image.url.takeIf { it.isNotBlank() }
    }?.let { fallback ->
        // Prefer the largest image, but never lose the only usable URL to a blank one.
        (maxByOrNull { it.width ?: 0 }?.url?.takeIf { it.isNotBlank() }) ?: fallback
    }
}
