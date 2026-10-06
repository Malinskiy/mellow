package dev.mellow.core.designsystem.component

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The maths of the page turn: a stiff page hinged on its left edge (the spine), seen through a perspective camera
 * whose vanishing point is the hinge — what a graphicsLayer with transformOrigin (0, 0.5) draws. Angles are in degrees:
 * 0 = the page lies on the stack, 180 = turned over to the left. Distances are in px from the hinge.
 *
 * Ported from the approved HyperFrames prototype (player-swipe/hyperframes-swipe, variant "page"), whose cover is
 * 844 px wide on a 1080 px screen; its pixel constants are scaled by the page's width here.
 */

/** Width of the prototype's cover, which its pixel constants refer to. */
internal const val PROTOTYPE_PAGE_PX = 844f

/** Prototype perspective (3200 px) per page width: at 90° the free edge is magnified 3200 / (3200 − 844) ≈ 1.36×. */
internal const val PERSPECTIVE_PER_PAGE_WIDTH = 3200f / PROTOTYPE_PAGE_PX

/**
 * A RenderNode's camera distance is in Skia's camera units, 72 px each (`Sk3DView::setCameraLocation`); Compose's
 * `graphicsLayer { cameraDistance }` passes its value straight through.
 */
internal const val CAMERA_DISTANCE_PX_PER_UNIT = 72f

/** Prototype drag over which the previous page's free edge catches up with the finger: 380 px of 1080, ≈145 dp. */
internal const val PREVIOUS_CATCH_UP_DP = 145f

/**
 * Drag over which the current page catches up with the finger. A page lifting off the stack first rises toward the
 * camera, so the touched point moves right before it moves left: no angle under 2·atan(r / P) (≈23° for a touch in
 * the middle) keeps it under a finger that moved left. The page eases up to the exact angle over this distance.
 */
internal const val NEXT_CATCH_UP_DP = 48f

/** Finger speed in the swipe direction above which a short swipe still commits: 1000 px/s on a 1080 px screen. */
internal const val FLING_DP_PER_SECOND = 380f

/** Commit motion: angular gravity (°/s²) and the restitution of the landing bounce. */
internal const val GRAVITY_DEG_PER_S2 = 1500f
internal const val LANDING_RESTITUTION = 0.18f

/** Fall back: `spring(dampingRatio = 0.7f, stiffness = 400f)`, overshoot reflected at 30 % (a bounce on the stack). */
internal const val FALL_BACK_DAMPING = 0.7f
internal const val FALL_BACK_STIFFNESS = 400f
internal const val FALL_BACK_RESTITUTION = 0.3f

/** With nothing to turn to, the page follows the finger at this fraction of its travel at first, then stiffens. */
internal const val RESISTANCE = 0.3f

/** …and never travels further than this fraction of the page width. */
internal const val RESISTANCE_LIMIT_PER_PAGE_WIDTH = 0.12f

/** A touch closer to the spine than this fraction of the page is treated as this far, so the angle stays tame. */
internal const val MIN_GRIP_PER_PAGE_WIDTH = 0.2f

private const val DEG = 180.0 / PI

/** The camera distance from the hinge, in px, that gives the prototype's perspective for a page [pageWidth] wide. */
internal fun perspectiveFor(pageWidth: Float): Float = pageWidth * PERSPECTIVE_PER_PAGE_WIDTH

/** `graphicsLayer { cameraDistance }` for a camera [perspective] px from the page. */
internal fun cameraDistanceFor(perspective: Float): Float = perspective / CAMERA_DISTANCE_PX_PER_UNIT

/**
 * Where a point [r] px from the hinge appears, horizontally, when the page is turned [angle]° toward the camera,
 * [perspective] px away: `r·cos a · P / (P − r·sin a)`.
 */
internal fun hingeProject(angle: Float, r: Float, perspective: Float): Float {
    val a = angle / DEG
    return (r * cos(a) * perspective / (perspective - r * sin(a))).toFloat()
}

/**
 * The inverse of [hingeProject] on its turning branch, where the point moves left as the page turns: the angle at
 * which a point [r] px from the hinge appears at [u]. From `u·P = r·(P·cos a + u·sin a) = r·√(P²+u²)·cos(a − θ)`
 * with θ = atan2(u, P): `a = θ + acos(u·P / (r·√(P²+u²)))`.
 *
 * Near 0° and 180° the point first moves the other way (rising toward the camera magnifies it), so a [u] beyond the
 * turning branch's reach gets the angle at its end.
 */
internal fun hingeAngle(u: Float, r: Float, perspective: Float): Float {
    val p = perspective.toDouble()
    // The furthest the point appears from the hinge, either side: where u·P = r·√(P²+u²).
    val furthest = r * p / sqrt(p * p - r.toDouble() * r)
    val x = u.toDouble().coerceIn(-furthest, furthest)
    val reach = hypot(p, x)
    val c = (x * p / (r * reach)).coerceIn(-1.0, 1.0)
    return ((atan2(x, p) + acos(c)) * DEG).toFloat()
}

/**
 * The angle of the current page while the finger, which went down [touchX] px from the hinge, has moved [drag] px
 * (negative = left). Once the page has caught up with the finger, over the first [catchUp] px, the point under the
 * finger stays under it, perspective included.
 */
internal fun nextPageAngle(
    touchX: Float,
    drag: Float,
    pageWidth: Float,
    perspective: Float,
    catchUp: Float,
): Float {
    val grip = touchX.coerceIn(pageWidth * MIN_GRIP_PER_PAGE_WIDTH, pageWidth)
    val caught = (-drag / catchUp).coerceIn(0f, 1f)
    return (hingeAngle(grip + drag, grip, perspective) * caught).coerceIn(0f, 180f)
}

/**
 * The angle of the previous page, which lies turned over (180°) to the left, while the finger drags right [drag] px
 * from [touchX]: it swings in and catches up with the finger over [catchUp] px, after which its free edge is under
 * the finger.
 */
internal fun previousPageAngle(
    touchX: Float,
    drag: Float,
    pageWidth: Float,
    perspective: Float,
    catchUp: Float,
): Float {
    val caught = (drag / catchUp).coerceIn(0f, 1f)
    val underFinger = hingeAngle(touchX + drag, pageWidth, perspective)
    return (180f + (underFinger - 180f) * caught).coerceIn(0f, 180f)
}

/** A drag toward a page that isn't there: follows at [RESISTANCE] at first and never goes past [limit]. */
internal fun resistedDrag(drag: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    return sign(drag) * limit * (1f - exp(-abs(drag) * RESISTANCE / limit))
}

/** How far toward its commit a turn is, 0..1: the next page turns 0 → 180°, the previous one 180 → 0°. */
internal fun turnProgress(direction: PageTurnDirection, angle: Float): Float = when (direction) {
    PageTurnDirection.Next -> angle / 180f
    PageTurnDirection.Previous -> 1f - angle / 180f
}

/**
 * Whether a released turn goes through: past halfway, or flung in its direction at more than [flingThreshold] (px/s)
 * once it is a tenth of the way. Never when there is no track that way ([enabled] false).
 */
internal fun shouldCommit(
    progress: Float,
    velocityInDirection: Float,
    flingThreshold: Float,
    enabled: Boolean,
): Boolean = enabled && (progress > 0.5f || (velocityInDirection > flingThreshold && progress > 0.1f))

/** The resting angle a cancelled turn falls back to, and the one a committed turn lands on. */
internal fun restAngle(direction: PageTurnDirection): Float = if (direction == PageTurnDirection.Next) 0f else 180f
internal fun goalAngle(direction: PageTurnDirection): Float = if (direction == PageTurnDirection.Next) 180f else 0f

/**
 * A fall-back spring's value as drawn: overshoot past [rest] is reflected at [restitution], the page bouncing on the
 * stack instead of passing through it. The next page rests at 0° (it can't go below), the previous one at 180°.
 */
internal fun reflectPastRest(
    value: Float,
    direction: PageTurnDirection,
    restitution: Float = FALL_BACK_RESTITUTION,
): Float {
    val rest = restAngle(direction)
    val past = if (direction == PageTurnDirection.Next) rest - value else value - rest
    if (past <= 0f) return value
    return if (direction == PageTurnDirection.Next) rest + past * restitution else rest - past * restitution
}

/**
 * Constant acceleration [gravity] toward [target], from [start] at [velocity] (°/s), bouncing on it with
 * [restitution] until a bounce is too small to see. Closed form per phase, so [valueAt] can be sampled at any time.
 */
internal class GravityFall(
    private val start: Float,
    private val velocity: Float,
    private val target: Float,
    private val gravity: Float = GRAVITY_DEG_PER_S2,
    private val restitution: Float = LANDING_RESTITUTION,
) {
    private class Phase(val startTime: Float, val from: Float, val velocity: Float, val duration: Float)

    private val acceleration = (if (target >= start) 1f else -1f) * gravity
    private val phases: List<Phase>

    /** When the page comes to rest on [target], in seconds. */
    val settleTime: Float

    init {
        val list = mutableListOf<Phase>()
        var time = 0f
        var x = start
        var v = velocity
        for (i in 0 until MAX_BOUNCES) {
            val duration = timeToReach(x, v)
            list += Phase(time, x, v, duration)
            time += duration
            v = -restitution * (v + acceleration * duration)
            x = target
            if (abs(v) < MIN_BOUNCE_SPEED) break
        }
        phases = list
        settleTime = time
    }

    fun valueAt(time: Float): Float {
        if (time >= settleTime) return target
        val phase = phases.last { time >= it.startTime }
        val t = time - phase.startTime
        return phase.from + phase.velocity * t + 0.5f * acceleration * t * t
    }

    /** First t > 0 at which `x + v·t + ½·a·t²` reaches the target. */
    private fun timeToReach(x: Float, v: Float): Float {
        val a = 0.5 * acceleration
        val b = v.toDouble()
        val c = (x - target).toDouble()
        val disc = b * b - 4 * a * c
        if (disc < 0) return 0f
        val root = sqrt(disc)
        return max((-b - root) / (2 * a), (-b + root) / (2 * a)).coerceAtLeast(0.0).toFloat()
    }

    private companion object {
        const val MAX_BOUNCES = 6
        const val MIN_BOUNCE_SPEED = 4f
    }
}

/** The page's opacity: it fades out as it lands to the left, between 150° and 180°. */
internal fun pageAlpha(angle: Float): Float = 1f - smoothStep(150f, 180f, angle)

/**
 * The black shade over the page: it darkens as it turns away from a key light in front, slightly right and above.
 * Brightness is `0.32 + 0.68·max(0, N·L)` with L = (0.45, −, 0.86), normalised so a flat page is unshaded.
 */
internal fun pageShade(angle: Float): Float {
    val a = angle / DEG
    val s = sin(a)
    val c = cos(a)
    val facing = if (angle <= 90f) -LIGHT_X * s + LIGHT_Z * c else LIGHT_X * s - LIGHT_Z * c
    val brightness = 0.32 + 0.68 * max(0.0, facing) / LIGHT_Z
    return (1.0 - brightness).coerceIn(0.0, 0.85).toFloat()
}

/** The general dimming of the cover underneath while the page is still low over it. */
internal fun underDim(angle: Float): Float {
    val open = 1f - angle.coerceIn(0f, 180f) / 180f
    return 0.35f * open * open
}

/** The soft band of shadow the raised page casts on the cover underneath, just past its projected free edge. */
internal class CastShadow(val edge: Float, val width: Float, val alpha: Float)

internal fun castShadow(angle: Float, pageWidth: Float, perspective: Float): CastShadow {
    val s = sin(angle / DEG).toFloat()
    val scale = pageWidth / PROTOTYPE_PAGE_PX
    val edge = max(0f, hingeProject(angle, pageWidth, perspective))
    val width = (40f + 360f * s) * scale
    val alpha = (0.62f - 0.3f * s) * (if (angle > 90f) 1f - (angle - 90f) / 90f else 1f)
    return CastShadow(edge, width, alpha.coerceIn(0f, 1f))
}

private fun smoothStep(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

private const val LIGHT_X = 0.45
private const val LIGHT_Z = 0.86
