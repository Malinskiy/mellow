package dev.mellow.core.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class PickSavedPositionTest {

    private val savedWithQueue = SavedPosition(revision = 5, index = 2, positionMs = 1_000)

    @Test
    fun `uses the latest position when it belongs to the saved queue`() {
        val latest = SavedPosition(revision = 5, index = 3, positionMs = 42_000)
        assertEquals(latest, pickSavedPosition(5, savedWithQueue, latest))
    }

    @Test
    fun `ignores a position left over from a previous queue`() {
        val stale = SavedPosition(revision = 4, index = 9, positionMs = 99_000)
        assertEquals(savedWithQueue, pickSavedPosition(5, savedWithQueue, stale))
    }

    @Test
    fun `uses the position saved with the queue when there is no other`() {
        assertEquals(savedWithQueue, pickSavedPosition(5, savedWithQueue, null))
    }
}
