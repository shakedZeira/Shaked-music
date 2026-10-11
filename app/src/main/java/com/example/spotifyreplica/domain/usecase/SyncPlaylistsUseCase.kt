package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import javax.inject.Inject

/**
 * Pulls the user's Spotify playlists and caches them in Room so the library can render offline.
 *
 * Returns the number of playlists written on success. Repository failures are propagated untouched
 * (as a [Result.failure]) — nothing is cached in that case — so the caller can surface a message.
 *
 * [cache] is exposed separately so a caller that already has a fresh [List] of spotify summaries
 * (e.g. `LibraryViewModel.refresh()`) can persist them without a second `/me/playlists` round-trip.
 */
class SyncPlaylistsUseCase @Inject constructor(
    private val catalog: MusicCatalogRepository,
    private val library: LibraryRepository,
) {

    suspend operator fun invoke(): Result<Int> =
        catalog.myPlaylists().mapCatching { playlists -> cache(playlists) }

    /** Caches [playlists] (stamping [PlaylistEntity.syncedAtMs]) and returns how many were written. */
    suspend fun cache(playlists: List<PlaylistSummary>): Int {
        val now = System.currentTimeMillis()
        library.upsertPlaylists(playlists.map { it.toEntity(syncedAtMs = now) })
        return playlists.size
    }
}

internal fun PlaylistSummary.toEntity(syncedAtMs: Long): PlaylistEntity = PlaylistEntity(
    spotifyId = id,
    name = name,
    ownerName = ownerName,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    syncedAtMs = syncedAtMs,
)
