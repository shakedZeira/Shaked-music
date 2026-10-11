package com.example.spotifyreplica.data.db.entity

import androidx.room.Entity

/**
 * Join row between [PlaylistEntity] and [TrackEntity], capturing the track's [position] inside the
 * playlist so the playlist can be rendered in the original order.
 */
@Entity(tableName = "playlist_track", primaryKeys = ["playlistId", "trackSpotifyId"])
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackSpotifyId: String,
    val position: Int
)
