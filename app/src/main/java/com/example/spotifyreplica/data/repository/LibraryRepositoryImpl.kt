package com.example.spotifyreplica.data.repository

import com.example.spotifyreplica.data.db.dao.DownloadDao
import com.example.spotifyreplica.data.db.dao.PlaylistDao
import com.example.spotifyreplica.data.db.dao.TrackDao
import com.example.spotifyreplica.data.db.entity.DownloadEntity
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.data.db.entity.TrackEntity
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Room-backed [LibraryRepository]. */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao
) : LibraryRepository {

    /**
     * Combines the queue with cached tracks so each item can carry the track title. Both Room flows
     * emit an initial snapshot, so the combined flow emits immediately even when empty.
     */
    override fun observeDownloads(): Flow<List<DownloadItem>> =
        combine(downloadDao.observeAll(), trackDao.observeAll()) { downloads, tracks ->
            val titles = tracks.associate { it.spotifyId to it.title }
            downloads.map { entity -> entity.toDomain(title = titles[entity.spotifyId]) }
        }

    override fun observeLibraryTracks(): Flow<List<LibraryTrack>> =
        trackDao.observeDownloaded().map { tracks ->
            tracks.mapNotNull { entity -> entity.localPath?.let { entity.toLibraryTrack(it) } }
        }

    override fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observePlaylists()

    override suspend fun upsertDownload(item: DownloadItem) {
        downloadDao.upsert(item.toEntity(updatedAtMs = System.currentTimeMillis()))
    }

    override suspend fun updateDownloadProgress(id: String, bytesDownloaded: Long, totalBytes: Long?) {
        downloadDao.updateProgress(id, bytesDownloaded, totalBytes)
    }

    override suspend fun updateDownloadStatus(
        id: String,
        status: DownloadStatus,
        error: String?
    ) {
        downloadDao.updateStatus(id, status.name, error)
    }

    override suspend fun removeDownload(id: String) {
        downloadDao.delete(id)
    }

    override suspend fun markTrackDownloaded(
        spotifyId: String,
        localPath: String,
        sizeBytes: Long?,
        container: String,
        bitrateKbps: Int?,
        downloadedAtMs: Long
    ) {
        trackDao.markDownloaded(
            spotifyId = spotifyId,
            localPath = localPath,
            sizeBytes = sizeBytes,
            container = container,
            bitrateKbps = bitrateKbps,
            downloadedAtMs = downloadedAtMs
        )
    }

    override suspend fun clearAll() {
        downloadDao.deleteAll()
    }

    override suspend fun cacheTrack(track: Track) {
        val existing = trackDao.getById(track.id)
        trackDao.upsert(
            TrackEntity(
                spotifyId = track.id,
                title = track.title,
                artistsCsv = TrackEntity.artistsToCsv(track.artists),
                album = track.album,
                durationMs = track.durationMs,
                artworkUrl = track.artworkUrl,
                localPath = existing?.localPath,
                sizeBytes = existing?.sizeBytes,
                container = existing?.container ?: "",
                bitrateKbps = existing?.bitrateKbps,
                downloadedAtMs = existing?.downloadedAtMs
            )
        )
    }

    override suspend fun cachedTrack(spotifyId: String): Track? =
        trackDao.getById(spotifyId)?.toTrack()

    override suspend fun upsertTrack(
        track: Track,
        localPath: String?,
        sizeBytes: Long?,
        container: String,
        bitrateKbps: Int?,
        downloadedAtMs: Long?
    ) {
        trackDao.upsert(
            TrackEntity(
                spotifyId = track.id,
                title = track.title,
                artistsCsv = TrackEntity.artistsToCsv(track.artists),
                album = track.album,
                durationMs = track.durationMs,
                artworkUrl = track.artworkUrl,
                localPath = localPath,
                sizeBytes = sizeBytes,
                container = container,
                bitrateKbps = bitrateKbps,
                downloadedAtMs = downloadedAtMs
            )
        )
    }
}

private fun DownloadEntity.toDomain(title: String?): DownloadItem = DownloadItem(
    id = id,
    spotifyId = spotifyId,
    playlistId = playlistId,
    title = title,
    status = DownloadStatus.fromString(status),
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    error = error
)

private fun DownloadItem.toEntity(updatedAtMs: Long): DownloadEntity = DownloadEntity(
    id = id,
    spotifyId = spotifyId,
    playlistId = playlistId,
    status = status.name,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    error = error,
    updatedAtMs = updatedAtMs
)

private fun TrackEntity.toLibraryTrack(localPath: String): LibraryTrack = LibraryTrack(
    spotifyId = spotifyId,
    title = title,
    artists = artistList(),
    album = album,
    artworkUrl = artworkUrl,
    localPath = localPath,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    container = container,
    bitrateKbps = bitrateKbps
)

private fun TrackEntity.toTrack(): Track = Track(
    id = spotifyId,
    title = title,
    artists = artistList(),
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isrc = null,
    spotifyUri = null
)
