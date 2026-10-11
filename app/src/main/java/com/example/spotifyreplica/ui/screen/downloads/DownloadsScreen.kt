package com.example.spotifyreplica.ui.screen.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import java.util.Locale

/**
 * The download queue: one row per track with live progress pulled from Room, plus cancel/retry.
 *
 * Kept as a zero-argument `@Composable` (the nav graph constructs it) — the ViewModel is resolved
 * via [hiltViewModel], so the route in `AppNavHost` never has to change.
 */
@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val viewModel: DownloadsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DownloadsContent(
        uiState = uiState,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
private fun DownloadsContent(
    uiState: DownloadsUiState,
    onCancel: (String) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(items = uiState.items, key = { it.id }) { item ->
            DownloadRow(item = item, onCancel = onCancel, onRetry = onRetry)
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onCancel: (String) -> Unit,
    onRetry: (DownloadItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: item.spotifyId,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLabel(item.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(item.status),
                )
            }

            when (item.status) {
                DownloadStatus.QUEUED, DownloadStatus.RUNNING -> IconButton(
                    onClick = { onCancel(item.spotifyId) },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> IconButton(
                    onClick = { onRetry(item) },
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry download")
                }

                DownloadStatus.COMPLETED -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.RUNNING) {
            Spacer(Modifier.height(6.dp))
            val fraction = progressFraction(item)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = sizeLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item.error?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.CANCELLED,
    DownloadStatus.QUEUED,
    DownloadStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

private fun progressFraction(item: DownloadItem): Float? {
    val total = item.totalBytes ?: return null
    if (total <= 0L) return null
    return (item.bytesDownloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun sizeLabel(item: DownloadItem): String {
    val done = formatBytes(item.bytesDownloaded)
    val total = item.totalBytes?.let { formatBytes(it) }
    return if (total != null) "$done / $total" else done
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
