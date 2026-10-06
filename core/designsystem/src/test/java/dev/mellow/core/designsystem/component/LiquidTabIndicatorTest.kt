package dev.mellow.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The liquid tab indicator's geometry, on the library's tabs as measured on a Pixel 10 (px). */
class LiquidTabIndicatorTest {

    // Albums, Artists, Tracks, Genres, Playlists.
    private val lefts = floatArrayOf(42f, 265f, 473f, 682f, 895f)
    private val rights = floatArrayOf(244f, 452f, 661f, 874f, 1108f)

    @Test
    fun `at rest the pill is exactly the tab it is on`() {
        for (tab in lefts.indices) {
            assertEquals(lefts[tab], LiquidTabIndicator.swipeLeft(lefts, tab.toFloat()), EPS)
            assertEquals(rights[tab], LiquidTabIndicator.swipeRight(rights, tab.toFloat()), EPS)
        }
    }

    @Test
    fun `mid-swipe the leading edge runs ahead and the trailing edge holds back`() {
        // 40 % of the way from Albums to Artists.
        val left = LiquidTabIndicator.swipeLeft(lefts, 0.4f)
        val right = LiquidTabIndicator.swipeRight(rights, 0.4f)

        assertEquals(42f + 223f * 0.064f, left, EPS) // trail(0.4) = 0.4³
        assertEquals(244f + 208f * 0.784f, right, EPS) // lead(0.4) = 1 - 0.6³
        assertTrue("the pill spans both tabs", right > rights[1] - 50f && left < rights[0])
    }

    @Test
    fun `swiping back the left edge leads`() {
        // 90 % of the way from Albums to Artists, coming back from Artists: the left edge has moved further than the
        // page (a tenth of the way back), the right edge less.
        val left = LiquidTabIndicator.swipeLeft(lefts, 0.9f)
        val right = LiquidTabIndicator.swipeRight(rights, 0.9f)
        val pageLeft = 42f + 223f * 0.9f
        val pageRight = 244f + 208f * 0.9f

        assertTrue(left < pageLeft)
        assertTrue(right > pageRight)
    }

    @Test
    fun `without liquid the pill slides with the page`() {
        assertEquals(42f + 223f * 0.4f, LiquidTabIndicator.swipeLeft(lefts, 0.4f, liquid = false), EPS)
        assertEquals(244f + 208f * 0.4f, LiquidTabIndicator.swipeRight(rights, 0.4f, liquid = false), EPS)
    }

    @Test
    fun `the pill stays within the tabs whatever the pages report`() {
        assertEquals(lefts[0], LiquidTabIndicator.swipeLeft(lefts, -0.3f), EPS)
        assertEquals(rights[4], LiquidTabIndicator.swipeRight(rights, 4.2f), EPS)
    }

    @Test
    fun `a tap's head arrives first, overshoots slightly and settles`() {
        val (from, to) = 2f to 4f // Tracks to Playlists
        val head = (0..100).map { LiquidTabIndicator.tapHead(it / 100f, from, to) }
        val tail = (0..100).map { LiquidTabIndicator.tapTail(it / 100f) }

        assertEquals(0f, head.first(), EPS)
        assertEquals(1f, head.last(), EPS)
        assertEquals(1f, tail.last(), EPS)
        assertTrue("the head leads the tail", head.zip(tail).all { (h, t) -> h >= t - EPS })
        val overshootTabs = (head.max() - 1f) * (to - from)
        assertTrue("overshoot $overshootTabs tabs", overshootTabs in 0.01f..0.05f)
        assertEquals(1f, LiquidTabIndicator.tapHead(0.72f, from, to), EPS) // the head is home at 72 %
    }

    @Test
    fun `a long tap overshoots no further than a short one`() {
        val short = (0..100).maxOf { LiquidTabIndicator.tapHead(it / 100f, 0f, 1f) } - 1f
        val long = ((0..100).maxOf { LiquidTabIndicator.tapHead(it / 100f, 4f, 0f) } - 1f) * 4f

        assertTrue("a neighbour: $short tabs", short in 0.03f..0.06f)
        assertTrue("four tabs: $long tabs", long in 0f..short)
    }

    @Test
    fun `a tap stretches the pill across every tab in between`() {
        // Playlists back to Albums, halfway through: the left (leading) edge is nearly at Albums, the right edge
        // still near Playlists.
        val left = LiquidTabIndicator.tapLeft(lefts, 4f, 0f, 0.4f)
        val right = LiquidTabIndicator.tapRight(rights, 4f, 0f, 0.4f)

        assertTrue("left $left", left < lefts[1])
        assertTrue("right $right", right > rights[2])
        assertEquals(lefts[0], LiquidTabIndicator.tapLeft(lefts, 4f, 0f, 1f), EPS)
        assertEquals(rights[0], LiquidTabIndicator.tapRight(rights, 4f, 0f, 1f), EPS)
    }

    @Test
    fun `taps take longer the further they go, never more than 400 ms`() {
        assertEquals(280, LiquidTabIndicator.tapDurationMillis(1f))
        assertEquals(320, LiquidTabIndicator.tapDurationMillis(2f))
        assertEquals(400, LiquidTabIndicator.tapDurationMillis(4f))
        assertEquals(400, LiquidTabIndicator.tapDurationMillis(9f))
    }

    @Test
    fun `the pill thins only a little when stretched`() {
        val pitch = LiquidTabIndicator.pitch(lefts, rights)
        val half = 40f

        assertEquals(half, LiquidTabIndicator.radius(half, 42f, 244f, 202f, pitch), EPS)
        val stretched = LiquidTabIndicator.radius(half, 42f, 244f + pitch * 2, 202f, pitch)
        assertEquals(half * (1f - LiquidTabIndicator.SQUISH), stretched, EPS)
        assertTrue("barely thinner", LiquidTabIndicator.SQUISH in 0f..0.1f)
    }

    @Test
    fun `the pitch is the average distance between tab centres`() {
        assertEquals((1001.5f - 143f) / 4f, LiquidTabIndicator.pitch(lefts, rights), EPS)
        assertEquals(100f, LiquidTabIndicator.pitch(floatArrayOf(0f), floatArrayOf(100f)), EPS)
    }

    @Test
    fun `the bar scrolls to centre the pill, within its scroll range`() {
        assertEquals(0, LiquidTabIndicator.scrollFor(center = 143f, viewport = 1080, maxScroll = 70))
        assertEquals(27, LiquidTabIndicator.scrollFor(center = 567f, viewport = 1080, maxScroll = 70))
        assertEquals(70, LiquidTabIndicator.scrollFor(center = 1001.5f, viewport = 1080, maxScroll = 70))
        assertEquals(0, LiquidTabIndicator.scrollFor(center = 900f, viewport = 1080, maxScroll = 0))
    }

    @Test
    fun `positions between and beyond tabs interpolate the edges`() {
        assertEquals(42f + 223f * 0.5f, LiquidTabIndicator.edgeAt(lefts, 0.5f), EPS)
        assertEquals(895f + 213f * 0.1f, LiquidTabIndicator.edgeAt(lefts, 4.1f), EPS)
        assertEquals(50f, LiquidTabIndicator.edgeAt(floatArrayOf(50f), 3f), EPS)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
