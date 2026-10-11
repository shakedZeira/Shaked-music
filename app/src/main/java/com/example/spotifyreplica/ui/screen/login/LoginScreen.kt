package com.example.spotifyreplica.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Spotify PKCE login screen: "Connect Spotify", progress, and error rendering. */
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Spotify Replica", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sign in to browse your playlists and tracks.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        when (val current = state) {
            LoginUiState.NotLoggedIn -> {
                Button(onClick = viewModel::login) { Text("Connect Spotify") }
            }

            LoginUiState.Authorizing -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(text = "Waiting for Spotify…", style = MaterialTheme.typography.bodyMedium)
            }

            is LoginUiState.LoggedIn -> {
                Text(
                    text = "Signed in as ${current.userName}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is LoginUiState.Error -> {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::login) { Text("Try again") }
            }
        }
    }
}
