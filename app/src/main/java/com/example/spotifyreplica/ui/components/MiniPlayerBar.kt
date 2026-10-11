package com.example.spotifyreplica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotifyreplica.playback.PlayerState
import com.example.spotifyreplica.ui.screen.player.PlayerViewModel

/**
 * Persistent mini player shown above the bottom navigation bar.
 *
 * Kept as a zero-argument `@Composable` (constructed directly by `MainActivity`). It resolves its own
 * [PlayerViewModel] via [hiltViewModel] and observes the same application-scoped playback session as
 * the full `PlayerScreen`, so controls stay in sync wherever they're shown.
 *
 * This bar intentionally performs no navigation (the nav route is owned by `AppNavHost`/`MainActivity`,
 * which are out of scope for this phase); it exposes the transport control only.
 */
@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    MiniPlayerContent(
        state = state,
        onTogglePlayPause = viewModel::togglePlayPause,
        modifier = modifier,
    )
}

@Composable
private fun MiniPlayerContent(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                AsyncImage(
                    model = track?.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing is playing",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (track != null) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artists?.joinToString(", ")
                        ?: "Pick a downloaded track to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                enabled = state.hasMedia,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) MiniPauseIcon else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Minimal pause glyph. `material-icons-extended` is intentionally not a dependency (see
 * `app/build.gradle.kts`), so the icon is drawn here instead of referencing `Icons.Filled.Pause`.
 */
private val MiniPauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MiniPause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFF000000))) {
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
    }.build()
}
