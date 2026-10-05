package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.TrackArtistCrossRef
import dev.mellow.core.database.paging.KeysetPagingKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumArtistKeysetPagingTest {

    private lateinit var db: MellowDatabase
    private lateinit var albumQueries: AlbumKeysetQueryFactory
    private lateinit var artistQueries: ArtistKeysetQueryFactory
    private val completedAlbumIds = mutableSetOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumQueries = AlbumKeysetQueryFactory(db)
        artistQueries = ArtistKeysetQueryFactory(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `albums page without skips for every order download and genre filter`() = runTest {
        val albums = seedAlbums()

        for (order in ORDERS) {
            for (downloadedOnly in listOf(false, true)) {
                for (genre in listOf(null, "Rock")) {
                    val expected = albumReference(albums, order, genre, downloadedOnly)
                    val source = albumQueries.libraryPagingSource(SERVER, order, genre, downloadedOnly)

                    val actual = source.loadAll(pageSize = 5)

                    assertEquals(
                        "order=$order downloadedOnly=$downloadedOnly genre=$genre",
                        expected.map { it.id },
                        actual.map { it.id },
                    )
                }
            }
        }
    }

    @Test
    fun `album middle refresh prepends and appends to the same filtered list`() = runTest {
        val albums = seedAlbums()

        for (order in ORDERS) {
            for (downloadedOnly in listOf(false, true)) {
                for (genre in listOf(null, "Rock")) {
                    val expected = albumReference(albums, order, genre, downloadedOnly)
                    val source = albumQueries.libraryPagingSource(SERVER, order, genre, downloadedOnly)
                    val refresh = source.refresh(KeysetPagingKey.Position(expected.size / 2), loadSize = 5)

                    val actual = source.loadAround(refresh, pageSize = 5)

                    assertEquals(
                        "order=$order downloadedOnly=$downloadedOnly genre=$genre",
                        expected.map { it.id },
                        actual.map { it.id },
                    )
                }
            }
        }
    }

    @Test
    fun `album name descending is the exact reverse and null years sort last`() = runTest {
        seedAlbums()

        val ascending = albumQueries.libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, null, false).loadAll(5)
        val descending = albumQueries.libraryPagingSource(SERVER, LibraryOrder.NAME_DESC, null, false).loadAll(5)
        val byYear = albumQueries.libraryPagingSource(SERVER, LibraryOrder.YEAR, null, false).loadAll(5)

        assertEquals(ascending.map { it.id }.reversed(), descending.map { it.id })
        assertTrue(byYear.takeLast(1).single().year == null)
    }

    @Test
    fun `album placeholders report the full filtered count`() = runTest {
        val albums = seedAlbums()
        val expected = albumReference(albums, LibraryOrder.RECENTLY_ADDED, "Rock", downloadedOnly = true)
        val source = albumQueries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, "Rock", true)

        val page = source.refresh(KeysetPagingKey.Position(expected.size / 2), loadSize = 5)

        assertEquals(expected.size, page.itemsBefore + page.data.size + page.itemsAfter)
        assertTrue(source.jumpingSupported)
    }

    @Test
    fun `artist aliases merge and indexed counts equal the old coalesce query`() = runTest {
        seedArtists()

        for (downloadedOnly in listOf(false, true)) {
            val old = oldArtistRows(downloadedOnly)
            val new = artistQueries.libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, downloadedOnly).loadAll(2)

            assertEquals(old, new.associate { it.artist.id to it.localAlbumCount })
            assertEquals(new.size, new.map { it.artist.id }.toSet().size)
        }
    }

    @Test
    fun `artists page every name and sort-name order in both download modes`() = runTest {
        seedArtists()

        for (order in ORDERS) {
            for (downloadedOnly in listOf(false, true)) {
                val rows = artistQueries.libraryPagingSource(SERVER, order, downloadedOnly).loadAll(2)
                val expected = when (order) {
                    LibraryOrder.NAME_ASC -> rows.sortedWith(
                        compareBy<ArtistWithAlbumCount, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.name }
                            .thenBy { it.artist.sortName }
                            .thenBy { it.artist.id },
                    )
                    LibraryOrder.NAME_DESC -> rows.sortedWith(
                        compareBy<ArtistWithAlbumCount, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.name }
                            .thenBy { it.artist.sortName }
                            .thenBy { it.artist.id }
                            .reversed(),
                    )
                    else -> rows.sortedWith(compareBy({ it.artist.sortName }, { it.artist.id }))
                }

                assertEquals(
                    "order=$order downloadedOnly=$downloadedOnly",
                    expected.map { it.artist.id },
                    rows.map { it.artist.id },
                )
            }
        }
    }

    @Test
    fun `album and artist dependencies invalidate their paging sources`() = runTest {
        seedArtists()
        val albumSource = albumQueries.libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, null, true)
        albumSource.refresh(null, 5)
        assertFalse(albumSource.invalid)
        db.downloadDao().upsertAll(listOf(download("track-c1", DownloadEntity.STATUS_DOWNLOADING)))
        albumSource.awaitInvalid()

        val artistSource = artistQueries.libraryPagingSource(SERVER, LibraryOrder.NAME_ASC, false)
        artistSource.refresh(null, 5)
        assertFalse(artistSource.invalid)
        db.albumDao().upsertAlbums(listOf(album("new-album", artistId = "c1", resolvedArtistId = "c1")))
        artistSource.awaitInvalid()
    }

    private suspend fun seedAlbums(): List<AlbumEntity> {
        val names = listOf("alpha", "Alpha", "ALPHA", "beta", "Beta", "gamma")
        val years = listOf<Int?>(null, 1999, 2000, 2000, 2020)
        val albums = (0 until 41).map { index ->
            album(
                id = "a${index.toString().padStart(2, '0')}",
                name = names[index % names.size],
                year = years[index % years.size],
                genres = when {
                    index == 40 -> listOf("Punk Rock")
                    index % 3 == 0 -> listOf("Rock", "Pop")
                    else -> listOf("Jazz")
                },
                dateAdded = (index % 7).toLong(),
            ).copy(sortName = "sort-${index % 4}")
        }
        db.albumDao().upsertAlbums(albums)
        db.albumDao().upsertAlbums(listOf(album("other").copy(serverId = "other")))
        val tracks = albums.map { row -> track("track-${row.id}", albumId = row.id) }
        db.trackDao().upsertTracks(tracks)
        db.downloadDao().upsertAll(
            tracks.mapIndexedNotNull { index, track ->
                when {
                    index % 2 == 0 -> download(track.id, DownloadEntity.STATUS_COMPLETED).also {
                        completedAlbumIds += checkNotNull(track.albumId)
                    }
                    index % 5 == 0 -> download(track.id, DownloadEntity.STATUS_DOWNLOADING)
                    else -> null
                }
            },
        )
        return albums
    }

    private suspend fun seedArtists() {
        db.artistDao().upsertArtists(
            listOf(
                artist("c1", "Alpha").copy(sortName = "two"),
                artist("raw-1", "alpha").copy(sortName = "unused-1"),
                artist("raw-2", "ALPHA").copy(sortName = "unused-2"),
                artist("c2", "beta").copy(sortName = "one"),
                artist("c3", "Beta").copy(sortName = "three"),
            ),
        )
        db.artistAliasDao().upsertAliases(
            listOf(
                alias("c1", "c1"),
                alias("raw-1", "c1"),
                alias("raw-2", "c1"),
                alias("c2", "c2"),
                alias("c3", "c3"),
            ),
        )
        db.albumDao().upsertAlbums(
            listOf(
                album("resolved-1", artistId = "raw-1", resolvedArtistId = "c1"),
                album("resolved-2", artistId = "raw-2", resolvedArtistId = "c1"),
                album("unresolved-canonical", artistId = "c1"),
                // The old COALESCE query credits this to raw-1, not c1, until alias resolution fills resolvedArtistId.
                album("unresolved-alias", artistId = "raw-1"),
                album("c2-album", artistId = "c2", resolvedArtistId = "c2"),
            ),
        )
        db.trackDao().upsertTracks(
            listOf(
                track("track-c1", albumId = "resolved-1").copy(artistId = "raw-1"),
                track("track-c2", albumId = "c2-album").copy(artistId = "nobody"),
            ),
        )
        db.trackDao().insertTrackArtists(listOf(TrackArtistCrossRef("track-c2", "c2", "beta", 0)))
        db.downloadDao().upsertAll(
            listOf(
                download("track-c1", DownloadEntity.STATUS_COMPLETED),
                download("track-c2", DownloadEntity.STATUS_COMPLETED),
            ),
        )
    }

    private fun albumReference(
        albums: List<AlbumEntity>,
        order: Int,
        genre: String?,
        downloadedOnly: Boolean,
    ): List<AlbumEntity> {
        val filtered = albums.filter { row ->
            (!downloadedOnly || row.id in completedAlbumIds) && (genre == null || genre in row.genres)
        }
        val comparator: Comparator<AlbumEntity> = when (order) {
            LibraryOrder.NAME_ASC -> compareBy<AlbumEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.sortName }
                .thenBy { it.id }
            LibraryOrder.NAME_DESC -> compareBy<AlbumEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.sortName }
                .thenBy { it.id }
                .reversed()
            LibraryOrder.YEAR -> compareByDescending<AlbumEntity> { it.year ?: Int.MIN_VALUE }
                .thenBy { it.sortName }
                .thenBy { it.id }
            else -> compareByDescending<AlbumEntity> { it.dateAdded }
                .thenBy { it.sortName }
                .thenBy { it.id }
        }
        return filtered.sortedWith(comparator)
    }

    private fun oldArtistRows(downloadedOnly: Boolean): Map<String, Int> {
        val flag = if (downloadedOnly) 1 else 0
        val query = SimpleSQLiteQuery(
            """
            SELECT a.id, COALESCE(c.albumCount, 0) AS localAlbumCount
            FROM artists a
            INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
            LEFT JOIN (
                SELECT COALESCE(resolvedArtistId, artistId) AS artistKey, COUNT(*) AS albumCount
                FROM albums
                WHERE serverId = ? AND (? = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
                GROUP BY artistKey
            ) c ON c.artistKey = a.id
            WHERE a.serverId = ? AND (? = 0 OR a.id IN ($DOWNLOADED_ARTIST_IDS))
            GROUP BY aa.canonicalArtistId
            """.trimIndent(),
            arrayOf<Any>(SERVER, flag, SERVER, flag),
        )
        return db.openHelper.readableDatabase.query(query).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
            }
        }
    }

    private companion object {
        const val SERVER = LibraryPagingQueriesTest.SERVER
        val ORDERS = listOf(
            LibraryOrder.RECENTLY_ADDED,
            LibraryOrder.NAME_ASC,
            LibraryOrder.NAME_DESC,
            LibraryOrder.YEAR,
        )

        fun album(
            id: String,
            name: String = id,
            year: Int? = null,
            genres: List<String> = emptyList(),
            artistId: String? = null,
            resolvedArtistId: String? = null,
            dateAdded: Long = 0,
        ) = LibraryPagingQueriesTest.album(
            id = id,
            name = name,
            year = year,
            genres = genres,
            artistId = artistId,
            resolvedArtistId = resolvedArtistId,
            dateAdded = dateAdded,
        )

        fun artist(id: String, name: String) = LibraryPagingQueriesTest.artist(id, name)
        fun alias(raw: String, canonical: String) = LibraryPagingQueriesTest.alias(raw, canonical)
        fun track(id: String, albumId: String?) = LibraryPagingQueriesTest.track(id, albumId = albumId)
        fun download(trackId: String, status: Int) = LibraryPagingQueriesTest.download(trackId, status)
    }
}

private suspend fun <T : Any> PagingSource<KeysetPagingKey, T>.loadAll(pageSize: Int): List<T> {
    val items = mutableListOf<T>()
    var page = refresh(null, pageSize)
    while (true) {
        items += page.data
        val next = page.nextKey ?: return items
        page = append(next, pageSize)
    }
}

private suspend fun <T : Any> PagingSource<KeysetPagingKey, T>.loadAround(
    refresh: PagingSource.LoadResult.Page<KeysetPagingKey, T>,
    pageSize: Int,
): List<T> {
    val before = ArrayDeque<List<T>>()
    var previous = refresh.prevKey
    while (previous != null) {
        val page = prepend(previous, pageSize)
        before.addFirst(page.data)
        previous = page.prevKey
    }
    val items = before.flatten().toMutableList().apply { addAll(refresh.data) }
    var next = refresh.nextKey
    while (next != null) {
        val page = append(next, pageSize)
        items += page.data
        next = page.nextKey
    }
    return items
}

private suspend fun <T : Any> PagingSource<KeysetPagingKey, T>.refresh(
    key: KeysetPagingKey?,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, T> =
    load(PagingSource.LoadParams.Refresh(key, loadSize, placeholdersEnabled = true)).page()

private suspend fun <T : Any> PagingSource<KeysetPagingKey, T>.append(
    key: KeysetPagingKey,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, T> =
    load(PagingSource.LoadParams.Append(key, loadSize, placeholdersEnabled = true)).page()

private suspend fun <T : Any> PagingSource<KeysetPagingKey, T>.prepend(
    key: KeysetPagingKey,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, T> =
    load(PagingSource.LoadParams.Prepend(key, loadSize, placeholdersEnabled = true)).page()

private fun <T : Any> PagingSource.LoadResult<KeysetPagingKey, T>.page():
    PagingSource.LoadResult.Page<KeysetPagingKey, T> {
    assertTrue("Unexpected load result: $this", this is PagingSource.LoadResult.Page)
    return this as PagingSource.LoadResult.Page
}

private suspend fun PagingSource<*, *>.awaitInvalid() {
    repeat(100) {
        if (invalid) return
        withContext(Dispatchers.IO) { Thread.sleep(10) }
    }
    assertTrue("PagingSource was not invalidated", invalid)
}
