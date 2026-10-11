package com.example.spotifyreplica.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Spotify track plus its local download state.
 *
 * [localPath] is null until the track has been downloaded; the remaining download columns are only
 * meaningful once [localPath] is set.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val spotifyId: String,
    val title: String,
    val artistsCsv: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val localPath: String?,
    val sizeBytes: Long?,
    val container: String,
    val bitrateKbps: Int?,
    val downloadedAtMs: Long?
) {
    /** Splits [artistsCsv] back into a list of artist names. */
    fun artistList(): List<String> =
        artistsCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        /** Joins artist names into the single CSV column stored by Room. */
        fun artistsToCsv(artists: List<String>): String =
            artists.joinToString(", ") { it.trim() }.trim()
    }
}
