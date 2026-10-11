package com.example.spotifyreplica.data.remote.source

import com.example.spotifyreplica.domain.model.Track
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.downloader.Downloader

/**
 * Optional smoke test for the YouTube resolver — NO network touch. It proves:
 *  1. [YouTubeResolver] can be constructed and [NewPipeBridge] initializes NewPipe exactly once;
 *  2. A failing extraction (offline downloader) is handled by [YouTubeResolver.resolve], returning
 *     null instead of throwing (the contract the download worker relies on).
 *
 * Robolectric is required because NewPipeExtractor reads locales/resources during init
 * (isIncludeAndroidResources is enabled in the module's testOptions).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeResolverSmokeTest {

    private class OfflineDownloader : Downloader() {
        override fun execute(
            request: org.schabi.newpipe.extractor.downloader.Request,
        ): org.schabi.newpipe.extractor.downloader.Response {
            throw IOException("offline — tests never touch the network")
        }
    }

    @Test
    fun `offline resolution returns null and double init is a no-op`() = runTest {
        val downloader = OfflineDownloader()

        // First init, then an implicit second init inside resolve() — must stay a safe no-op.
        NewPipeBridge.ensureInitialized(downloader)
        NewPipeBridge.ensureInitialized(downloader)

        val resolver = YouTubeResolver(downloader)
        val track = Track(
            id = "yt-smoke",
            title = "Some Song That Does Not Exist On YouTube In Tests",
            artists = listOf("Some Artist"),
            album = "Album",
            artworkUrl = null,
            durationMs = 180_000,
            isrc = null,
            spotifyUri = null,
        )

        assertNull("a failed extraction must surface as null, not throw", resolver.resolve(track))
    }
}