package com.example.spotifyreplica

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Also serves as WorkManager's [Configuration.Provider] so `@HiltWorker` workers get their
 * dependencies injected by [HiltWorkerFactory]. Using the default WorkManager services means no
 * custom `SystemForegroundService` is needed (the library declares it with `dataSync`).
 */
@HiltAndroidApp
class SpotifyReplicaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
