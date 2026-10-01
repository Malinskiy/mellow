package dev.mellow.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRestoreTest {

    @Test
    fun `restores the saved track and position when everything is available`() {
        val plan = planQueueRestore(queueSize = 4, savedIndex = 2, savedPositionMs = 61_000L) { true }
        assertEquals(RestorePlan(listOf(0, 1, 2, 3), startIndex = 2, startPositionMs = 61_000L), plan)
    }

    @Test
    fun `shifts the start index when earlier tracks are gone`() {
        val plan = planQueueRestore(queueSize = 4, savedIndex = 2, savedPositionMs = 5_000L) { it != 0 }
        assertEquals(RestorePlan(listOf(1, 2, 3), startIndex = 1, startPositionMs = 5_000L), plan)
    }

    @Test
    fun `starts the next track from the beginning when the saved track is gone`() {
        val plan = planQueueRestore(queueSize = 4, savedIndex = 1, savedPositionMs = 5_000L) { it != 1 }
        assertEquals(RestorePlan(listOf(0, 2, 3), startIndex = 1, startPositionMs = 0L), plan)
    }

    @Test
    fun `falls back to the last available track when none follow the saved one`() {
        val plan = planQueueRestore(queueSize = 4, savedIndex = 3, savedPositionMs = 5_000L) { it < 2 }
        assertEquals(RestorePlan(listOf(0, 1), startIndex = 1, startPositionMs = 0L), plan)
    }

    @Test
    fun `clamps an out of range saved index`() {
        val plan = planQueueRestore(queueSize = 3, savedIndex = 7, savedPositionMs = 5_000L) { true }
        assertEquals(RestorePlan(listOf(0, 1, 2), startIndex = 2, startPositionMs = 5_000L), plan)
    }

    @Test
    fun `returns null when nothing is available`() {
        assertNull(planQueueRestore(queueSize = 3, savedIndex = 0, savedPositionMs = 0L) { false })
    }
}
