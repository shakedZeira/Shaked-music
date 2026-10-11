package com.example.spotifyreplica.di

import android.content.Context
import com.example.spotifyreplica.data.remote.source.LocalFileResolver
import com.example.spotifyreplica.data.remote.source.OkHttpDownloader
import com.example.spotifyreplica.data.remote.source.ResolvedAudioCache
import com.example.spotifyreplica.data.remote.source.YouTubeResolver
import com.example.spotifyreplica.data.settings.SettingsManager
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.source.AudioSourceResolver
import com.example.spotifyreplica.domain.source.ResolvedAudio
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Wires the swappable audio-sourcing engine:
 *  - `@Named("local")` -> [LocalFileResolver] (compliant default, MediaStore-backed)
 *  - `@Named("youtube")` -> [YouTubeResolver] (OPT-IN, behind `SettingsManager.audioSource`)
 *  - the unqualified singleton -> a router that reads the user's source preference and delegates.
 *
 * The router is what every other consumer injects, so the YouTube implementation stays isolated and
 * can be dropped without touching download/playback/UI code.
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideResolvedAudioCache(): ResolvedAudioCache = ResolvedAudioCache()

    /** A dedicated OkHttp client for NewPipeExtractor — independent of the Spotify clients. */
    @Provides
    @Singleton
    @Named(QUALIFIER_YOUTUBE_CLIENT)
    fun provideYouTubeOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideOkHttpDownloader(
        @Named(QUALIFIER_YOUTUBE_CLIENT) client: OkHttpClient,
    ): OkHttpDownloader = OkHttpDownloader(client)

    @Provides
    @Singleton
    @Named(QUALIFIER_SOURCE_LOCAL)
    fun provideLocalResolver(@ApplicationContext context: Context): AudioSourceResolver =
        LocalFileResolver(context)

    @Provides
    @Singleton
    @Named(QUALIFIER_SOURCE_YOUTUBE)
    fun provideYouTubeResolver(downloader: OkHttpDownloader): AudioSourceResolver =
        YouTubeResolver(downloader)

    @Provides
    @Singleton
    fun provideAudioSourceResolver(
        settingsManager: SettingsManager,
        @Named(QUALIFIER_SOURCE_LOCAL) local: AudioSourceResolver,
        @Named(QUALIFIER_SOURCE_YOUTUBE) youtube: AudioSourceResolver,
    ): AudioSourceResolver = RoutingAudioSourceResolver(settingsManager, local, youtube)

    internal const val QUALIFIER_YOUTUBE_CLIENT = "youtubeOkHttpClient"
    internal const val QUALIFIER_SOURCE_LOCAL = "local"
    internal const val QUALIFIER_SOURCE_YOUTUBE = "youtube"
}

/**
 * Routes [resolve] calls to the implementation matching `SettingsManager.audioSource` (default:
 * local). Anything that isn't explicitly `youtube` falls back to the compliant local resolver.
 */
internal class RoutingAudioSourceResolver(
    private val settingsManager: SettingsManager,
    private val localResolver: AudioSourceResolver,
    private val youtubeResolver: AudioSourceResolver,
) : AudioSourceResolver {

    override suspend fun resolve(track: Track): ResolvedAudio? {
        val selected = runCatching { settingsManager.audioSource.first() }
            .getOrDefault(SettingsManager.AUDIO_SOURCE_LOCAL)
        val resolver = if (selected == SettingsManager.AUDIO_SOURCE_YOUTUBE) {
            youtubeResolver
        } else {
            localResolver
        }
        return resolver.resolve(track)
    }
}