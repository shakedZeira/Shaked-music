package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Enqueues a whole playlist onto the shared download queue.
 *
 * WorkManager owns the (bounded) concurrency, so this simply asks [DownloadRepository] for one unit
 * of work per track; the returned ids preserve the playlist order.
 *
 * Two entry points exist because the caller may or may not have the rich [PlaylistSummary]:
 *  - [invoke] (summary + tracks) keeps the playlist association on every queued item.
 *  - [invoke] (raw id + tracks) is used when only the route's `playlistId` is known.
 */
class DownloadPlaylistUseCase @Inject constructor(
    private val downloads: DownloadRepository,
) {

    /** Enqueues every track of [playlist]; returns the WorkRequest ids in playlist order. */
    suspend operator fun invoke(
        playlist: PlaylistSummary,
        tracks: List<Track>,
    ): List<UUID> = downloads.enqueuePlaylist(playlist, tracks)

    /** Enqueues every track with the raw [playlistId] (no summary needed). */
    suspend operator fun invoke(playlistId: String, tracks: List<Track>): List<UUID> =
        tracks.map { track -> downloads.enqueue(track, playlistId) }
}
