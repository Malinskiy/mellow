package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.perf.FakeLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * How SQLite runs the hot queries: each uses the index meant for it, and the lists read that index in order instead
 * of sorting. Checks the statements Room actually runs (paging wraps a query in a LIMIT/OFFSET and a COUNT), on a
 * small library: the plan doesn't depend on the size, and QueryBenchmark measures the time on a million tracks.
 *
 * Robolectric's SQLite (3.32) is newer than the oldest supported Android's (3.18); the plans are rechecked on an
 * Android 8 emulator before a release that changes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueryPlanTest {

    private lateinit var db: MellowDatabase
    private val statements = Collections.synchronizedList(mutableListOf<Pair<String, List<Any?>>>())
    private lateinit var library: FakeLibrary.Contents
    private val server = FakeLibrary.SERVER

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(RoomDatabase.QueryCallback { sql, args -> statements += sql to args }, Runnable::run)
            .build()
        library = FakeLibrary().fill(db.openHelper.writableDatabase, 3_000)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `each order of the Tracks tab reads its index without sorting`() = runTest {
        val indexes = mapOf(
            LibraryOrder.RECENTLY_ADDED to "index_tracks_serverId_dateAdded_id",
            LibraryOrder.NAME_ASC to "index_tracks_serverId_name_dateAdded_id",
            LibraryOrder.NAME_DESC to "index_tracks_serverId_name_dateAdded_id",
            LibraryOrder.YEAR to "index_tracks_serverId_albumName_dateAdded_id",
        )
        for ((order, index) in indexes) {
            val page = plans { db.trackDao().getLibraryTracks(server, order, false).loadAt(1_000) }
                .single { "LIMIT" in it.sql }
            page.assertUses(index)
            page.assertNoSort()
            val slice = plans { db.trackDao().getLibraryTracksSlice(server, order, false, 500, 1_000) }.single()
            slice.assertUses(index)
            slice.assertNoSort()
        }
    }

    @Test
    fun `downloaded only starts from the downloads`() = runTest {
        val page = plans { db.trackDao().getLibraryTracks(server, LibraryOrder.NAME_ASC, true).loadAt(0) }
            .single { "LIMIT" in it.sql }

        val firstTable = page.details.first { it.startsWith("SEARCH") || it.startsWith("SCAN") }
        assertTrue(
            "Expected to start from the downloads:\n$page",
            "index_downloads_status_serverId_trackId" in firstTable,
        )
    }

    @Test
    fun `album and artist screens read their tracks by index`() = runTest {
        plans { db.trackDao().getTracksByAlbumSync(library.sampleAlbumId) }.single().apply {
            assertUses("index_tracks_albumId_discNumber_trackNumber_id")
            assertNoSort()
        }
        plans { db.trackDao().getTracksByResolvedArtistSync(library.sampleArtistId) }.single().apply {
            assertUses("index_tracks_resolvedArtistId_playCount")
            assertNoSort()
        }
        plans { db.trackDao().countTracksByResolvedArtist(library.sampleArtistId) }.single()
            .assertUses("index_tracks_resolvedArtistId_playCount")
    }

    @Test
    fun `home and favorites read their tracks by index`() = runTest {
        plans { db.trackDao().getRecentlyPlayedTracks(server) }.single().apply {
            assertUses("index_tracks_serverId_lastPlayedAt")
            assertNoSort()
        }
        plans { db.trackDao().getMostPlayed(server).first() }.single().apply {
            assertUses("index_tracks_serverId_playCount")
            assertNoSort()
        }
        plans { db.trackDao().getFavoriteTracksPaged(server, false).loadAt(0) }.single { "LIMIT" in it.sql }.apply {
            assertUses("index_tracks_serverId_isFavorite")
            assertNoSort()
        }
    }

    @Test
    fun `Android Auto's songs read their index`() = runTest {
        plans { db.trackDao().getTracksByServerPaged(server, false, 500, 1_000) }.single().apply {
            assertUses("index_tracks_serverId_sortName_id")
            assertNoSort()
        }
    }

    @Test
    fun `the whole-library shuffle reads only the rows it picks`() = runTest {
        // As many row numbers as a pick of 500 draws: with this many, SQLite would rather filter by the server's index.
        plans { db.trackDao().getTracksByRowids(server, (1L..640L).toList()) }.single()
            .assertUses("INTEGER PRIMARY KEY (rowid=?)")
        plans { db.trackDao().getRowidRange() }.single().details.forEach {
            assertFalse("Reads the whole table:\n$it", it.startsWith("SCAN") && "tracks" in it)
        }
    }

    /** A statement and how SQLite runs it (EXPLAIN QUERY PLAN's details, one line per step). */
    private class Plan(val sql: String, val details: List<String>) {
        fun assertUses(index: String) = assertTrue("Expected $index in:\n$this", details.any { index in it })
        fun assertNoSort() = assertFalse(
            "Sorts instead of reading an index in order:\n$this",
            // Also "FOR RIGHT PART OF ORDER BY" and "FOR LAST TERM OF ORDER BY": partial sorts.
            details.any { "TEMP B-TREE FOR" in it && "ORDER BY" in it },
        )
        override fun toString() = "$sql\n" + details.joinToString("\n") { "  $it" }
    }

    /** The plans of the queries [block] runs (not Room's own bookkeeping). */
    private suspend fun plans(block: suspend () -> Unit): List<Plan> {
        statements.clear()
        block()
        return statements.toList()
            .filter { (sql, _) -> sql.trimStart().startsWith("SELECT", ignoreCase = true) && "room_" !in sql }
            .map { (sql, args) ->
                val explain = SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", args.toTypedArray())
                val details = db.openHelper.readableDatabase.query(explain).use { cursor ->
                    val detail = cursor.getColumnIndexOrThrow("detail")
                    buildList { while (cursor.moveToNext()) add(cursor.getString(detail)) }
                }
                Plan(sql.trim().replace(Regex("\\s+"), " "), details)
            }
    }

    private suspend fun <V : Any> PagingSource<Int, V>.loadAt(position: Int) {
        val result = load(PagingSource.LoadParams.Refresh(position, 60, placeholdersEnabled = true))
        check(result is PagingSource.LoadResult.Page) { "Load at $position failed: $result" }
    }
}
