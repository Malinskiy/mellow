package dev.mellow.core.designsystem.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import androidx.compose.runtime.MonotonicFrameClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Next and Previous buttons turning the page: one turn at a time, each press landing the one before, and the cover
 * ending on wherever the player went. Frames come every 16 ms of virtual time.
 */
class PageTurnButtonsTest {

    /** A queue of tracks 0..9 standing in for the player: covers "cover-N", keys "track-N". */
    private var index = 3
    private val skips = mutableListOf<PageTurnDirection>()
    private val state = PageTurnState(pose = null)

    private fun live() = PageFaces(
        key = "track-$index",
        current = "cover-$index",
        next = "cover-${index + 1}",
        previous = "cover-${index - 1}",
        canGoNext = index < 9,
        canGoPrevious = index > 0,
    )

    private fun next(): PageTurnTarget? {
        skips += PageTurnDirection.Next
        if (index == 9) return null
        index++
        return PageTurnTarget("track-$index", "cover-$index")
    }

    private fun previous(restarts: Boolean): PageTurnTarget? {
        skips += PageTurnDirection.Previous
        if (restarts || index == 0) return null
        index--
        return PageTurnTarget("track-$index", "cover-$index")
    }

    /** A press, with the faces the cover composed [composedFaces] (they may lag a burst of presses). */
    private fun TestScope.press(
        direction: PageTurnDirection,
        composedFaces: PageFaces = live(),
        restarts: Boolean = false,
        animate: Boolean = true,
    ) {
        state.turnByButton(direction, composedFaces, frameScope(), animate) {
            if (direction == PageTurnDirection.Next) next() else previous(restarts)
        }
    }

    private fun TestScope.frameScope() = CoroutineScope(coroutineContext + Job() + FrameClock(this))

    private class FrameClock(private val scope: TestScope) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(16)
            return onFrame(scope.testScheduler.currentTime * 1_000_000)
        }
    }

    @Test
    fun `Next turns the current page over onto the next cover and lands there`() = runTest {
        press(PageTurnDirection.Next)

        advanceTimeBy(150)
        assertEquals(PageTurnDirection.Next, state.direction)
        assertTrue("${state.angle}", state.angle > 20f && state.angle < 180f)
        assertEquals("cover-3", state.frozen?.current)
        assertEquals("cover-4", state.frozen?.next)

        advanceTimeBy(350)
        assertTrue("landed at ${state.angle}", state.angle > 170f || state.direction == null)
        advanceUntilIdle()
        assertNull(state.direction)
        assertNull(state.frozen)
        assertEquals("cover-4", state.pending?.target)
        assertTrue(state.pending!!.appliesTo("track-3"))
        assertTrue(!state.pending!!.appliesTo("track-4"))
    }

    @Test
    fun `the turn lands in about 300 ms`() = runTest {
        press(PageTurnDirection.Next)
        var landedAt = -1L
        while (testScheduler.currentTime < 1000) {
            advanceTimeBy(4)
            if (landedAt < 0 && state.angle >= 175f) landedAt = testScheduler.currentTime
        }
        assertTrue("landed at $landedAt ms", landedAt in 280L..360L)
    }

    @Test
    fun `five quick Next presses never run two turns and end on the last track`() = runTest {
        val jobs = mutableListOf<Job>()
        repeat(5) { i ->
            // The cover only recomposes between some presses: the faces it saw may still be the first track's.
            val composed = if (i % 2 == 0) live() else PageFaces("track-3", "cover-3", "cover-4", "cover-2", true, true)
            press(PageTurnDirection.Next, composedFaces = composed)
            jobs += state.settling!!
            assertEquals("never more than one turn", 1, jobs.count { it.isActive })
            // Each turn starts from the cover the last one went to.
            assertEquals("cover-${3 + i}", state.frozen?.current)
            assertEquals("cover-${4 + i}", state.frozen?.next)
            advanceTimeBy(60)
        }
        advanceUntilIdle()

        assertEquals(List(5) { PageTurnDirection.Next }, skips)
        assertEquals(8, index)
        assertNull(state.direction)
        assertEquals("cover-8", state.pending?.target)
        assertEquals("track-8", state.pending?.toKey)
    }

    @Test
    fun `mixed Next and Previous presses end on the playing track`() = runTest {
        press(PageTurnDirection.Next)
        advanceTimeBy(40)
        press(PageTurnDirection.Next)
        advanceTimeBy(40)
        press(PageTurnDirection.Previous)
        assertEquals(PageTurnDirection.Previous, state.direction)
        assertEquals("cover-5", state.frozen?.current)
        assertEquals("cover-4", state.frozen?.previous)
        advanceUntilIdle()

        assertEquals(4, index)
        assertEquals("cover-4", state.pending?.target)
    }

    @Test
    fun `Previous that goes back brings the previous page over from the left`() = runTest {
        press(PageTurnDirection.Previous)

        assertEquals(180f, state.angle, 0f)
        advanceTimeBy(150)
        assertEquals(PageTurnDirection.Previous, state.direction)
        assertTrue("${state.angle}", state.angle < 175f && state.angle > 0f)
        assertEquals("cover-2", state.frozen?.previous)
        advanceUntilIdle()
        assertEquals("cover-2", state.pending?.target)
    }

    @Test
    fun `Previous that restarts the track turns nothing`() = runTest {
        press(PageTurnDirection.Previous, restarts = true)

        assertEquals(listOf(PageTurnDirection.Previous), skips)
        assertNull(state.direction)
        assertNull(state.settling)
        assertNull(state.pending)
    }

    @Test
    fun `a restarting Previous still lands a turn under way`() = runTest {
        press(PageTurnDirection.Next)
        advanceTimeBy(100)
        press(PageTurnDirection.Previous, restarts = true)

        assertNull(state.direction)
        assertNull(state.frozen)
        assertEquals("cover-4", state.pending?.target)
    }

    @Test
    fun `Next at the end of the queue turns nothing`() = runTest {
        index = 9
        press(PageTurnDirection.Next)

        assertNull(state.direction)
        assertNull(state.pending)
    }

    @Test
    fun `a press while a finger turns the page only skips`() = runTest {
        state.dragging = true
        state.direction = PageTurnDirection.Next
        state.angle = 40f

        press(PageTurnDirection.Next)

        assertEquals(4, index)
        assertEquals(40f, state.angle, 0f)
        assertNull(state.settling)
        assertNull(state.pending)
    }

    @Test
    fun `with animations off a press just swaps the cover`() = runTest {
        press(PageTurnDirection.Next, animate = false)

        assertNull(state.direction)
        assertNull(state.settling)
        assertEquals("cover-4", state.pending?.target)
    }

    @Test
    fun `the button turn's starting speed lands it in the set time`() {
        val v = buttonTurnVelocity(180f)
        val fall = GravityFall(0f, v, 180f)
        assertEquals(180f, fall.valueAt(BUTTON_TURN_SECONDS - 0.001f), 1f)
        assertEquals(-v, buttonTurnVelocity(-180f), 0f)
    }
}
