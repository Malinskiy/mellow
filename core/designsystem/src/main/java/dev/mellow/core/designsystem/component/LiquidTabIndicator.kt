package dev.mellow.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Geometry of the tab bar's liquid-stretch indicator. Everything is a pure function, so the bar can draw it straight
 * from the pager's position without any state of its own.
 *
 * A position is a fractional tab index: 1.4 is 40 % of the way from tab 1 to tab 2. Edges are the chips' left or
 * right edges in the tab row's pixels, one per tab, in order.
 *
 * While the pager is dragged the pill's leading edge runs ahead of the page and its trailing edge holds back, so it
 * stretches across both chips and contracts onto the new one. A tap animates its own progress instead: the head
 * shoots to the target with a slight overshoot and the tail follows on a slower curve.
 */
internal object LiquidTabIndicator {

    /** How much thinner the pill gets at full stretch, as a fraction of its height: barely, so it reads as liquid. */
    const val SQUISH = 0.06f

    /** The longest a tap's animation takes, however far it goes. */
    const val MAX_TAP_MILLIS = 400

    private const val TAP_BASE_MILLIS = 240
    private const val TAP_MILLIS_PER_TAB = 40

    /** The part of a tap's animation in which the head arrives; the tail takes all of it. */
    private const val HEAD_SHARE = 0.72f

    /**
     * The head's overshoot (the back-out easing's s, divided by the tabs jumped): a twentieth of a tab to a
     * neighbour, a few pixels on longer jumps, so the head never flies far past the target.
     */
    private const val OVERSHOOT = 1.1f

    private val TailEasing = CubicBezierEasing(0.55f, 0f, 0.25f, 1f)

    /** [edges] at [position], linear between tabs and extended past the first and last tab. */
    fun edgeAt(edges: FloatArray, position: Float): Float {
        val last = edges.lastIndex
        if (last <= 0) return edges.firstOrNull() ?: 0f
        val i = floor(position).toInt().coerceIn(0, last - 1)
        return edges[i] + (edges[i + 1] - edges[i]) * (position - i)
    }

    /** The leading edge's share of the way between two tabs: ahead of the page. */
    fun lead(fraction: Float): Float {
        val rest = 1f - fraction
        return 1f - rest * rest * rest
    }

    /** The trailing edge's share of the way between two tabs: behind the page. */
    fun trail(fraction: Float): Float = fraction * fraction * fraction

    /**
     * The pill's left edge while the pager is at [position]. Moving right the left edge trails; moving left it leads,
     * which the same curve gives as the fraction falls from 1. [liquid] = false is a plain sliding pill.
     */
    fun swipeLeft(lefts: FloatArray, position: Float, liquid: Boolean = true): Float =
        swipeEdge(lefts, position) { if (liquid) trail(it) else it }

    /** The pill's right edge while the pager is at [position]; see [swipeLeft]. */
    fun swipeRight(rights: FloatArray, position: Float, liquid: Boolean = true): Float =
        swipeEdge(rights, position) { if (liquid) lead(it) else it }

    private inline fun swipeEdge(edges: FloatArray, position: Float, shape: (Float) -> Float): Float {
        val last = edges.lastIndex
        if (last <= 0) return edges.firstOrNull() ?: 0f
        val p = position.coerceIn(0f, last.toFloat())
        val i = floor(p).toInt().coerceAtMost(last - 1)
        return edges[i] + (edges[i + 1] - edges[i]) * shape(p - i)
    }

    /** How far the head of a tap from [from] to [to] has got at [progress] (0..1), as a share of the way. */
    fun tapHead(progress: Float, from: Float, to: Float): Float {
        val x = (progress / HEAD_SHARE).coerceIn(0f, 1f) - 1f
        val s = OVERSHOOT / abs(to - from).coerceAtLeast(1f)
        return 1f + (s + 1f) * x * x * x + s * x * x
    }

    /** How far the tail of a tap has got at [progress] (0..1), as a share of the way. */
    fun tapTail(progress: Float): Float = TailEasing.transform(progress.coerceIn(0f, 1f))

    /** The pill's left edge at [progress] of a tap from tab [from] to tab [to]. */
    fun tapLeft(lefts: FloatArray, from: Float, to: Float, progress: Float): Float {
        val share = if (to < from) tapHead(progress, from, to) else tapTail(progress)
        return edgeAt(lefts, from + (to - from) * share)
    }

    /** The pill's right edge at [progress] of a tap from tab [from] to tab [to]. */
    fun tapRight(rights: FloatArray, from: Float, to: Float, progress: Float): Float {
        val share = if (to > from) tapHead(progress, from, to) else tapTail(progress)
        return edgeAt(rights, from + (to - from) * share)
    }

    /** How long a tap's animation over [tabs] tabs takes. */
    fun tapDurationMillis(tabs: Float): Int =
        min(MAX_TAP_MILLIS, TAP_BASE_MILLIS + (TAP_MILLIS_PER_TAB * abs(tabs)).roundToInt())

    /** The average distance between neighbouring tabs' centres, or the one tab's width. */
    fun pitch(lefts: FloatArray, rights: FloatArray): Float {
        if (lefts.isEmpty()) return 1f
        val last = lefts.lastIndex
        if (last == 0) return (rights[0] - lefts[0]).coerceAtLeast(1f)
        val first = (lefts[0] + rights[0]) / 2f
        val end = (lefts[last] + rights[last]) / 2f
        return ((end - first) / last).coerceAtLeast(1f)
    }

    /**
     * The pill's corner radius: half its resting height, a little less the more it is stretched past the width of
     * the tab it is on ([restingWidth]), fully thinned by [squish] at a stretch of one [pitch].
     */
    fun radius(
        halfHeight: Float,
        left: Float,
        right: Float,
        restingWidth: Float,
        pitch: Float,
        squish: Float = SQUISH,
    ): Float {
        val stretch = ((right - left - restingWidth) / pitch).coerceIn(0f, 1f)
        return halfHeight * (1f - squish * stretch)
    }

    /** The scroll that centres [center] in a [viewport] wide bar, within what the bar can scroll. */
    fun scrollFor(center: Float, viewport: Int, maxScroll: Int): Int =
        (center - viewport / 2f).roundToInt().coerceIn(0, maxScroll.coerceAtLeast(0))
}
