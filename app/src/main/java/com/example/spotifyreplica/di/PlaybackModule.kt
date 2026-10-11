package com.example.spotifyreplica.di

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.SessionToken
import com.example.spotifyreplica.playback.PlaybackConnection
import com.example.spotifyreplica.playback.PlaybackService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the Media3 playback layer.
 *
 * [PlaybackConnection] is application-scoped so the controller (and its [kotlinx.coroutines.flow.StateFlow]
 * of playback state) is shared by every screen and by the persistent mini player.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    /**
     * The token used to bind a [androidx.media3.session.MediaController] to
     * [PlaybackService]. Building it here keeps the service's [ComponentName] in one place.
     */
    @Provides
    @Singleton
    fun provideSessionToken(@ApplicationContext context: Context): SessionToken =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    @Provides
    @Singleton
    fun providePlaybackConnection(
        @ApplicationContext context: Context,
        sessionToken: SessionToken,
    ): PlaybackConnection = PlaybackConnection(context, sessionToken)
}
