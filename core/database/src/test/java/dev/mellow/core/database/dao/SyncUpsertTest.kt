package dev.mellow.core.database.dao

import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Saving server data must not wipe values Mellow derives or keeps locally. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncUpsertTest {

    private lateinit var db: MellowDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `re-saving a track from the server keeps its resolved artist`() = runTest {
        val dao = db.trackDao()
        dao.upsertTracks(listOf(track("t1", artistId = "raw")))
        db.artistAliasDao().upsertAliases(listOf(alias("raw", "canonical")))
        dao.resolveArtistAliases(SERVER)

        // The server's copy never carries resolvedArtistId.
        dao.upsertTracks(listOf(track("t1", artistId = "raw", playCount = 5)))

        val saved = dao.getTrackById("t1")!!
        assertEquals("canonical", saved.resolvedArtistId)
        assertEquals(5, saved.playCount)
        assertEquals(listOf("t1"), dao.getTracksByResolvedArtistSync("canonical").map { it.id })
    }

    @Test
    fun `a new track gets its resolved artist from the aliases`() = runTest {
        db.artistAliasDao().upsertAliases(listOf(alias("raw", "canonical")))

        db.trackDao().upsertTracks(listOf(track("t1", artistId = "raw"), track("t2", artistId = "unknown")))

        assertEquals("canonical", db.trackDao().getTrackById("t1")!!.resolvedArtistId)
        assertNull(db.trackDao().getTrackById("t2")!!.resolvedArtistId)
    }

    @Test
    fun `re-saving an album from the server keeps its resolved artist`() = runTest {
        val dao = db.albumDao()
        dao.upsertAlbums(listOf(album("a1", artistId = "raw")))
        db.artistAliasDao().upsertAliases(listOf(alias("raw", "canonical")))
        dao.resolveArtistAliases(SERVER)

        dao.upsertAlbums(listOf(album("a1", artistId = "raw")))

        assertEquals(listOf("a1"), dao.getAllAlbumsByResolvedArtist("canonical").map { it.id })
    }

    @Test
    fun `re-saving artists from the server keeps favorites`() = runTest {
        val dao = db.artistDao()
        dao.upsertArtists(listOf(artist("fav"), artist("other")))
        dao.setFavoriteByIds(listOf("fav"), true)

        // Jellyfin's /Artists reports IsFavorite = false even for favorites.
        dao.upsertArtists(listOf(artist("fav", name = "Renamed"), artist("other")))

        assertTrue(dao.getArtistById("fav")!!.isFavorite)
        assertEquals("Renamed", dao.getArtistById("fav")!!.name)
        assertFalse(dao.getArtistById("other")!!.isFavorite)
        assertEquals(listOf("fav"), dao.getFavoriteArtistIds(SERVER))
    }

    @Test
    fun `saving more rows than SQLite's parameter limit works`() = runTest {
        db.artistAliasDao().upsertAliases(listOf(alias("raw", "canonical")))
        val tracks = (1..2_000).map { track("t$it", artistId = "raw") }

        db.trackDao().upsertTracks(tracks)
        db.trackDao().upsertTracks(tracks)

        assertEquals(2_000, db.trackDao().countTracksByResolvedArtist("canonical"))
    }

    private companion object {
        const val SERVER = "server"

        fun alias(raw: String, canonical: String) = ArtistAliasEntity(SERVER, raw, canonical, 0)

        fun track(id: String, artistId: String?, playCount: Int = 0) = TrackEntity(
            id = id, serverId = SERVER, name = id, sortName = id, albumId = null, albumName = null,
            artistId = artistId, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = playCount, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )

        fun album(id: String, artistId: String?) = AlbumEntity(
            id = id, serverId = SERVER, name = id, sortName = id, artistId = artistId, artistName = null,
            year = null, trackCount = 0, genres = emptyList(), imageTag = null, isFavorite = false,
            resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )

        fun artist(id: String, name: String = id) = ArtistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = name, musicBrainzId = null,
            lastSynced = 0,
        )
    }
}
