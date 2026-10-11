package com.example.spotifyreplica.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.spotifyreplica.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access to cached track metadata and local download state.
 *
 * Observables return [Flow] so the UI updates automatically on writes.
 */
@Dao
interface TrackDao {

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE spotifyId = :spotifyId")
    fun observeById(spotifyId: String): Flow<TrackEntity?>

    /** Tracks that have been fully downloaded to local storage. */
    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY downloadedAtMs DESC")
    fun observeDownloaded(): Flow<List<TrackEntity>>

    @Query(
        """
        UPDATE tracks
        SET localPath = :localPath,
            sizeBytes = :sizeBytes,
            container = :container,
            bitrateKbps = :bitrateKbps,
            downloadedAtMs = :downloadedAtMs
        WHERE spotifyId = :spotifyId
        """
    )
    suspend fun markDownloaded(
        spotifyId: String,
        localPath: String,
        sizeBytes: Long?,
        container: String,
        bitrateKbps: Int?,
        downloadedAtMs: Long
    )

    @Query("DELETE FROM tracks WHERE spotifyId = :spotifyId")
    suspend fun deleteById(spotifyId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE spotifyId = :spotifyId)")
    suspend fun exists(spotifyId: String): Boolean

    @Query("SELECT * FROM tracks WHERE spotifyId = :spotifyId")
    suspend fun getById(spotifyId: String): TrackEntity?

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}
