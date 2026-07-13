package dev.mellow.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.MellowImage
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerPlaybackControls
import dev.mellow.feature.player.PlayerProgressBar
import dev.mellow.feature.player.PlayerTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SheetAnchor { Collapsed, Expanded }
enum class SheetPage { Player, Queue, Lyrics }

@Stable
class ExpandableSheetState(
    internal val anchoredState: AnchoredDraggableState<SheetAnchor>,
    private val scope: CoroutineScope,
) {
    val isExpanded: Boolean
        get() = anchoredState.currentValue == SheetAnchor.Expanded

    val dragFraction: Float
        get() {
            val offset = anchoredState.offset
            return if (offset.isNaN()) 0f
            else anchoredState.progress(SheetAnchor.Collapsed, SheetAnchor.Expanded)
                .coerceIn(0f, 1f)
        }

    fun expand() {
        scope.launch { anchoredState.animateToAnchor(SheetAnchor.Expanded) }
    }

    fun collapse() {
        scope.launch { anchoredState.animateToAnchor(SheetAnchor.Collapsed) }
    }

    internal fun updateAnchors(anchors: DraggableAnchors<SheetAnchor>) {
        anchoredState.updateAnchors(anchors)
    }
}

@Composable
fun rememberExpandableSheetState(): ExpandableSheetState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val anchoredState = rememberSaveable(
        saver = AnchoredDraggableState.Saver(),
    ) {
        AnchoredDraggableState(
            initialValue = SheetAnchor.Collapsed,
            positionalThreshold = { distance -> distance * 0.3f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    return remember(anchoredState, scope) { ExpandableSheetState(anchoredState, scope) }
}

@Composable
fun ExpandablePlayerSheet(
    sheetState: ExpandableSheetState,
    trackName: String,
    artistName: String,
    albumImageUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    playerContent: @Composable (onCollapse: () -> Unit, onQueueClick: () -> Unit, onLyricsClick: () -> Unit) -> Unit,
    queueContent: @Composable (onBack: () -> Unit) -> Unit,
    lyricsContent: @Composable (onBack: () -> Unit) -> Unit,
    bottomNavHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val state = sheetState.anchoredState
    var currentPage by remember { mutableStateOf(SheetPage.Player) }

    LaunchedEffect(sheetState.isExpanded) {
        if (!sheetState.isExpanded) currentPage = SheetPage.Player
    }

    val miniPlayerHeightPx = with(density) { MellowSpacing.MiniPlayerHeight.toPx() }
    val miniPaddingVerticalPx = with(density) { (MellowSpacing.Sp1 * 2).toPx() }
    val collapsedStripPx = miniPlayerHeightPx + miniPaddingVerticalPx

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxHeightPx = constraints.maxHeight.toFloat()

        val navBarPx = with(density) {
            WindowInsets.navigationBars.getBottom(density).toFloat()
        }

        val collapsedOffsetPx = maxHeightPx - navBarPx - bottomNavHeightPx - collapsedStripPx

        LaunchedEffect(collapsedOffsetPx, maxHeightPx) {
            sheetState.updateAnchors(
                DraggableAnchors {
                    SheetAnchor.Collapsed at collapsedOffsetPx
                    SheetAnchor.Expanded at 0f
                },
            )
        }

        val dragFraction by remember {
            derivedStateOf { sheetState.dragFraction }
        }

        val bgAlpha by remember {
            derivedStateOf { (dragFraction * 4f).coerceIn(0f, 1f) }
        }
        val expandedAlpha by remember {
            derivedStateOf {
                val f = (dragFraction * 2f).coerceIn(0f, 1f)
                f * f
            }
        }
        val showExpanded by remember {
            derivedStateOf { dragFraction > 0.05f }
        }

        val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.requireOffset()

        BackHandler(enabled = sheetState.isExpanded) {
            when (currentPage) {
                SheetPage.Queue, SheetPage.Lyrics -> currentPage = SheetPage.Player
                SheetPage.Player -> sheetState.collapse()
            }
        }

        val isSheetVisible = dragFraction > 0f
        val sheetHeightPx = (maxHeightPx - currentOffset).coerceIn(0f, maxHeightPx)
        val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

        val sheetNestedScroll = remember(state) {
            object : NestedScrollConnection {
                var isChildAtTop = false

                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (sheetState.isExpanded && available.y < 0f) {
                        isChildAtTop = false
                    }
                    return if (isChildAtTop && available.y < 0f && source == NestedScrollSource.UserInput) {
                        state.dispatchRawDelta(available.y)
                        available
                    } else {
                        androidx.compose.ui.geometry.Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (!isChildAtTop) {
                        isChildAtTop = consumed.y == 0f && available.y > 0f
                    }
                    return if (isChildAtTop && source == NestedScrollSource.UserInput) {
                        state.dispatchRawDelta(available.y)
                        available
                    } else {
                        androidx.compose.ui.geometry.Offset.Zero
                    }
                }

                override suspend fun onPreFling(
                    available: androidx.compose.ui.unit.Velocity,
                ): androidx.compose.ui.unit.Velocity {
                    return if (isChildAtTop) {
                        val velocity = -available.y
                        if (velocity > 250f) {
                            sheetState.collapse()
                        }
                        available
                    } else {
                        androidx.compose.ui.unit.Velocity.Zero
                    }
            }
        }
        }

        if (isSheetVisible) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeightDp)
                .align(Alignment.BottomCenter)
                .clipToBounds()
                .nestedScroll(sheetNestedScroll)
                .anchoredDraggable(state, Orientation.Vertical),
        ) {
            // LAYER 1 — Expanded background
            if (showExpanded) {
                val fullHeightDp = with(density) { maxHeightPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(fullHeightDp)
                        .graphicsLayer { alpha = bgAlpha }
                        .background(MellowTheme.colors.background),
                )
                if (albumImageUrl != null) {
                    AsyncImage(
                        model = albumImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(fullHeightDp)
                            .blur(120.dp)
                            .graphicsLayer { alpha = bgAlpha * 0.35f },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(fullHeightDp)
                        .graphicsLayer { alpha = bgAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MellowTheme.colors.background.copy(alpha = 0.5f),
                                    MellowTheme.colors.background.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                )
            }

            // LAYER 2 — Expanded content
            if (showExpanded) {
                val fullHeightDp = with(density) { maxHeightPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(fullHeightDp)
                        .graphicsLayer { alpha = expandedAlpha }
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    when (currentPage) {
                        SheetPage.Player -> {
                            playerContent(
                                { sheetState.collapse() },
                                { currentPage = SheetPage.Queue },
                                { currentPage = SheetPage.Lyrics },
                            )
                        }
                        SheetPage.Queue -> {
                            queueContent { currentPage = SheetPage.Player }
                        }
                        SheetPage.Lyrics -> {
                            lyricsContent { currentPage = SheetPage.Player }
                        }
                    }
                }
            }



        }
        }
    }
}

private suspend fun <T> AnchoredDraggableState<T>.animateToAnchor(
    target: T,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 400f,
    ),
) {
    val targetOffset = anchors.positionOf(target)
    if (targetOffset.isNaN()) return
    anchoredDrag(targetValue = target) { _, _ ->
        var prev = requireOffset()
        animate(
            initialValue = prev,
            targetValue = targetOffset,
            animationSpec = animationSpec,
        ) { value, _ ->
            dragTo(value)
            prev = value
        }
    }
}

@Stable
class SharedArtPositions {
    var miniOffset by mutableStateOf(Offset.Zero)
    var miniSize by mutableStateOf(IntSize.Zero)
    var expandedOffset by mutableStateOf(Offset.Zero)
    var expandedSize by mutableStateOf(IntSize.Zero)
}

fun Modifier.trackArtPosition(positions: SharedArtPositions, isMini: Boolean): Modifier =
    onGloballyPositioned { coords ->
        if (isMini) {
            positions.miniOffset = coords.positionInWindow()
            positions.miniSize = coords.size
        } else {
            positions.expandedOffset = coords.positionInWindow()
            positions.expandedSize = coords.size
        }
    }

@Composable
fun SharedArtOverlay(
    imageUrl: String?,
    dragFraction: Float,
    positions: SharedArtPositions,
    parentOffset: Offset,
    modifier: Modifier = Modifier,
) {
    if (dragFraction <= 0f || dragFraction >= 1f) return
    if (positions.miniSize == IntSize.Zero || positions.expandedSize == IntSize.Zero) return

    val artFraction = run {
        val f = dragFraction.coerceIn(0f, 1f)
        f * f * (3f - 2f * f)
    }

    val miniSide = minOf(positions.miniSize.width, positions.miniSize.height).toFloat()
    val expSide = minOf(positions.expandedSize.width, positions.expandedSize.height).toFloat()

    val miniCenterX = positions.miniOffset.x - parentOffset.x + positions.miniSize.width / 2f
    val miniCenterY = positions.miniOffset.y - parentOffset.y + positions.miniSize.height / 2f
    val expCenterX = positions.expandedOffset.x - parentOffset.x + positions.expandedSize.width / 2f
    val expCenterY = positions.expandedOffset.y - parentOffset.y + positions.expandedSize.height / 2f

    MellowImage(
        model = imageUrl,
        contentDescription = "Album art",
        modifier = modifier
            .layout { measurable, constraints ->
                val side = lerp(miniSide, expSide, artFraction).roundToInt().coerceAtLeast(1)
                val cx = lerp(miniCenterX, expCenterX, artFraction)
                val yFraction = kotlin.math.sqrt(dragFraction.coerceIn(0f, 1f))
                val cy = lerp(miniCenterY, expCenterY, yFraction)
                val x = (cx - side / 2f).roundToInt()
                val y = (cy - side / 2f).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(x, y)
                }
            }
            .clip(
                RoundedCornerShape(
                    androidx.compose.ui.unit.lerp(8.dp, 16.dp, artFraction),
                ),
            )
            .background(MellowTheme.colors.surface),
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
