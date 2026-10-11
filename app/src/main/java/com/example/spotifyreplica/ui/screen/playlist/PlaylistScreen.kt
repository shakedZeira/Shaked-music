package com.example.spotifyreplica.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.ui.components.DownloadButton
import com.example.spotifyreplica.ui.components.TrackRow

/**
 * A single Spotify playlist: header (artwork, name, owner, count), a "Download all" action and a
 * paged track list with per-row download state.
 *
 * The playlist id is read from the navigation argument inside [PlaylistViewModel], so this screen
 * only needs a back callback.
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlaylistViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlaylistContent(
        uiState = uiState,
        onBack = onBack,
        onDownloadTrack = viewModel::downloadTrack,
        onDownloadAll = viewModel::downloadAll,
        onPlayTrack = viewModel::play,
        onPlayDownloaded = viewModel::playDownloaded,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistContent(
    uiState: PlaylistUiState,
    onBack: () -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onDownloadAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayDownloaded: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = BackIcon, contentDescription = "Back")
            }
        }

        when {
            uiState.isLoading && uiState.tracks.isEmpty() -> LoadingBox()

            uiState.tracks.isEmpty() ->
                if (uiState.error != null) {
                    ErrorState(message = uiState.error, onRetry = onRetry)
                } else {
                    EmptyState()
                }

            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState = listState, uiState = uiState, onLoadMore = onLoadMore)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") {
                        PlaylistHeader(
                            uiState = uiState,
                            onDownloadAll = onDownloadAll,
                            onPlayDownloaded = onPlayDownloaded,
                        )
                    }

                    itemsIndexed(
                        items = uiState.tracks,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        TrackRow(
                            index = index + 1,
                            title = track.title,
                            artists = track.artists,
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            trailing = {
                                DownloadButton(
                                    status = uiState.statusByTrackId[track.id],
                                    downloaded = uiState.downloadedIds.contains(track.id),
                                    onDownload = { onDownloadTrack(track) },
                                )
                            },
                            onClick = if (uiState.downloadedIds.contains(track.id)) {
                                { onPlayTrack(track) }
                            } else {
                                null
                            },
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item(key = "footer-loading") { FooterProgress() }
                    } else if (uiState.error != null) {
                        item(key = "footer-error") {
                            FooterError(message = uiState.error, onRetry = onRetry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    uiState: PlaylistUiState,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by derivedStateOf {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= uiState.tracks.size - LOAD_MORE_THRESHOLD
    }
    LaunchedEffect(shouldLoadMore, uiState.tracks.size, uiState.isLoadingMore) {
        if (shouldLoadMore && !uiState.isLoadingMore) onLoadMore()
    }
}

@Composable
private fun PlaylistHeader(
    uiState: PlaylistUiState,
    onDownloadAll: () -> Unit,
    onPlayDownloaded: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = uiState.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uiState.ownerName.isNotBlank()) {
                    Text(
                        text = "By ${uiState.ownerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = trackCountLabel(uiState.trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDownloadAll) { Text("Download all") }
            if (uiState.hasDownloads) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onPlayDownloaded) { Text("Play downloaded") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "This playlist is empty or unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun FooterProgress() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun FooterError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

private fun trackCountLabel(trackCount: Int): String =
    if (trackCount == 1) "1 song" else "$trackCount songs"

private const val LOAD_MORE_THRESHOLD = 3

/**
 * Material "back" glyph inlined as an [ImageVector] because the project only ships
 * `material-icons-core` (and `Icons.Filled.ArrowBack` is deprecated in favour of the auto-mirrored
 * variant).
 */
private val BackIcon: ImageVector = ImageVector.Builder(
    name = "ArrowBack",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.42f, 18.59f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        close()
    }
}.build()
