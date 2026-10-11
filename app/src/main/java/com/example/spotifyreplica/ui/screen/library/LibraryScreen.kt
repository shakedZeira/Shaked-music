package com.example.spotifyreplica.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.ui.components.DownloadButton
import com.example.spotifyreplica.ui.components.PlaylistCard
import com.example.spotifyreplica.ui.components.TrackRow

/**
 * The library: the user's Spotify playlists plus everything downloaded for offline listening.
 *
 * When logged out, a "Connect Spotify" call-to-action routes to the login screen ([onConnectSpotify])
 * while the local downloads remain browsable. Tapping a playlist hands its id to [onPlaylistClick].
 */
@Composable
fun LibraryScreen(
    onConnectSpotify: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryContent(
        uiState = uiState,
        onConnectSpotify = onConnectSpotify,
        onPlaylistClick = onPlaylistClick,
        onRefresh = viewModel::refresh,
        onLogout = viewModel::logout,
        onPlayAll = viewModel::playAll,
        onPlay = viewModel::play,
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState,
    onConnectSpotify: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (LibraryTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LibraryHeader(
            loggedIn = uiState.loggedIn,
            onRefresh = onRefresh,
            onLogout = onLogout,
        )

        if (uiState.isLoading && uiState.playlists.isEmpty() && uiState.downloads.isEmpty()) {
            LoadingBox()
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!uiState.loggedIn) {
                item(key = "connect") { ConnectSpotifySection(onConnectSpotify) }
            }

            uiState.error?.let { message ->
                item(key = "error") { ErrorSection(message = message, onRetry = onRefresh) }
            }

            item(key = "downloads-header") {
                SectionHeader(
                    title = "Your Downloads",
                    action = if (uiState.downloads.isNotEmpty()) "Play all" to onPlayAll else null,
                )
            }

            if (uiState.downloads.isEmpty()) {
                item(key = "downloads-empty") { EmptyHint("No downloads yet") }
            } else {
                itemsIndexed(
                    items = uiState.downloads,
                    key = { _, track -> track.spotifyId },
                ) { index, track ->
                    TrackRow(
                        index = index + 1,
                        title = track.title,
                        artists = track.artists,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        trailing = {
                            DownloadButton(
                                status = uiState.statusByTrackId[track.spotifyId],
                                downloaded = true,
                                onDownload = {},
                            )
                        },
                        onClick = { onPlay(track) },
                    )
                }
            }

            if (uiState.loggedIn) {
                item(key = "playlists-header") { SectionHeader(title = "Playlists") }

                if (uiState.playlists.isEmpty()) {
                    item(key = "playlists-empty") { EmptyHint("No playlists found") }
                } else {
                    items(items = uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            name = playlist.name,
                            ownerName = playlist.ownerName,
                            artworkUrl = playlist.artworkUrl,
                            trackCount = playlist.trackCount,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    loggedIn: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        if (loggedIn) {
            TextButton(onClick = onLogout) { Text("Log out") }
        }
        IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh library")
        }
    }
}

@Composable
private fun ConnectSpotifySection(onConnectSpotify: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connect Spotify to browse your playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onConnectSpotify) { Text("Connect Spotify") }
    }
}

@Composable
private fun SectionHeader(title: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = action.second) { Text(action.first) }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ErrorSection(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
