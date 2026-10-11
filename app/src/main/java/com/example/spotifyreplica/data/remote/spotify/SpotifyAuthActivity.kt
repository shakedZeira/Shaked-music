package com.example.spotifyreplica.data.remote.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives the `spotifyreplica://callback` redirect from the Custom Tab and finishes the PKCE
 * login. The token exchange happens off the main thread and the outcome is persisted to
 * [SettingsManager.spotifyLoginMessage] so the login screen can surface it.
 */
@AndroidEntryPoint
class SpotifyAuthActivity : ComponentActivity() {

    @Inject lateinit var authManager: SpotifyAuthManager
    @Inject lateinit var settingsManager: SettingsManager

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent?) {
        if (handled) return
        handled = true
        val uri: Uri? = intent?.data
        if (uri != null) {
            appScope.launch { completeLogin(uri) }
        }
        finish()
    }

    private suspend fun completeLogin(callbackUri: Uri) {
        if (!settingsManager.hasPendingLogin()) {
            settingsManager.setSpotifyLoginMessage("Spotify login cancelled")
            return
        }
        val error = authManager.completePendingLogin(callbackUri)
        if (error == null) {
            settingsManager.clearSpotifyLoginMessage()
        } else {
            settingsManager.setSpotifyLoginMessage(error)
        }
    }
}
