package com.example.spotifyreplica.domain.source

import com.example.spotifyreplica.domain.model.Track

/**
 * The swappable audio-sourcing engine.
 *
 * Implementations resolve a Spotify [Track]'s metadata into a playable/downloadable audio URL.
 * The default implementation indexes audio already on the device (`LocalFileResolver`); the
 * YouTube-backed implementation is OPT-IN and isolated (see `data/remote/source/YouTubeResolver`).
 *
 * Resolving is deliberately a *lookup*, not a download: the caller (a download worker) invokes
 * [resolve] inside `doWork()` because YouTube stream URLs expire quickly.
 *
 * @return The resolved audio, or null when no source could be found for [track].
 */
interface AudioSourceResolver {
    suspend fun resolve(track: Track): ResolvedAudio?
}