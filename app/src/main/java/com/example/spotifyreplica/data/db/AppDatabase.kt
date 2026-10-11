package com.example.spotifyreplica.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.spotifyreplica.data.db.dao.DownloadDao
import com.example.spotifyreplica.data.db.dao.PlaylistDao
import com.example.spotifyreplica.data.db.dao.TrackDao
import com.example.spotifyreplica.data.db.entity.DownloadEntity
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.data.db.entity.PlaylistTrackCrossRef
import com.example.spotifyreplica.data.db.entity.TrackEntity

/** Local Room database holding cached Spotify metadata, library membership, and the download queue. */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}
