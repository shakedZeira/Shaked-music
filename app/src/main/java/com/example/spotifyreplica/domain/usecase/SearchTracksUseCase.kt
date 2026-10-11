package com.example.spotifyreplica.domain.usecase

import com.example.spotifyreplica.domain.model.Page
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.repository.MusicCatalogRepository
import javax.inject.Inject

/**
 * Searches the Spotify catalog for tracks, one page at a time.
 *
 * The 2026 Web API only allows dev-mode apps to request **at most 10** search results per call
 * (see Appendix C of the plan), so callers never pass a limit: [PAGE_SIZE] is the only value used
 * and paging is done by advancing [offset] in steps of [PAGE_SIZE].
 *
 * Blank/whitespace queries short-circuit to an empty [Page] and never hit the network — this both
 * avoids burning the (tight) search rate limit on a keystroke and keeps the UI's empty state cheap.
 */
class SearchTracksUseCase @Inject constructor(
    private val repository: MusicCatalogRepository,
) {

    suspend operator fun invoke(query: String, offset: Int): Result<Page<Track>> {
        val trimmed = query.trim()
        val safeOffset = offset.coerceAtLeast(0)
        if (trimmed.isEmpty()) {
            return Result.success(emptyPage(safeOffset))
        }
        return repository.searchTracks(query = trimmed, offset = safeOffset, limit = PAGE_SIZE)
    }

    private fun emptyPage(offset: Int): Page<Track> = Page(
        items = emptyList(),
        offset = offset,
        limit = PAGE_SIZE,
        total = 0,
        hasMore = false,
    )

    companion object {
        /** 2026 dev-mode hard cap: `GET /search` accepts at most 10 tracks per request. */
        const val PAGE_SIZE = 10
    }
}
