package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The paged, sliced and sampled queries that let the app show and play large lists a part at a time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryPagingQueriesTest {

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
    fun `the tracks tab pages through every track, newest first, past the old 500 cap`() = runTest {
        db.trackDao().upsertTracks((1..1_234).map { track("t$it", dateAdded = it.toLong()) })

        val ids = db.trackDao().getLibraryTracks(SERVER, LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
            .loadAll(pageSize = 100)
            .map { it.id }

        assertEquals((1_234 downTo 1).map { "t$it" }, ids)
    }

    @Test
    fun `tracks added at the same time are ordered by ID, so pages never overlap or skip`() = runTest {
        db.trackDao().upsertTracks((1..250).map { track("t${it.toString().padStart(3, '0')}", dateAdded = 7) })

        val ids = db.trackDao().getLibraryTracks(SERVER, LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
            .loadAll(pageSize = 7)
            .map { it.id }

        assertEquals((1..250).map { "t${it.toString().padStart(3, '0')}" }, ids)
    }

    @Test
    fun `name orders ignore case and keep the newest first among equal names`() = runTest {
        db.trackDao().upsertTracks(
            listOf(
                track("old-a", name = "A", dateAdded = 1),
                track("b", name = "b", dateAdded = 2),
                track("new-a", name = "a", dateAdded = 3),
                track("c", name = "C", dateAdded = 4),
            ),
        )

        assertEquals(listOf("new-a", "old-a", "b", "c"), libraryTrackIds(LibraryOrder.NAME_ASC))
        assertEquals(listOf("c", "b", "new-a", "old-a"), libraryTrackIds(LibraryOrder.NAME_DESC))
    }

    @Test
    fun `the year order sorts tracks by album name, Z to A, tracks without an album last`() = runTest {
        db.trackDao().upsertTracks(
            listOf(
                track("none", albumName = null, dateAdded = 4),
                track("amnesiac", albumName = "Amnesiac", dateAdded = 1),
                track("kid-a", albumName = "Kid A", dateAdded = 2),
                track("bends", albumName = "The Bends", dateAdded = 3),
            ),
        )

        assertEquals(listOf("bends", "kid-a", "amnesiac", "none"), libraryTrackIds(LibraryOrder.YEAR))
    }

    @Test
    fun `downloaded only keeps tracks whose download completed`() = runTest {
        db.trackDao().upsertTracks(listOf(track("done", dateAdded = 1), track("busy", dateAdded = 2), track("none", dateAdded = 3)))
        db.downloadDao().upsertAll(listOf(download("done", DownloadEntity.STATUS_COMPLETED), download("busy", DownloadEntity.STATUS_DOWNLOADING)))

        val ids = db.trackDao().getLibraryTracks(SERVER, LibraryOrder.RECENTLY_ADDED, downloadedOnly = true)
            .loadAll()
            .map { it.id }

        assertEquals(listOf("done"), ids)
    }

    @Test
    fun `a slice is the same part of the list a page shows`() = runTest {
        db.trackDao().upsertTracks((1..40).map { track("t$it", name = "n${it % 7}", dateAdded = it.toLong()) })
        val all = libraryTrackIds(LibraryOrder.NAME_ASC)

        val slice = db.trackDao().getLibraryTracksSlice(SERVER, LibraryOrder.NAME_ASC, false, limit = 5, offset = 10)

        assertEquals(all.subList(10, 15), slice.map { it.id })
    }

    @Test
    fun `other servers' tracks never show`() = runTest {
        db.trackDao().upsertTracks(listOf(track("mine", dateAdded = 1), track("theirs", serverId = "other", dateAdded = 2)))

        assertEquals(listOf("mine"), libraryTrackIds(LibraryOrder.RECENTLY_ADDED))
        assertEquals(listOf("mine"), db.trackDao().getTracksByServerPaged(SERVER, false, limit = 10, offset = 0).map { it.id })
    }

    @Test
    fun `favorite tracks keep the order they were saved in, and positions and counts match it`() = runTest {
        db.trackDao().upsertTracks((1..6).map { track("t$it", isFavorite = it % 2 == 0) })

        val paged = db.trackDao().getFavoriteTracksPaged(SERVER, downloadedOnly = false).loadAll(pageSize = 2).map { it.id }

        assertEquals(listOf("t2", "t4", "t6"), paged)
        assertEquals(listOf("t4", "t6"), db.trackDao().getFavoriteTracksSlice(SERVER, false, limit = 5, offset = 1).map { it.id })
        assertEquals(3, db.trackDao().countFavoriteTracks(SERVER, downloadedOnly = false))
        assertEquals(1, db.trackDao().countFavoriteTracksBefore(SERVER, "t4"))
        assertEquals(0, db.trackDao().countFavoriteTracksBefore(SERVER, "not-a-track"))
    }

    @Test
    fun `a favorites shuffle is a bounded random pick of the favorites, of downloads only if asked`() = runTest {
        db.trackDao().upsertTracks(
            (1..30).map { track("fav$it", isFavorite = true) } +
                (1..10).map { track("other$it") } +
                listOf(track("theirs", serverId = "elsewhere", isFavorite = true)),
        )
        db.downloadDao().upsertAll((1..4).map { download("fav$it", DownloadEntity.STATUS_COMPLETED) })
        val favorites = (1..30).map { "fav$it" }.toSet()

        val sample = db.trackDao().getRandomFavoriteTracks(SERVER, downloadedOnly = false, limit = 10).map { it.id }
        val all = db.trackDao().getRandomFavoriteTracks(SERVER, downloadedOnly = false, limit = 500).map { it.id }
        val downloaded = db.trackDao().getRandomFavoriteTracks(SERVER, downloadedOnly = true, limit = 500).map { it.id }

        assertEquals(10, sample.toSet().size)
        assertTrue(favorites.containsAll(sample))
        assertEquals(favorites, all.toSet())
        assertEquals(30, all.size)
        assertEquals((1..4).map { "fav$it" }.toSet(), downloaded.toSet())
        assertEquals(4, db.trackDao().countFavoriteTracks(SERVER, downloadedOnly = true))
    }

    @Test
    fun `downloaded only keeps favorites whose download completed`() = runTest {
        db.trackDao().upsertTracks(listOf(track("kept", isFavorite = true), track("dropped", isFavorite = true)))
        db.downloadDao().upsertAll(listOf(download("kept", DownloadEntity.STATUS_COMPLETED)))

        assertEquals(listOf("kept"), db.trackDao().getFavoriteTracksPaged(SERVER, downloadedOnly = true).loadAll().map { it.id })
    }

    @Test
    fun `a track's position among the server's tracks matches the sliced order`() = runTest {
        db.trackDao().upsertTracks((1..30).map { track("t$it", name = "n${it % 4}") })
        val ordered = db.trackDao().getTracksByServerPaged(SERVER, downloadedOnly = false, limit = 100, offset = 0)

        ordered.forEachIndexed { index, track ->
            assertEquals(index, db.trackDao().countTracksBefore(SERVER, track.sortName, track.id))
        }
    }

    @Test
    fun `the genre filter matches whole genres only`() = runTest {
        db.albumDao().upsertAlbums(
            listOf(
                album("punk", genres = listOf("Punk Rock")),
                album("rock-pop", genres = listOf("Pop", "Rock")),
                album("none"),
            ),
        )

        val rock = db.albumDao().getLibraryAlbums(SERVER, LibraryOrder.NAME_ASC, genre = "Rock", downloadedOnly = false)
            .loadAll()
            .map { it.id }

        assertEquals(listOf("rock-pop"), rock)
    }

    @Test
    fun `album orders`() = runTest {
        db.albumDao().upsertAlbums(
            listOf(
                album("b", name = "b", year = 1997, dateAdded = 1),
                album("a", name = "A", year = null, dateAdded = 3),
                album("c", name = "c", year = 2007, dateAdded = 2),
            ),
        )

        assertEquals(listOf("a", "c", "b"), libraryAlbumIds(LibraryOrder.RECENTLY_ADDED))
        assertEquals(listOf("a", "b", "c"), libraryAlbumIds(LibraryOrder.NAME_ASC))
        assertEquals(listOf("c", "b", "a"), libraryAlbumIds(LibraryOrder.NAME_DESC))
        assertEquals(listOf("c", "b", "a"), libraryAlbumIds(LibraryOrder.YEAR))
    }

    @Test
    fun `downloaded only keeps albums with a downloaded track`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("kept"), album("dropped")))
        db.trackDao().upsertTracks(listOf(track("t1", albumId = "kept"), track("t2", albumId = "dropped")))
        db.downloadDao().upsertAll(listOf(download("t1", DownloadEntity.STATUS_COMPLETED)))

        val ids = db.albumDao().getLibraryAlbums(SERVER, LibraryOrder.NAME_ASC, genre = null, downloadedOnly = true)
            .loadAll()
            .map { it.id }

        assertEquals(listOf("kept"), ids)
    }

    @Test
    fun `genres come from distinct genre lists, with album counts for the top genres`() = runTest {
        db.albumDao().upsertAlbums(
            listOf(
                album("a1", name = "a1", genres = listOf("Rock", "Pop")),
                album("a2", name = "a2", genres = listOf("Rock", "Pop")),
                album("a3", name = "a3", genres = listOf("Jazz")),
                album("a4", name = "a4"),
            ),
        )

        val raw = db.albumDao().observeRawGenreStrings(SERVER, downloadedOnly = false).first()
        val counts = db.albumDao().observeGenreAlbumCounts(SERVER).first()

        assertEquals(setOf("Rock|||Pop", "Jazz"), raw.toSet())
        assertEquals(listOf(GenreAlbumCount("Rock|||Pop", 2), GenreAlbumCount("Jazz", 1)), counts)
    }

    @Test
    fun `the artists tab merges aliases and counts the albums credited to each artist`() = runTest {
        db.artistDao().upsertArtists(listOf(artist("canonical", "Radiohead"), artist("alias", "radiohead"), artist("solo", "Bon Iver")))
        db.artistAliasDao().upsertAliases(
            listOf(alias("canonical", "canonical"), alias("alias", "canonical"), alias("solo", "solo")),
        )
        db.albumDao().upsertAlbums(
            listOf(
                album("ok", artistId = "alias", resolvedArtistId = "canonical"),
                album("kid-a", artistId = "canonical"),
                album("bon-iver", artistId = "solo"),
            ),
        )

        val artists = db.artistDao().getLibraryArtists(SERVER, LibraryOrder.NAME_ASC, downloadedOnly = false).loadAll()

        assertEquals(listOf("solo", "canonical"), artists.map { it.artist.id })
        assertEquals(listOf(1, 2), artists.map { it.localAlbumCount })
    }

    @Test
    fun `playlist tracks page in playlist order, and positions and counts match it`() = runTest {
        db.trackDao().upsertTracks((1..5).map { track("t$it") })
        db.playlistDao().upsert(playlist("p"))
        db.playlistDao().insertPlaylistTracks(listOf("t5", "t3", "t1", "t4").mapIndexed { i, id -> crossRef("p", id, i) })

        val paged = db.playlistDao().getPlaylistTracksPaged("p", downloadedOnly = false).loadAll(pageSize = 3).map { it.id }

        assertEquals(listOf("t5", "t3", "t1", "t4"), paged)
        assertEquals(listOf("t1", "t4"), db.playlistDao().getPlaylistTracksSlice("p", false, limit = 9, offset = 2).map { it.id })
        assertEquals(4, db.playlistDao().countPlaylistTracks("p"))
        paged.forEachIndexed { index, id -> assertEquals(index, db.playlistDao().countPlaylistTracksBefore("p", id)) }
        assertEquals(0, db.playlistDao().countPlaylistTracksBefore("p", "t2"))
    }

    @Test
    fun `a playlist shuffle is a bounded random pick of that playlist's tracks only`() = runTest {
        db.trackDao().upsertTracks((1..40).map { track("t$it") })
        db.playlistDao().upsert(playlist("p"))
        db.playlistDao().upsert(playlist("other"))
        db.playlistDao().insertPlaylistTracks((1..30).map { crossRef("p", "t$it", it) })
        db.playlistDao().insertPlaylistTracks((31..40).map { crossRef("other", "t$it", it) })
        val inPlaylist = (1..30).map { "t$it" }.toSet()

        val sample = db.playlistDao().getRandomPlaylistTracks("p", limit = 10).map { it.id }
        val all = db.playlistDao().getRandomPlaylistTracks("p", limit = 500).map { it.id }

        assertEquals(10, sample.toSet().size)
        assertTrue(inPlaylist.containsAll(sample))
        assertEquals(inPlaylist, all.toSet())
        assertEquals(30, all.size)
    }

    @Test
    fun `server albums and canonical artists come a slice at a time`() = runTest {
        db.albumDao().upsertAlbums((1..12).map { album("a${it.toString().padStart(2, '0')}") })
        db.artistDao().upsertArtists((1..12).map { artist("r${it.toString().padStart(2, '0')}", "Artist ${it.toString().padStart(2, '0')}") })
        db.artistAliasDao().upsertAliases((1..12).map { "r${it.toString().padStart(2, '0')}" }.map { alias(it, it) })

        val albums = db.albumDao().getAlbumsByServerSlice(SERVER, downloadedOnly = false, limit = 5, offset = 10)
        val artists = db.artistDao().getCanonicalArtistsSlice(SERVER, downloadedOnly = false, limit = 5, offset = 10)

        assertEquals(listOf("a11", "a12"), albums.map { it.id })
        assertEquals(listOf("r11", "r12"), artists.map { it.id })
    }

    @Test
    fun `home rows read no more rows than they show`() = runTest {
        db.albumDao().upsertAlbums(
            (1..30).map { album("a${it.toString().padStart(2, '0')}", dateAdded = it.toLong()) } +
                listOf(album("fav", isFavorite = true), album("played")),
        )
        db.trackDao().upsertTracks(
            listOf(track("t1", albumId = "played", playCount = 5)) + (1..9).map { track("f$it", isFavorite = true) },
        )

        val recent = db.albumDao().observeRecentlyAddedAlbums(SERVER, limit = 12).first()
        val random = db.albumDao().observeRandomAlbums(SERVER, limit = 24).first()
        val picks = db.albumDao().getRandomFavoriteOrMostPlayedAlbums(SERVER, 20, downloadedOnly = false, limit = 10)
        val favoriteTracks = db.trackDao().observeRandomFavoriteTracks(SERVER, limit = 5).first()

        assertEquals((30 downTo 19).map { "a$it" }, recent.map { it.id })
        assertEquals(24, random.map { it.id }.toSet().size)
        assertEquals(setOf("fav", "played"), picks.map { it.id }.toSet())
        assertEquals(5, favoriteTracks.size)
        assertTrue(favoriteTracks.all { it.isFavorite })
    }

    private suspend fun libraryTrackIds(sort: Int): List<String> =
        db.trackDao().getLibraryTracks(SERVER, sort, downloadedOnly = false).loadAll().map { it.id }

    private suspend fun libraryAlbumIds(sort: Int): List<String> =
        db.albumDao().getLibraryAlbums(SERVER, sort, genre = null, downloadedOnly = false).loadAll().map { it.id }

    /** Loads every page the way Paging does: a refresh, then appends until there's no next page. */
    private suspend fun <T : Any> PagingSource<Int, T>.loadAll(pageSize: Int = 50): List<T> {
        val items = mutableListOf<T>()
        var page = load(PagingSource.LoadParams.Refresh(key = null, loadSize = pageSize, placeholdersEnabled = false))
        while (true) {
            assertTrue("Unexpected $page", page is PagingSource.LoadResult.Page)
            page as PagingSource.LoadResult.Page
            items += page.data
            val next = page.nextKey ?: return items
            page = load(PagingSource.LoadParams.Append(key = next, loadSize = pageSize, placeholdersEnabled = false))
        }
    }

    private companion object {
        const val SERVER = "server"

        fun track(
            id: String,
            serverId: String = SERVER,
            name: String = id,
            albumId: String? = null,
            albumName: String? = null,
            isFavorite: Boolean = false,
            playCount: Int = 0,
            dateAdded: Long = 0,
        ) = TrackEntity(
            id = id, serverId = serverId, name = name, sortName = name, albumId = albumId, albumName = albumName,
            artistId = null, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = isFavorite, playCount = playCount, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = dateAdded, lastSynced = 0,
        )

        fun album(
            id: String,
            name: String = id,
            year: Int? = null,
            genres: List<String> = emptyList(),
            artistId: String? = null,
            resolvedArtistId: String? = null,
            isFavorite: Boolean = false,
            dateAdded: Long = 0,
        ) = AlbumEntity(
            id = id, serverId = SERVER, name = name, sortName = name, artistId = artistId, artistName = null,
            year = year, trackCount = 0, genres = genres, imageTag = null, isFavorite = isFavorite,
            resolvedArtistId = resolvedArtistId, dateAdded = dateAdded, lastSynced = 0,
        )

        fun artist(id: String, name: String) = ArtistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = name.lowercase(),
            musicBrainzId = null, lastSynced = 0,
        )

        fun alias(raw: String, canonical: String) = ArtistAliasEntity(SERVER, raw, canonical, 0)

        fun download(trackId: String, status: Int) = DownloadEntity(
            trackId = trackId, albumId = null, serverId = SERVER, status = status, progress = 0f,
            bytesDownloaded = 0, totalBytes = 0, quality = "original", filePath = null, requestedAt = 0,
            completedAt = 0, errorMessage = null, lastSynced = 0,
        )

        fun playlist(id: String) = PlaylistEntity(
            id = id, serverId = SERVER, name = id, sortName = id, trackCount = 0, durationMs = 0, imageTag = null,
            isFavorite = false, isLocal = false, lastSynced = 0,
        )

        fun crossRef(playlistId: String, trackId: String, position: Int) =
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = position, addedAt = 0)
    }
}
