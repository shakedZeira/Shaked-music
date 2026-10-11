package com.example.spotifyreplica.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.spotifyreplica.domain.model.LibraryTrack
import java.io.File

/**
 * Pure mapping between the offline library model and Media3's [MediaItem].
 *
 * Downloaded audio lives on disk (see `StoragePaths`), so the media URI is derived from the file
 * path. Paths that already carry a scheme (`content://`, `file://`, `http(s)://`) are parsed as-is;
 * anything else is treated as a filesystem path and wrapped with [Uri.fromFile].
 */
object MediaItemMapper {

    /** Converts a downloaded [LibraryTrack] into a playable [MediaItem]. */
    fun toMediaItem(track: LibraryTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists.joinToString(ARTIST_SEPARATOR))
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.spotifyId)
            .setUri(localUri(track.localPath))
            .setMediaMetadata(metadata)
            .build()
    }

    /** Resolves a local library path (absolute path or existing URI string) to a media [Uri]. */
    internal fun localUri(localPath: String): Uri {
        val hasScheme = localPath.startsWith("content://") ||
            localPath.startsWith("file://") ||
            localPath.startsWith("http://") ||
            localPath.startsWith("https://") ||
            localPath.startsWith("android.resource://")
        return if (hasScheme) Uri.parse(localPath) else Uri.fromFile(File(localPath))
    }

    private const val ARTIST_SEPARATOR = ", "
}

/** Convenience extension mirroring [MediaItemMapper.toMediaItem]. */
fun LibraryTrack.toMediaItem(): MediaItem = MediaItemMapper.toMediaItem(this)
