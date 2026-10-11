package com.example.spotifyreplica.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.data.db.entity.PlaylistTrackCrossRef
import com.example.spotifyreplica.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** Access to cached playlists and their ordered track membership. */
@Dao
interface PlaylistDao {

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefs(playlistId: String)

    /**
     * Atomically replaces the track membership of [playlistId]: deletes the existing
     * `playlist_track` rows and inserts [refs] in a single transaction.
     */
    @Transaction
    suspend fun replaceCrossRefs(playlistId: String, refs: List<PlaylistTrackCrossRef>) {
        deleteCrossRefs(playlistId)
        insertCrossRefs(refs)
    }

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    /**
     * The tracks belonging to [playlistId], ordered by their playlist position, via an inner join
     * on `playlist_track`.
     */
    @Query(
        """
        SELECT t.* FROM tracks AS t
        INNER JOIN playlist_track AS pt ON pt.trackSpotifyId = t.spotifyId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun observeWithTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("DELETE FROM playlists WHERE spotifyId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    /** Deletes the playlist row together with its membership rows in one transaction. */
    @Transaction
    suspend fun deletePlaylistWithTracks(playlistId: String) {
        deleteCrossRefs(playlistId)
        deletePlaylist(playlistId)
    }

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_track WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getCrossRefs(playlistId: String): List<PlaylistTrackCrossRef>
}
