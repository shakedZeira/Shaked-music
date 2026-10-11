package com.example.spotifyreplica.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed settings. Phase 0/1 only wires the DataStore plumbing; the Spotify token
 * flows are added in Phase 2 and the audio-source selector is consumed in Phase 4.
 */
@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private const val AUDIO_SOURCE_KEY = "audio_source"

        /** Compliant default: index audio already on the device instead of downloading it. */
        const val AUDIO_SOURCE_LOCAL = "local"
    }

    private val audioSourceKey = stringPreferencesKey(AUDIO_SOURCE_KEY)

    val audioSource: Flow<String> = dataStore.data
        .map { it[audioSourceKey] ?: AUDIO_SOURCE_LOCAL }
        .distinctUntilChanged()

    suspend fun setAudioSource(source: String) {
        dataStore.edit { it[audioSourceKey] = source }
    }
}
