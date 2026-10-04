package dev.mellow.core.database.perf

import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The benchmark's library generator, run small so it keeps working between benchmark runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FakeLibraryTest {

    private val databases = mutableListOf<MellowDatabase>()

    @After
    fun tearDown() = databases.forEach { it.close() }

    @Test
    fun `fills a library of the requested size with a realistic shape`() {
        val db = database()
        val contents = FakeLibrary().fill(db.openHelper.writableDatabase, 2_000)

        assertEquals(2_000, count(db, "SELECT COUNT(*) FROM tracks WHERE serverId = '${FakeLibrary.SERVER}'"))
        assertEquals(contents.albums, count(db, "SELECT COUNT(*) FROM albums"))
        assertEquals(contents.artists, count(db, "SELECT COUNT(*) FROM artists"))
        assertTrue("another server's tracks are mixed in", count(db, "SELECT COUNT(*) FROM tracks") > 2_000)
        assertTrue("albums have 8 to 20 tracks", contents.albums in 2_000 / 20..2_000 / 8 + 1)
        assertTrue("some are favorites", count(db, "SELECT COUNT(*) FROM tracks WHERE isFavorite = 1") > 0)
        assertTrue("some are played", count(db, "SELECT COUNT(*) FROM tracks WHERE playCount > 0") > 0)
        val sampleAlbumTracks = "SELECT COUNT(*) FROM tracks WHERE albumId = '${contents.sampleAlbumId}'"
        assertTrue("the sample album has tracks", count(db, sampleAlbumTracks) > 0)
    }

    @Test
    fun `the same seed gives the same library`() {
        val first = database()
        val second = database()
        FakeLibrary(seed = 7).fill(first.openHelper.writableDatabase, 500)
        FakeLibrary(seed = 7).fill(second.openHelper.writableDatabase, 500)

        assertEquals(ids(first), ids(second))
    }

    private fun database() =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }

    private fun count(db: MellowDatabase, sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun ids(db: MellowDatabase): List<String> =
        db.openHelper.readableDatabase.query("SELECT id FROM tracks ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
}
