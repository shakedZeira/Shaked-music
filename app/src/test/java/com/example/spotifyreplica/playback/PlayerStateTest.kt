package com.example.spotifyreplica.playback

import com.example.spotifyreplica.domain.model.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [PlayerState]'s derived helpers. [PlayerState] deliberately carries no Media3
 * dependency (its repeat-mode constants mirror `Player.REPEAT_MODE_*`), so these run as plain JUnit.
 */
class PlayerStateTest {

    // --- progressFraction --------------------------------------------------------------------

    @Test
    fun `progress is zero when the duration is unknown`() {
        assertEquals(0f, PlayerState(positionMs = 5_000L, durationMs = 0L).progressFraction, 0f)
    }

    @Test
    fun `progress reflects the position over the duration`() {
        assertEquals(
            0.25f,
            PlayerState(positionMs = 50_000L, durationMs = 200_000L).progressFraction,
            0.0001f,
        )
    }

    @Test
    fun `progress is clamped to one when the position overshoots`() {
        assertEquals(
            1f,
            PlayerState(positionMs = 999_000L, durationMs = 200_000L).progressFraction,
            0f,
        )
    }

    @Test
    fun `negative positions clamp to zero`() {
        assertEquals(0f, PlayerState(positionMs = -10L, durationMs = 200_000L).progressFraction, 0f)
    }

    // --- seekTarget --------------------------------------------------------------------------

    @Test
    fun `seekTarget fast-forwards within the duration`() {
        assertEquals(
            60_000L,
            PlayerState.seekTarget(positionMs = 50_000L, durationMs = 200_000L, deltaMs = 10_000L),
        )
    }

    @Test
    fun `seekTarget clamps a forward jump at the duration`() {
        assertEquals(
            200_000L,
            PlayerState.seekTarget(positionMs = 195_000L, durationMs = 200_000L, deltaMs = 10_000L),
        )
    }

    @Test
    fun `seekTarget clamps a rewind at zero`() {
        assertEquals(
            0L,
            PlayerState.seekTarget(positionMs = 5_000L, durationMs = 200_000L, deltaMs = -10_000L),
        )
    }

    @Test
    fun `seekTarget leaves an unknown duration unclamped above`() {
        assertEquals(
            20_000L,
            PlayerState.seekTarget(positionMs = 10_000L, durationMs = 0L, deltaMs = 10_000L),
        )
    }

    @Test
    fun `seekTarget treats a negative position as zero`() {
        assertEquals(
            10_000L,
            PlayerState.seekTarget(positionMs = -5_000L, durationMs = 200_000L, deltaMs = 10_000L),
        )
    }

    // --- hasMedia ----------------------------------------------------------------------------

    @Test
    fun `hasMedia is false until a track is loaded`() {
        assertFalse(PlayerState().hasMedia)
        assertTrue(PlayerState(currentTrack = sampleTrack("a")).hasMedia)
    }

    // --- hasNext / hasPrevious ---------------------------------------------------------------

    @Test
    fun `next and previous are disabled without a current index`() {
        val state = PlayerState(
            currentTrack = sampleTrack("a"),
            queue = listOf(sampleTrack("a"), sampleTrack("b")),
            currentIndex = -1,
        )
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `at the start of a queue next is enabled and previous is not`() {
        val state = queueOf3(currentIndex = 0, repeatMode = PlayerState.REPEAT_MODE_OFF)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `in the middle of a queue both next and previous are enabled`() {
        val state = queueOf3(currentIndex = 1, repeatMode = PlayerState.REPEAT_MODE_OFF)
        assertTrue(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    @Test
    fun `at the end of a queue next is disabled and previous is enabled`() {
        val state = queueOf3(currentIndex = 2, repeatMode = PlayerState.REPEAT_MODE_OFF)
        assertFalse(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    @Test
    fun `a single-item queue disables both directions when not repeating`() {
        val state = PlayerState(
            currentTrack = sampleTrack("a"),
            queue = listOf(sampleTrack("a")),
            currentIndex = 0,
            repeatMode = PlayerState.REPEAT_MODE_OFF,
        )
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `repeat-all keeps both directions enabled at the queue boundaries`() {
        val start = queueOf3(currentIndex = 0, repeatMode = PlayerState.REPEAT_MODE_ALL)
        assertTrue(start.hasNext)
        assertTrue(start.hasPrevious)

        val end = queueOf3(currentIndex = 2, repeatMode = PlayerState.REPEAT_MODE_ALL)
        assertTrue(end.hasNext)
        assertTrue(end.hasPrevious)
    }

    @Test
    fun `repeat-all keeps a single-item queue navigable`() {
        val state = PlayerState(
            currentTrack = sampleTrack("a"),
            queue = listOf(sampleTrack("a")),
            currentIndex = 0,
            repeatMode = PlayerState.REPEAT_MODE_ALL,
        )
        assertTrue(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    @Test
    fun `repeat-one still honours queue boundaries for manual skips`() {
        val state = queueOf3(currentIndex = 2, repeatMode = PlayerState.REPEAT_MODE_ONE)
        assertFalse(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    private fun queueOf3(currentIndex: Int, repeatMode: Int) = PlayerState(
        currentTrack = sampleTrack("tracks[$currentIndex]"),
        queue = listOf(sampleTrack("a"), sampleTrack("b"), sampleTrack("c")),
        currentIndex = currentIndex,
        repeatMode = repeatMode,
    )

    private fun sampleTrack(id: String) = LibraryTrack(
        spotifyId = id,
        title = "Song $id",
        artists = listOf("Artist"),
        album = "Album",
        artworkUrl = null,
        localPath = "/data/user/0/app/files/$id.opus",
        sizeBytes = 1_024L,
        durationMs = 200_000L,
        container = "OGG",
        bitrateKbps = 320,
    )
}
