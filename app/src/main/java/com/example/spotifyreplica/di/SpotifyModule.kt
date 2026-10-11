package com.example.spotifyreplica.di

import com.example.spotifyreplica.BuildConfig
import com.example.spotifyreplica.data.remote.spotify.BearerAuthInterceptor
import com.example.spotifyreplica.data.remote.spotify.SpotifyApi
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthClient
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthClientImpl
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthManager
import com.example.spotifyreplica.data.remote.spotify.SpotifyAuthService
import com.example.spotifyreplica.data.settings.SettingsManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Spotify-specific wiring: the bearer-token interceptor + authenticated API client, and the PKCE
 * auth manager/client. Mirrors the reference project's module, split so the shared Moshi/client
 * live in [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    @Provides
    @Singleton
    fun provideBearerAuthInterceptor(
        settingsManager: SettingsManager,
        @ApplicationScope scope: CoroutineScope,
    ): BearerAuthInterceptor {
        // Mirror the token into a hot cache so the interceptor (called on OkHttp threads) never
        // suspends or touches DataStore.
        val tokenCache = MutableStateFlow<String?>(null)
        scope.launch {
            settingsManager.spotifyAccessToken.collect { tokenCache.value = it }
        }
        return BearerAuthInterceptor { tokenCache.value }
    }

    @Provides
    @Singleton
    @Named(NetworkModule.QUALIFIER_SPOTIFY)
    fun provideSpotifyOkHttpClient(
        bearerAuthInterceptor: BearerAuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(bearerAuthInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideSpotifyApi(
        @Named(NetworkModule.QUALIFIER_SPOTIFY) spotifyOkHttpClient: OkHttpClient,
        moshi: Moshi,
    ): SpotifyApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL_SPOTIFY)
        .client(spotifyOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyApi::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthClient(authService: SpotifyAuthService): SpotifyAuthClient =
        SpotifyAuthClientImpl(authService)

    @Provides
    @Singleton
    fun provideSpotifyAuthManager(
        settingsManager: SettingsManager,
        authClient: SpotifyAuthClient,
    ): SpotifyAuthManager = SpotifyAuthManager(settingsManager, authClient)
}
