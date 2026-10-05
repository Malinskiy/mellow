package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.TrackArtistCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Downloaded only" finds an artist by who the downloaded tracks are by, not by the name written on them, and a genre
 * matches only albums tagged with exactly that genre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtistAndGenreFiltersTest {

    private lateinit var db: MellowDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    /**
     * Jellyfin lists "The Beatles" twice; the aliases merge them into "Beatles". The downloaded track still says
     * "The Beatles".
     */
    private suspend fun downloadTheBeatles() {
        db.artistDao().upsertArtists(listOf(artist("beatles", "Beatles"), artist("the-beatles", "The Beatles"), artist("other", "Other")))
        db.artistAliasDao().upsertAliases(
            listOf(alias("beatles", "beatles"), alias("the-beatles", "beatles"), alias("other", "other")),
        )
        db.trackDao().upsertTracks(
            listOf(track("t1", artistId = "the-beatles", artistName = "The Beatles"), track("t2", artistId = "other", artistName = "Other")),
        )
        db.downloadDao().upsertAll(listOf(download("t1")))
    }

    @Test
    fun `downloaded only shows an artist whose downloaded track names it differently`() = runTest {
        downloadTheBeatles()

        val artists = ArtistKeysetQueryFactory(db)
            .libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, downloadedOnly = true)
            .loadAll()

        assertEquals(listOf("beatles"), artists.map { it.artist.id })
    }

    @Test
    fun `downloaded only shows every artist a downloaded track credits`() = runTest {
        db.artistDao().upsertArtists(listOf(artist("x", "X"), artist("y", "Y")))
        db.artistAliasDao().upsertAliases(listOf(alias("x", "x"), alias("y", "y")))
        db.trackDao().upsertTracks(listOf(track("duet", artistId = "x", artistName = "X, Y")))
        db.trackDao().insertTrackArtists(
            listOf(TrackArtistCrossRef("duet", "x", "X", 0), TrackArtistCrossRef("duet", "y", "Y", 1)),
        )
        db.downloadDao().upsertAll(listOf(download("duet")))

        val artists = ArtistKeysetQueryFactory(db)
            .libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, downloadedOnly = true)
            .loadAll()

        assertEquals(listOf("x", "y"), artists.map { it.artist.id })
    }

    @Test
    fun `downloaded only keeps a favorite artist whose downloaded track names it differently`() = runTest {
        downloadTheBeatles()
        db.artistDao().setFavoriteByIds(listOf("beatles", "other"), true)

        val favorites = db.artistDao().getFavoriteArtistsPaged(SERVER, downloadedOnly = true).loadAll()

        assertEquals(listOf("beatles"), favorites.map { it.id })
    }

    @Test
    fun `Android Auto's downloaded artists include one whose downloaded track names it differently`() = runTest {
        downloadTheBeatles()

        val artists = db.artistDao().getCanonicalArtistsSlice(SERVER, downloadedOnly = true, limit = 10, offset = 0)

        assertEquals(listOf("beatles"), artists.map { it.id })
    }

    @Test
    fun `offline search finds the artist of a downloaded track under any of its names`() = runTest {
        downloadTheBeatles()

        val found = db.artistDao().search(SERVER, "e", downloadedOnly = true)

        assertEquals(listOf("beatles", "the-beatles"), found.map { it.id })
    }

    @Test
    fun `Android Auto's genre lists only albums tagged with exactly that genre`() = runTest {
        db.albumDao().upsertAlbums(
            listOf(
                album("rap", listOf("Rap")),
                album("rap-metal", listOf("Rap Metal")),
                album("pop-rap", listOf("Pop Rap")),
                album("hip-hop-rap", listOf("Hip Hop", "Rap")),
                album("lower", listOf("rap")),
            ),
        )

        val albums = db.albumDao().getAlbumsByGenreSlice("Rap", SERVER, downloadedOnly = false, limit = 100, offset = 0)

        assertEquals(listOf("hip-hop-rap", "rap"), albums.map { it.id })
    }

    private suspend fun <K : Any, T : Any> PagingSource<K, T>.loadAll(): List<T> {
        val page = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (page as PagingSource.LoadResult.Page).data
    }

    private companion object {
        const val SERVER = "server"

        fun artist(id: String, name: String) = ArtistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = name.lowercase(),
            musicBrainzId = null, lastSynced = 0,
        )

        fun alias(raw: String, canonical: String) = ArtistAliasEntity(SERVER, raw, canonical, 0)

        fun track(id: String, artistId: String, artistName: String) = TrackEntity(
            id = id, serverId = SERVER, name = id, sortName = id, albumId = null, albumName = null,
            artistId = artistId, artistName = artistName, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )

        fun album(id: String, genres: List<String>) = AlbumEntity(
            id = id, serverId = SERVER, name = id, sortName = id, artistId = null, artistName = null, year = null,
            trackCount = 0, genres = genres, imageTag = null, isFavorite = false, resolvedArtistId = null,
            dateAdded = 0, lastSynced = 0,
        )

        fun download(trackId: String) = DownloadEntity(
            trackId = trackId, albumId = null, serverId = SERVER, status = DownloadEntity.STATUS_COMPLETED,
            progress = 1f, bytesDownloaded = 0, totalBytes = 0, quality = "original", filePath = null,
            requestedAt = 0, completedAt = 0, errorMessage = null, lastSynced = 0,
        )
    }
}
