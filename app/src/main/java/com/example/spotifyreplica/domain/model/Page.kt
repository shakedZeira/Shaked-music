package com.example.spotifyreplica.domain.model

/**
 * One page of a paginated Spotify collection.
 *
 * [hasMore] is true when a subsequent request with `offset = offset + items.size` may return more
 * rows (either the API supplied a `next` link or `total` has not been reached yet).
 */
data class Page<T>(
    val items: List<T>,
    val offset: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean,
)
