package dev.mellow.core.database.dao

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A shuffle picks IDs at random, then loads those tracks; the loaded tracks must keep the random order. */
class PickRandomTracksTest {

    private val trackDao = mockk<TrackDao>()

    @Test
    fun `picked tracks play in the order they were picked, not the order the database returns them`() = runTest {
        coEvery { trackDao.getRandomTrackIds(SERVER, false, 3) } returns listOf("c", "a", "b")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf("a", "b", "c").map { track(it) }

        val picked = trackDao.pickRandomTracks(SERVER, downloadedOnly = false, limit = 3)

        assertEquals(listOf("c", "a", "b"), picked.map { it.id })
    }

    @Test
    fun `a track removed between the pick and the load is left out`() = runTest {
        coEvery { trackDao.getRandomTrackIds(SERVER, true, 3) } returns listOf("c", "gone", "a")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf("a", "c").map { track(it) }

        val picked = trackDao.pickRandomTracks(SERVER, downloadedOnly = true, limit = 3)

        assertEquals(listOf("c", "a"), picked.map { it.id })
    }

    private fun track(id: String) = LibraryPagingQueriesTest.track(id)

    private companion object {
        const val SERVER = LibraryPagingQueriesTest.SERVER
    }
}
