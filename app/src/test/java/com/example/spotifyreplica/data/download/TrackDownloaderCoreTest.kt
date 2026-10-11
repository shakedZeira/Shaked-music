package com.example.spotifyreplica.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.LibraryRepository
import com.example.spotifyreplica.domain.source.AudioContainer
import com.example.spotifyreplica.domain.source.AudioSourceResolver
import com.example.spotifyreplica.domain.source.ResolvedAudio
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [TrackDownloaderCore] end to end against `file:` and synthetic HTTP sources, with a
 * real [TagWriter] and a relaxed [LibraryRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackDownloaderCoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storagePaths: StoragePaths
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storagePaths = StoragePaths(context)
        storagePaths.musicRoot().deleteRecursively()
        repository = mockk(relaxed = true)
    }

    private fun coreFor(
        resolver: AudioSourceResolver,
        client: OkHttpClient = OkHttpClient(),
    ): TrackDownloaderCore = TrackDownloaderCore(
        audioSourceResolver = resolver,
        okHttpClient = client,
        libraryRepository = repository,
        tagWriter = TagWriter(client),
        storagePaths = storagePaths,
        context = context,
    )

    @Test
    fun `copies a local file, tags it and records it in the library`() = runTest {
        val source = tempFolder.newFile("source.mp3").apply { writeBytes(minimalMp3()) }
        val track = sampleTrack(id = "local-1", artworkUrl = null)
        val core = coreFor(FakeResolver(ResolvedAudio(source.absolutePath, AudioContainer.MP3, 128, null)))

        val outcome = core.download(track, playlistId = null)

        assertTrue("expected success but was $outcome", outcome is TrackOutcome.Success)
        val success = outcome as TrackOutcome.Success
        val target = storagePaths.fileFor(null, track.id, "mp3")
        assertTrue(target.isFile)
        assertEquals(target.absolutePath, success.localPath)
        assertEquals(target.length(), success.sizeBytes)
        assertFalse(storagePaths.partFileFor(target).exists())

        val tag = AudioFileIO.read(target).tag
        assertEquals("Blinding Lights", tag.getFirst(FieldKey.TITLE))

        coVerify { repository.upsertTrack(track, target.absolutePath, any(), "mp3", 128, any()) }
    }

    @Test
    fun `handles a file scheme url`() = runTest {
        val source = tempFolder.newFile("scheme.mp3").apply { writeBytes(minimalMp3()) }
        val track = sampleTrack(id = "file-1", artworkUrl = null)
        val url = "file:///" + source.absolutePath.replace('\\', '/')
        val core = coreFor(FakeResolver(ResolvedAudio(url, AudioContainer.MP3, null, null)))

        val outcome = core.download(track, playlistId = null)

        assertTrue("expected success but was $outcome", outcome is TrackOutcome.Success)
        assertTrue(storagePaths.fileFor(null, track.id, "mp3").isFile)
    }

    @Test
    fun `downloads over http and writes the final file`() = runTest {
        val track = sampleTrack(id = "http-1", artworkUrl = null)
        val client = clientReturning(code = 200, body = minimalMp3())
        val core = coreFor(
            FakeResolver(ResolvedAudio("https://example.com/audio.mp3", AudioContainer.MP3, 128, null)),
            client,
        )

        val outcome = core.download(track, playlistId = "playlist-1")

        assertTrue("expected success but was $outcome", outcome is TrackOutcome.Success)
        val target = storagePaths.fileFor("playlist-1", track.id, "mp3")
        assertTrue(target.isFile)
        assertEquals((target.length()), (outcome as TrackOutcome.Success).sizeBytes)
        assertFalse(storagePaths.partFileFor(target).exists())
    }

    @Test
    fun `http 5xx is retryable and leaves no partial files`() = runTest {
        val track = sampleTrack(id = "retry-1")
        val core = coreFor(
            FakeResolver(ResolvedAudio("https://example.com/broken.mp3", AudioContainer.MP3, null, null)),
            clientReturning(code = 500),
        )

        val outcome = core.download(track, playlistId = null)

        assertTrue("expected retryable but was $outcome", outcome is TrackOutcome.Retryable)
        val target = storagePaths.fileFor(null, track.id, "mp3")
        assertFalse(target.exists())
        assertFalse(storagePaths.partFileFor(target).exists())
    }

    @Test
    fun `http 4xx other than retryable codes is permanent`() = runTest {
        val track = sampleTrack(id = "gone-1")
        val core = coreFor(
            FakeResolver(ResolvedAudio("https://example.com/missing.mp3", AudioContainer.MP3, null, null)),
            clientReturning(code = 404),
        )

        val outcome = core.download(track, playlistId = null)

        assertTrue("expected permanent but was $outcome", outcome is TrackOutcome.Permanent)
    }

    @Test
    fun `a null source resolves to NoSource without touching disk`() = runTest {
        val track = sampleTrack(id = "none-1")
        val core = coreFor(FakeResolver(result = null))

        val outcome = core.download(track, playlistId = null)

        assertEquals(TrackOutcome.NoSource, outcome)
        assertFalse(storagePaths.fileFor(null, track.id, "mp3").exists())
    }

    private fun clientReturning(code: Int, body: ByteArray = ByteArray(0)): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(body.toResponseBody("audio/mpeg".toMediaTypeOrNull()))
                    .build()
            }
            .build()

    private class FakeResolver(private val result: ResolvedAudio?) : AudioSourceResolver {
        override suspend fun resolve(track: Track): ResolvedAudio? = result
    }
}
