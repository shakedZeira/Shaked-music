package com.example.spotifyreplica.data.download

import com.example.spotifyreplica.domain.model.Track

/** Size of one MPEG1 Layer III 128 kbps / 44.1 kHz frame (144 * 128000 / 44100 ≈ 417). */
internal const val MP3_FRAME_SIZE = 417

/**
 * Builds a syntactically valid MP3 stream: every frame starts with the sync + MPEG1 Layer III
 * header `FF FB 90 00`, followed by zeroed payload. Enough frames for jaudiotagger to parse an
 * audio header and to exercise tag writing, without shipping binary fixtures.
 */
internal fun minimalMp3(frames: Int = 48): ByteArray {
    require(frames > 0) { "frames must be positive" }
    val bytes = ByteArray(frames * MP3_FRAME_SIZE)
    var offset = 0
    repeat(frames) {
        bytes[offset] = 0xFF.toByte()
        bytes[offset + 1] = 0xFB.toByte()
        bytes[offset + 2] = 0x90.toByte()
        bytes[offset + 3] = 0x00
        offset += MP3_FRAME_SIZE
    }
    return bytes
}

/** A representative track with every field populated, for round-trip and tagging tests. */
internal fun sampleTrack(
    id: String = "track-1",
    title: String = "Blinding Lights",
    artists: List<String> = listOf("The Weeknd"),
    album: String = "After Hours",
    artworkUrl: String? = null,
    durationMs: Long = 200_000L,
    isrc: String? = "USUG11904206",
    spotifyUri: String? = "spotify:track:$id",
): Track = Track(
    id = id,
    title = title,
    artists = artists,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isrc = isrc,
    spotifyUri = spotifyUri,
)
