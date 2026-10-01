package dev.mellow.core.designsystem.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * One hide-on-scroll state shared by every bar that slides away while content scrolls (the top toolbar and the
 * bottom navigation bar), so they always hide and come back together.
 *
 * [fraction] goes from `0` (shown) to `1` (hidden); each bar moves by `fraction` times its own height. Scrolling content
 * forward hides the bars as the content moves. Scrolling back reveals them first, before the content moves. When the
 * gesture ends they settle fully shown or hidden.
 *
 * Attach [nestedScrollConnection] once, around the scrolling content.
 */
@Stable
class HideOnScrollState {
    var fraction by mutableFloatStateOf(0f)
        private set

    /** Whether the bars last settled hidden. Changes only when a gesture ends, not on every frame. */
    var isSettledHidden by mutableStateOf(false)
        private set

    /** When disabled the bars stay shown and scrolling is ignored. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                fraction = 0f
                isSettledHidden = false
            }
        }

    private val partHeights = mutableStateMapOf<Any, Float>()

    /** Scroll distance that takes the bars from shown to hidden: the tallest bar currently on screen. */
    private val distancePx: Float get() = partHeights.values.maxOrNull() ?: 0f

    internal fun setPartHeight(key: Any, heightPx: Float) {
        partHeights[key] = heightPx
    }

    internal fun removePart(key: Any) {
        partHeights.remove(key)
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val distance = distancePx
            if (!enabled || distance <= 0f || available.y <= 0f || fraction <= 0f) return Offset.Zero
            val revealPx = minOf(available.y, fraction * distance)
            fraction -= revealPx / distance
            return Offset(0f, revealPx)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val distance = distancePx
            if (enabled && distance > 0f && consumed.y < 0f) {
                fraction = (fraction - consumed.y / distance).coerceIn(0f, 1f)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** Animates to fully shown or hidden, whichever is closer. */
    suspend fun settle() {
        if (!enabled) return
        val hide = fraction > 0.5f
        animateTo(if (hide) 1f else 0f)
        isSettledHidden = hide
    }

    /** Brings the bars back, e.g. after navigating to another destination. */
    suspend fun show() {
        isSettledHidden = false
        animateTo(0f)
    }

    private suspend fun animateTo(target: Float) {
        if (fraction == target) return
        animate(fraction, target, animationSpec = spring(stiffness = 600f)) { value, _ -> fraction = value }
    }
}

@Composable
fun rememberHideOnScrollState(): HideOnScrollState = remember { HideOnScrollState() }

/**
 * The app-wide [HideOnScrollState], provided by the app shell. `null` where no shell provides one (previews,
 * screenshot tests); components then fall back to their own behaviour.
 */
val LocalHideOnScrollState = staticCompositionLocalOf<HideOnScrollState?> { null }

/**
 * Registers a bar with [state] for as long as it is in the composition, so its height counts towards the scroll
 * distance. Returns the key to pass to [hideOnScrollBottom] or use with [HideOnScrollState.setPartHeight].
 */
@Composable
fun rememberHideOnScrollPart(state: HideOnScrollState): Any {
    val key = remember { Any() }
    DisposableEffect(state, key) { onDispose { state.removePart(key) } }
    return key
}

/**
 * For a bar anchored to the bottom: slides it down by [HideOnScrollState.fraction] of its height and shrinks its
 * reported height to match, so whatever sits above it (mini player, scaffold padding) follows. The offset is read
 * during layout only, so scrolling relayouts the bar without recomposing it.
 */
fun Modifier.hideOnScrollBottom(state: HideOnScrollState, key: Any): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val offset = (state.fraction * placeable.height).roundToInt().coerceIn(0, placeable.height)
        layout(placeable.width, placeable.height - offset) { placeable.place(0, 0) }
    }
    .onSizeChanged { state.setPartHeight(key, it.height.toFloat()) }

/** Registers a top bar's height; the bar itself translates by `-fraction * height`. */
internal fun Modifier.hideOnScrollTopHeight(state: HideOnScrollState, key: Any): Modifier =
    onSizeChanged { state.setPartHeight(key, it.height.toFloat()) }
