package com.example.spotifyreplica.di

import com.example.spotifyreplica.data.repository.MusicCatalogRepositoryImpl
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Spotify-backed [MusicCatalogRepository]. Phase 2 defined the interface + `@Inject`
 * implementation but never published a binding (no consumer existed until the search use case), so
 * this module supplies the missing `@Provides`.
 */
@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {

    @Provides
    @Singleton
    fun provideMusicCatalogRepository(impl: MusicCatalogRepositoryImpl): MusicCatalogRepository = impl
}
