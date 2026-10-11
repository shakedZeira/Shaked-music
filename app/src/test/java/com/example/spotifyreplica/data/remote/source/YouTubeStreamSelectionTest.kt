package com.example.spotifyreplica.data.remote.source

import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.source.AudioContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream

/**
 * Pure-function tests for the YouTube stream selection: container-family preference (M4A/AAC over
 * Opus), bitrate tie-breaking within a family, skipping non-URL streams, and never picking
 * video-only/unknown formats.
 */
class YouTubeStreamSelectionTest {

    @Test
    fun `prefers m4a itag 140 over opus itag 251`() {
        val streams = listOf(audio(251, MediaFormat.WEBMA_OPUS, 160), audio(140, MediaFormat.M4A, 128))
        val best = selectBestAudioStream(streams)
        assertEquals(140, best!!.getItag())
        assertSame(AudioContainer.M4A, containerFor(best))
    }

    @Test
    fun `prefers m4a itag 141 over opus itag 249`() {
        val streams = listOf(audio(249, MediaFormat.WEBMA_OPUS, 50), audio(141, MediaFormat.M4A, 256))
        val best = selectBestAudioStream(streams)
        assertEquals(141, best!!.getItag())
    }

    @Test
    fun `within the m4a family higher bitrate wins`() {
        val streams = listOf(
            audio(140, MediaFormat.M4A, 128),
            audio(141, MediaFormat.M4A, 256),
        )
        val best = selectBestAudioStream(streams)
        assertEquals(141, best!!.getItag())
    }

    @Test
    fun `within the opus family higher bitrate wins`() {
        val streams = listOf(
            audio(249, MediaFormat.WEBMA_OPUS, 50),
            audio(251, MediaFormat.WEBMA_OPUS, 160),
        )
        assertTrue(selectBestAudioStream(streams)!!.getItag() == 251)
    }

    @Test
    fun `opus is chosen when no m4a is available`() {
        val streams = listOf(audio(251, MediaFormat.WEBMA_OPUS, 160))
        val best = selectBestAudioStream(streams)
        assertEquals(251, best!!.getItag())
        assertSame(AudioContainer.WEBM_OPUS, containerFor(best))
    }

    @Test
    fun `video-only or unknown formats are ranked last and never chosen while audio exists`() {
        val videoOnly = audio(137, MediaFormat.MPEG_4, 0, url = false) // even when in the list
        val opus = audio(251, MediaFormat.WEBMA_OPUS, 160)
        val best = selectBestAudioStream(listOf(videoOnly, opus))
        assertEquals(251, best!!.getItag())
    }

    @Test
    fun `returns null for an empty stream list`() {
        assertNull(selectBestAudioStream(emptyList()))
    }

    @Test
    fun `skips non-URL streams and returns null when only those exist`() {
        val nonUrl = audio(140, MediaFormat.M4A, 128, url = false)
        assertNull(selectBestAudioStream(listOf(nonUrl)))
    }

    @Test
    fun `container mapping covers m4a opus and mp3`() {
        assertSame(AudioContainer.M4A, containerFor(audio(141, MediaFormat.M4A, 256)))
        assertSame(AudioContainer.WEBM_OPUS, containerFor(audio(251, MediaFormat.WEBMA_OPUS, 160)))
        assertSame(AudioContainer.WEBM_OPUS, containerFor(audio(251, MediaFormat.OPUS, 160)))
        assertSame(AudioContainer.MP3, containerFor(audio(100, MediaFormat.MP3, 128)))
    }

    @Test
    fun `search query combines title artists and audio marker`() {
        val track = Track(
            id = "t1",
            title = "Blinding Lights",
            artists = listOf("The Weeknd", "Daft Punk"),
            album = "After Hours",
            artworkUrl = null,
            durationMs = 200_000,
            isrc = null,
            spotifyUri = null,
        )
        assertEquals("Blinding Lights The Weeknd Daft Punk audio", buildSearchQuery(track))
    }

    private fun audio(itag: Int, format: MediaFormat, bitrateKbps: Int, url: Boolean = true): AudioStream =
        AudioStream.Builder()
            .setId(itag.toString())
            .setContent(if (url) "https://example.com/$itag" else "raw-$itag", url)
            .setMediaFormat(format)
            .setItagItem(ItagItem(itag, ItagItem.ItagType.AUDIO, format, bitrateKbps * 1000))
            .setAverageBitrate(bitrateKbps * 1000)
            .build()
}