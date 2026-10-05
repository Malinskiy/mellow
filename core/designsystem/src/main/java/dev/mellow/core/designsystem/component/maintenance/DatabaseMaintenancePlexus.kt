package dev.mellow.core.designsystem.component.maintenance

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.Region
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Logo path data from ic_launcher_foreground.xml
private const val LOGO_PATH_DATA = "M1853,4098c-33,-16,-36,-38,-12,-72c118,-159,245,-395,279,-518c49,-177,5,-383,-130,-603c-49,-81,-123,-176,-228,-292l-73,-82l105,-138c58,-76,168,-221,246,-323c183,-240,329,-455,304,-447c-10,3,-48,18,-84,34c-56,24,-78,28,-160,28c-82,0,-103,-4,-150,-26c-113,-53,-201,-160,-254,-304c-41,-113,-48,-273,-17,-366c66,-199,350,-519,545,-614c54,-26,57,-27,76,-10c18,16,17,19,-44,92c-224,266,-275,542,-131,702c52,57,127,97,220,116c114,23,249,4,398,-56l68,-27l-32,56c-17,32,-77,118,-132,192c-143,192,-298,423,-336,504c-58,121,-58,221,-1,378c47,128,172,340,315,533l54,73l-331,499c-181,274,-358,540,-391,591c-34,50,-66,92,-72,91c-5,0,-20,-5,-32,-11z"

/** The logo path fits a square of this many units (after flipping it upright), its ink centred in it. */
private const val LOGO_UNITS = 447f

/** The logo's box when the prototype drew it on a 1080 px wide phone; links, points and drift scale from it. */
private const val REFERENCE_LOGO_SIZE = 1.6f * LOGO_UNITS

/** Width the prototype's sizes were tuned for. */
private const val REFERENCE_SLOT = 1080f

/**
 * The logo's square box, relative to the plexus's shorter side, as the prototype drew it on a phone: the glyph's ink
 * (375.5 of the box's 447 units tall) is then 56% of that side, clear of the edges when the logo holds.
 */
private const val LOGO_SIZE_FRACTION = REFERENCE_LOGO_SIZE / REFERENCE_SLOT

/** One loop of the plexus: the points drift, settle into the logo, hold it, then drift off again. */
const val PLEXUS_LOOP_SECONDS = 10f

/** When, in each loop, the first point starts settling into the logo. */
private const val SETTLE_START_SECONDS = 1f

/** When, in each loop, the first point leaves the logo again. */
private const val RELEASE_START_SECONDS = 6f

/** How long one point takes to settle into the logo, or to leave it. */
private const val MOVE_SECONDS = 2.5f

/** Points further from the middle of the list move later: the furthest by this much. */
private const val MAX_STAGGER_SECONDS = 1.5f

/**
 * When, in each loop, the last point has settled: from here until [PLEXUS_LOGO_HOLD_END_SECONDS] the logo holds
 * still.
 */
const val PLEXUS_LOGO_HOLD_START_SECONDS = SETTLE_START_SECONDS + MAX_STAGGER_SECONDS + MOVE_SECONDS

/** When, in each loop, the first point leaves the logo. */
const val PLEXUS_LOGO_HOLD_END_SECONDS = RELEASE_START_SECONDS

/** The frame drawn instead of animating (battery saver, animations off): the middle of the hold. */
private const val STILL_FRAME_SECONDS = (PLEXUS_LOGO_HOLD_START_SECONDS + PLEXUS_LOGO_HOLD_END_SECONDS) / 2f

private const val POINT_COUNT = 350
private const val NUM_BUCKETS = 8

/** Points 0 until this sit on the glyph's outline; the rest fill it. */
private const val CONTOUR_COUNT = (POINT_COUNT * 0.4f).toInt()

/** Opacity of the glow under the settled logo's inner links. */
private const val GLOW_ALPHA = 0.25f

/** Points closer than this (at the reference size) are linked. */
private const val MAX_LINK_DISTANCE = 130f

/** Below this logo size (px) nothing is drawn: a collapsed layout, mid-resize. */
private const val MIN_LOGO_SIZE = 16f

/** Interior points are picked by rejection sampling: give up after this many tries rather than spin. */
private const val MAX_SAMPLING_TRIES = 200_000

private const val LINE_WIDTH = 2f
private const val POINT_WIDTH = 4.5f
private const val GLOW_WIDTH = 6f
private const val MIN_DRIFT_RADIUS = 100f
private const val DRIFT_RADIUS_RANGE = 200f

/** Points fainter than this (at the cloud's rim) aren't drawn. */
private const val MIN_POINT_ALPHA = 0.1f

/** The outer share of the cloud's radius over which points and links fade out. */
private const val RIM_FADE_WIDTH = 0.3f

/** A point's drift circle is at most this share of the cloud's radius, so the cloud keeps a full, even disc. */
private const val MAX_ORBIT_SHARE = 0.35f

/**
 * Where the plexus draws, in its own coordinates: the points drift inside [driftArea]; the logo's square box, centred
 * on [logoCenter], is [logoSize] wide, and the links' reach, the points, the lines and the drift scale with it.
 */
private data class PlexusGeometry(
    val driftArea: Rect,
    val logoCenter: Offset,
    val logoSize: Float,
) {
    companion object {
        /** Drifting all over [slot], with the logo in its centre, sized from its shorter side. */
        fun inSlot(slot: Rect): PlexusGeometry =
            PlexusGeometry(slot, slot.center, slot.minDimension * LOGO_SIZE_FRACTION)
    }
}

/**
 * The plexus's time, hoisted so a screen can follow the animation: [seconds] since it started, and whether it shows
 * the [isStill] logo (battery saver, or animations off) rather than animating. In loop time, the logo holds from
 * [PLEXUS_LOGO_HOLD_START_SECONDS] to [PLEXUS_LOGO_HOLD_END_SECONDS] every [PLEXUS_LOOP_SECONDS].
 */
@Stable
class PlexusClock {
    var seconds: Float by mutableFloatStateOf(0f)
        internal set

    var isStill: Boolean by mutableStateOf(false)
        internal set
}

@Composable
fun rememberPlexusClock(): PlexusClock = remember { PlexusClock() }

private class PRNG(private var a: Int) {
    fun nextFloat(): Float {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        val res = (t xor (t ushr 14))
        return (res.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

private class PlexusState {
    val targetX = FloatArray(POINT_COUNT)
    val targetY = FloatArray(POINT_COUNT)
    val cx = FloatArray(POINT_COUNT)

    // The round cloud's centre and radius, and each point's fade towards its rim (1 inside, 0 at the edge), so the
    // circle reads as a soft round cloud rather than a ragged cut-out.
    var discX = 0f
    var discY = 0f
    var discRadius = 1f
    val rimFade = FloatArray(POINT_COUNT)
    val cy = FloatArray(POINT_COUNT)
    val r1 = FloatArray(POINT_COUNT)
    val r2 = FloatArray(POINT_COUNT)
    val phaseX = FloatArray(POINT_COUNT)
    val phaseY = FloatArray(POINT_COUNT)

    val currentX = FloatArray(POINT_COUNT)
    val currentY = FloatArray(POINT_COUNT)
    val resolveProgress = FloatArray(POINT_COUNT)

    // Every link of a frame, grouped by brightness bucket: one shared buffer, filled by counting sort (a first pass
    // counts each bucket's links, a second writes them at their bucket's offset), so each bucket is one drawLines call.
    // N·(N−1)/2 links at most, 4 floats each: about 1 MB.
    val lineBuffer = FloatArray(POINT_COUNT * (POINT_COUNT - 1) / 2 * 4)
    val lineCounts = IntArray(NUM_BUCKETS)
    val lineOffsets = IntArray(NUM_BUCKETS)
    val lineFill = IntArray(NUM_BUCKETS)

    // Pairs that may stay linked once the logo has formed: close at their targets and with the link inside the
    // glyph. Linking every close pair would bridge the glyph's concave cut-outs (the hook) and fill them in.
    val linkedInGlyph = BooleanArray(POINT_COUNT * POINT_COUNT)

    // Only links between filling points glow, so the glow never softens the outline (it blurred the hook and notches).
    val glowBuffer = FloatArray((POINT_COUNT - CONTOUR_COUNT) * (POINT_COUNT - CONTOUR_COUNT - 1) / 2 * 4)
    var glowCount = 0
    val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = android.graphics.Color.argb(255, 245, 245, 244)
    }

    val pointBuckets = Array(NUM_BUCKETS) { FloatArray(POINT_COUNT * 2) }
    val pointCounts = IntArray(NUM_BUCKETS)

    val linePaints = Array(NUM_BUCKETS) { i ->
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = android.graphics.Color.argb(((i + 1) * 255 / NUM_BUCKETS), 245, 245, 244)
        }
    }

    val pointPaints = Array(NUM_BUCKETS) { i ->
        Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            val a = 0.6f + (i / (NUM_BUCKETS - 1).toFloat()) * 0.4f
            color = android.graphics.Color.argb((a * 255).toInt(), 245, 245, 244)
        }
    }

    /** The geometry the targets and drift were laid out for. */
    var geometry: PlexusGeometry? = null
        private set

    /** Links reach this far, in px. */
    var maxLinkDistance = 0f
        private set

    private var defaultSize = Size.Zero
    private var defaultGeometry = PlexusGeometry(Rect.Zero, Offset.Zero, 0f)

    /** The plexus filling [size]; allocates only when [size] changes. */
    fun geometryFor(size: Size): PlexusGeometry {
        if (size != defaultSize) {
            defaultSize = size
            defaultGeometry = PlexusGeometry.inSlot(Rect(Offset.Zero, size))
        }
        return defaultGeometry
    }

    /** Lays the points out for [geometry], unless they already are: on the first frame and when the layout changes. */
    fun prepare(geometry: PlexusGeometry) {
        if (geometry == this.geometry) return
        this.geometry = geometry

        val unit = geometry.logoSize / REFERENCE_LOGO_SIZE
        maxLinkDistance = MAX_LINK_DISTANCE * unit
        for (paint in linePaints) paint.strokeWidth = max(1f, LINE_WIDTH * unit)
        for (paint in pointPaints) paint.strokeWidth = max(1.5f, POINT_WIDTH * unit)
        glowPaint.strokeWidth = max(1f, GLOW_WIDTH * unit)

        val composePath = Path()
        PathParser().parsePathString(LOGO_PATH_DATA).toPath(composePath)
        val logoPath = composePath.asAndroidPath()
        val scale = geometry.logoSize / LOGO_UNITS
        logoPath.transform(
            Matrix().apply {
                // The path's raw coordinates are ten times the logo's units, upside down.
                postScale(0.1f, -0.1f)
                postTranslate(-LOGO_UNITS / 2f, LOGO_UNITS / 2f)
                postScale(scale, scale)
                postTranslate(geometry.logoCenter.x, geometry.logoCenter.y)
            },
        )

        val measure = PathMeasure(logoPath, false)
        val totalLength = measure.length
        val pos = FloatArray(2)
        for (i in 0 until CONTOUR_COUNT) {
            measure.getPosTan(i.toFloat() / CONTOUR_COUNT * totalLength, pos, null)
            targetX[i] = pos[0]
            targetY[i] = pos[1]
        }

        val bounds = android.graphics.RectF()
        logoPath.computeBounds(bounds, true)
        val region = Region()
        region.setPath(
            logoPath,
            Region(
                (bounds.left - 1).toInt(),
                (bounds.top - 1).toInt(),
                (bounds.right + 1).toInt(),
                (bounds.bottom + 1).toInt(),
            ),
        )

        val prng = PRNG(42)
        var index = CONTOUR_COUNT
        var tries = 0
        while (index < POINT_COUNT && tries < MAX_SAMPLING_TRIES) {
            tries++
            val x = bounds.left + prng.nextFloat() * bounds.width()
            val y = bounds.top + prng.nextFloat() * bounds.height()
            if (region.contains(x.toInt(), y.toInt())) {
                targetX[index] = x
                targetY[index] = y
                index++
            }
        }
        // A degenerate glyph (too small to hold points): stack the rest on the outline rather than spin.
        while (index < POINT_COUNT) {
            targetX[index] = targetX[index % CONTOUR_COUNT]
            targetY[index] = targetY[index % CONTOUR_COUNT]
            index++
        }

        val maxDistSq = maxLinkDistance * maxLinkDistance
        for (i in 0 until POINT_COUNT) {
            for (j in i + 1 until POINT_COUNT) {
                val dx = targetX[i] - targetX[j]
                val dy = targetY[i] - targetY[j]
                // Inside at a quarter, half and three quarters: a link across a tight concave curve can have its
                // midpoint inside and still cut outside the glyph.
                var inside = dx * dx + dy * dy < maxDistSq
                var f = 0.25f
                while (inside && f < 1f) {
                    inside = region.contains(
                        (targetX[i] + (targetX[j] - targetX[i]) * f).toInt(),
                        (targetY[i] + (targetY[j] - targetY[i]) * f).toInt(),
                    )
                    f += 0.25f
                }
                linkedInGlyph[i * POINT_COUNT + j] = inside
            }
        }

        // Each point circles a centre of its own, and the cloud is round: the centres are spread evenly over a disc in the
        // drift area (radius ∝ √u keeps the density even), and each circle shrinks so it never leaves the disc.
        val area = geometry.driftArea
        discRadius = min(area.width, area.height) / 2f
        discX = area.center.x
        discY = area.center.y
        for (i in 0 until POINT_COUNT) {
            val wanted = (MIN_DRIFT_RADIUS + prng.nextFloat() * DRIFT_RADIUS_RANGE) * unit
            val orbit = min(wanted, discRadius * MAX_ORBIT_SHARE)
            val reach = (discRadius - orbit).coerceAtLeast(0f)
            val distance = reach * sqrt(prng.nextFloat())
            val angle = prng.nextFloat() * (Math.PI.toFloat() * 2f)
            cx[i] = discX + cos(angle) * distance
            cy[i] = discY + sin(angle) * distance
            r1[i] = orbit
            r2[i] = orbit
            phaseX[i] = prng.nextFloat() * (Math.PI.toFloat() * 2f)
            phaseY[i] = prng.nextFloat() * (Math.PI.toFloat() * 2f)
        }
    }
}


/**
 * How bright the link between points [i] and [j] is, or a negative value for no link. Once both are mostly settled
 * into the logo, only pairs linked inside the glyph keep their link.
 */
private fun PlexusState.linkAlpha(i: Int, j: Int, maxDist: Float, maxDistSq: Float, pulseAlpha: Float): Float {
    val dx = currentX[i] - currentX[j]
    val dy = currentY[i] - currentY[j]
    val distSq = dx * dx + dy * dy
    if (distSq >= maxDistSq) return -1f
    val avgResolve = (resolveProgress[i] + resolveProgress[j]) * 0.5f
    if (avgResolve > 0.5f && !linkedInGlyph[i * POINT_COUNT + j]) return -1f
    val falloff = 1f - sqrt(distSq) / maxDist
    val alpha = falloff * falloff
    val lineAlpha = if (avgResolve > 0.8f) alpha * 0.9f else alpha * 0.6f + avgResolve * 0.3f
    return lineAlpha * pulseAlpha * min(rimFade[i], rimFade[j])
}

private fun easeInOutCubic(x: Float): Float {
    return if (x < 0.5f) {
        4f * x * x * x
    } else {
        val p = -2f * x + 2f
        1f - (p * p * p) / 2f
    }
}

/** How far point [index] is into the logo at loop time [t]: 0 drifting, 1 settled. */
private fun resolveProgress(t: Float, index: Int): Float {
    val stagger = abs(index - POINT_COUNT / 2) / (POINT_COUNT / 2f) * MAX_STAGGER_SECONDS
    val settleStart = SETTLE_START_SECONDS + stagger
    val releaseStart = RELEASE_START_SECONDS + stagger
    return when {
        t < settleStart -> 0f
        t < settleStart + MOVE_SECONDS -> easeInOutCubic((t - settleStart) / MOVE_SECONDS)
        t < releaseStart -> 1f
        t < releaseStart + MOVE_SECONDS -> 1f - easeInOutCubic((t - releaseStart) / MOVE_SECONDS)
        else -> 0f
    }
}

/**
 * Points drifting all over this composable, linked when close, that settle into the Mellow logo in its middle (sized
 * from its shorter side), hold it and drift off again, every [PLEXUS_LOOP_SECONDS]. With battery saver or animations
 * off, a still logo that gently pulses. Transparent: draw a background behind it.
 *
 * @param clock the animation's time, for a screen that follows it.
 * @param fixedTimeSeconds draw this moment and don't animate (screenshot tests).
 */
@Composable
fun DatabaseMaintenancePlexus(
    modifier: Modifier = Modifier,
    clock: PlexusClock = rememberPlexusClock(),
    fixedTimeSeconds: Float? = null,
) {
    val context = LocalContext.current
    val animationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val still = LocalBatterySaverActive.current || animationsOff
    SideEffect { clock.isStill = still }

    if (fixedTimeSeconds == null) {
        LaunchedEffect(clock) {
            // Carries on from the clock's time if this restarts (the clock outlives it).
            val start = withInfiniteAnimationFrameNanos { it } - (clock.seconds * 1_000_000_000L).toLong()
            while (true) {
                withInfiniteAnimationFrameNanos { now -> clock.seconds = (now - start) / 1_000_000_000f }
            }
        }
    }

    val state = remember { PlexusState() }

    Canvas(modifier = modifier) {
        val layout = state.geometryFor(size)
        if (layout.logoSize < MIN_LOGO_SIZE || layout.driftArea.width <= 0f || layout.driftArea.height <= 0f) {
            return@Canvas
        }
        state.prepare(layout)

        val elapsed = fixedTimeSeconds ?: clock.seconds
        val time = if (still) STILL_FRAME_SECONDS else elapsed % PLEXUS_LOOP_SECONDS
        val pulseAlpha = if (still) 0.5f + 0.3f * sin(elapsed) else 1f
        val angle = (time / PLEXUS_LOOP_SECONDS) * (Math.PI.toFloat() * 2f)

        var totalResolve = 0f
        for (i in 0 until POINT_COUNT) {
            val progress = resolveProgress(time, i)
            state.resolveProgress[i] = progress
            totalResolve += progress

            val driftX = state.cx[i] + cos(angle + state.phaseX[i]) * state.r1[i]
            val driftY = state.cy[i] + sin(angle + state.phaseY[i]) * state.r2[i]

            state.currentX[i] = driftX + (state.targetX[i] - driftX) * progress
            state.currentY[i] = driftY + (state.targetY[i] - driftY) * progress
            // Fade over the outer part of the disc; settled points (the logo) never fade.
            val fromCentre = hypot(driftX - state.discX, driftY - state.discY) / state.discRadius
            val fade = ((1f - fromCentre) / RIM_FADE_WIDTH).coerceIn(0f, 1f)
            state.rimFade[i] = fade + (1f - fade) * progress
        }
        val avgGlobalResolve = totalResolve / POINT_COUNT

        for (i in 0 until NUM_BUCKETS) {
            state.lineCounts[i] = 0
            state.pointCounts[i] = 0
        }

        val maxDist = state.maxLinkDistance
        val maxDistSq = maxDist * maxDist

        // Still: points only, no neighbour search.
        state.glowCount = 0
        if (!still) {
            for (pass in 0..1) {
                for (i in 0 until POINT_COUNT) {
                    for (j in i + 1 until POINT_COUNT) {
                        val lineAlpha = state.linkAlpha(i, j, maxDist, maxDistSq, pulseAlpha)
                        if (lineAlpha <= 0f) continue
                        val bucket = (lineAlpha * (NUM_BUCKETS - 1)).toInt().coerceIn(0, NUM_BUCKETS - 1)
                        if (pass == 0) {
                            state.lineCounts[bucket] += 4
                            val settled = (state.resolveProgress[i] + state.resolveProgress[j]) * 0.5f > 0.8f
                            if (settled && i >= CONTOUR_COUNT && j >= CONTOUR_COUNT) {
                                val at = state.glowCount
                                state.glowBuffer[at] = state.currentX[i]
                                state.glowBuffer[at + 1] = state.currentY[i]
                                state.glowBuffer[at + 2] = state.currentX[j]
                                state.glowBuffer[at + 3] = state.currentY[j]
                                state.glowCount += 4
                            }
                        } else {
                            val at = state.lineOffsets[bucket] + state.lineFill[bucket]
                            state.lineBuffer[at] = state.currentX[i]
                            state.lineBuffer[at + 1] = state.currentY[i]
                            state.lineBuffer[at + 2] = state.currentX[j]
                            state.lineBuffer[at + 3] = state.currentY[j]
                            state.lineFill[bucket] += 4
                        }
                    }
                }
                if (pass == 0) {
                    var offset = 0
                    for (b in 0 until NUM_BUCKETS) {
                        state.lineOffsets[b] = offset
                        state.lineFill[b] = 0
                        offset += state.lineCounts[b]
                    }
                }
            }
        }

        for (i in 0 until POINT_COUNT) {
            val alpha = (0.6f + state.resolveProgress[i] * 0.4f) * pulseAlpha * state.rimFade[i]
            if (alpha < MIN_POINT_ALPHA) continue
            val bucketIdx = (alpha * (NUM_BUCKETS - 1)).toInt().coerceIn(0, NUM_BUCKETS - 1)
            val baseIdx = state.pointCounts[bucketIdx]
            state.pointBuckets[bucketIdx][baseIdx] = state.currentX[i]
            state.pointBuckets[bucketIdx][baseIdx + 1] = state.currentY[i]
            state.pointCounts[bucketIdx] += 2
        }

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val glowScale = if (avgGlobalResolve > 0.8f) (avgGlobalResolve - 0.8f) / 0.2f else 0f

            if (glowScale > 0f && !still && state.glowCount > 0) {
                state.glowPaint.alpha = (GLOW_ALPHA * 255 * glowScale * pulseAlpha).toInt().coerceIn(0, 255)
                nativeCanvas.drawLines(state.glowBuffer, 0, state.glowCount, state.glowPaint)
            }

            if (!still) {
                for (i in 0 until NUM_BUCKETS) {
                    val count = state.lineCounts[i]
                    if (count > 0) {
                        val alpha = ((i + 1) * 255 / NUM_BUCKETS) * pulseAlpha
                        state.linePaints[i].alpha = alpha.toInt().coerceIn(0, 255)
                        nativeCanvas.drawLines(state.lineBuffer, state.lineOffsets[i], count, state.linePaints[i])
                    }
                }
            }

            for (i in 0 until NUM_BUCKETS) {
                val count = state.pointCounts[i]
                if (count > 0) {
                    val baseAlpha = 0.6f + (i / (NUM_BUCKETS - 1).toFloat()) * 0.4f
                    state.pointPaints[i].alpha = (baseAlpha * 255 * pulseAlpha).toInt().coerceIn(0, 255)
                    nativeCanvas.drawPoints(state.pointBuckets[i], 0, count, state.pointPaints[i])
                }
            }
        }
    }
}
