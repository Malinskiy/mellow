package dev.mellow.core.database.dao

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a shuffle picks: which query, and that the tracks come back in the order picked. */
class PickRandomTracksTest {

    private val trackDao = mockk<TrackDao>()

    @Test
    fun `a small library is sorted at random and plays in the order picked`() = runTest {
        coEvery { trackDao.getRowidRange() } returns RowidRange(low = 1, high = 10)
        coEvery { trackDao.getRandomTrackIds(SERVER, false, 3) } returns listOf("c", "a", "b")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf("a", "b", "c").map { track(it) }

        val picked = trackDao.pickRandomTracks(SERVER, downloadedOnly = false, limit = 3)

        assertEquals(listOf("c", "a", "b"), picked.map { it.id })
        coVerify(exactly = 0) { trackDao.getTracksByRowids(any(), any()) }
    }

    @Test
    fun `downloads only picks among the downloads, leaving out a track removed meanwhile`() = runTest {
        coEvery { trackDao.getRandomDownloadedTrackIds(SERVER, 3) } returns listOf("c", "gone", "a")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf("a", "c").map { track(it) }

        val picked = trackDao.pickRandomTracks(SERVER, downloadedOnly = true, limit = 3)

        assertEquals(listOf("c", "a"), picked.map { it.id })
        coVerify(exactly = 0) { trackDao.getRowidRange() }
    }

    @Test
    fun `an empty library picks nothing`() = runTest {
        coEvery { trackDao.getRowidRange() } returns RowidRange(low = null, high = null)

        assertEquals(emptyList<String>(), trackDao.pickRandomTracks(SERVER, downloadedOnly = false, limit = 3))
    }

    private fun track(id: String) = LibraryPagingQueriesTest.track(id)

    private companion object {
        const val SERVER = LibraryPagingQueriesTest.SERVER
    }
}
