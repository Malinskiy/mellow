package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.data.preferences.SavedQueue
import dev.mellow.core.database.DatabaseTransactionRunner
import dev.mellow.core.database.RoomTransactionRunner
import dev.mellow.core.database.entity.SyncPassKind
import dev.mellow.core.network.datasource.PagedItems
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Library sync of a small library: one page per listing. Larger libraries: [LibrarySyncPagingTest]. */
class LibrarySyncTest : LibrarySyncHarness() {

    private val a1 = artist(1)
    private val a2 = artist(2)
    private val a3 = artist(3)
    private val al1 = album(1, a1)
    private val al2 = album(2, a2)
    private val al3 = album(3, a3)
    private val t1 = track(1, al1, a1)
    private val t2 = track(2, al2, a2)
    private val t3 = track(3, al3, a3)
    private val t4 = track(4, album = null, artist = null)

    @Before
    fun fillServer() {
        artists = listOf(a1, a2, a3)
        albums = listOf(al1, al2, al3)
        tracks = listOf(t1, t2, t3, t4)
    }

    @Test
    fun `a failed incremental sync records nothing`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val recorded = preferences.readLibrarySyncState()
        val lastSynced = preferences.lastSyncTimestamp.first()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("Connection reset")

        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)

        coVerify { dataSource.getTracksPaged(USER, 0, any(), isNull(inverse = true)) }
        assertEquals(recorded, preferences.readLibrarySyncState())
        assertEquals(lastSynced, preferences.lastSyncTimestamp.first())
        assertEquals(1, preferences.syncCount.first())
        assertFalse(isRebuildPending())
    }

    @Test
    fun `changes are fetched from the start of the last successful sync, less the overlap`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val firstStart = preferences.readLibrarySyncState().lastStartedAt
        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), captureNullable(windows)) } answers {
            Thread.sleep(SLOW_REQUEST_MS) // changes saved on the server meanwhile must fall into the next window
            page(albums, secondArg(), thirdArg())
        }
        val beforeSecond = System.currentTimeMillis()

        assertSuccess(repository.syncLibrary(SERVER))

        assertEquals(listOf(utc(firstStart - LibraryRepositoryImpl.INCREMENTAL_SYNC_OVERLAP_MS)), windows.distinct())
        val secondStart = preferences.readLibrarySyncState().lastStartedAt
        val secondEnd = preferences.lastSyncTimestamp.first()
        assertTrue("$secondStart isn't when the sync started", secondStart in beforeSecond..secondEnd - SLOW_REQUEST_MS)
    }

    @Test
    fun `a failed first full pass is a full pass again next time`() = runTest {
        val repository = repository()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")

        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertTrue(isRebuildPending())
        assertEquals(0L, preferences.lastSyncTimestamp.first())

        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(windows)) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        assertSuccess(repository.syncLibrary(SERVER))

        assertTrue(windows.isNotEmpty())
        assertTrue("a full pass lists every track", windows.all { it == null })
        assertFalse(isRebuildPending())
        assertNotNull(db.trackDao().getTrackById(t4.key))
    }

    @Test
    fun `an interrupted rebuild is retried as a full pass`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        preferences.requestFullPass()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertTrue(isRebuildPending())

        tracks = listOf(t1, t3, t4)
        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(windows)) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        assertSuccess(repository.syncLibrary(SERVER))

        assertTrue("a full pass lists every track", windows.all { it == null })
        assertNull("deleted on the server", db.trackDao().getTrackById(t2.key))
        assertFalse(isRebuildPending())
    }

    @Test
    fun `a full pass removes what the server deleted and keeps what the device still uses`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val audiobook = track(5, album = null, artist = a1, kind = BaseItemKind.AUDIO_BOOK)
        unlisted = listOf(audiobook)
        db.trackDao().upsertTracks(listOf(audiobook.toTrackEntity(SERVER))) // saved from a playlist
        db.downloadDao().upsert(download(t3.key))
        coEvery { queue.load() } returns SavedQueue(SERVER, listOf(t4.key), 0, 0, false, 0)
        artists = listOf(a1)
        albums = listOf(al1)
        tracks = listOf(t1)
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertNull(db.trackDao().getTrackById(t2.key))
        assertNull(db.albumDao().getAlbumById(al2.key))
        assertNull(db.artistDao().getArtistById(a2.key))
        assertEquals(emptyList<String>(), db.trackDao().getArtistNamesForTrack(t2.key))
        assertEquals(emptyList<String>(), db.albumDao().getArtistNamesForAlbum(al2.key))
        // Downloaded: kept with its album and artist.
        assertNotNull(db.trackDao().getTrackById(t3.key))
        assertNotNull(db.albumDao().getAlbumById(al3.key))
        assertNotNull(db.artistDao().getArtistById(a3.key))
        assertEquals(listOf(a3.name), db.trackDao().getArtistNamesForTrack(t3.key))
        // In the play queue: kept.
        assertNotNull(db.trackDao().getTrackById(t4.key))
        // Still on the server, outside the listings: kept.
        assertNotNull(db.trackDao().getTrackById(audiobook.key))
        assertNotNull(db.trackDao().getTrackById(t1.key))
        coVerify(exactly = 0) { dataSource.getTracksByIds(USER, match { ids -> t3.id in ids }) }
        assertEquals(0, db.syncPassDao().count(SyncPassKind.TRACK))
        assertFalse(isRebuildPending())
    }

    @Test
    fun `a track queued while the full pass checks the server is kept`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        tracks = listOf(t1, t3, t4)
        preferences.requestFullPass()
        var savedQueue: SavedQueue? = null
        coEvery { queue.load() } answers { savedQueue }
        coEvery { dataSource.getTracksByIds(USER, any()) } answers {
            // While the pass asks the server about t2, the user queues it.
            savedQueue = SavedQueue(SERVER, listOf(t2.key), 0, 0, false, 0)
            (tracks + unlisted).withIds(secondArg())
        }

        assertSuccess(repository.syncLibrary(SERVER))

        assertNotNull(db.trackDao().getTrackById(t2.key))
        assertEquals(listOf(a2.name), db.trackDao().getArtistNamesForTrack(t2.key))
    }

    @Test
    fun `no track is deleted when the play queue can't be read`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        tracks = listOf(t1, t3, t4)
        preferences.requestFullPass()
        coEvery { queue.load() } throws IOException("Unreadable queue")

        assertSuccess(repository.syncLibrary(SERVER))

        assertNotNull(db.trackDao().getTrackById(t2.key))
        assertFalse(isRebuildPending())
    }

    @Test
    fun `the removal is one transaction`() = runTest {
        assertSuccess(repository().syncLibrary(SERVER))
        tracks = listOf(t1, t3, t4)
        albums = listOf(al1, al3)
        artists = listOf(a1, a3)
        preferences.requestFullPass()
        // The process dies just before the transaction that removed t2 commits.
        val crashing = object : DatabaseTransactionRunner {
            private val room = RoomTransactionRunner(db)
            override suspend fun <T> invoke(block: suspend () -> T): T = room {
                val result = block()
                if (db.trackDao().getTrackById(t2.key) == null) throw IOException("process died")
                result
            }
        }

        val result = repository(crashing).syncLibrary(SERVER)

        assertEquals("process died", (result as MellowResult.Error).exception.message)
        assertNotNull(db.trackDao().getTrackById(t2.key))
        assertNotNull(db.albumDao().getAlbumById(al2.key))
        assertNotNull(db.artistDao().getArtistById(a2.key))
        assertEquals(listOf(a2.name), db.trackDao().getArtistNamesForTrack(t2.key))
        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al2.key))
        assertTrue(isRebuildPending())
    }

    @Test
    fun `a full pass that saw a different count than the server reports deletes nothing`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val lastSynced = preferences.lastSyncTimestamp.first()
        tracks = listOf(t1, t3, t4)
        // A track is added while the pass pages through the tracks: the count asked for at the end is one more.
        coEvery { dataSource.getTracksPaged(USER, 0, 1, null) } returns PagedItems(tracks.take(1), tracks.size + 1)
        preferences.requestFullPass()

        val result = repository.syncLibrary(SERVER)

        assertTrue((result as MellowResult.Error).exception is LibraryChangedDuringSyncException)
        assertNotNull("not swept", db.trackDao().getTrackById(t2.key))
        coVerify(exactly = 0) { dataSource.getTracksByIds(any(), any()) }
        assertTrue(isRebuildPending())
        assertEquals(lastSynced, preferences.lastSyncTimestamp.first())
    }

    @Test
    fun `artist links survive an interrupted full pass`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        preferences.requestFullPass()

        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertLinksIntact()

        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } answers {
            page(albums, secondArg(), thirdArg())
        }
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertLinksIntact()
    }

    @Test
    fun `a full pass replaces the artist links of what it saved`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val retagged = al1.copy(albumArtists = listOf(NameGuidPair(a2.name, a2.id)))
        albums = listOf(retagged, al2, al3)
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al1.key))
    }

    @Test
    fun `two syncs started together run one after the other and remove things once`() = runBlocking {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        tracks = listOf(t1, t3, t4)
        preferences.requestFullPass()
        val gate = CompletableDeferred<Unit>()
        val artistRequests = AtomicInteger()
        coEvery { dataSource.getArtistsPaged(USER, any(), any()) } coAnswers {
            artistRequests.incrementAndGet()
            gate.await()
            page(artists, secondArg(), thirdArg())
        }
        val trackWindows = Collections.synchronizedList(mutableListOf<LocalDateTime?>())
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(trackWindows)) } answers {
            page(tracks, secondArg(), thirdArg())
        }

        val first = async(Dispatchers.Default) { repository.syncLibrary(SERVER) }
        withTimeout(5_000) { while (artistRequests.get() == 0) delay(10) }
        val second = async(Dispatchers.Default) { repository.syncLibrary(SERVER) }
        delay(300) // ample time for the second sync to start paging if nothing held it back
        assertEquals("the second sync waits for the first", 1, artistRequests.get())
        gate.complete(Unit)

        assertSuccess(first.await())
        assertSuccess(second.await())
        assertNull(db.trackDao().getTrackById(t2.key))
        coVerify(exactly = 1) { dataSource.getTracksByIds(USER, any()) }
        // The first sync ran the pending full pass, the second only asked for what changed since.
        val fullPassRequests = trackWindows.takeWhile { it == null }
        val laterRequests = trackWindows.drop(fullPassRequests.size)
        assertTrue(fullPassRequests.isNotEmpty())
        assertTrue(laterRequests.isNotEmpty() && laterRequests.all { it != null })
        assertEquals(3, preferences.syncCount.first())
        assertFalse(isRebuildPending())
    }

    private suspend fun assertLinksIntact() {
        assertEquals(listOf(a1.name), db.albumDao().getArtistNamesForAlbum(al1.key))
        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al2.key))
        assertEquals(listOf(a1.name), db.trackDao().getArtistNamesForTrack(t1.key))
        assertEquals(listOf(a2.name), db.trackDao().getArtistNamesForTrack(t2.key))
    }

    private companion object {
        const val SLOW_REQUEST_MS = 20L
    }
}
