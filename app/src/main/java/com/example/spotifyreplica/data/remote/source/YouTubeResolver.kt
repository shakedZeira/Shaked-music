package com.example.spotifyreplica.data.remote.source

import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.source.AudioContainer
import com.example.spotifyreplica.domain.source.AudioSourceResolver
import com.example.spotifyreplica.domain.source.ResolvedAudio
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * OPT-IN resolver backed by NewPipeExtractor's YouTube service. It is ONLY consulted when the
 * user has explicitly selected `SettingsManager.AUDIO_SOURCE_YOUTUBE` (off by default) — see
 * [com.example.spotifyreplica.di.SourceModule]. Personal/sideload-only per implementation plan Q3.
 *
 * Semantics:
 *  - Searches `"<title> <artists> audio"` and opens the first stream result.
 *  - Keeps a stub/full reference of plan §14 Q4: prefer M4A/AAC audio-only (itags 140/141), else
 *    Opus (249/250/251) — implemented by the pure [selectBestAudioStream].
 *  - NEVER reads video-only streams ([StreamExtractor.getAudioStreams] returns audio-only already).
 *  - NewPipe does not expose stream URL expiry, so [ResolvedAudio.expiresAtMs] is null and the
 *    result is (correctly) never cached by [ResolvedAudioCache].
 *  - Resolves the URL on every call; the caller (a download worker) invokes [resolve] inside
 *    `doWork()` because these URLs expire quickly.
 */
class YouTubeResolver @Inject constructor(
    private val downloader: Downloader,
) : AudioSourceResolver {

    override suspend fun resolve(track: Track): ResolvedAudio? = withContext(Dispatchers.IO) {
        runCatching {
            NewPipeBridge.ensureInitialized(downloader)

            val service = ServiceList.YouTube
            val page = service.getSearchExtractor(buildSearchQuery(track)).getInitialPage()

            // Prefer genuine stream results (skip channels/playlists that occasionally rank first).
            val streamItem = page.items.asSequence()
                .filterIsInstance<StreamInfoItem>()
                .firstOrNull()
                ?: page.items.firstOrNull()
                ?: return@runCatching null

            val streamExtractor = service.getStreamExtractor(streamItem.url)
            streamExtractor.fetchPage()

            val best = selectBestAudioStream(streamExtractor.getAudioStreams())
                ?: return@runCatching null

            val url = best.content.takeIf { best.isUrl() } ?: return@runCatching null
            ResolvedAudio(
                url = url,
                container = containerFor(best),
                bitrateKbps = best.getAverageBitrate().takeIf { it > 0 }?.div(1000),
                expiresAtMs = null, // NewPipe does not expose stream expiry -> never cached.
            )
        }.getOrNull()
    }
}

/**
 * NewPipe.init is a global, once-only step (extractors NPE when no downloader is registered).
 * This bridge keeps the guard in one place; repeated calls are cheap no-ops.
 */
internal object NewPipeBridge {
    @Volatile
    private var initialized = false

    fun ensureInitialized(downloader: Downloader): Downloader {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader, Localization.DEFAULT)
                    initialized = true
                }
            }
        }
        return downloader
    }
}

/** Pure query builder: `"<title> <artists joined> audio"`. */
internal fun buildSearchQuery(track: Track): String {
    val artistPart = track.artists.joinToString(" ")
    return listOf(track.title, artistPart, "audio")
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
}

/**
 * Pure audio-only stream selection: rank by container family (M4A/AAC first, then Opus, then MP3,
 * everything else last) and within a family prefer the higher average bitrate. Ties resolve to
 * whichever NewPipe listed first. Never considers video-only streams — only [AudioStream]s that
 * are real URLs are eligible.
 */
internal fun selectBestAudioStream(streams: List<AudioStream>): AudioStream? =
    streams
        .filter { it.isUrl() }
        .minWithOrNull(
            compareBy<AudioStream> { streamRank(it) }
                .thenByDescending { it.getAverageBitrate() },
        )

internal fun streamRank(stream: AudioStream): Int {
    val itag = stream.getItag()
    val format = stream.getFormat()
    return when {
        itag == 140 || itag == 141 || format == MediaFormat.M4A -> 0
        itag == 249 || itag == 250 || itag == 251 ||
            format == MediaFormat.OPUS || format == MediaFormat.WEBMA_OPUS -> 1
        format == MediaFormat.MP3 -> 2
        else -> 3
    }
}

/**
 * Maps a chosen [AudioStream] to our domain container. Falls back to itag inspection when the
 * media format is missing/unknown; defaults to M4A (widely supported by ExoPlayer).
 */
internal fun containerFor(stream: AudioStream): AudioContainer {
    val format = stream.getFormat()
    return when (format) {
        MediaFormat.M4A -> AudioContainer.M4A
        MediaFormat.OPUS, MediaFormat.WEBMA_OPUS, MediaFormat.WEBMA -> AudioContainer.WEBM_OPUS
        MediaFormat.MP3 -> AudioContainer.MP3
        else -> when (stream.getItag()) {
            140, 141 -> AudioContainer.M4A
            249, 250, 251 -> AudioContainer.WEBM_OPUS
            else -> AudioContainer.M4A
        }
    }
}