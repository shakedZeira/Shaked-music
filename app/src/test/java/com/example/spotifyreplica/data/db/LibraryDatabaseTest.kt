package com.example.spotifyreplica.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.spotifyreplica.data.db.dao.DownloadDao
import com.example.spotifyreplica.data.db.dao.PlaylistDao
import com.example.spotifyreplica.data.db.dao.TrackDao
import com.example.spotifyreplica.data.db.entity.DownloadEntity
import com.example.spotifyreplica.data.db.entity.PlaylistEntity
import com.example.spotifyreplica.data.db.entity.PlaylistTrackCrossRef
import com.example.spotifyreplica.data.db.entity.TrackEntity
import com.example.spotifyreplica.data.repository.LibraryRepositoryImpl
import com.example.spotifyreplica.domain.model.DownloadItem
import com.example.spotifyreplica.domain.model.DownloadStatus
import com.example.spotifyreplica.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + in-memory Room tests covering entity round-trips, the playlist join, and the
 * [LibraryRepository] flows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var downloadDao: DownloadDao
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackDao = db.trackDao()
        playlistDao = db.playlistDao()
        downloadDao = db.downloadDao()
        repository = LibraryRepositoryImpl(trackDao, playlistDao, downloadDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region entities

    @Test
    fun trackEntity_roundTrips() = runTest {
        val track = trackEntity(spotifyId = "track-1", localPath = null)
        trackDao.upsert(track)

        val loaded = trackDao.getById("track-1")
        assertEquals(track, loaded)
        assertTrue(trackDao.exists("track-1"))
        assertNull(loaded?.localPath)
    }

    @Test
    fun playlistEntity_andCrossRefs_roundTrip() = runTest {
        playlistDao.upsert(playlistEntity("playlist-1"))
        trackDao.upsert(trackEntity(spotifyId = "track-1"))
        trackDao.upsert(trackEntity(spotifyId = "track-2"))

        playlistDao.replaceCrossRefs(
            "playlist-1",
            listOf(
                PlaylistTrackCrossRef("playlist-1", "track-2", position = 0),
                PlaylistTrackCrossRef("playlist-1", "track-1", position = 1)
            )
        )

        val playlists = playlistDao.observePlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("playlist-1", playlists.first().spotifyId)

        val ordered = playlistDao.observeWithTracks("playlist-1").first()
        assertEquals(listOf("track-2", "track-1"), ordered.map { it.spotifyId })
    }

    @Test
    fun downloadEntity_roundTrips() = runTest {
        val entity = DownloadEntity(
            id = "dl-1",
            spotifyId = "track-1",
            playlistId = "playlist-1",
            status = DownloadStatus.RUNNING.name,
            bytesDownloaded = 512L,
            totalBytes = 2048L,
            error = null,
            updatedAtMs = 1_000L
        )
        downloadDao.upsert(entity)

        assertEquals(entity, downloadDao.getById("dl-1"))
        assertEquals(1, downloadDao.observeAll().first().size)
    }

    // endregion

    // region repository flows

    @Test
    fun observeDownloads_emitsOnWrite() = runTest {
        repository.upsertDownload(
            DownloadItem(
                id = "dl-1",
                spotifyId = "track-1",
                playlistId = null,
                title = "Track One",
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = 1024L,
                error = null
            )
        )

        val items = repository.observeDownloads().first()
        assertEquals(1, items.size)
        assertEquals("dl-1", items.first().id)
        assertEquals(DownloadStatus.QUEUED, items.first().status)
    }

    @Test
    fun observeDownloads_enrichesTitleFromCachedTrack() = runTest {
        trackDao.upsert(trackEntity(spotifyId = "track-1", title = "Cached Title"))
        repository.upsertDownload(
            DownloadItem(
                id = "dl-1",
                spotifyId = "track-1",
                playlistId = null,
                title = null,
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = null,
                error = null
            )
        )

        val item = repository.observeDownloads().first().single()
        assertEquals("Cached Title", item.title)
    }

    @Test
    fun updateDownloadProgress_and_status_arePersisted() = runTest {
        repository.upsertDownload(
            DownloadItem(
                id = "dl-1",
                spotifyId = "track-1",
                playlistId = null,
                title = null,
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = null,
                error = null
            )
        )

        repository.updateDownloadProgress("dl-1", bytesDownloaded = 256L, totalBytes = 1024L)
        repository.updateDownloadStatus("dl-1", DownloadStatus.FAILED, error = "boom")

        val entity = downloadDao.getById("dl-1")!!
        assertEquals(256L, entity.bytesDownloaded)
        assertEquals(1024L, entity.totalBytes)
        assertEquals(DownloadStatus.FAILED.name, entity.status)
        assertEquals("boom", entity.error)
    }

    @Test
    fun markTrackDownloaded_thenObserveLibraryTracks_emitsTrack() = runTest {
        trackDao.upsert(
            trackEntity(
                spotifyId = "track-1",
                title = "Song",
                artistsCsv = "Artist A, Artist B",
                durationMs = 180_000L
            )
        )

        repository.markTrackDownloaded(
            spotifyId = "track-1",
            localPath = "/data/music/track-1.m4a",
            sizeBytes = 4_096L,
            container = "m4a",
            bitrateKbps = 320,
            downloadedAtMs = 42L
        )

        val tracks = repository.observeLibraryTracks().first()
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("track-1", track.spotifyId)
        assertEquals("/data/music/track-1.m4a", track.localPath)
        assertEquals(listOf("Artist A", "Artist B"), track.artists)
        assertEquals(320, track.bitrateKbps)
        assertEquals(4_096L, track.sizeBytes)
    }

    @Test
    fun observeLibraryTracks_ignoresTracksWithoutLocalPath() = runTest {
        trackDao.upsert(trackEntity(spotifyId = "track-1", localPath = null))
        trackDao.upsert(trackEntity(spotifyId = "track-2", localPath = "/data/track-2.mp3"))

        val tracks = repository.observeLibraryTracks().first()
        assertEquals(listOf("track-2"), tracks.map { it.spotifyId })
    }

    @Test
    fun removeDownload_and_clearAll_mutateQueue() = runTest {
        repository.upsertDownload(downloadItem("dl-1"))
        repository.upsertDownload(downloadItem("dl-2"))
        repository.removeDownload("dl-1")
        assertEquals(1, repository.observeDownloads().first().size)

        repository.clearAll()
        assertTrue(repository.observeDownloads().first().isEmpty())
    }

    @Test
    fun downloadDao_aggregates() = runTest {
        downloadDao.upsert(downloadEntity("dl-1", DownloadStatus.COMPLETED, bytes = 1_000L))
        downloadDao.upsert(downloadEntity("dl-2", DownloadStatus.RUNNING, bytes = 500L))
        downloadDao.upsert(downloadEntity("dl-3", DownloadStatus.COMPLETED, bytes = 250L))

        assertEquals(1_750L, downloadDao.totalDownloadedBytes()!!)
        assertEquals(2, downloadDao.countByStatus(DownloadStatus.COMPLETED.name))
        assertEquals(1, downloadDao.countByStatus(DownloadStatus.RUNNING.name))

        downloadDao.clearCompleted()
        assertEquals(1, downloadDao.observeAll().first().size)
    }

    @Test
    fun downloadStatus_fromString_isSafe() {
        assertEquals(DownloadStatus.RUNNING, DownloadStatus.fromString("RUNNING"))
        assertEquals(DownloadStatus.COMPLETED, DownloadStatus.fromString("completed"))
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.fromString("not-a-status"))
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.fromString(null))
        assertFalse(DownloadStatus.fromString("garbage") == DownloadStatus.CANCELLED)
    }

    // endregion

    // region fixtures

    private fun trackEntity(
        spotifyId: String = "track-1",
        title: String = "Title",
        artistsCsv: String = "Artist",
        album: String = "Album",
        durationMs: Long = 200_000L,
        artworkUrl: String? = "https://art.example/cover.jpg",
        localPath: String? = null,
        sizeBytes: Long? = null,
        container: String = "m4a",
        bitrateKbps: Int? = 256,
        downloadedAtMs: Long? = null
    ) = TrackEntity(
        spotifyId = spotifyId,
        title = title,
        artistsCsv = artistsCsv,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        localPath = localPath,
        sizeBytes = sizeBytes,
        container = container,
        bitrateKbps = bitrateKbps,
        downloadedAtMs = downloadedAtMs
    )

    private fun playlistEntity(spotifyId: String) = PlaylistEntity(
        spotifyId = spotifyId,
        name = "My Playlist",
        ownerName = "Owner",
        artworkUrl = null,
        trackCount = 2,
        syncedAtMs = 123L
    )

    private fun downloadItem(id: String) = DownloadItem(
        id = id,
        spotifyId = "track-$id",
        playlistId = null,
        title = null,
        status = DownloadStatus.QUEUED,
        bytesDownloaded = 0L,
        totalBytes = null,
        error = null
    )

    private fun downloadEntity(
        id: String,
        status: DownloadStatus,
        bytes: Long
    ) = DownloadEntity(
        id = id,
        spotifyId = "track-$id",
        playlistId = null,
        status = status.name,
        bytesDownloaded = bytes,
        totalBytes = bytes,
        error = null,
        updatedAtMs = 1L
    )

    // endregion
}
