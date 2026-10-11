package com.example.spotifyreplica.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.spotifyreplica.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access to the download queue. Progress/status updates bump `updatedAtMs` at the SQLite level so
 * callers only supply the fields they care about.
 */
@Dao
interface DownloadDao {

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Upsert
    suspend fun upsertAll(downloads: List<DownloadEntity>)

    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE spotifyId = :spotifyId ORDER BY updatedAtMs DESC LIMIT 1")
    fun observeBySpotifyId(spotifyId: String): Flow<DownloadEntity?>

    @Query(
        """
        UPDATE downloads
        SET bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            updatedAtMs = (strftime('%s', 'now') * 1000)
        WHERE id = :id
        """
    )
    suspend fun updateProgress(id: String, bytesDownloaded: Long, totalBytes: Long?)

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            error = :error,
            updatedAtMs = (strftime('%s', 'now') * 1000)
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("SELECT SUM(bytesDownloaded) FROM downloads")
    suspend fun totalDownloadedBytes(): Long?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<DownloadEntity>

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
