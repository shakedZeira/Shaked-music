package com.example.spotifyreplica.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.ui.components.SettingsRow
import java.util.Locale

/**
 * Settings screen: account, audio-source selector, storage accounting, notification permission and
 * about. The ViewModel is resolved via `hiltViewModel()` so the nav route stays zero-argument apart
 * from [onConnectSpotify].
 */
@Composable
fun SettingsScreen(
    onConnectSpotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onConnectSpotify = onConnectSpotify,
        onSignOut = viewModel::logout,
        onAudioSourceChange = viewModel::setAudioSource,
        onClearDownloads = viewModel::clearDownloads,
        onDismissLoginMessage = viewModel::clearLoginMessage,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onConnectSpotify: () -> Unit,
    onSignOut: () -> Unit,
    onAudioSourceChange: (String) -> Unit,
    onClearDownloads: () -> Unit,
    onDismissLoginMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // --- Account -----------------------------------------------------------------------------
        SectionHeader("Account")
        if (uiState.loggedIn) {
            SettingsRow(
                title = "Signed in",
                subtitle = "Your Spotify metadata library is connected.",
            )
            Button(
                onClick = onSignOut,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Sign out")
            }
        } else {
            SettingsRow(
                title = "Not signed in",
                subtitle = "Connect Spotify to browse your playlists and tracks.",
            )
            Button(
                onClick = onConnectSpotify,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Connect Spotify")
            }
        }
        uiState.loginMessage?.let { message ->
            LoginMessage(message = message, onDismiss = onDismissLoginMessage)
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        // --- Audio source ------------------------------------------------------------------------
        SectionHeader("Audio source")
        val localSelected = uiState.audioSource == SettingsManager.AUDIO_SOURCE_LOCAL
        SettingsRow(
            title = "On-device library (recommended)",
            subtitle = "Play audio files you already own on this device. Fully offline.",
            onClick = { onAudioSourceChange(SettingsManager.AUDIO_SOURCE_LOCAL) },
            trailing = {
                RadioButton(
                    selected = localSelected,
                    onClick = { onAudioSourceChange(SettingsManager.AUDIO_SOURCE_LOCAL) },
                )
            },
        )
        SettingsRow(
            title = "YouTube (opt-in)",
            subtitle = "Download from YouTube instead. Off by default.",
            onClick = { onAudioSourceChange(SettingsManager.AUDIO_SOURCE_YOUTUBE) },
            trailing = {
                RadioButton(
                    selected = !localSelected,
                    onClick = { onAudioSourceChange(SettingsManager.AUDIO_SOURCE_YOUTUBE) },
                )
            },
        )
        if (!localSelected) {
            Warning(
                text = "YouTube downloads are for personal, sideloaded use only. Doing so is " +
                    "outside YouTube's Terms of Service, may be copyright infringement, and can " +
                    "break at any time. Use only for content you have the rights to.",
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        // --- Storage -----------------------------------------------------------------------------
        SectionHeader("Storage")
        SettingsRow(
            title = "Downloaded tracks",
            subtitle = uiState.downloadedTrackCount.toString(),
        )
        SettingsRow(
            title = "Size on device",
            subtitle = formatBytes(uiState.downloadedBytes),
        )
        Button(
            onClick = { showClearDialog = true },
            enabled = !uiState.clearing &&
                (uiState.downloadedTrackCount > 0 || uiState.downloadedBytes > 0L),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (uiState.clearing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (uiState.clearing) "Clearing…" else "Clear downloads")
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        // --- Notifications -----------------------------------------------------------------------
        SectionHeader("Notifications")
        NotificationRow()

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        // --- About -------------------------------------------------------------------------------
        SectionHeader("About")
        SettingsRow(title = "Spotify Replica", subtitle = "Version 1.0.0")
        Text(
            text = "A personal app for browsing Spotify playlist and track metadata. Audio is " +
                "played from files already on your device, or — if you opt in — downloaded from " +
                "YouTube. Spotify's Web API provides metadata only; no Spotify audio is downloaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(32.dp))
    }

    if (showClearDialog) {
        ClearDownloadsDialog(
            onConfirm = {
                showClearDialog = false
                onClearDownloads()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun ClearDownloadsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear downloads?") },
        text = {
            Text(
                "This cancels pending downloads and deletes every downloaded track from this " +
                    "device. This cannot be undone.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Clear") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun LoginMessage(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss message")
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NotificationRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        SettingsRow(
            title = "Download notifications",
            subtitle = "Not required on this Android version.",
        )
        return
    }

    val context = LocalContext.current
    var granted by remember { mutableStateOf(isNotificationGranted(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    SettingsRow(
        title = "Download notifications",
        subtitle = if (granted) "Granted" else "Not granted",
        trailing = {
            if (!granted) {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow")
                }
            }
        },
    )
}

private fun isNotificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
