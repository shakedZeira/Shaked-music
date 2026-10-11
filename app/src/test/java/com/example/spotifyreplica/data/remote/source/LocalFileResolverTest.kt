package com.example.spotifyreplica.data.remote.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the MediaStore matching logic. These never touch MediaStore/Android —
 * they exercise the scoring + best-match rules [LocalFileResolver] uses.
 */
class LocalFileResolverTest {

    // --- scoreMatch: title/artist equality + normalization ------------------------------------

    @Test
    fun `exact title and exact artist score 1`() {
        val score = scoreMatch("Blinding Lights", listOf("The Weeknd"), "Blinding Lights", "The Weeknd")
        assertEquals(1.0f, score, 0.0001f)
    }

    @Test
    fun `case punctuation and separators are normalized away`() {
        val score = scoreMatch(
            queryTitle = "Blinding! LIGHTS...",
            queryArtists = listOf("The-Weeknd_"),
            mediaTitle = "Blinding Lights",
            mediaArtist = "The Weeknd",
        )
        assertEquals(1.0f, score, 0.0001f)
    }

    @Test
    fun `parenthetical edition suffix still fuzzy-matches`() {
        val score = scoreMatch(
            queryTitle = "Blinding Lights",
            queryArtists = listOf("The Weeknd"),
            mediaTitle = "Blinding Lights (2020)",
            mediaArtist = "The Weeknd",
        )
        assertTrue("expected a fuzzy match (got $score)", score >= MATCH_THRESHOLD)
    }

    @Test
    fun `exact title with missing artist metadata still scores above threshold`() {
        val score = scoreMatch("Blinding Lights", listOf("The Weeknd"), "Blinding Lights", null)
        assertEquals(0.7f, score, 0.0001f)
        assertTrue(score >= MATCH_THRESHOLD)
    }

    // --- scoreMatch: fuzzy threshold ----------------------------------------------------------

    @Test
    fun `similar title plus matching artist passes the fuzzy threshold`() {
        val score = scoreMatch(
            "Blinding Lights",
            listOf("The Weeknd"),
            "Blinding Lights (Remastered 2020)",
            "The Weeknd",
        )
        assertTrue("expected a fuzzy match (got $score)", score >= MATCH_THRESHOLD)
    }

    @Test
    fun `unrelated title scores zero even with matching artist`() {
        val score = scoreMatch("Starboy", listOf("The Weeknd"), "Blinding Lights", "The Weeknd")
        assertEquals(0f, score, 0.0001f)
    }

    @Test
    fun `empty or blank input scores zero`() {
        assertEquals(0f, scoreMatch("", listOf(), "", ""), 0f)
        assertEquals(0f, scoreMatch("   ", listOf("x"), "   ", "x"), 0f)
        assertEquals(0f, scoreMatch("Song", listOf("Artist"), null, null), 0f)
    }

    // --- bestMatch: selection + tie-breaking --------------------------------------------------

    @Test
    fun `bestMatch picks the highest scoring candidate`() {
        val candidates = listOf(
            candidate("content://a", "Blinding Lights (remaster)", "The Weeknd", 100L),
            candidate("content://b", "Blinding Lights", "The Weeknd", 50L),
        )
        val best = bestMatch("Blinding Lights", listOf("The Weeknd"), candidates)
        assertNotNull(best)
        assertEquals("content://b", best!!.uri)
    }

    @Test
    fun `bestMatch breaks score ties toward most recently added`() {
        val candidates = listOf(
            candidate("content://old", "Blinding Lights", "The Weeknd", dateAddedMs = 1_000L),
            candidate("content://new", "Blinding Lights", "The Weeknd", dateAddedMs = 9_000L),
        )
        val best = bestMatch("Blinding Lights", listOf("The Weeknd"), candidates)
        assertEquals("content://new", best!!.uri)
    }

    @Test
    fun `bestMatch returns null when nothing clears the threshold`() {
        val candidates = listOf(
            candidate("content://a", "Some Other Song", "Someone Else", 100L),
            candidate("content://b", "Another Track", null, 200L),
        )
        assertNull(bestMatch("Totally Unrelated Title", listOf("Unknown Artist"), candidates))
    }

    @Test
    fun `bestMatch returns null for an empty candidate list`() {
        assertNull(bestMatch("Blinding Lights", listOf("The Weeknd"), emptyList()))
    }

    @Test
    fun `a title-only match survives when the device has no artist info`() {
        val candidates = listOf(candidate("content://k", "Blinding Lights", null, 5L))
        val best = bestMatch("Blinding Lights", listOf("The Weeknd"), candidates)
        assertNotNull(best)
        assertEquals("content://k", best!!.uri)
    }

    private fun candidate(uri: String, title: String?, artist: String?, dateAddedMs: Long) =
        MediaTrackCandidate(uri = uri, title = title, artist = artist, dateAddedMs = dateAddedMs)
}