package com.example.spotifyreplica.domain.source

/**
 * The container/codec family a resolved audio source is delivered in.
 *
 * The download layer uses this to pick a file extension and, where relevant, metadata
 * tagging strategy (M4A vs MP3 vs WebM/Opus all write tags differently).
 */
enum class AudioContainer {
    /** AAC in an MP4 container (YouTube itags 140/141) — preferred for tagging + ExoPlayer. */
    M4A,

    /** Opus in a WebM/OGG-style container (YouTube itags 249/250/251). */
    WEBM_OPUS,

    /** MP3. */
    MP3,

    /** A local device file (already on disk); no download copy is required. */
    LOCAL,
}