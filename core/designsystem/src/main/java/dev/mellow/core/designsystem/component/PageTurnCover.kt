package dev.mellow.core.designsystem.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowTheme

/** Which way a page turns: left to the next track, right (back) to the previous one. */
enum class PageTurnDirection { Next, Previous }

/** A page held still at [angle]° (0 = lying on the stack, 180 = turned over), for previews and screenshots. */
@Immutable
data class PageTurnPose(val direction: PageTurnDirection, val angle: Float)

/**
 * The now-playing cover as a record album page: swipe left and the page turns over its left edge (the spine) onto the
 * next track's cover underneath; swipe right and the previous page, lying turned over to the left, comes back over it.
 *
 * The point you hold stays under your finger, perspective included. Let go past halfway, or with a flick, and the
 * page falls the rest of the way; otherwise it falls back. A swipe toward a track that isn't there ([canGoNext] or
 * [canGoPrevious] false) meets strong resistance and never turns.
 *
 * The cover holds every gesture that starts on it until it knows what the finger means (see [classifySwipe]): a thumb
 * arc counts as a swipe, and only a clear drag down goes to the sheet the cover sits in, through [onSheetDrag] (px,
 * down positive) and [onSheetDragEnd] (px/s). Without [onSheetDrag] (no sheet) a vertical drag is let go.
 * [onGestureLog], for tuning in debug builds, gets one line per gesture: its samples and the decision.
 *
 * [current], [next] and [previous] are image models (see [MellowImage]); all three stay loaded so a turn never waits
 * for one. [trackKey] identifies the current track: a turn calls [onNext] or [onPrevious] when it is let go, and the
 * cover keeps showing the page it turned to until the key changes. A track change from anywhere else just shows the
 * new cover.
 *
 * Transforms only, inside layers: the cover's measured size and position never change, so whatever tracks them
 * through [modifier] (the shared mini ↔ expanded artwork) keeps working.
 */
@Composable
fun PageTurnCover(
    current: Any?,
    modifier: Modifier = Modifier,
    trackKey: Any? = current,
    next: Any? = null,
    previous: Any? = null,
    canGoNext: Boolean = false,
    canGoPrevious: Boolean = false,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    contentDescription: String? = "Album art",
    shape: Shape = MellowShapes.Large,
    fallbackIconSize: Dp? = 64.dp,
    pose: PageTurnPose? = null,
    onSheetDrag: ((Float) -> Unit)? = null,
    onSheetDragEnd: (Float) -> Unit = {},
    onGestureLog: ((String) -> Unit)? = null,
) {
    val state = remember { PageTurnState(pose) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val live = PageFaces(trackKey, current, next, previous, canGoNext, canGoPrevious)
    val targets = rememberUpdatedState(
        CoverGestureTargets(
            faces = live,
            onNext = onNext,
            onPrevious = onPrevious,
            sheet = onSheetDrag?.let { SheetHandoff(it, onSheetDragEnd) },
            log = onGestureLog,
        ),
    )

    LaunchedEffect(trackKey) {
        // The track the turn went to has arrived (or something else changed it): show what the player has.
        if (state.pending?.fromKey != trackKey) state.pending = null
    }
    LaunchedEffect(state.pending) {
        if (state.pending != null) {
            delay(PENDING_TIMEOUT_MS)
            state.pending = null
        }
    }

    val pending = state.pending?.takeIf { it.fromKey == trackKey }
    val faces = state.frozen ?: if (pending != null) live.copy(current = pending.target) else live
    val surface = MellowTheme.colors.surface

    Box(
        modifier = modifier
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                role = Role.Image
                customActions = buildList {
                    if (canGoPrevious) add(CustomAccessibilityAction("Previous track") { onPrevious(); true })
                    if (canGoNext) add(CustomAccessibilityAction("Next track") { onNext(); true })
                }
            }
            .pointerInput(state) {
                detectCoverGestures(state, scope, haptics, targets)
            },
    ) {
        // Drawn bottom to top: a next turn lifts the current page off the next cover, a previous turn lays the
        // previous page over the current one.
        PageFace(
            model = faces.next,
            blank = !faces.canGoNext,
            role = { state.roleOfNext() },
            state = state,
            shape = shape,
            surface = surface,
            fallbackIconSize = fallbackIconSize,
        )
        PageFace(
            model = faces.current,
            blank = false,
            role = { state.roleOfCurrent() },
            state = state,
            shape = shape,
            surface = surface,
            fallbackIconSize = fallbackIconSize,
        )
        PageFace(
            model = faces.previous,
            blank = !faces.canGoPrevious,
            role = { state.roleOfPrevious() },
            state = state,
            shape = shape,
            surface = surface,
            fallbackIconSize = fallbackIconSize,
        )
    }
}

/** What the gesture works with: the models and the ways it can go, frozen while a turn is under way. */
@Immutable
internal data class PageFaces(
    val key: Any?,
    val current: Any?,
    val next: Any?,
    val previous: Any?,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
)

/** After a committed turn: keep showing [target] while the player still reports the track it left, [fromKey]. */
internal class PendingTurn(val fromKey: Any?, val target: Any?)

internal enum class FaceRole { Hidden, Flat, Page, Under }

@Stable
internal class PageTurnState(pose: PageTurnPose?) {
    /** The turn under way, null when the cover lies still. */
    var direction by mutableStateOf(pose?.direction)
    var angle by mutableFloatStateOf(pose?.angle ?: 0f)

    /** A sideways nudge of the current page when there is no previous one to bring back. */
    var shift by mutableFloatStateOf(0f)
    var frozen by mutableStateOf<PageFaces?>(null)
    var pending by mutableStateOf<PendingTurn?>(null)
    var settling: Job? = null
    var pastHalfway = false

    /** A previous turn with a page to bring back. A pose (previews, screenshots) has no frozen faces and always has. */
    private val previousTurns: Boolean
        get() = direction == PageTurnDirection.Previous && (frozen?.canGoPrevious ?: true)

    fun roleOfNext(): FaceRole = if (direction == PageTurnDirection.Next) FaceRole.Under else FaceRole.Hidden

    fun roleOfCurrent(): FaceRole = when {
        direction == PageTurnDirection.Next -> FaceRole.Page
        previousTurns -> FaceRole.Under
        else -> FaceRole.Flat
    }

    fun roleOfPrevious(): FaceRole = if (previousTurns) FaceRole.Page else FaceRole.Hidden

    fun reset() {
        direction = null
        angle = 0f
        shift = 0f
        frozen = null
        pastHalfway = false
    }
}

@Composable
private fun PageFace(
    model: Any?,
    blank: Boolean,
    role: () -> FaceRole,
    state: PageTurnState,
    shape: Shape,
    surface: Color,
    fallbackIconSize: Dp?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val r = role()
                this.shape = shape
                clip = true
                alpha = when (r) {
                    FaceRole.Hidden -> 0f
                    FaceRole.Page -> pageAlpha(state.angle)
                    else -> 1f
                }
                translationX = if (r == FaceRole.Flat) state.shift else 0f
                if (r == FaceRole.Page) {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = PAGE_ROTATION_SIGN * state.angle
                    cameraDistance = cameraDistanceFor(perspectiveFor(size.width))
                } else {
                    rotationY = 0f
                }
            }
            .drawWithContent {
                when (role()) {
                    FaceRole.Hidden -> Unit
                    FaceRole.Flat -> drawContent()
                    FaceRole.Page -> drawPage(state.angle)
                    FaceRole.Under -> drawUnder(state.angle)
                }
            }
            .background(surface),
    ) {
        if (!blank) {
            MellowImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackIconSize = fallbackIconSize,
            )
        }
    }
}

/** The turning page: its face up to 90°, then its back (a warm grey board), shaded by how far it faces away. */
private fun ContentDrawScope.drawPage(angle: Float) {
    if (angle <= 90f) drawContent() else drawPageBack()
    val shade = pageShade(angle)
    if (shade > 0f) drawRect(Color.Black, alpha = shade)
}

/** The cover underneath: dimmed while the page is low over it, and in the soft shadow of the page's free edge. */
private fun ContentDrawScope.drawUnder(angle: Float) {
    drawContent()
    val perspective = perspectiveFor(size.width)
    val cast = castShadow(angle, size.width, perspective)
    val end = cast.edge + cast.width
    if (cast.alpha > 0f && end > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = cast.alpha),
                cast.edge / end to Color.Black.copy(alpha = cast.alpha),
                (cast.edge + cast.width * 0.35f) / end to Color.Black.copy(alpha = cast.alpha * 0.45f),
                1f to Color.Transparent,
                startX = 0f,
                endX = end,
            ),
        )
    }
    val dim = underDim(angle)
    if (dim > 0f) drawRect(Color.Black, alpha = dim)
}

/** The back of the page, seen mirrored: a board with a faint inset rule. */
private fun DrawScope.drawPageBack() {
    drawRect(
        Brush.horizontalGradient(
            0f to BACK_AT_SPINE,
            0.45f to BACK_MIDDLE,
            1f to BACK_AT_EDGE,
        ),
    )
    val inset = size.width * BACK_INSET_PER_PAGE_WIDTH
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(inset),
        style = Stroke(width = stroke),
    )
}

/** Where a drag down on the cover goes: the sheet the cover sits in, dragged by px and let go at px/s. */
internal class SheetHandoff(val onDrag: (Float) -> Unit, val onDragEnd: (Float) -> Unit)

/** What the cover's gestures act on, read when a gesture starts. */
internal class CoverGestureTargets(
    val faces: PageFaces,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val sheet: SheetHandoff?,
    val log: ((String) -> Unit)?,
)

/**
 * The one arbiter for gestures that start on the cover. From the down it holds the gesture (consumes every move), so
 * the sheet's own vertical drag never starts on it, and [classifySwipe] decides what the finger means: a page turn,
 * a drag down that it hands to the sheet (catching up with the finger at once, then 1:1, let go with the finger's
 * velocity), or nothing (it lets go). Once decided, the decision holds for the rest of the gesture.
 */
private suspend fun PointerInputScope.detectCoverGestures(
    state: PageTurnState,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    targets: State<CoverGestureTargets>,
) {
    val flingThreshold = FLING_DP_PER_SECOND.dp.toPx()
    val catchUp = PageTurnCatchUp(NEXT_CATCH_UP_DP.dp.toPx(), PREVIOUS_CATCH_UP_DP.dp.toPx())
    // Read once: the node may be gone by the time a gesture ends (a drag that collapses the sheet removes the cover).
    val pxPerDp = density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (state.settling?.isActive == true) return@awaitEachGesture
        val gesture = targets.value
        val samples = mutableListOf(SwipeSample(down.uptimeMillis, down.position.x, down.position.y))
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        var classification: SwipeClassification? = null
        try {
            while (classification == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                samples.record(change)
                tracker.addPointerInputChange(change)
                val classified = classifySwipe(samples, pxPerDp, hasSheet = gesture.sheet != null)
                if (classified.decision != SwipeDecision.Undecided) classification = classified
                if (classified.decision != SwipeDecision.Cancel) change.consume()
            }
            val last = samples.last()
            when (classification?.decision) {
                SwipeDecision.Horizontal -> {
                    val turn = PageTurnDrag(
                        state = state,
                        faces = gesture.faces,
                        touchX = down.position.x,
                        pageWidth = size.width.toFloat(),
                        catchUp = catchUp,
                        haptics = haptics,
                    )
                    turnPage(down.id, turn, last.x - down.position.x, samples, tracker) { released ->
                        val velocity = if (released) tracker.calculateVelocity() else Velocity.Zero
                        val end = samples.last()
                        val flingSpeed = turnFlingSpeed(
                            direction = state.direction ?: PageTurnDirection.Next,
                            velocity = Offset(velocity.x, velocity.y),
                            chord = Offset(end.x - down.position.x, end.y - down.position.y),
                        )
                        // A turn only goes through if the track it started from is still the current one.
                        val stillCurrent = released && targets.value.faces.key == gesture.faces.key
                        state.settling = scope.launch {
                            turn.release(
                                velocityX = velocity.x,
                                flingSpeed = flingSpeed,
                                flingThreshold = flingThreshold,
                                allowCommit = stillCurrent,
                                onNext = gesture.onNext,
                                onPrevious = gesture.onPrevious,
                            )
                        }
                    }
                }
                SwipeDecision.Vertical -> gesture.sheet?.let { sheet ->
                    dragSheet(down.id, sheet, last.y - down.position.y, samples, tracker)
                }
                SwipeDecision.Cancel -> if (gesture.log != null) observeUntilUp(down.id, samples)
                else -> Unit
            }
        } finally {
            if (samples.size > 1) gesture.log?.invoke(swipeLogLine(samples, pxPerDp, classification))
        }
    }
}

/** Turns the page from [startDrag] px (the whole horizontal way the finger has come) until the finger lifts. */
private suspend fun AwaitPointerEventScope.turnPage(
    pointer: PointerId,
    turn: PageTurnDrag,
    startDrag: Float,
    samples: MutableList<SwipeSample>,
    tracker: VelocityTracker,
    onEnd: (released: Boolean) -> Unit,
) {
    turn.state.frozen = turn.faces
    var drag = startDrag
    turn.dragTo(drag)
    var released = false
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: break
            samples.record(change)
            tracker.addPointerInputChange(change)
            drag += change.positionChange().x
            change.consume()
            if (!change.pressed) break
            turn.dragTo(drag)
        }
        released = true
    } finally {
        onEnd(released)
    }
}

/** Drags the sheet: first the whole [startDrag] px the finger has come down, then 1:1, and lets it go. */
private suspend fun AwaitPointerEventScope.dragSheet(
    pointer: PointerId,
    sheet: SheetHandoff,
    startDrag: Float,
    samples: MutableList<SwipeSample>,
    tracker: VelocityTracker,
) {
    sheet.onDrag(startDrag)
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: break
            samples.record(change)
            tracker.addPointerInputChange(change)
            val delta = change.positionChange().y
            change.consume()
            if (delta != 0f) sheet.onDrag(delta)
            if (!change.pressed) break
        }
    } finally {
        // Cancelled (the expanded player leaves when the sheet is nearly down): let go at the finger's last speed.
        sheet.onDragEnd(tracker.calculateVelocity().y)
    }
}

/** Only for the gesture log: follows a released gesture to its end without touching it. */
private suspend fun AwaitPointerEventScope.observeUntilUp(pointer: PointerId, samples: MutableList<SwipeSample>) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return
        samples.record(change)
        if (!change.pressed) return
    }
}

/**
 * Adds [change]'s positions, the batched historical ones first, to the samples. Positions are local to the cover,
 * which moves with the sheet once a drag goes to it, so they are shifted to continue from the last sample: the
 * samples stay in the frame of the down point.
 */
private fun MutableList<SwipeSample>.record(change: PointerInputChange) {
    val last = last()
    val shiftX = last.x - change.previousPosition.x
    val shiftY = last.y - change.previousPosition.y
    change.historical.forEach { add(SwipeSample(it.uptimeMillis, it.position.x + shiftX, it.position.y + shiftY)) }
    add(SwipeSample(change.uptimeMillis, change.position.x + shiftX, change.position.y + shiftY))
}

/** The drags (px) over which the next and the previous page catch up with the finger. */
internal class PageTurnCatchUp(val next: Float, val previous: Float)

/** One drag of the cover, from the touch down at [touchX] (px from the spine). */
internal class PageTurnDrag(
    val state: PageTurnState,
    val faces: PageFaces,
    private val touchX: Float,
    private val pageWidth: Float,
    private val catchUp: PageTurnCatchUp,
    private val haptics: HapticFeedback?,
) {
    private val perspective = perspectiveFor(pageWidth)
    private val resistanceLimit = pageWidth * RESISTANCE_LIMIT_PER_PAGE_WIDTH
    private var lastDrag = 0f

    fun dragTo(drag: Float) {
        lastDrag = drag
        val direction = when {
            drag < 0f -> PageTurnDirection.Next
            drag > 0f -> PageTurnDirection.Previous
            else -> state.direction ?: PageTurnDirection.Next
        }
        state.direction = direction
        if (direction == PageTurnDirection.Previous && !faces.canGoPrevious) {
            state.shift = resistedDrag(drag, resistanceLimit)
            state.angle = 180f
        } else {
            state.shift = 0f
            state.angle = angleFor(direction, drag)
        }
        val past = enabled(direction) && turnProgress(direction, state.angle) > 0.5f
        if (past && !state.pastHalfway) haptics?.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        state.pastHalfway = past
    }

    /** The page's angle for a drag of [drag] px, resisted when there is no track that way. */
    fun angleFor(direction: PageTurnDirection, drag: Float): Float = when (direction) {
        PageTurnDirection.Next -> nextPageAngle(
            touchX = touchX,
            drag = if (faces.canGoNext) drag else resistedDrag(drag, resistanceLimit),
            pageWidth = pageWidth,
            perspective = perspective,
            catchUp = catchUp.next,
        )
        PageTurnDirection.Previous -> previousPageAngle(touchX, drag, pageWidth, perspective, catchUp.previous)
    }

    private fun enabled(direction: PageTurnDirection) = when (direction) {
        PageTurnDirection.Next -> faces.canGoNext
        PageTurnDirection.Previous -> faces.canGoPrevious
    }

    /**
     * Let go at [velocityX] px/s. A committed turn calls [onNext] / [onPrevious] right away and falls the rest of the
     * way under gravity; a cancelled one springs back with a bounce on the stack.
     */
    suspend fun release(
        velocityX: Float,
        flingSpeed: Float,
        flingThreshold: Float,
        allowCommit: Boolean,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ) {
        val direction = state.direction
        if (direction == null) {
            state.reset()
            return
        }
        if (direction == PageTurnDirection.Previous && !faces.canGoPrevious) {
            animate(state.shift, 0f, velocityX, spring(FALL_BACK_DAMPING, FALL_BACK_STIFFNESS)) { value, _ ->
                state.shift = value
            }
            state.reset()
            return
        }
        val start = state.angle
        val angularVelocity = angularVelocity(direction, velocityX)
        val commit = allowCommit &&
            shouldCommit(turnProgress(direction, start), flingSpeed, flingThreshold, enabled(direction))
        if (commit) {
            val target = if (direction == PageTurnDirection.Next) faces.next else faces.previous
            state.pending = PendingTurn(faces.key, target)
            if (direction == PageTurnDirection.Next) onNext() else onPrevious()
            val fall = GravityFall(start, angularVelocity, goalAngle(direction))
            val startNanos = withFrameNanos { it }
            var elapsed = 0f
            while (elapsed < fall.settleTime) {
                elapsed = withFrameNanos { (it - startNanos) / 1e9f }
                state.angle = fall.valueAt(elapsed)
            }
        } else {
            val spec = spring<Float>(FALL_BACK_DAMPING, FALL_BACK_STIFFNESS)
            animate(start, restAngle(direction), angularVelocity, spec) { value, _ ->
                state.angle = reflectPastRest(value, direction)
            }
        }
        state.reset()
    }

    /** The page's angular speed (°/s) when the finger moves at [velocityX]: the mapping's slope times the speed. */
    private fun angularVelocity(direction: PageTurnDirection, velocityX: Float): Float {
        val slope = (angleFor(direction, lastDrag + 1f) - angleFor(direction, lastDrag - 1f)) / 2f
        return (slope * velocityX).coerceIn(-MAX_ANGULAR_VELOCITY, MAX_ANGULAR_VELOCITY)
    }
}

/** A positive rotationY turns the right edge away from the camera; the page's free edge comes toward it. */
private const val PAGE_ROTATION_SIGN = -1f

/** Caps the release spin from a flick right by the spine, where a pixel of finger is many degrees of page. */
private const val MAX_ANGULAR_VELOCITY = 3000f

/** How long the cover waits for the player to report the track a turn went to before showing what it has. */
private const val PENDING_TIMEOUT_MS = 1500L

private const val BACK_INSET_PER_PAGE_WIDTH = 18f / PROTOTYPE_PAGE_PX
private val BACK_AT_SPINE = Color(0xFF514943)
private val BACK_MIDDLE = Color(0xFF5B534C)
private val BACK_AT_EDGE = Color(0xFF4A433D)
