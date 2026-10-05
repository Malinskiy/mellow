package dev.mellow.core.database.dao

import androidx.room.Room
import app.cash.turbine.test
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Home's Recently Played albums: the walk over the latest plays gives what grouping the whole history gave. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentlyPlayedAlbumsTest {

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
    fun `the walk gives the albums and order of grouping every play by album`() = runTest {
        val random = Random(5)
        // 60 albums, 40 in the library (the rest were removed), tracks played at distinct times, another server's
        // plays mixed in, and tracks without an album.
        db.albumDao().upsertAlbums((0 until 40).map { album("a$it") })
        val playTimes = (1L..3_000L).shuffled(random).iterator()
        val tracks = (0 until 1_500).map { i ->
            val played = random.nextInt(3) != 0
            TestEntities.track(
                "t$i",
                serverId = if (i % 25 == 0) "elsewhere" else SERVER,
                albumId = if (i % 31 == 0) null else "a${random.nextInt(60)}",
            ).copy(lastPlayedAt = if (played) playTimes.next() else 0)
        }
        db.trackDao().upsertTracks(tracks)

        for (limit in listOf(1, 5, 20, 39, 40, 100)) {
            assertEquals(
                "limit $limit",
                grouped(limit).map { it.id },
                db.albumDao().getRecentlyPlayedAlbums(SERVER, limit).map { it.id },
            )
        }
    }

    @Test
    fun `nothing played gives nothing`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("a1")))
        db.trackDao().upsertTracks(listOf(TestEntities.track("t1", albumId = "a1")))

        assertEquals(emptyList<AlbumEntity>(), db.albumDao().getRecentlyPlayedAlbums(SERVER, 20))
    }

    @Test
    fun `the row updates when a track is played`() = runTest {
        db.albumDao().upsertAlbums(listOf(album("a1"), album("a2")))
        db.trackDao().upsertTracks(
            listOf(
                TestEntities.track("t1", albumId = "a1").copy(lastPlayedAt = 10),
                TestEntities.track("t2", albumId = "a2").copy(lastPlayedAt = 20),
            ),
        )
        RecentlyPlayedAlbumsObserver(db).observe(SERVER, 20).test {
            assertEquals(listOf("a2", "a1"), awaitItem().map { it.id })

            db.trackDao().updateLastPlayedAt("t1", 30)

            assertEquals(listOf("a1", "a2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** What the row's query used to do: group every played track by album, latest play first. */
    private fun grouped(limit: Int): List<AlbumEntity> {
        val query = SimpleSQLiteQuery(
            """
            SELECT a.id FROM albums a
            INNER JOIN (
                SELECT albumId, MAX(lastPlayedAt) AS maxPlayed FROM tracks
                WHERE serverId = ? AND lastPlayedAt > 0 AND albumId IS NOT NULL
                GROUP BY albumId
            ) t ON a.id = t.albumId
            ORDER BY t.maxPlayed DESC
            LIMIT ?
            """,
            arrayOf(SERVER, limit),
        )
        val ids = db.openHelper.readableDatabase.query(query).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return ids.map { album(it) }
    }

    private fun album(id: String) = TestEntities.album(id)

    private companion object {
        const val SERVER = TestEntities.SERVER
    }
}
