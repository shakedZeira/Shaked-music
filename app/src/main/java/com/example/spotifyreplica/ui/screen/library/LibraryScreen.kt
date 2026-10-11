package com.example.spotifyreplica.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.spotifyreplica.ui.navigation.Screen
import com.example.spotifyreplica.ui.screen.login.LoginViewModel

/**
 * Placeholder — replaced in Phase 7 (library browsing UI).
 *
 * The only affordance added here is a "Connect Spotify" call-to-action so the PKCE flow is
 * reachable from the app's start destination. [onNavigateToLogin] lets a nav host route to
 * [Screen.Login]; when it is not supplied (the Phase 2 nav graph calls `LibraryScreen()` directly)
 * the CTA starts the login flow itself, so login is always reachable.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogin: (() -> Unit)? = null,
) {
    val loginViewModel: LoginViewModel = hiltViewModel()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (onNavigateToLogin != null) onNavigateToLogin() else loginViewModel.login()
            },
        ) {
            Text("Connect Spotify")
        }
    }
}
