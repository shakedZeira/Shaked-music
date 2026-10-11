package com.example.spotifyreplica.di

import android.content.Context
import androidx.work.WorkManager
import com.example.spotifyreplica.data.download.DownloadQueue
import com.example.spotifyreplica.data.repository.DownloadRepositoryImpl
import com.example.spotifyreplica.domain.repository.DownloadRepository
import com.example.spotifyreplica.domain.repository.LibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Download-engine wiring:
 *  - [WorkManager], whose real factory is the Hilt-provided worker factory (see
 *    `SpotifyReplicaApplication`). No custom service is needed; WorkManager's default
 *    `SystemJobService`/`SystemForegroundService` are used.
 *  - a dedicated streaming [OkHttpClient] (long-ish read/write timeouts) so downloads never share
 *    the authenticated Spotify client.
 *  - the [DownloadRepository] binding used by the UI.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideDownloadRepository(
        downloadQueue: DownloadQueue,
        libraryRepository: LibraryRepository,
    ): DownloadRepository = DownloadRepositoryImpl(downloadQueue, libraryRepository)
}
