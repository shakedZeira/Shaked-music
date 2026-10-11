package com.example.spotifyreplica.data.remote.spotify

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.spotifyreplica.BuildConfig
import com.example.spotifyreplica.data.settings.SettingsManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Spotify PKCE (Authorization Code with Proof Key for Code Exchange) helper. There is no client
 * secret: the app is a public client. Ported from the reference project, with the redirect scheme
 * changed to `spotifyreplica://callback` and read-only scopes.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val authClient: SpotifyAuthClient,
) {
    private val random = SecureRandom()

    fun generateCodeVerifier(): String = randomOpaque()

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }

    fun generateState(): String = randomOpaque()

    fun loginUrl(verifier: String, state: String): String =
        Uri.parse(BuildConfig.SPOTIFY_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

    /**
     * Finishes a pending PKCE login from the Spotify redirect Uri. The verifier/state are read back
     * from [settingsManager]; callers must confirm a login is pending first
     * (see [SettingsManager.hasPendingLogin]), so a blank verifier means nothing was stored.
     *
     * Returns null on success, or a human-readable error message on any failure.
     */
    suspend fun completePendingLogin(callbackUri: Uri): String? {
        val verifier = settingsManager.spotifyPendingVerifier.first()
        val expectedState = settingsManager.spotifyPendingState.first()
        if (verifier.isBlank()) return "No pending login"

        callbackUri.getQueryParameter("error")?.let { return it }
        val code = callbackUri.getQueryParameter("code")
        if (code.isNullOrBlank()) return "No authorization code received"
        if (callbackUri.getQueryParameter("state") != expectedState) return "State mismatch"

        return try {
            val response = authClient.exchangeCode(
                code = code,
                redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
                clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                codeVerifier = verifier,
            )
            settingsManager.saveSpotifyTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAtMs = System.currentTimeMillis() + response.expiresIn * MILLIS_PER_SECOND,
            )
            settingsManager.setSpotifyLoggedIn(true)
            settingsManager.clearPendingLogin()
            null
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "Token exchange failure", e)
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val detail = body?.take(ERROR_BODY_CHARS)?.takeIf { it.isNotBlank() }
            listOfNotNull("Token exchange failed (HTTP ${e.code()})", detail).joinToString(": ")
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failure", e)
            "Token exchange failed (${e::class.java.simpleName}${e.message?.let { ": $it" } ?: ""})"
        }
    }

    /** Refreshes the access token when it is missing/expired. Returns false when not logged in. */
    suspend fun ensureFreshToken(): Boolean {
        val expiresAtMs = settingsManager.spotifyTokenExpiresAtMs.first()
        if (expiresAtMs - System.currentTimeMillis() > REFRESH_AHEAD_MS) {
            return true
        }
        val refreshToken = settingsManager.spotifyRefreshToken.first()
        if (refreshToken.isNullOrBlank()) {
            return false
        }
        return runCatching {
            val response = authClient.refreshToken(
                refreshToken = refreshToken,
                clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            )
            settingsManager.saveSpotifyTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresAtMs = System.currentTimeMillis() + response.expiresIn * MILLIS_PER_SECOND,
            )
            settingsManager.setSpotifyLoggedIn(true)
        }.isSuccess
    }

    private fun randomOpaque(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    companion object {
        private const val TAG = "SpotifyReplicaAuth"

        /** Read-only scopes only. */
        const val SCOPES =
            "playlist-read-private playlist-read-collaborative user-library-read user-read-private"
        const val REFRESH_AHEAD_MS = 5 * 60 * 1000L
        const val MILLIS_PER_SECOND = 1000L
        const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        private const val ERROR_BODY_CHARS = 200
    }
}
