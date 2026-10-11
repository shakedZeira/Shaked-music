package com.example.spotifyreplica.data.remote.spotify

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Spotify Accounts token endpoint. PKCE public client: only `client_id` is sent (no secret).
 * The Retrofit base URL is derived from `BuildConfig.SPOTIFY_TOKEN_URL`, so the path here stays
 * relative to the accounts host.
 */
interface SpotifyAuthService {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun token(
        @Field("grant_type") grantType: String,
        @Field("code") code: String?,
        @Field("redirect_uri") redirectUri: String?,
        @Field("client_id") clientId: String?,
        @Field("code_verifier") codeVerifier: String?,
        @Field("refresh_token") refreshToken: String?,
    ): TokenResponse
}
