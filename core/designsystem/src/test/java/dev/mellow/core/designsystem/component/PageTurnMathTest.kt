package dev.mellow.core.designsystem.component

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page turn's maths, on the prototype's geometry: an 844 px cover, perspective 3200 px. */
class PageTurnMathTest {

    private val page = PROTOTYPE_PAGE_PX
    private val perspective = perspectiveFor(page)
    private val fling = 1000f
    private val catchUp = 380f
    private val nextCatchUp = 126f

    @Test
    fun `the prototype's perspective magnifies the free edge 1_36 times at 90 degrees`() {
        assertEquals(3200f, perspective, 0.5f)
        assertEquals(1.36f, perspective / (perspective - page), 0.01f)
        // Compose hands cameraDistance to the RenderNode, whose camera unit is 72 px.
        assertEquals(44.44f, cameraDistanceFor(perspective), 0.01f)
    }

    @Test
    fun `turning to the next page keeps the touched point under the finger`() {
        val touch = 652f // the prototype's touch, 652 px from the spine
        for (drag in listOf(-126f, -190f, -400f, -600f, -652f, -900f, -1100f)) {
            val angle = nextPageAngle(touch, drag, page, perspective, nextCatchUp)
            val under = hingeProject(angle, touch, perspective)
            assertEquals("drag $drag → $angle°", touch + drag, under, 1f)
        }
    }

    @Test
    fun `the touched point stays under the finger wherever the page is held`() {
        for (touch in listOf(200f, 420f, 800f)) {
            for (drag in listOf(-130f, -150f, -300f)) {
                val angle = nextPageAngle(touch, drag, page, perspective, nextCatchUp)
                assertEquals("touch $touch, drag $drag", touch + drag, hingeProject(angle, touch, perspective), 1f)
            }
        }
    }

    @Test
    fun `the page lifts smoothly from the stack while it catches up with the finger`() {
        assertEquals(0f, nextPageAngle(652f, 0f, page, perspective, nextCatchUp), 0f)
        var last = 0f
        for (drag in 1..400) {
            val angle = nextPageAngle(652f, -drag.toFloat(), page, perspective, nextCatchUp)
            assertTrue("drag $drag: $angle after $last", angle > last && angle - last < 1f)
            last = angle
        }
    }

    @Test
    fun `the page is upright when the finger reaches the spine`() {
        assertEquals(90f, nextPageAngle(500f, -500f, page, perspective, nextCatchUp), 0.01f)
        assertTrue(nextPageAngle(500f, -1000f, page, perspective, nextCatchUp) > 150f)
    }

    @Test
    fun `the previous page starts turned over and catches up with the finger`() {
        val touch = 132f // the prototype's touch for a right swipe
        assertEquals(180f, previousPageAngle(touch, 0f, page, perspective, catchUp), 0.01f)
        // Partway through the catch-up the free edge is between its turned-over spot and the finger.
        val halfway = previousPageAngle(touch, catchUp / 2, page, perspective, catchUp)
        val edge = hingeProject(halfway, page, perspective)
        assertTrue("edge $edge", edge > -page && edge < touch + catchUp / 2)
        // Past it, the free edge is under the finger.
        for (drag in listOf(catchUp, 500f, 600f)) {
            val angle = previousPageAngle(touch, drag, page, perspective, catchUp)
            assertEquals("drag $drag", touch + drag, hingeProject(angle, page, perspective), 1f)
        }
    }

    @Test
    fun `the previous page lifts steadily as the finger moves right`() {
        var last = 181f
        for (drag in 0..800 step 20) {
            val angle = previousPageAngle(132f, drag.toFloat(), page, perspective, catchUp)
            assertTrue("drag $drag: $angle after $last", angle <= last)
            last = angle
        }
        assertTrue("$last", last < 20f)
    }

    @Test
    fun `past halfway a turn commits without a fling`() {
        assertTrue(shouldCommit(progress = 0.55f, velocityInDirection = 0f, flingThreshold = fling, enabled = true))
    }

    @Test
    fun `a flick commits a turn a tenth of the way or more`() {
        // The prototype's swipe: 76° at release, finger at 2060 px/s.
        val progress = turnProgress(PageTurnDirection.Next, 76f)
        assertTrue(shouldCommit(progress, velocityInDirection = 2060f, flingThreshold = fling, enabled = true))
    }

    @Test
    fun `a flick that is too short does not commit`() {
        assertFalse(shouldCommit(progress = 0.08f, velocityInDirection = 3000f, flingThreshold = fling, enabled = true))
    }

    @Test
    fun `a slow drag let go before halfway falls back`() {
        val progress = turnProgress(PageTurnDirection.Next, 37f)
        assertFalse(shouldCommit(progress, velocityInDirection = 0f, flingThreshold = fling, enabled = true))
        assertFalse(shouldCommit(progress, velocityInDirection = 900f, flingThreshold = fling, enabled = true))
    }

    @Test
    fun `a wobble that ends moving backward never commits`() {
        assertFalse(shouldCommit(progress = 0.3f, velocityInDirection = -2000f, flingThreshold = fling, enabled = true))
    }

    @Test
    fun `nothing commits toward a track that is not there`() {
        assertFalse(shouldCommit(progress = 0.9f, velocityInDirection = 5000f, flingThreshold = fling, enabled = false))
    }

    @Test
    fun `progress runs toward the target page`() {
        assertEquals(0.5f, turnProgress(PageTurnDirection.Next, 90f), 0f)
        assertEquals(0f, turnProgress(PageTurnDirection.Previous, 180f), 0f)
        assertEquals(0.75f, turnProgress(PageTurnDirection.Previous, 45f), 1e-6f)
    }

    @Test
    fun `at the end of the queue the page resists and stays below halfway`() {
        val limit = page * RESISTANCE_LIMIT_PER_PAGE_WIDTH
        assertEquals(0f, resistedDrag(0f, limit), 0f)
        assertEquals(-RESISTANCE * 10f, resistedDrag(-10f, limit), 0.2f)
        for (drag in listOf(-100f, -400f, -2000f)) {
            val resisted = resistedDrag(drag, limit)
            assertTrue("drag $drag → $resisted", abs(resisted) < limit)
            assertTrue("drag $drag → $resisted", abs(resisted) <= abs(drag) * RESISTANCE + 1e-3f)
            val angle = nextPageAngle(652f, resisted, page, perspective, nextCatchUp)
            assertTrue("drag $drag → $angle°", turnProgress(PageTurnDirection.Next, angle) < 0.5f)
        }
        assertEquals(-resistedDrag(-40f, limit), resistedDrag(40f, limit), 0f)
    }

    @Test
    fun `gravity lands the page on its goal with a small bounce`() {
        val fall = GravityFall(start = 76f, velocity = 600f, target = 180f)
        assertTrue(fall.settleTime in 0.1f..1f)
        assertEquals(76f, fall.valueAt(0f), 1e-3f)
        assertEquals(180f, fall.valueAt(fall.settleTime), 0f)
        var peakBounce = 180f
        var reached = false
        var t = 0f
        while (t < fall.settleTime) {
            val value = fall.valueAt(t)
            assertTrue("$value at $t", value <= 180f + 1e-3f)
            if (abs(value - 180f) < 1f) reached = true
            if (reached) peakBounce = minOf(peakBounce, value)
            t += 0.002f
        }
        assertTrue("bounce $peakBounce", peakBounce < 180f && peakBounce > 170f)
    }

    @Test
    fun `gravity brings a previous page down to rest`() {
        val fall = GravityFall(start = 120f, velocity = -400f, target = 0f)
        assertEquals(0f, fall.valueAt(fall.settleTime + 1f), 0f)
        assertTrue(fall.valueAt(fall.settleTime / 4) < 120f)
    }

    @Test
    fun `a fall-back overshoot bounces off the stack`() {
        assertEquals(30f, reflectPastRest(30f, PageTurnDirection.Next), 0f)
        assertEquals(3f, reflectPastRest(-10f, PageTurnDirection.Next), 1e-5f)
        assertEquals(177f, reflectPastRest(190f, PageTurnDirection.Previous), 1e-4f)
    }

    @Test
    fun `shading follows the prototype`() {
        assertEquals(0f, pageShade(0f), 1e-6f)
        assertEquals(0.45f, pageShade(45f), 0.02f)
        assertEquals(0.62f, pageShade(60f), 0.03f)
        assertEquals(1f, pageAlpha(150f), 0f)
        assertEquals(0f, pageAlpha(180f), 0f)
        assertEquals(0.35f, underDim(0f), 1e-6f)
        assertEquals(0f, underDim(180f), 1e-6f)
    }

    @Test
    fun `the shadow band sits past the projected free edge and widens as the page lifts`() {
        val low = castShadow(10f, page, perspective)
        val high = castShadow(70f, page, perspective)
        assertEquals(hingeProject(10f, page, perspective), low.edge, 0.01f)
        assertTrue(high.width > low.width && high.alpha < low.alpha)
        assertEquals(0f, castShadow(120f, page, perspective).edge, 0f)
        assertEquals(0f, castShadow(180f, page, perspective).alpha, 1e-6f)
    }
}
