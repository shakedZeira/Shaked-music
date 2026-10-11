package com.example.spotifyreplica.domain.source

/**
 * The result of resolving a Spotify [com.example.spotifyreplica.domain.model.Track] to a concrete,
 * playable/downloadable audio sink.
 *
 * @param url          A direct audio URL, or a `content://`/`file://` URI for local files.
 * @param container    The container/codec family of [url].
 * @param bitrateKbps  Approximate bitrate in kbps, or null when unknown (e.g. local files).
 * @param expiresAtMs  Epoch millis after which [url] is no longer guaranteed valid, or null when
 *                     the URL never expires. YouTube-derived URLs are time-limited and MUST NOT be
 *                     cached past this value — callers resolve at download time.
 */
data class ResolvedAudio(
    val url: String,
    val container: AudioContainer,
    val bitrateKbps: Int?,
    val expiresAtMs: Long?,
)