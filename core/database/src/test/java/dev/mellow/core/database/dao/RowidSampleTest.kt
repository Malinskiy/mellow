package dev.mellow.core.database.dao

import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
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
import kotlin.random.Random

/** The whole-library shuffle's pick by row number, on a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RowidSampleTest {

    private lateinit var db: MellowDatabase
    private val tracks get() = db.trackDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `every track of the server is equally likely, and other servers' tracks never come up`() = runTest {
        // 600 of this server's tracks with 200 of another server's mixed in, and some deleted: gaps in the row numbers.
        tracks.upsertTracks(
            (0 until 800).map { i -> track("t$i", serverId = if (i % 4 == 3) "elsewhere" else SERVER) },
        )
        (0 until 800 step 37).forEach { tracks.deleteById("t$it") }
        val library = (0 until 800).filter { it % 4 != 3 && it % 37 != 0 }.map { "t$it" }.toSet()
        val random = Random(1)
        val counts = HashMap<String, Int>()
        val picks = 3_000

        repeat(picks) {
            val picked = tracks.pickRandomTracks(SERVER, downloadedOnly = false, limit = 20, random = random)
            assertEquals(20, picked.size)
            assertEquals("no repeats", 20, picked.map { it.id }.toSet().size)
            picked.forEach { counts.merge(it.id, 1, Int::plus) }
        }

        assertTrue("only this server's tracks", library.containsAll(counts.keys))
        assertEquals("every track came up", library, counts.keys)
        // Each track is expected picks * 20 / tracks times (about 104); allow for chance, not for a skew.
        val expected = picks * 20.0 / library.size
        val likely = (expected * 0.6).toInt()..(expected * 1.4).toInt()
        counts.values.forEach { assertTrue("$it times, expected about $expected", it in likely) }
    }

    @Test
    fun `the picks are in random order, not row order`() = runTest {
        tracks.upsertTracks((0 until 1_000).map { track("t${it.toString().padStart(4, '0')}") })

        val picked = tracks.pickRandomTracks(SERVER, downloadedOnly = false, limit = 30, random = Random(2))

        assertEquals(30, picked.size)
        assertTrue(picked.map { it.id } != picked.map { it.id }.sorted())
    }

    @Test
    fun `a library mostly of another server's tracks still finds every one of this server's`() = runTest {
        // Row numbers span 2,000, enough to sample 50 by row number, but only 20 are this server's: sampling gives up
        // and the pick sorts this server's tracks instead.
        tracks.upsertTracks(
            (0 until 2_000).map { i -> track("t$i", serverId = if (i % 100 == 0) SERVER else "elsewhere") },
        )

        val picked = tracks.pickRandomTracks(SERVER, downloadedOnly = false, limit = 50, random = Random(3))

        assertEquals((0 until 2_000 step 100).map { "t$it" }.toSet(), picked.map { it.id }.toSet())
    }

    @Test
    fun `a library smaller than the pick returns all of it`() = runTest {
        tracks.upsertTracks((0 until 40).map { track("t$it") })

        val picked = tracks.pickRandomTracks(SERVER, downloadedOnly = false, limit = 500, random = Random(4))

        assertEquals((0 until 40).map { "t$it" }.toSet(), picked.map { it.id }.toSet())
    }

    private fun track(id: String, serverId: String = SERVER) = LibraryPagingQueriesTest.track(id, serverId = serverId)

    private companion object {
        const val SERVER = LibraryPagingQueriesTest.SERVER
    }
}
