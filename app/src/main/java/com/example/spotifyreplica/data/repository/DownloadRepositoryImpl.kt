package com.example.spotifyreplica.data.repository

import com.example.spotifyreplica.data.download.DownloadQueue
import com.example.spotifyreplica.data.download.defaultDownloadId
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.PlaylistSummary
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Coordinates [DownloadQueue] (WorkManager) with [LibraryRepository] (Room) for the UI. */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadQueue: DownloadQueue,
    private val libraryRepository: LibraryRepository,
) : DownloadRepository {

    override fun observeQueue(): Flow<List<DownloadItem>> = libraryRepository.observeDownloads()

    override suspend fun enqueue(track: Track, playlistId: String?): UUID =
        downloadQueue.enqueue(track, playlistId)

    override suspend fun enqueuePlaylist(playlist: PlaylistSummary, tracks: List<Track>): List<UUID> =
        downloadQueue.enqueuePlaylist(playlist, tracks)

    override suspend fun cancel(spotifyId: String) {
        downloadQueue.cancel(spotifyId)
        libraryRepository.removeDownload(defaultDownloadId(spotifyId))
    }

    override suspend fun retry(item: DownloadItem) {
        val track = libraryRepository.cachedTrack(item.spotifyId) ?: return
        downloadQueue.enqueue(track, item.playlistId)
    }
}
