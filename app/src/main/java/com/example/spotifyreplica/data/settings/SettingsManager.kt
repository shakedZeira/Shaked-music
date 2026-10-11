package com.example.spotifyreplica.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed settings. Phase 2 adds the Spotify token/PKCE flows (ported from the reference
 * project); the audio-source selector is consumed in Phase 4.
 */
@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private const val AUDIO_SOURCE_KEY = "audio_source"

        private const val SPOTIFY_ACCESS_TOKEN_KEY = "spotify_access_token"
        private const val SPOTIFY_REFRESH_TOKEN_KEY = "spotify_refresh_token"
        private const val SPOTIFY_TOKEN_EXPIRES_AT_KEY = "spotify_token_expires_at"
        private const val SPOTIFY_LOGGED_IN_KEY = "spotify_logged_in"
        private const val SPOTIFY_PENDING_VERIFIER_KEY = "spotify_pending_verifier"
        private const val SPOTIFY_PENDING_STATE_KEY = "spotify_pending_state"
        private const val SPOTIFY_LOGIN_MESSAGE_KEY = "spotify_login_message"

        /** Compliant default: index audio already on the device instead of downloading it. */
        const val AUDIO_SOURCE_LOCAL = "local"
    }

    private val audioSourceKey = stringPreferencesKey(AUDIO_SOURCE_KEY)

    private val spotifyAccessTokenKey = stringPreferencesKey(SPOTIFY_ACCESS_TOKEN_KEY)
    private val spotifyRefreshTokenKey = stringPreferencesKey(SPOTIFY_REFRESH_TOKEN_KEY)
    private val spotifyTokenExpiresAtKey = longPreferencesKey(SPOTIFY_TOKEN_EXPIRES_AT_KEY)
    private val spotifyLoggedInKey = booleanPreferencesKey(SPOTIFY_LOGGED_IN_KEY)
    private val spotifyPendingVerifierKey = stringPreferencesKey(SPOTIFY_PENDING_VERIFIER_KEY)
    private val spotifyPendingStateKey = stringPreferencesKey(SPOTIFY_PENDING_STATE_KEY)
    private val spotifyLoginMessageKey = stringPreferencesKey(SPOTIFY_LOGIN_MESSAGE_KEY)

    val audioSource: Flow<String> = dataStore.data
        .map { it[audioSourceKey] ?: AUDIO_SOURCE_LOCAL }
        .distinctUntilChanged()

    val spotifyAccessToken: Flow<String> = dataStore.data
        .map { it[spotifyAccessTokenKey] ?: "" }
        .distinctUntilChanged()

    val spotifyRefreshToken: Flow<String> = dataStore.data
        .map { it[spotifyRefreshTokenKey] ?: "" }
        .distinctUntilChanged()

    val spotifyTokenExpiresAtMs: Flow<Long> = dataStore.data
        .map { it[spotifyTokenExpiresAtKey] ?: 0L }
        .distinctUntilChanged()

    val spotifyLoggedIn: Flow<Boolean> = dataStore.data
        .map { it[spotifyLoggedInKey] ?: false }
        .distinctUntilChanged()

    val spotifyPendingVerifier: Flow<String> = dataStore.data
        .map { it[spotifyPendingVerifierKey] ?: "" }
        .distinctUntilChanged()

    val spotifyPendingState: Flow<String> = dataStore.data
        .map { it[spotifyPendingStateKey] ?: "" }
        .distinctUntilChanged()

    /** Last login outcome written by the OAuth callback; null means "no message". */
    val spotifyLoginMessage: Flow<String?> = dataStore.data
        .map { prefs -> prefs[spotifyLoginMessageKey]?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()

    suspend fun setAudioSource(source: String) {
        dataStore.edit { it[audioSourceKey] = source }
    }

    suspend fun saveSpotifyTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
    ) {
        dataStore.edit {
            it[spotifyAccessTokenKey] = accessToken
            it[spotifyRefreshTokenKey] = refreshToken ?: ""
            it[spotifyTokenExpiresAtKey] = expiresAtMs
        }
    }

    suspend fun clearSpotifyTokens() {
        dataStore.edit {
            it[spotifyAccessTokenKey] = ""
            it[spotifyRefreshTokenKey] = ""
            it[spotifyTokenExpiresAtKey] = 0L
            it[spotifyLoggedInKey] = false
        }
    }

    suspend fun setSpotifyLoggedIn(loggedIn: Boolean) {
        dataStore.edit { it[spotifyLoggedInKey] = loggedIn }
    }

    suspend fun setPendingLogin(verifier: String, state: String) {
        dataStore.edit {
            it[spotifyPendingVerifierKey] = verifier
            it[spotifyPendingStateKey] = state
        }
    }

    suspend fun clearPendingLogin() {
        dataStore.edit {
            it[spotifyPendingVerifierKey] = ""
            it[spotifyPendingStateKey] = ""
        }
    }

    suspend fun hasPendingLogin(): Boolean = spotifyPendingVerifier.first().isNotBlank()

    suspend fun setSpotifyLoginMessage(message: String?) {
        dataStore.edit { it[spotifyLoginMessageKey] = message ?: "" }
    }

    suspend fun clearSpotifyLoginMessage() {
        dataStore.edit { it[spotifyLoginMessageKey] = "" }
    }
}
