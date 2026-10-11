package com.example.spotifyreplica.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.model.Track

/**
 * One Spotify search result: artwork, title (bold), artists, album and a trailing action whose
 * appearance mirrors the track's position in the download queue.
 *
 * [status] is `null` when the track has never been downloaded. The row is intentionally dumb — it
 * never touches the network or the queue; [onClick] is the single "download / retry" entry point.
 */
@Composable
fun SearchResultRow(
    track: Track,
    status: DownloadStatus?,
    onClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artists.isNotEmpty()) {
                Text(
                    text = track.artists.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track.album.isNotBlank()) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        DownloadAction(status = status, onClick = { onClick(track) })
    }
}

/** Trailing affordance that reflects [DownloadStatus] (or an untouched track when `null`). */
@Composable
private fun DownloadAction(status: DownloadStatus?, onClick: () -> Unit) {
    when (status) {
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> Box(
            modifier = Modifier.size(ACTION_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }

        DownloadStatus.COMPLETED -> Box(
            modifier = Modifier.size(ACTION_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Retry download",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        null -> IconButton(onClick = onClick) {
            Icon(
                imageVector = DownloadIcon,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val ACTION_SIZE = 48.dp

/**
 * Material "download" glyph inlined as an [ImageVector] because the project only ships
 * `material-icons-core` (no extended icon pack, hence no `Icons.Filled.Download`).
 */
private val DownloadIcon: ImageVector = ImageVector.Builder(
    name = "Download",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // Tray.
        moveTo(5f, 20f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        close()
        // Arrow.
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        close()
    }
}.build()
