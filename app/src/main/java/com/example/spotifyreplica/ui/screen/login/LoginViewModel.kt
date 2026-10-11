package com.example.spotifyreplica.ui.screen.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyreplica.BuildConfig
import com.example.spotifyreplica.data.remote.spotify.SpotifyApi
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthManager
import com.example.spotifyreplica.data.remote.spotify.SpotifyBrowserLauncher
import com.example.spotifyreplica.data.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the login screen should render. */
sealed interface LoginUiState {
    data object NotLoggedIn : LoginUiState
    data object Authorizing : LoginUiState
    data class LoggedIn(val userName: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

/**
 * Drives the Spotify PKCE login. State is derived from [SettingsManager] (`spotifyLoggedIn` /
 * `spotifyLoginMessage`) so a login completed by [com.example.spotifyreplica.data.remote.spotify.SpotifyAuthActivity]
 * is reflected here without any extra plumbing.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: SpotifyAuthManager,
    private val settingsManager: SettingsManager,
    private val api: SpotifyApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val authorizing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val userName = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoginUiState> = combine(
        settingsManager.spotifyLoggedIn,
        authorizing,
        errorMessage,
        userName,
    ) { loggedIn, isAuthorizing, error, name ->
        when {
            error != null -> LoginUiState.Error(error)
            loggedIn -> LoginUiState.LoggedIn(name ?: DEFAULT_USER_NAME)
            isAuthorizing -> LoginUiState.Authorizing
            else -> LoginUiState.NotLoggedIn
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoginUiState.NotLoggedIn)

    init {
        // Surface (and consume) the outcome written by the OAuth callback.
        viewModelScope.launch {
            settingsManager.spotifyLoginMessage.collect { message ->
                if (message != null) {
                    authorizing.value = false
                    errorMessage.value = message
                    settingsManager.clearSpotifyLoginMessage()
                }
            }
        }
        // Once logged in, resolve a display name for the profile.
        viewModelScope.launch {
            settingsManager.spotifyLoggedIn.collect { loggedIn ->
                if (loggedIn) loadProfileName() else userName.value = null
            }
        }
    }

    /** Opens the Spotify authorization page in a Custom Tab (PKCE S256). */
    fun login() {
        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) {
            errorMessage.value = "Missing Spotify client id — add SPOTIFY_CLIENT_ID to spotify.properties"
            return
        }
        viewModelScope.launch {
            errorMessage.value = null
            authorizing.value = true
            val verifier = authManager.generateCodeVerifier()
            val state = authManager.generateState()
            settingsManager.setPendingLogin(verifier = verifier, state = state)
            val url = authManager.loginUrl(verifier = verifier, state = state)
            SpotifyBrowserLauncher.open(context, url).onFailure { error ->
                authorizing.value = false
                errorMessage.value = error.message ?: "Could not open the browser"
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    private suspend fun loadProfileName() {
        if (userName.value != null) return
        runCatching { api.me().displayName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { userName.value = it }
    }

    private companion object {
        const val DEFAULT_USER_NAME = "Spotify account"
    }
}
