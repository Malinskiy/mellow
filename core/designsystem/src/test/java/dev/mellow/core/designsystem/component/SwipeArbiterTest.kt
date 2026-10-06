package dev.mellow.core.designsystem.component

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cover's gesture classifier on synthetic traces. Thumb arcs are circular arcs around a pivot below the path (the
 * base of the thumb): a right thumb swiping left starts heading down-left and steepens; a left thumb swiping left
 * starts heading up-left. Samples every 8 ms (a 120 Hz touch panel), positions in px at the Pixel 10's density.
 */
class SwipeArbiterTest {

    private val density = 2.6375f

    @Test
    fun `straight swipes either way turn the page`() {
        assertEquals(SwipeDecision.Horizontal, decide(arc(direction = -1, angleBelow = 0f, radius = 1e6f)))
        assertEquals(SwipeDecision.Horizontal, decide(arc(direction = 1, angleBelow = 0f, radius = 1e6f)))
    }

    @Test
    fun `right-thumb arcs swiping left from 35 to 50 degrees below horizontal turn the page`() {
        for (angle in listOf(35f, 40f, 45f, 50f)) {
            for (radius in listOf(250f, 350f, 450f)) {
                for (start in listOf(300f to 300f, 340f to 120f, 220f to 260f)) {
                    val trace = arc(direction = -1, angleBelow = angle, radius = radius, startDp = start)
                    assertEquals("$angle° r=$radius from $start", SwipeDecision.Horizontal, decide(trace))
                }
            }
        }
    }

    @Test
    fun `a 55 degree arc still turns the page while it is gentle`() {
        // 55° is 2° short of the horizontal limit (≈57°); an arc that steepens by more than ~2.5° over the first
        // 20 dp (radius under ~450 dp) crosses it before the decision.
        for (radius in listOf(600f, 1000f, 1e6f)) {
            val trace = arc(direction = -1, angleBelow = 55f, radius = radius)
            assertEquals("r=$radius", SwipeDecision.Horizontal, decide(trace))
        }
    }

    @Test
    fun `left-thumb arcs swiping right and downward turn the page`() {
        for (angle in listOf(35f, 45f, 50f)) {
            for (radius in listOf(250f, 450f)) {
                val trace = arc(direction = 1, angleBelow = angle, radius = radius, startDp = 80f to 300f)
                assertEquals("$angle° r=$radius", SwipeDecision.Horizontal, decide(trace))
            }
        }
    }

    @Test
    fun `upward-curving arcs turn the page`() {
        for (angle in listOf(-20f, -35f, -50f, -55f)) {
            for (direction in listOf(-1, 1)) {
                val trace = arc(direction = direction, angleBelow = angle, radius = 300f)
                assertEquals("$angle° dir $direction", SwipeDecision.Horizontal, decide(trace))
            }
        }
    }

    @Test
    fun `a drag straight down or within 15 degrees of it goes to the sheet`() {
        for (fromVertical in listOf(-15f, -8f, 0f, 8f, 15f)) {
            val trace = line(angleFromVerticalDown = fromVertical)
            assertEquals("$fromVertical°", SwipeDecision.Vertical, decide(trace))
        }
    }

    @Test
    fun `without a sheet a drag down is let go`() {
        assertEquals(SwipeDecision.Cancel, decide(line(angleFromVerticalDown = 0f), hasSheet = false))
        assertEquals(SwipeDecision.Cancel, decide(line(angleFromVerticalDown = 10f), hasSheet = false))
    }

    @Test
    fun `a drag straight up is never the sheet's and turns nothing`() {
        assertEquals(SwipeDecision.Cancel, decide(line(angleFromVerticalDown = 180f)))
        assertEquals(SwipeDecision.Cancel, decide(line(angleFromVerticalDown = 175f)))
    }

    @Test
    fun `an upward drag with a real sideways part turns the page`() {
        // A quick flick 32° off straight up (11 dp between samples): scoreX is 0.62 of scoreY, under the horizontal
        // ratio, but 11.7 dp when it is classified at 22 dp.
        val trace = line(angleFromVerticalDown = 148f, lengthDp = 40f, speed = 1.375f)
        assertEquals(SwipeDecision.Horizontal, decide(trace))
    }

    @Test
    fun `jitter under 20 dp is undecided`() {
        val samples = mutableListOf(SwipeSample(0, 500f, 900f))
        val wobble = listOf(6f to 3f, -4f to 9f, 10f to -5f, -12f to 8f, 14f to 10f, -9f to -12f)
        wobble.forEachIndexed { i, (dx, dy) ->
            samples += SwipeSample(8L * (i + 1), 500f + dx * density, 900f + dy * density)
            assertEquals(SwipeDecision.Undecided, classifySwipe(samples, density, hasSheet = true).decision)
        }
    }

    @Test
    fun `with the default thresholds a steep diagonal goes to the sheet at 20 dp`() {
        val trace = line(angleFromVerticalDown = 30f) // 60° below horizontal
        val decided = firstDecision(trace)
        assertEquals(SwipeDecision.Vertical, decided.decision)
        assertTrue("${decided.distanceDp}", decided.distanceDp < CLASSIFY_DP + 3f)
    }

    @Test
    fun `an ambiguous diagonal waits and resolves by 32 dp`() {
        // A tuning with a band between the regions (horizontal up to ≈57°, vertical from ≈63°): 60° is in it.
        val tuning = SwipeTuning(verticalRatio = 2f)
        val trace = line(angleFromVerticalDown = 30f, lengthDp = 60f)
        val decided = firstDecision(trace, tuning = tuning)
        assertEquals(SwipeDecision.Vertical, decided.decision)
        assertTrue("${decided.distanceDp}", decided.distanceDp >= DECIDE_BY_DP - 0.01f)
        assertTrue("${decided.distanceDp}", decided.distanceDp <= DECIDE_BY_DP + 4.01f) // 4 dp between samples
        // Before 32 dp it was undecided.
        val early = trace.takeWhile { distanceDp(trace.first(), it) < 30f }
        assertEquals(SwipeDecision.Undecided, classifySwipe(early, density, hasSheet = true, tuning).decision)
    }

    @Test
    fun `the latest direction counts`() {
        // 19 dp straight down, then 12 dp sharply left: by the displacement alone (12 vs 19) it would go to the sheet.
        val samples = mutableListOf(SwipeSample(0, 500f, 500f))
        for (i in 1..4) samples += SwipeSample(8L * i, 500f, 500f + 4.75f * i * density)
        for (i in 1..2) samples += SwipeSample(32L + 8L * i, 500f - 6f * i * density, 500f + 19f * density)
        val result = firstDecision(samples)
        assertEquals(SwipeDecision.Horizontal, result.decision)
        assertTrue(12f < HORIZONTAL_RATIO * 19f)
    }

    @Test
    fun `the log line has every sample and the decision`() {
        val samples = listOf(SwipeSample(1000, 100f, 200f), SwipeSample(1008, 90f, 204f), SwipeSample(1016, 40f, 210f))
        val result = classifySwipe(samples, 2f, hasSheet = true)
        assertEquals(
            "samples=[0,0.0,0.0;8,-10.0,4.0;16,-60.0,10.0] dp=2.0 down=100.0,200.0 decision=H at=30.4 " +
                "scoreX=30.0 scoreY=5.0",
            swipeLogLine(samples, 2f, result),
        )
        assertEquals(
            "samples=[0,0.0,0.0;8,-10.0,4.0] dp=2.0 down=100.0,200.0 decision=undecided",
            swipeLogLine(samples.take(2), 2f, null),
        )
    }

    private fun decide(trace: List<SwipeSample>, hasSheet: Boolean = true) = firstDecision(trace, hasSheet).decision

    /** Feeds the trace sample by sample, as the pointer handler does, and returns the first decision. */
    private fun firstDecision(
        trace: List<SwipeSample>,
        hasSheet: Boolean = true,
        tuning: SwipeTuning = SwipeTuning(),
    ): SwipeClassification {
        for (n in 2..trace.size) {
            val result = classifySwipe(trace.subList(0, n), density, hasSheet, tuning)
            if (result.decision != SwipeDecision.Undecided) return result
        }
        error("never decided")
    }

    /**
     * A thumb arc: from [startDp], heading [direction] (−1 left, +1 right) [angleBelow]° below horizontal (negative =
     * above), around a pivot [radius] dp away below the path, at [speed] dp/ms.
     */
    private fun arc(
        direction: Int,
        angleBelow: Float,
        radius: Float,
        startDp: Pair<Float, Float> = 300f to 300f,
        lengthDp: Float = 150f,
        speed: Float = 1.2f,
    ): List<SwipeSample> {
        val phi = angleBelow * PI / 180
        val hx = direction * cos(phi)
        val hy = sin(phi)
        // The pivot is on the normal of the heading that points down the screen.
        val (nx, ny) = if (hx > 0) -hy to hx else hy to -hx
        val cx = startDp.first + radius * nx
        val cy = startDp.second + radius * ny
        val rx = startDp.first - cx
        val ry = startDp.second - cy
        val turn = sign(-ry * hx + rx * hy)
        val samples = mutableListOf<SwipeSample>()
        var t = 0L
        while (t * speed <= lengthDp) {
            val theta = turn * t * speed / radius
            val x = cx + rx * cos(theta) - ry * sin(theta)
            val y = cy + rx * sin(theta) + ry * cos(theta)
            samples += SwipeSample(t, (x * density).toFloat(), (y * density).toFloat())
            t += 8
        }
        return samples
    }

    /** A straight drag [angleFromVerticalDown]° from straight down (positive = toward the right; 180 = straight up). */
    private fun line(angleFromVerticalDown: Float, lengthDp: Float = 60f, speed: Float = 0.5f): List<SwipeSample> {
        val a = angleFromVerticalDown * PI / 180
        val samples = mutableListOf<SwipeSample>()
        var t = 0L
        while (t * speed <= lengthDp) {
            val d = t * speed
            samples += SwipeSample(t, (500 + d * sin(a) * density).toFloat(), (900 + d * cos(a) * density).toFloat())
            t += 8
        }
        return samples
    }

    private fun distanceDp(a: SwipeSample, b: SwipeSample) = hypot((b.x - a.x) / density, (b.y - a.y) / density)
}
