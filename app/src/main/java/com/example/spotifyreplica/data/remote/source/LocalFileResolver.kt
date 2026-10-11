package com.example.spotifyreplica.data.remote.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.spotifyreplica.domain.model.Track
import com.example.spotifyreplica.domain.source.AudioContainer
import com.example.spotifyreplica.domain.source.AudioSourceResolver
import com.example.spotifyreplica.domain.source.ResolvedAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The compliant, default [AudioSourceResolver]: it matches a Spotify [Track] against audio that is
 * ALREADY on the device via [MediaStore.Audio]. No content is downloaded, which keeps the app fully
 * legal. The scoring/match logic lives in the internal pure functions [scoreMatch] / [bestMatch]
 * so it is unit-testable without a MediaStore.
 */
@Singleton
class LocalFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioSourceResolver {

    override suspend fun resolve(track: Track): ResolvedAudio? = withContext(Dispatchers.IO) {
        val best = bestMatch(track.title, track.artists, queryMediaAudioCandidates())
            ?: return@withContext null
        ResolvedAudio(
            url = best.uri,
            container = AudioContainer.LOCAL,
            bitrateKbps = null,
            expiresAtMs = null,
        )
    }

    /**
     * Reads id/title/artist/date-added for every audio file MediaStore knows about. Ties are broken
     * in favor of most-recently-added by [bestMatch], so we request newest-first for free.
     */
    private fun queryMediaAudioCandidates(): List<MediaTrackCandidate> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val cursor = runCatching {
            context.contentResolver.query(collection, projection, null, null, sortOrder)
        }.getOrNull() ?: return emptyList()

        val candidates = ArrayList<MediaTrackCandidate>(cursor.count.coerceAtLeast(0))
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val addedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                candidates += MediaTrackCandidate(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = titleCol.takeIf { c -> c >= 0 }?.let { c -> it.getString(c) },
                    artist = artistCol.takeIf { c -> c >= 0 }?.let { c -> it.getString(c) },
                    dateAddedMs = if (addedCol >= 0) it.getLong(addedCol) * 1000L else 0L,
                )
            }
        }
        return candidates
    }
}

/**
 * A single row of MediaStore audio, kept dumb so the matching logic ([bestMatch]) is a pure
 * transform over [MediaTrackCandidate]s.
 */
internal data class MediaTrackCandidate(
    val uri: String,
    val title: String?,
    val artist: String?,
    val dateAddedMs: Long = 0L,
)

/** Minimum title fuzzy-similarity (0..1, see [titleSimilarity]) for a non-exact title to count. */
internal const val MIN_TITLE_SIMILARITY = 0.5f

/** Minimum total score (0..1) for a candidate to be considered a match by [bestMatch]. */
internal const val MATCH_THRESHOLD = 0.5f

/**
 * Normalizes text for comparison: lowercase, strip punctuation/non-letter glyphs, collapse runs of
 * whitespace. Keeps inner spaces so word-boundary bigrams survive for [titleSimilarity].
 */
internal fun normalizeForMatch(text: String?): String {
    if (text == null) return ""
    return text
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Dice-coefficient over character bigrams. Has a strong sense of "similar but not equal" while
 * returning 0.0 for unrelated titles.
 */
internal fun titleSimilarity(a: String, b: String): Float {
    if (a == b) return 1f
    if (a.isEmpty() || b.isEmpty()) return 0f
    val aBigrams = a.windowed(2).toSet()
    val bBigrams = b.windowed(2).toSet()
    if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0f
    val intersection = aBigrams.intersect(bBigrams).size.toFloat()
    return (2f * intersection) / (aBigrams.size + bBigrams.size)
}

/**
 * Scores how well a MediaStore row matches the query track. Title dominates (0.7 weight); artist
 * overlap (fraction of query artists matched by any device artist token) adds up to 0.3. Returns
 * 0.0 when the title is too dissimilar to even consider a match.
 */
internal fun scoreMatch(
    queryTitle: String,
    queryArtists: List<String>,
    mediaTitle: String?,
    mediaArtist: String?,
): Float {
    val qTitle = normalizeForMatch(queryTitle)
    val mTitle = normalizeForMatch(mediaTitle)
    if (qTitle.isEmpty() || mTitle.isEmpty()) return 0f

    val titleScore: Float = if (mTitle == qTitle) {
        1f
    } else {
        val similarity = titleSimilarity(qTitle, mTitle)
        if (similarity < MIN_TITLE_SIMILARITY) 0f else similarity
    }
    if (titleScore == 0f) return 0f

    val qArtists = queryArtists.map { normalizeForMatch(it) }.filter { it.isNotEmpty() }
    val mArtists = normalizeForMatch(mediaArtist)
        .split(' ')
        .filter { it.isNotEmpty() }

    val artistScore: Float = when {
        qArtists.isEmpty() || mArtists.isEmpty() -> 0f
        else -> {
            // Count how many QUERY artists are covered by any device token (capped at 1.0 by the
            // division). Substring checks require tokens of length >= 3 to avoid short-token
            // false positives (e.g. "we" inside "weeknd").
            val matchedQueryArtists = qArtists.count { q ->
                mArtists.any { m ->
                    q == m ||
                        (q.length >= 3 && m.length >= 3 && (q.contains(m) || m.contains(q)))
                }
            }
            matchedQueryArtists.toFloat() / qArtists.size
        }
    }

    return (titleScore * 0.7f) + (artistScore * 0.3f)
}

/**
 * Returns the best [MediaTrackCandidate] for the query track, or null when nothing clears
 * [MATCH_THRESHOLD]. Ties (same score) are broken in favor of the most-recently-added file.
 */
internal fun bestMatch(
    queryTitle: String,
    queryArtists: List<String>,
    candidates: List<MediaTrackCandidate>,
): MediaTrackCandidate? =
    candidates
        .map { candidate -> candidate to scoreMatch(queryTitle, queryArtists, candidate.title, candidate.artist) }
        .filter { (_, score) -> score >= MATCH_THRESHOLD }
        .maxWithOrNull(
            compareBy<Pair<MediaTrackCandidate, Float>> { it.second }
                .thenBy { it.first.dateAddedMs },
        )
        ?.first