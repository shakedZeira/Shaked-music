package com.example.spotifyreplica.di

import android.content.Context
import androidx.room.Room
import com.example.spotifyreplica.data.db.AppDatabase
import com.example.spotifyreplica.data.db.dao.DownloadDao
import com.example.spotifyreplica.data.db.dao.PlaylistDao
import com.example.spotifyreplica.data.db.dao.TrackDao
import com.example.spotifyreplica.data.repository.LibraryRepositoryImpl
import com.example.spotifyreplica.domain.repository.LibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "spotify_replica.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideLibraryRepository(
        trackDao: TrackDao,
        playlistDao: PlaylistDao,
        downloadDao: DownloadDao
    ): LibraryRepository = LibraryRepositoryImpl(trackDao, playlistDao, downloadDao)
}
