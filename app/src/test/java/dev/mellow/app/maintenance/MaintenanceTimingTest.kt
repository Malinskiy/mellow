package dev.mellow.app.maintenance

import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_END_SECONDS
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_START_SECONDS
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOOP_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceTimingTest {

    private fun exit(doneAt: Float, minHold: Float = 1f) =
        exitTime(doneAt, holdStart = 5f, holdEnd = 6f, loop = 10f, minHold = minHold)

    @Test
    fun `work done before the first hold leaves once the logo has held for the minimum`() {
        assertEquals(6f, exit(doneAt = 0f), DELTA)
        assertEquals(6f, exit(doneAt = 2f), DELTA)
        assertEquals(5.5f, exit(doneAt = 2f, minHold = 0.5f), DELTA)
    }

    @Test
    fun `work done during a hold holds a while longer, but not past the hold`() {
        assertEquals(5.7f, exit(doneAt = 5.2f, minHold = 0.5f), DELTA)
        assertEquals(6f, exit(doneAt = 5.8f, minHold = 0.5f), DELTA)
        assertEquals(6f, exit(doneAt = 5.5f), DELTA)
        assertEquals(6f, exit(doneAt = 6f), DELTA)
    }

    @Test
    fun `work done after a hold leaves on the next loop's hold`() {
        assertEquals(16f, exit(doneAt = 6.01f), DELTA)
        assertEquals(16f, exit(doneAt = 12f), DELTA)
        assertEquals(16f, exit(doneAt = 15.5f), DELTA)
        assertEquals(26f, exit(doneAt = 17f), DELTA)
        assertEquals(15.5f, exit(doneAt = 9f, minHold = 0.5f), DELTA)
    }

    @Test
    fun `the exit always falls on the logo hold`() {
        var doneAt = 0f
        while (doneAt < 40f) {
            val exitAt = exit(doneAt)
            val inLoop = exitAt % 10f
            assertTrue("exit $exitAt before done $doneAt", exitAt >= doneAt)
            assertTrue("exit $exitAt off the hold", inLoop in 5f..6f)
            doneAt += 0.37f
        }
    }

    @Test
    fun `on the plexus's own hold, the content composes once the logo holds and the screen leaves at its end`() {
        assertEquals(3.6f, PLEXUS_LOGO_HOLD_START_SECONDS, DELTA)
        assertEquals(4.6f, PLEXUS_LOGO_HOLD_END_SECONDS, DELTA)
        assertEquals(9f, PLEXUS_LOOP_SECONDS, DELTA)

        assertEquals(MaintenanceExit(contentAt = 3.6f, exitAt = 4.6f), maintenanceExit(doneAt = 1.2f, still = false))
        assertEquals(MaintenanceExit(contentAt = 4.2f, exitAt = 4.6f), maintenanceExit(doneAt = 4.2f, still = false))
        assertEquals(MaintenanceExit(contentAt = 12.6f, exitAt = 13.6f), maintenanceExit(doneAt = 7f, still = false))
    }

    @Test
    fun `a still logo stays the minimum, then leaves as soon as the work is done`() {
        assertEquals(MaintenanceExit(contentAt = 0.3f, exitAt = MIN_STILL_SECONDS), maintenanceExit(0.3f, still = true))
        assertEquals(MaintenanceExit(contentAt = 4f, exitAt = 4f), maintenanceExit(4f, still = true))
        assertEquals(1.5f, MIN_STILL_SECONDS, DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
