package com.example.spotifyreplica.data.remote.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over [SpotifyAuthService] for the two PKCE grants we use. */
interface SpotifyAuthClient {
    suspend fun exchangeCode(code: String, redirectUri: String, clientId: String, codeVerifier: String): TokenResponse

    suspend fun refreshToken(refreshToken: String, clientId: String): TokenResponse
}

@Singleton
class SpotifyAuthClientImpl @Inject constructor(
    private val service: SpotifyAuthService,
) : SpotifyAuthClient {

    override suspend fun exchangeCode(
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String,
    ): TokenResponse = service.token(
        grantType = "authorization_code",
        code = code,
        redirectUri = redirectUri,
        clientId = clientId,
        codeVerifier = codeVerifier,
        refreshToken = null,
    )

    override suspend fun refreshToken(refreshToken: String, clientId: String): TokenResponse = service.token(
        grantType = "refresh_token",
        code = null,
        redirectUri = null,
        clientId = clientId,
        codeVerifier = null,
        refreshToken = refreshToken,
    )
}

/**
 * Opens an authorization URL in a Chrome Custom Tab (androidx.browser). Kept next to the auth
 * client so the whole PKCE hand-off lives behind one small surface.
 */
object SpotifyBrowserLauncher {
    fun open(context: Context, url: String): Result<Unit> = runCatching {
        val intent = CustomTabsIntent.Builder().build().intent
        intent.data = Uri.parse(url)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
