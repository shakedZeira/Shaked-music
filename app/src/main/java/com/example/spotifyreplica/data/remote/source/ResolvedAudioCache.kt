package com.example.spotifyreplica.data.remote.source

import com.example.spotifyreplica.domain.source.ResolvedAudio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of [ResolvedAudio] keyed by Spotify track id, used so the download worker does not
 * re-hit an I/O resolver for a track it already resolved this process run.
 *
 * Safety rules (per implementation plan):
 *  - Only entries with a non-null [ResolvedAudio.expiresAtMs] dated in the FUTURE are stored.
 *  - `get` returns null (and evicts) for absent/expired entries.
 *  - YouTubeResolver currently always returns `expiresAtMs = null`, so nothing from YouTube is ever
 *    cached. That is expected and safe: NewPipe does not expose stream URL expiry.
 */
@Singleton
class ResolvedAudioCache @Inject constructor() {

    private val lock = Any()
    private val cache = LinkedHashMap<String, ResolvedAudio>()

    /** Injectable clock so tests can simulate expiry deterministically. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /** @return the cached, non-expired audio for [spotifyId], or null when absent/expired. */
    suspend fun get(spotifyId: String): ResolvedAudio? {
        if (spotifyId.isBlank()) return null
        val now = clock()
        synchronized(lock) {
            val audio = cache[spotifyId] ?: return null
            val expiresAtMs = audio.expiresAtMs
            if (expiresAtMs == null || expiresAtMs <= now) {
                cache.remove(spotifyId)
                return null
            }
            return audio
        }
    }

    /** Stores [audio] only when it carries a future [ResolvedAudio.expiresAtMs]. */
    suspend fun put(spotifyId: String, audio: ResolvedAudio) {
        if (spotifyId.isBlank()) return
        val expiresAtMs = audio.expiresAtMs ?: return
        if (expiresAtMs <= clock()) return
        synchronized(lock) {
            cache[spotifyId] = audio
        }
    }

    internal fun size(): Int = synchronized(lock) { cache.size }
}