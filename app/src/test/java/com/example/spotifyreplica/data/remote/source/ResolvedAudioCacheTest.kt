package com.example.spotifyreplica.data.remote.source

import com.example.spotifyreplica.domain.source.AudioContainer
import com.example.spotifyreplica.domain.source.ResolvedAudio
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * In-memory cache behaviour: stores only future-expiring entries, evicts on read once expired, and
 * never caches null-expiry sources (which is exactly what the YouTube resolver produces).
 */
class ResolvedAudioCacheTest {

    private val cache = ResolvedAudioCache()
    private var currentTime = 1_000_000L

    @Before
    fun setUp() {
        currentTime = 1_000_000L
        cache.clock = { currentTime }
    }

    @Test
    fun `stores and returns an unexpired entry`() = runTest {
        cache.put("t1", audio(expiresAtMs = currentTime + 60_000))
        assertEquals(audio(expiresAtMs = currentTime + 60_000), cache.get("t1"))
    }

    @Test
    fun `entry that expires between put and get is evicted and returns null`() = runTest {
        cache.put("t1", audio(expiresAtMs = currentTime + 1_000))
        assertEquals(1, cache.size())

        currentTime += 5_000

        assertNull(cache.get("t1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `null-expiry entries are never stored`() = runTest {
        cache.put("t1", ResolvedAudio("http://x", AudioContainer.M4A, 128, expiresAtMs = null))
        assertNull(cache.get("t1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `past-expiry entries are rejected on put`() = runTest {
        cache.put("t1", audio(expiresAtMs = currentTime - 1))
        assertEquals(0, cache.size())
        assertNull(cache.get("t1"))
    }

    @Test
    fun `absent and blank keys return null`() = runTest {
        assertNull(cache.get("missing"))
        assertNull(cache.get(""))
        cache.put("", audio(expiresAtMs = currentTime + 100))
        assertEquals(0, cache.size())
    }

    private fun audio(expiresAtMs: Long?) = ResolvedAudio(
        url = "http://audio.example/track",
        container = AudioContainer.M4A,
        bitrateKbps = 256,
        expiresAtMs = expiresAtMs,
    )
}