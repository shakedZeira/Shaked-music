package com.example.spotifyreplica.data.remote.spotify

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request when an access token is available.
 * Ported from the reference project; the token is read from a cache that is kept up to date from
 * DataStore so the interceptor itself never blocks.
 */
class BearerAuthInterceptor(
    private val accessTokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = accessTokenProvider()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
