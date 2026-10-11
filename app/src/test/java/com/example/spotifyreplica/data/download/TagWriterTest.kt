package com.example.spotifyreplica.data.download

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies jaudiotagger tag writing against a generated MP3. No network is used (artworkUrl is null
 * in the assertions that matter), so this runs as a plain JVM test.
 */
class TagWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val writer = TagWriter(OkHttpClient())

    @Test
    fun `stamps title artist and album on an mp3`() = runTest {
        val file = tempFolder.newFile("song.mp3")
        file.writeBytes(minimalMp3())

        val track = sampleTrack(
            title = "Blinding Lights",
            artists = listOf("The Weeknd"),
            album = "After Hours",
        )

        assertTrue(writer.stampMetadata(file, track, artworkUrl = null))

        val tag = AudioFileIO.read(file).tag
        assertEquals("Blinding Lights", tag.getFirst(FieldKey.TITLE))
        assertEquals("The Weeknd", tag.getFirst(FieldKey.ARTIST))
        assertEquals("After Hours", tag.getFirst(FieldKey.ALBUM))
    }

    @Test
    fun `joins multiple artists into the artist field`() = runTest {
        val file = tempFolder.newFile("collab.mp3")
        file.writeBytes(minimalMp3())

        val track = sampleTrack(artists = listOf("Artist A", "Artist B"))
        assertTrue(writer.stampMetadata(file, track, artworkUrl = null))

        val tag = AudioFileIO.read(file).tag
        assertEquals("Artist A, Artist B", tag.getFirst(FieldKey.ARTIST))
    }

    @Test
    fun `missing file is reported as not stamped and never throws`() = runTest {
        val missing = File(tempFolder.root, "does-not-exist.mp3")
        assertFalse(writer.stampMetadata(missing, sampleTrack(), artworkUrl = null))
    }
}
