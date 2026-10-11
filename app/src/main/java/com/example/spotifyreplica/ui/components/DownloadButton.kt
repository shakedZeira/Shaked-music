package com.example.spotifyreplica.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.spotifyreplica.domain.model.DownloadStatus

/**
 * Trailing download affordance shared by the playlist and library rows.
 *
 * The visual state is derived from two inputs:
 *  - [downloaded] is true when a completed file exists on disk (the offline source of truth, from
 *    Room), which wins over [status] so a completed download never shows a stale "queued" spinner.
 *  - [status] is the live queue entry for the track (or `null` when it was never enqueued).
 *
 * [onCancel] is optional: when supplied, the queued/downloading spinner becomes tappable to cancel.
 */
@Composable
fun DownloadButton(
    status: DownloadStatus?,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    when {
        downloaded || status == DownloadStatus.COMPLETED -> Box(
            modifier = Modifier.size(ACTION_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING ->
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PROGRESS_SIZE),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(ACTION_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PROGRESS_SIZE),
                        strokeWidth = 2.dp,
                    )
                }
            }

        status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED ->
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry download",
                    tint = MaterialTheme.colorScheme.error,
                )
            }

        else -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = DownloadIcon,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val ACTION_SIZE = 48.dp
private val PROGRESS_SIZE = 22.dp

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
