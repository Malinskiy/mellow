package dev.mellow.core.designsystem.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/** Where a button skip went: the track's key and its cover (an image model, see [MellowImage]). */
@Immutable
class PageTurnTarget(val key: Any?, val cover: Any?)

/**
 * Lets Next and Previous buttons turn the page of a [PageTurnCover] (pass it as `buttons`). [turn] skips right away
 * and turns the page to wherever the skip went; without a cover on screen it only skips.
 */
@Stable
class PageTurnButtons {
    internal var handler: ((PageTurnDirection, () -> PageTurnTarget?) -> Unit)? = null

    /** Runs [skip] (which returns where the player went, null if it stayed) and turns the page there. */
    fun turn(direction: PageTurnDirection, skip: () -> PageTurnTarget?) {
        val turnPage = handler
        if (turnPage == null) skip() else turnPage(direction, skip)
    }
}

@Composable
fun rememberPageTurnButtons(): PageTurnButtons = remember { PageTurnButtons() }

/** How long a button's page turn takes to land: snappier than a released swipe, still long enough to read. */
internal const val BUTTON_TURN_SECONDS = 0.3f

/** A button turn's first frame is already one 60 Hz frame into it, so the page moves on the frame after the press. */
internal const val BUTTON_TURN_LEAD_SECONDS = 1f / 60f

/**
 * A button press: lands any turn under way at once (its page shows the track it went to), skips, and if the player
 * moved, turns the page from the cover now on top to [skip]'s target. [live] are the faces the cover last composed.
 *
 * Neither the turn's top page nor its target come from the player's next/previous state, which may still be catching
 * up with a previous press: the top page is what the cover shows, the target is where the skip says it went. So a burst
 * of presses never queues turns and ends on the track that is playing.
 */
internal fun PageTurnState.turnByButton(
    direction: PageTurnDirection,
    live: PageFaces,
    scope: CoroutineScope,
    animate: Boolean,
    skip: () -> PageTurnTarget?,
) {
    if (dragging) {
        // The finger has the page; its release sees the track changed and lays the page back.
        skip()
        return
    }
    settling?.cancel()
    settling = null
    reset()
    val onTop = pending?.takeIf { it.appliesTo(live.key) }?.target ?: live.current
    val target = skip() ?: return
    pending = PendingTurn(fromKey = live.key, target = target.cover, toKey = target.key)
    if (!animate) return
    val next = direction == PageTurnDirection.Next
    frozen = live.copy(
        key = target.key,
        current = onTop,
        next = if (next) target.cover else live.next,
        previous = if (next) live.previous else target.cover,
        canGoNext = next || live.canGoNext,
        canGoPrevious = !next || live.canGoPrevious,
    )
    this.direction = direction
    val start = restAngle(direction)
    val goal = goalAngle(direction)
    angle = start
    settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        fall(start, buttonTurnVelocity(goal - start), goal, lead = BUTTON_TURN_LEAD_SECONDS)
        reset()
    }
}

/**
 * The starting angular velocity (°/s) that lands a page [distance]° away in [BUTTON_TURN_SECONDS] under the page's
 * gravity: `d = v·t + ½·g·t²`.
 */
internal fun buttonTurnVelocity(distance: Float, seconds: Float = BUTTON_TURN_SECONDS): Float {
    val sign = if (distance >= 0f) 1f else -1f
    val v = (kotlin.math.abs(distance) - 0.5f * GRAVITY_DEG_PER_S2 * seconds * seconds) / seconds
    return sign * v
}
