package dev.mellow.core.database.dao

import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumArtistCrossRef
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.LyricsEntity
import dev.mellow.core.database.entity.SyncPassKind
import dev.mellow.core.database.entity.TrackArtistCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The full library pass deletes what the server no longer has, and nothing the device still needs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPassDaoTest {

    private lateinit var db: MellowDatabase
    private val dao get() = db.syncPassDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `unseen items leave out seen ones, downloaded tracks and other servers`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("seen"), album("unseen"), album("other", server = OTHER)))
        db.trackDao().upsertTracks(
            listOf(track("seen"), track("unseen"), track("downloaded"), track("other", server = OTHER)),
        )
        db.downloadDao().upsert(download("downloaded"))
        dao.mark(SyncPassKind.ALBUM, listOf("seen"))
        dao.mark(SyncPassKind.TRACK, listOf("seen"))

        assertEquals(listOf("unseen"), dao.getUnseenAlbumIds(SERVER))
        assertEquals(listOf("unseen"), dao.getUnseenTrackIds(SERVER))
    }

    @Test
    fun `marking the same item twice counts it once`() = runTest {
        dao.mark(SyncPassKind.TRACK, listOf("t1", "t2"))
        dao.mark(SyncPassKind.TRACK, listOf("t2", "t3"))
        dao.mark(SyncPassKind.ALBUM, listOf("t1"))

        assertEquals(3, dao.count(SyncPassKind.TRACK))
        assertEquals(1, dao.count(SyncPassKind.ALBUM))
    }

    @Test
    fun `gone tracks are deleted with their artist links and lyrics, downloaded ones are kept`() = runTest {
        db.trackDao().upsertTracks(listOf(track("gone"), track("downloaded"), track("kept")))
        db.trackDao().insertTrackArtists(listOf(trackLink("gone", "artist"), trackLink("kept", "artist")))
        db.lyricsDao().upsert(LyricsEntity("gone", SERVER, "[]", 0))
        db.lyricsDao().upsert(LyricsEntity("kept", SERVER, "[]", 0))
        db.downloadDao().upsert(download("downloaded"))
        dao.mark(SyncPassKind.GONE_TRACK, listOf("gone", "downloaded"))

        assertEquals(1, dao.deleteGoneTracks(SERVER))
        assertEquals(1, dao.deleteOrphanedLyrics(SERVER))

        assertNull(db.trackDao().getTrackById("gone"))
        assertNotNull(db.trackDao().getTrackById("downloaded"))
        assertNotNull(db.trackDao().getTrackById("kept"))
        assertEquals(emptyList<String>(), db.trackDao().getArtistNamesForTrack("gone"))
        assertEquals(listOf("artist"), db.trackDao().getArtistNamesForTrack("kept"))
        assertNull(db.lyricsDao().getLyrics("gone"))
        assertNotNull(db.lyricsDao().getLyrics("kept"))
    }

    @Test
    fun `gone albums are deleted with their artist links unless a remaining track belongs to them`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("gone"), album("still used")))
        db.albumDao().insertAlbumArtists(listOf(albumLink("gone", "artist"), albumLink("still used", "artist")))
        db.trackDao().upsertTracks(listOf(track("downloaded", albumId = "still used")))
        dao.mark(SyncPassKind.GONE_ALBUM, listOf("gone", "still used"))

        assertEquals(1, dao.deleteGoneAlbums(SERVER))

        assertNull(db.albumDao().getAlbumById("gone"))
        assertNotNull(db.albumDao().getAlbumById("still used"))
        assertEquals(emptyList<String>(), db.albumDao().getArtistNamesForAlbum("gone"))
        assertEquals(listOf("artist"), db.albumDao().getArtistNamesForAlbum("still used"))
    }

    @Test
    fun `unseen artists are deleted unless an album or track still links to them`() = runTest {
        db.artistDao().upsertArtists(
            listOf(
                artist("seen"), artist("gone"), artist("album link"), artist("track link"),
                artist("album artist"), artist("track artist"), artist("other", server = OTHER),
            ),
        )
        db.albumDao().upsertAlbums(listOf(album("a1", artistId = "album artist")))
        db.albumDao().insertAlbumArtists(listOf(albumLink("a1", "album link")))
        db.trackDao().upsertTracks(listOf(track("t1", artistId = "track artist")))
        db.trackDao().insertTrackArtists(listOf(trackLink("t1", "track link")))
        dao.mark(SyncPassKind.ARTIST, listOf("seen"))

        assertEquals(1, dao.deleteUnseenArtists(SERVER))

        assertNull(db.artistDao().getArtistById("gone"))
        listOf("seen", "album link", "track link", "album artist", "track artist", "other").forEach {
            assertNotNull(it, db.artistDao().getArtistById(it))
        }
    }

    @Test
    fun `replacing artist links touches only the given items`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("a1"), album("a2")))
        db.albumDao().insertAlbumArtists(listOf(albumLink("a1", "old"), albumLink("a2", "old")))
        db.trackDao().upsertTracks(listOf(track("t1"), track("t2")))
        db.trackDao().insertTrackArtists(listOf(trackLink("t1", "old"), trackLink("t2", "old")))

        db.albumDao().replaceAlbumArtists(listOf("a1"), listOf(albumLink("a1", "new")))
        db.trackDao().replaceTrackArtists(listOf("t1"), listOf(trackLink("t1", "new")))

        assertEquals(listOf("new"), db.albumDao().getArtistNamesForAlbum("a1"))
        assertEquals(listOf("old"), db.albumDao().getArtistNamesForAlbum("a2"))
        assertEquals(listOf("new"), db.trackDao().getArtistNamesForTrack("t1"))
        assertEquals(listOf("old"), db.trackDao().getArtistNamesForTrack("t2"))
    }

    private companion object {
        const val SERVER = "server"
        const val OTHER = "other server"

        fun track(id: String, server: String = SERVER, albumId: String? = null, artistId: String? = null) = TrackEntity(
            id = id, serverId = server, name = id, sortName = id, albumId = albumId, albumName = null,
            artistId = artistId, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )

        fun album(id: String, server: String = SERVER, artistId: String? = null) = AlbumEntity(
            id = id, serverId = server, name = id, sortName = id, artistId = artistId, artistName = null,
            year = null, trackCount = 0, genres = emptyList(), imageTag = null, isFavorite = false,
            resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )

        fun artist(id: String, server: String = SERVER) = ArtistEntity(
            id = id, serverId = server, name = id, sortName = id, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = id, musicBrainzId = null,
            lastSynced = 0,
        )

        fun albumLink(albumId: String, artistId: String) = AlbumArtistCrossRef(albumId, artistId, artistId, 0)

        fun trackLink(trackId: String, artistId: String) = TrackArtistCrossRef(trackId, artistId, artistId, 0)

        fun download(trackId: String) = DownloadEntity(
            trackId = trackId, albumId = null, serverId = SERVER, status = DownloadEntity.STATUS_COMPLETED,
            progress = 1f, bytesDownloaded = 1, totalBytes = 1, quality = "original", filePath = "/music/$trackId",
            requestedAt = 0, completedAt = 0, errorMessage = null, lastSynced = 0,
        )
    }
}
