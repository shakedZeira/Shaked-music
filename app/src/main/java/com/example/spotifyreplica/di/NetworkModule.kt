package com.example.spotifyreplica.di

import com.example.spotifyreplica.BuildConfig
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Shared, unauthenticated network plumbing: the Moshi instance and the Retrofit client for the
 * Spotify Accounts token endpoint (which must NOT carry a bearer token).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    @Named(QUALIFIER_AUTH)
    fun provideAuthOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideSpotifyAuthService(
        @Named(QUALIFIER_AUTH) authOkHttpClient: OkHttpClient,
        moshi: Moshi,
    ): SpotifyAuthService = Retrofit.Builder()
        .baseUrl(accountsBaseUrl())
        .client(authOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyAuthService::class.java)

    /**
     * Derives the accounts base URL (`https://accounts.spotify.com/`) from the configured token
     * URL so there is a single source of truth in `BuildConfig`.
     */
    internal fun accountsBaseUrl(): String {
        val tokenUrl = BuildConfig.SPOTIFY_TOKEN_URL
        val marker = "/api/"
        val index = tokenUrl.indexOf(marker)
        return if (index >= 0) tokenUrl.substring(0, index + 1) else "https://accounts.spotify.com/"
    }

    /** Qualifier names kept here so [SpotifyModule] and this module cannot drift apart. */
    internal const val QUALIFIER_AUTH = "spotifyAuthClient"
    internal const val QUALIFIER_SPOTIFY = "spotifyApiClient"
}
