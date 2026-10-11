package com.example.spotifyreplica.data.remote.spotify

import com.example.spotifyreplica.data.repository.coerceSearchLimit
import com.example.spotifyreplica.data.repository.emptyPage
import com.example.spotifyreplica.data.repository.toSummary
import com.example.spotifyreplica.data.repository.toTrackPage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO mapping tests for the 2026 Spotify Web API shapes. They parse representative JSON with the
 * same Moshi setup the app uses and assert the DTO -> domain mapping never crashes and never reads
 * removed fields (e.g. `popularity`).
 */
class SpotifyDtoMappingTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val playlistAdapter = moshi.adapter(SpotifyPlaylistDto::class.java)
    private val itemsPageAdapter = moshi.adapter(PlaylistItemsPage::class.java)
    private val searchAdapter = moshi.adapter(SearchResponse::class.java)

    @Test
    fun `parses 2026 playlist with items field and maps nested items dot item`() {
        val dto = playlistAdapter.fromJson(PLAYLIST_WITH_ITEMS_JSON)!!

        // Playlist itself (field is `items`, not `tracks`).
        assertEquals("p1", dto.id)
        assertEquals("Chill", dto.name)
        assertEquals("Alice", dto.owner?.displayName)
        assertEquals(2, dto.items?.total)

        val summary = dto.toSummary()
        assertEquals("Chill", summary.name)
        assertEquals("Alice", summary.ownerName)
        assertEquals(2, summary.trackCount)
        // Largest image wins.
        assertEquals("http://img/p1-large.jpg", summary.artworkUrl)

        val page = dto.items!!.toTrackPage()
        assertEquals(2, page.items.size)
        assertEquals(2, page.total)
        assertFalse(page.hasMore)

        val first = page.items.first()
        assertEquals("t1", first.id)
        assertEquals("Song One", first.title)
        assertEquals(listOf("Artist A"), first.artists)
        assertEquals("Album X", first.album)
        assertEquals(210_000L, first.durationMs)
        assertEquals("ISRC1", first.isrc)
        assertEquals("spotify:track:t1", first.spotifyUri)
        assertEquals("http://img/ax.jpg", first.artworkUrl)

        val second = page.items[1]
        assertEquals(listOf("Artist B", "Artist C"), second.artists)
        assertNull(second.artworkUrl) // empty images array -> null, never a crash
    }

    @Test
    fun `playlist with absent items degrades to an empty metadata-only page`() {
        val dto = playlistAdapter.fromJson(PLAYLIST_WITHOUT_ITEMS_JSON)!!

        assertNull(dto.items)
        val summary = dto.toSummary()
        assertEquals("No Access", summary.name)
        assertEquals("Bob", summary.ownerName)
        assertEquals(0, summary.trackCount)

        // Absent items -> an empty page, no crash.
        val page = dto.items?.toTrackPage() ?: emptyPage(offset = 0, limit = 50)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `items page with absent items array parses to an empty list`() {
        val page = itemsPageAdapter.fromJson("""{"total":5,"offset":0,"limit":50}""")!!

        assertTrue(page.items.isEmpty())
        val domain = page.toTrackPage()
        assertTrue(domain.items.isEmpty())
        assertEquals(5, domain.total)
    }

    @Test
    fun `null playlist item entries are skipped`() {
        val dto = playlistAdapter.fromJson(PLAYLIST_WITH_NULL_ITEM_JSON)!!
        val page = dto.items!!.toTrackPage()

        assertEquals(1, page.items.size)
        assertEquals("Real Song", page.items.first().title)
    }

    @Test
    fun `playlist with explicit null images maps without crashing`() {
        // Regression: Spotify sends `"images": null` for some playlists, which Moshi rejects for a
        // non-null Kotlin list even though the constructor has a default.
        val dto = playlistAdapter.fromJson(PLAYLIST_WITH_NULL_IMAGES_JSON)!!

        assertNull(dto.images)
        val summary = dto.toSummary()
        assertEquals("Empty", summary.name)
        assertNull(summary.artworkUrl)
    }

    @Test
    fun `local track with null id and null album images falls back to its uri`() {
        val dto = playlistAdapter.fromJson(PLAYLIST_WITH_LOCAL_FILE_JSON)!!
        val page = dto.items!!.toTrackPage()

        assertEquals(1, page.items.size)
        val local = page.items.first()
        assertEquals("spotify:local:Artist:Album:Song:123", local.id)
        assertNull(local.artworkUrl)
    }

    @Test
    fun `search paging parses and respects the limit-10 cap`() {
        val dto = searchAdapter.fromJson(searchJson(itemCount = 10, total = 30))!!

        val page = dto.toTrackPage(offset = 0, limit = 10)
        assertEquals(10, page.items.size)
        assertEquals(30, page.total)
        assertTrue(page.hasMore)
        assertEquals("Song 1", page.items.first().title)

        // The repository signature must coerce any caller-supplied limit to <= 10.
        assertEquals(10, coerceSearchLimit(50))
        assertEquals(10, coerceSearchLimit(10))
        assertEquals(3, coerceSearchLimit(3))
        assertEquals(1, coerceSearchLimit(0))
        assertEquals(1, coerceSearchLimit(-5))
    }

    @Test
    fun `search response without a tracks object is handled`() {
        val dto = searchAdapter.fromJson("""{"tracks":null}""")!!
        val page = dto.toTrackPage(offset = 20, limit = 10)

        assertTrue(page.items.isEmpty())
        assertEquals(20, page.offset)
        assertEquals(10, page.limit)
        assertFalse(page.hasMore)
    }

    private fun searchJson(itemCount: Int, total: Int): String = buildString {
        append("""{"tracks":{"total":$total,"offset":0,"limit":10,"next":"https://api.spotify.com/v1/search?offset=10","items":[""")
        for (i in 1..itemCount) {
            if (i > 1) append(',')
            append(
                """{"id":"s$i","name":"Song $i","artists":[{"name":"Artist $i"}],""" +
                    """"album":{"name":"Album","images":[{"url":"http://img/$i.jpg","width":640}]},""" +
                    """"duration_ms":${i * 1000},"external_ids":{"isrc":"ISRC$i"},"uri":"spotify:track:s$i"}""",
            )
        }
        append("]}}")
    }

    private companion object {
        val PLAYLIST_WITH_ITEMS_JSON = """
            {
              "id": "p1",
              "name": "Chill",
              "owner": { "display_name": "Alice" },
              "images": [
                { "url": "http://img/p1-small.jpg", "width": 64 },
                { "url": "http://img/p1-large.jpg", "width": 640 }
              ],
              "items": {
                "total": 2,
                "offset": 0,
                "limit": 100,
                "next": null,
                "items": [
                  {
                    "item": {
                      "id": "t1",
                      "name": "Song One",
                      "artists": [ { "name": "Artist A" } ],
                      "album": { "name": "Album X", "images": [ { "url": "http://img/ax.jpg", "width": 300 } ] },
                      "duration_ms": 210000,
                      "external_ids": { "isrc": "ISRC1" },
                      "uri": "spotify:track:t1",
                      "popularity": 77,
                      "available_markets": ["US"]
                    }
                  },
                  {
                    "item": {
                      "id": "t2",
                      "name": "Song Two",
                      "artists": [ { "name": "Artist B" }, { "name": "Artist C" } ],
                      "album": { "name": "Album Y", "images": [] },
                      "duration_ms": 180000,
                      "external_ids": { "isrc": "ISRC2" },
                      "uri": "spotify:track:t2"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val PLAYLIST_WITHOUT_ITEMS_JSON = """
            {
              "id": "p2",
              "name": "No Access",
              "owner": { "display_name": "Bob" },
              "images": [],
              "tracks": { "total": 0 }
            }
        """.trimIndent()

        val PLAYLIST_WITH_NULL_ITEM_JSON = """
            {
              "id": "p3",
              "name": "Mixed",
              "items": {
                "total": 2,
                "offset": 0,
                "limit": 50,
                "next": null,
                "items": [
                  { "item": null },
                  { "item": { "id": "t9", "name": "Real Song", "artists": [], "album": { "name": "", "images": [] }, "duration_ms": 1 } }
                ]
              }
            }
        """.trimIndent()
        val PLAYLIST_WITH_NULL_IMAGES_JSON = """
            {
              "id": "p2",
              "name": "Empty",
              "owner": { "display_name": "Bob" },
              "images": null,
              "items": { "total": 0, "offset": 0, "limit": 50, "items": [] }
            }
        """.trimIndent()

        val PLAYLIST_WITH_LOCAL_FILE_JSON = """
            {
              "id": "p3",
              "name": "Local",
              "images": null,
              "items": {
                "total": 1,
                "offset": 0,
                "limit": 50,
                "next": null,
                "items": [
                  {
                    "item": {
                      "id": null,
                      "name": "Song",
                      "artists": [ { "name": "Artist" } ],
                      "album": { "name": "Album", "images": null },
                      "duration_ms": 1000,
                      "uri": "spotify:local:Artist:Album:Song:123",
                      "is_local": true
                    }
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
