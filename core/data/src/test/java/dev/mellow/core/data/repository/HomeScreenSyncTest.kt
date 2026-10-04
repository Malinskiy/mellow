package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The home screen's quick sync, which the sync worker runs before the library sync. */
class HomeScreenSyncTest : LibrarySyncHarness() {

    private val artist = artist(1)
    private val listedAlbum = album(1, artist)
    private val listedTrack = track(1, listedAlbum, artist)

    /** Added on the server after a full pass listed the albums. */
    private val recentAlbum = album(2, artist)
    private val recentTrack = track(2, recentAlbum, artist)

    @Before
    fun fillServer() {
        artists = listOf(artist)
        albums = listOf(listedAlbum)
        tracks = listOf(listedTrack)
        coEvery { dataSource.getRecentlyAddedAlbums(USER, any()) } returns listOf(recentAlbum)
        coEvery { dataSource.getRecentlyPlayedItems(USER, any()) } returns listOf(recentTrack)
    }

    @Test
    fun `a home sync scheduled for a server that is no longer active writes nothing`() = runTest {
        // Scheduled while the previous server was active; the harness's server is the active one now.
        val result = repository().syncHomeScreenPriority("previous server")

        assertEquals(MellowResult.Success(emptySet<String>()), result)
        verify { dataSource wasNot Called }
        for (serverId in listOf("previous server", SERVER)) {
            assertEquals(0, countRows("albums", serverId))
            assertEquals(0, countRows("tracks", serverId))
        }
    }

    @Test
    fun `a home sync started during a full pass waits until the pass is done`() = runBlocking {
        val repository = repository()
        val pageHeld = CompletableDeferred<Unit>()
        val releasePage = CompletableDeferred<Unit>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } coAnswers {
            if (thirdArg<Int>() > 1) {
                // The pass has saved its artists and albums; it waits here before its tracks and the removal.
                pageHeld.complete(Unit)
                releasePage.await()
            }
            page(tracks, secondArg(), thirdArg())
        }
        val homeRequests = AtomicInteger()
        coEvery { dataSource.getRecentlyAddedAlbums(USER, any()) } answers {
            homeRequests.incrementAndGet()
            listOf(recentAlbum)
        }

        val pass = async(Dispatchers.Default) { repository.syncLibrary(SERVER) }
        withTimeout(5_000) { pageHeld.await() }
        val home = async(Dispatchers.Default) { repository.syncHomeScreenPriority(SERVER) }
        delay(300) // ample time for the home sync to fetch and save if nothing held it back
        assertEquals("the home sync waits for the pass", 0, homeRequests.get())
        assertNull(db.albumDao().getAlbumById(recentAlbum.key))
        releasePage.complete(Unit)

        assertSuccess(pass.await())
        assertEquals(MellowResult.Success(setOf(recentAlbum.key)), home.await())
        assertNotNull("saved after the pass, so not removed by it", db.albumDao().getAlbumById(recentAlbum.key))
        assertNotNull(db.trackDao().getTrackById(recentTrack.key))
        assertFalse(isRebuildPending())
    }

    @Test
    fun `cancelling a home sync stops it instead of returning an error`() = runBlocking {
        val fetching = CompletableDeferred<Unit>()
        coEvery { dataSource.getRecentlyAddedAlbums(USER, any()) } coAnswers {
            fetching.complete(Unit)
            awaitCancellation()
        }
        var returned: MellowResult<Set<String>>? = null

        val sync = launch(Dispatchers.Default) { returned = repository().syncHomeScreenPriority(SERVER) }
        withTimeout(5_000) { fetching.await() }
        sync.cancelAndJoin()

        assertNull("the cancellation came back as a result", returned)
        assertEquals(0, countRows("albums", SERVER))
    }
}
