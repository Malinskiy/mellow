package dev.mellow.core.database.dao

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.TrackEntity
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
class TrackKeysetPagingTest {

    private lateinit var db: MellowDatabase
    private lateinit var queries: TrackKeysetQueryFactory

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queries = TrackKeysetQueryFactory(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `every order and download filter pages forward without skips or duplicates`() = runTest {
        seedTiedTracks()

        for (order in ORDERS) {
            for (downloadedOnly in listOf(false, true)) {
                val expected = reference(order, downloadedOnly)
                val actual = queries.libraryPagingSource(SERVER, order, downloadedOnly).loadAll(pageSize = 7)
                assertEquals(
                    "order=$order downloadedOnly=$downloadedOnly",
                    expected.map { it.id },
                    actual.map { it.id },
                )
            }
        }
    }

    @Test
    fun `prepending from a middle refresh reaches the same top in every order and filter`() = runTest {
        seedTiedTracks()

        for (order in ORDERS) {
            for (downloadedOnly in listOf(false, true)) {
                val expected = reference(order, downloadedOnly)
                val source = queries.libraryPagingSource(SERVER, order, downloadedOnly)
                val refresh = source.refresh(KeysetPagingKey.Position(expected.size / 2), loadSize = 7)
                val actual = source.loadAround(refresh, pageSize = 7)
                assertEquals(
                    "order=$order downloadedOnly=$downloadedOnly",
                    expected.map { it.id },
                    actual.map { it.id },
                )
            }
        }
    }

    @Test
    fun `position refresh reports exact placeholders around the requested row`() = runTest {
        seedTiedTracks()
        val expected = reference(LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
        val source = queries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)

        val page = source.refresh(KeysetPagingKey.Position(23), loadSize = 7)

        assertEquals(20, page.itemsBefore)
        assertEquals(expected.size - 27, page.itemsAfter)
        assertEquals(expected[23].id, page.data[3].id)
        assertTrue(source.jumpingSupported)
    }

    @Test
    fun `row refresh key keeps the anchor after inserts and deletes above it`() = runTest {
        seedTiedTracks()
        val original = reference(LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
        val source = queries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
        val page = source.refresh(KeysetPagingKey.Position(30), loadSize = 9)
        val anchor = original[30]
        val state = PagingState(
            pages = listOf(page),
            anchorPosition = 30,
            config = PagingConfig(pageSize = 9, enablePlaceholders = true),
            leadingPlaceholderCount = page.itemsBefore,
        )
        val key = source.getRefreshKey(state)
        assertTrue(key is KeysetPagingKey.Row)

        db.trackDao().upsertTracks(
            listOf(
                track("newer-1", dateAdded = Long.MAX_VALUE),
                track("newer-2", dateAdded = Long.MAX_VALUE - 1),
            ),
        )
        db.trackDao().deleteById(original[5].id)

        val refreshed = queries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, false)
            .refresh(key, loadSize = 9)
        assertEquals(anchor.id, refreshed.data[4].id)
        assertEquals(31, refreshed.itemsBefore + 4)
    }

    @Test
    fun `track and download writes invalidate their paging sources`() = runTest {
        db.trackDao().upsertTracks(listOf(track("track")))
        db.downloadDao().upsertAll(listOf(download("track", DownloadEntity.STATUS_DOWNLOADING)))
        val trackSource = queries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, false)
        trackSource.refresh(null, loadSize = 7)
        assertFalse(trackSource.invalid)

        db.trackDao().incrementPlayCount("track")
        trackSource.awaitInvalid()

        val downloadSource = queries.libraryPagingSource(SERVER, LibraryOrder.RECENTLY_ADDED, true)
        downloadSource.refresh(null, loadSize = 7)
        assertFalse(downloadSource.invalid)

        db.downloadDao().upsertAll(listOf(download("track", DownloadEntity.STATUS_COMPLETED)))
        downloadSource.awaitInvalid()
    }

    @Test
    fun `library queue windows keep context and shift back at the end`() = runTest {
        db.trackDao().upsertTracks((0 until 900).map { index -> track("t$index", dateAdded = index.toLong()) })
        val ordered = reference(LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)

        assertWindow(ordered, anchor = 350, expectedStart = 250)
        assertWindow(ordered, anchor = 50, expectedStart = 0)
        assertWindow(ordered, anchor = 875, expectedStart = 400)
        assertTrue(
            queries.libraryQueueWindow(SERVER, LibraryOrder.RECENTLY_ADDED, false, "missing", 100, 500)
                .isEmpty(),
        )

        db.trackDao().deleteByServer(SERVER)
        db.trackDao().upsertTracks((0 until 80).map { index -> track("short$index", dateAdded = index.toLong()) })
        val short = reference(LibraryOrder.RECENTLY_ADDED, downloadedOnly = false)
        val window = queries.libraryQueueWindow(SERVER, LibraryOrder.RECENTLY_ADDED, false, short[30].id, 100, 500)
        assertEquals(short.map { it.id }, window.map { it.id })
        assertEquals(30, window.indexOfFirst { it.id == short[30].id })
    }

    @Test
    fun `Android Auto song window uses its sort-name key`() = runTest {
        val tracks = (0 until 700).map { index ->
            track("t$index", name = "name-$index").copy(sortName = index.toString().padStart(4, '0'))
        }
        db.trackDao().upsertTracks(tracks)
        val anchor = tracks[675]

        val window = queries.autoQueueWindow(SERVER, false, anchor.id, before = 100, size = 500)

        assertEquals((200 until 700).map { "t$it" }, window.map { it.id })
        assertEquals(475, window.indexOfFirst { it.id == anchor.id })
    }

    private suspend fun seedTiedTracks() {
        val names = listOf("alpha", "Alpha", "ALPHA", "beta", "Beta", "gamma", "Gamma")
        val albums = listOf<String?>(null, "Zulu", "Zulu", "Alpha", null, "Middle")
        val tracks = (0 until 77).map { index ->
            track(
                id = "t${index.toString().padStart(3, '0')}",
                name = names[index % names.size],
                albumName = albums[index % albums.size],
                dateAdded = (index % 9).toLong(),
            )
        }
        db.trackDao().upsertTracks(tracks)
        db.downloadDao().upsertAll(
            tracks.mapIndexedNotNull { index, track ->
                when {
                    index % 2 == 0 -> download(track.id, DownloadEntity.STATUS_COMPLETED)
                    index % 5 == 0 -> download(track.id, DownloadEntity.STATUS_DOWNLOADING)
                    else -> null
                }
            },
        )
    }

    private suspend fun reference(order: Int, downloadedOnly: Boolean): List<TrackEntity> =
        db.trackDao().getTracksRaw(LibraryTracksQuery.page(SERVER, order, downloadedOnly))

    private suspend fun assertWindow(ordered: List<TrackEntity>, anchor: Int, expectedStart: Int) {
        val window = queries.libraryQueueWindow(
            SERVER,
            LibraryOrder.RECENTLY_ADDED,
            downloadedOnly = false,
            trackId = ordered[anchor].id,
            before = 100,
            size = 500,
        )
        assertEquals(ordered.subList(expectedStart, expectedStart + 500).map { it.id }, window.map { it.id })
        assertEquals(anchor - expectedStart, window.indexOfFirst { it.id == ordered[anchor].id })
    }

    private companion object {
        const val SERVER = LibraryPagingQueriesTest.SERVER
        val ORDERS = listOf(
            LibraryOrder.RECENTLY_ADDED,
            LibraryOrder.NAME_ASC,
            LibraryOrder.NAME_DESC,
            LibraryOrder.YEAR,
        )

        fun track(
            id: String,
            name: String = id,
            albumName: String? = null,
            dateAdded: Long = 0,
        ): TrackEntity = LibraryPagingQueriesTest.track(
            id = id,
            name = name,
            albumName = albumName,
            dateAdded = dateAdded,
        )

        fun download(trackId: String, status: Int) = LibraryPagingQueriesTest.download(trackId, status)
    }
}

private suspend fun PagingSource<KeysetPagingKey, TrackEntity>.loadAll(pageSize: Int): List<TrackEntity> {
    val items = mutableListOf<TrackEntity>()
    var page = refresh(null, pageSize)
    while (true) {
        items += page.data
        val next = page.nextKey ?: return items
        page = append(next, pageSize)
    }
}

private suspend fun PagingSource<KeysetPagingKey, TrackEntity>.loadAround(
    refresh: PagingSource.LoadResult.Page<KeysetPagingKey, TrackEntity>,
    pageSize: Int,
): List<TrackEntity> {
    val before = ArrayDeque<List<TrackEntity>>()
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

private suspend fun PagingSource<KeysetPagingKey, TrackEntity>.refresh(
    key: KeysetPagingKey?,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, TrackEntity> =
    load(PagingSource.LoadParams.Refresh(key, loadSize, placeholdersEnabled = true)).page()

private suspend fun PagingSource<KeysetPagingKey, TrackEntity>.append(
    key: KeysetPagingKey,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, TrackEntity> =
    load(PagingSource.LoadParams.Append(key, loadSize, placeholdersEnabled = true)).page()

private suspend fun PagingSource<KeysetPagingKey, TrackEntity>.prepend(
    key: KeysetPagingKey,
    loadSize: Int,
): PagingSource.LoadResult.Page<KeysetPagingKey, TrackEntity> =
    load(PagingSource.LoadParams.Prepend(key, loadSize, placeholdersEnabled = true)).page()

private fun PagingSource.LoadResult<KeysetPagingKey, TrackEntity>.page():
    PagingSource.LoadResult.Page<KeysetPagingKey, TrackEntity> {
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
