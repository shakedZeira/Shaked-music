package com.example.spotifyreplica.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotifyreplica.domain.model.LibraryTrack
import com.example.spotifyreplica.playback.PlayerState
import java.util.Locale

/**
 * Full-screen player: the now-playing artwork, transport controls, seek bar, shuffle/repeat, and the
 * upcoming queue (falling back to the downloaded library when nothing is queued yet).
 *
 * Kept as a zero-argument `@Composable` (the nav graph constructs it) — the ViewModel is resolved via
 * [hiltViewModel], so `AppNavHost` never has to change. The underlying [PlaybackViewModel] shares the
 * singleton playback session with [com.example.spotifyreplica.ui.components.MiniPlayerBar].
 */
@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()

    PlayerContent(
        state = state,
        library = library,
        onTogglePlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSeek = viewModel::seekTo,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeatMode,
        onPlayFromList = viewModel::playQueue,
        modifier = modifier,
    )
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    library: List<LibraryTrack>,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayFromList: (List<LibraryTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    val list = state.queue.ifEmpty { library }
    val listLabel = if (state.queue.isNotEmpty()) "Queue" else "Your library"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            NowPlayingHeader(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        }

        if (list.isNotEmpty()) {
            item {
                Text(
                    text = listLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(
                items = list,
                key = { index, item -> "$index:${item.spotifyId}" },
            ) { index, item ->
                TrackRow(
                    track = item,
                    isCurrent = track != null && track.spotifyId == item.spotifyId,
                    onClick = { onPlayFromList(list, index) },
                )
            }
        } else if (track == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing is playing.\nDownload a track to start listening.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val track = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = track?.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = track?.title ?: "Nothing is playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artists?.joinToString(", ").orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SeekBar(state = state, onSeek = onSeek)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                Icon(
                    imageVector = PreviousIcon,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = onTogglePlayPause,
                enabled = state.hasMedia,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(
                    imageVector = NextIcon,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.shuffleEnabled,
                onClick = onToggleShuffle,
                label = { Text("Shuffle") },
            )
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = state.repeatMode != PlayerState.REPEAT_MODE_OFF,
                onClick = onCycleRepeat,
                label = { Text(repeatLabel(state.repeatMode)) },
            )
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit) {
    // Local drag value so the ticker doesn't fight the user's thumb. Cleared on track change.
    var dragFraction by remember(state.currentTrack?.spotifyId) {
        mutableStateOf<Float?>(null)
    }
    val fraction = (dragFraction ?: state.progressFraction).coerceIn(0f, 1f)
    val enabled = state.hasMedia && state.durationMs > 0L

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val dragged = dragFraction
                if (dragged != null && state.durationMs > 0L) {
                    onSeek((dragged * state.durationMs).toLong())
                }
                dragFraction = null
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: LibraryTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun repeatLabel(mode: Int): String = when (mode) {
    PlayerState.REPEAT_MODE_ONE -> "Repeat one"
    PlayerState.REPEAT_MODE_ALL -> "Repeat all"
    else -> "Repeat"
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/**
 * Custom vector icons (see note about `material-icons-core` in `app/build.gradle.kts`).
 * `material-icons-extended` is intentionally not a dependency, so the transport glyphs are drawn
 * here rather than referenced from `Icons.Filled.*`.
 */
private fun transportIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFF000000))) { block() }
    }.build()

private val PauseIcon: ImageVector by lazy {
    transportIcon("Pause") {
        moveTo(6f, 5f)
        horizontalLineTo(10f)
        verticalLineTo(19f)
        horizontalLineTo(6f)
        close()
        moveTo(14f, 5f)
        horizontalLineTo(18f)
        verticalLineTo(19f)
        horizontalLineTo(14f)
        close()
    }
}

private val NextIcon: ImageVector by lazy {
    transportIcon("Next") {
        moveTo(6f, 18f)
        lineTo(14.5f, 12f)
        lineTo(6f, 6f)
        close()
        moveTo(16f, 6f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(16f)
        close()
    }
}

private val PreviousIcon: ImageVector by lazy {
    transportIcon("Previous") {
        moveTo(6f, 6f)
        horizontalLineTo(8f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        close()
        moveTo(18f, 6f)
        verticalLineTo(18f)
        lineTo(9.5f, 12f)
        close()
    }
}
