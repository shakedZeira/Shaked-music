package com.example.spotifyreplica.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Spotify playlist the user owns, collaborates on, or follows. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val spotifyId: String,
    val name: String,
    val ownerName: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val syncedAtMs: Long?
)
