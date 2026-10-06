package dev.mellow.core.designsystem.component

import kotlin.math.abs
import kotlin.math.hypot

/*
 * Tells a swipe on the now-playing cover (turn the page) from a drag down (collapse the sheet) by what the finger is
 * doing over its first ~20–32 dp, not by a strict angle at the touch slop. A thumb swiping left sweeps an arc that
 * starts 35–50° below horizontal, so the classifier weighs the whole displacement and its latest direction, and
 * leans toward horizontal: the sheet only gets a drag that is clearly downward.
 */

/** Distance from the down point (dp) at which a gesture is first classified. */
internal const val CLASSIFY_DP = 20f

/** Distance (dp) by which an ambiguous gesture is decided anyway: the larger score wins. */
internal const val DECIDE_BY_DP = 32f

/** Weight of the displacement since the down point in a score; the rest is the latest direction. */
internal const val DISPLACEMENT_WEIGHT = 0.65f

/** The latest direction: the move over the last [RECENT_SAMPLES] samples (current vs the one this many back). */
internal const val RECENT_SAMPLES = 3

/** Horizontal (the page) when scoreX ≥ this × scoreY, about 57° from horizontal. */
internal const val HORIZONTAL_RATIO = 0.65f

/** Vertical (the sheet) only when scoreY ≥ this × scoreX and the finger went down. */
internal const val VERTICAL_RATIO = 1.2f

/** An upward-dominant drag still turns the page if its horizontal score is at least this (dp). */
internal const val MEANINGFUL_HORIZONTAL_DP = 10f

/**
 * The classifier's thresholds, defaulting to the constants above. Note that with the defaults the horizontal region
 * (up to ≈57° from horizontal) and the vertical one (from ≈50°) overlap, horizontal taking precedence, so a downward
 * drag with a sheet is never left undecided; raising [verticalRatio] above 1 / [horizontalRatio] ≈ 1.54 opens an
 * undecided band between them, resolved at [decideByDp].
 */
internal data class SwipeTuning(
    val classifyDp: Float = CLASSIFY_DP,
    val decideByDp: Float = DECIDE_BY_DP,
    val horizontalRatio: Float = HORIZONTAL_RATIO,
    val verticalRatio: Float = VERTICAL_RATIO,
    val meaningfulHorizontalDp: Float = MEANINGFUL_HORIZONTAL_DP,
)

internal enum class SwipeDecision { Undecided, Horizontal, Vertical, Cancel }

/** A pointer position (px) at [timeMillis]. */
internal class SwipeSample(val timeMillis: Long, val x: Float, val y: Float)

/** The decision, with what it was based on (dp): the distance from the down point and both scores. */
internal class SwipeClassification(
    val decision: SwipeDecision,
    val distanceDp: Float,
    val scoreX: Float,
    val scoreY: Float,
)

/**
 * Classifies a gesture from its [samples] (the first is the down point; positions in px, [density] px per dp).
 * [hasSheet]: whether there is a sheet to hand a drag down to.
 *
 * - Nothing before [CLASSIFY_DP]: undecided.
 * - Scores: `0.65·|displacement| + 0.35·|latest move|` per axis.
 * - Horizontal when scoreX ≥ 0.65·scoreY, either way: the cover claims it even toward a track that isn't there (the
 *   page then just resists).
 * - Vertical when scoreY ≥ 1.2·scoreX, the finger went down and there is a sheet. Down-dominant with no sheet: cancel.
 * - Up-dominant: never the sheet; horizontal if scoreX is at least [MEANINGFUL_HORIZONTAL_DP], else cancel.
 * - Otherwise undecided until [DECIDE_BY_DP], then the larger score wins under the same rules, else cancel.
 */
internal fun classifySwipe(
    samples: List<SwipeSample>,
    density: Float,
    hasSheet: Boolean,
    tuning: SwipeTuning = SwipeTuning(),
): SwipeClassification {
    val down = samples.first()
    val current = samples.last()
    val recent = samples[maxOf(0, samples.size - 1 - RECENT_SAMPLES)]
    val dispX = (current.x - down.x) / density
    val dispY = (current.y - down.y) / density
    val recentX = (current.x - recent.x) / density
    val recentY = (current.y - recent.y) / density
    val distance = hypot(dispX, dispY)
    val scoreX = DISPLACEMENT_WEIGHT * abs(dispX) + (1 - DISPLACEMENT_WEIGHT) * abs(recentX)
    val scoreY = DISPLACEMENT_WEIGHT * abs(dispY) + (1 - DISPLACEMENT_WEIGHT) * abs(recentY)

    fun result(decision: SwipeDecision) = SwipeClassification(decision, distance, scoreX, scoreY)

    if (distance < tuning.classifyDp) return result(SwipeDecision.Undecided)
    val downward = dispY > 0f
    val decision = when {
        scoreX >= tuning.horizontalRatio * scoreY -> SwipeDecision.Horizontal
        !downward ->
            if (scoreX >= tuning.meaningfulHorizontalDp) SwipeDecision.Horizontal else SwipeDecision.Cancel
        !hasSheet -> SwipeDecision.Cancel
        scoreY >= tuning.verticalRatio * scoreX -> SwipeDecision.Vertical
        distance < tuning.decideByDp -> SwipeDecision.Undecided
        scoreX >= scoreY -> SwipeDecision.Horizontal
        else -> SwipeDecision.Vertical
    }
    return result(decision)
}

/** One line for the gesture log: every sample (ms since down, px from the down point) and the decision. */
internal fun swipeLogLine(samples: List<SwipeSample>, density: Float, classification: SwipeClassification?): String {
    val down = samples.first()
    val points = samples.joinToString(";") { s ->
        "${s.timeMillis - down.timeMillis},${round1(s.x - down.x)},${round1(s.y - down.y)}"
    }
    val decision = when (classification?.decision) {
        SwipeDecision.Horizontal -> "H"
        SwipeDecision.Vertical -> "V"
        SwipeDecision.Cancel -> "cancel"
        SwipeDecision.Undecided, null -> "undecided"
    }
    val at = classification?.let {
        " at=${round1(it.distanceDp)} scoreX=${round1(it.scoreX)} scoreY=${round1(it.scoreY)}"
    } ?: ""
    return "samples=[$points] dp=$density down=${round1(down.x)},${round1(down.y)} decision=$decision$at"
}

private fun round1(value: Float): String = "%.1f".format(java.util.Locale.ROOT, value)
