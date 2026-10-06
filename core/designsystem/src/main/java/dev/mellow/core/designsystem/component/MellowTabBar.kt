package dev.mellow.core.designsystem.component

import android.content.Context
import android.provider.Settings
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

/**
 * A row of tabs whose pill moves with taps. For tabs with pages that swipe, use the overload taking a
 * [MellowTabPagerState].
 */
@Composable
fun MellowTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabBar(tabs = tabs, selectedIndex = selectedIndex, pager = null, onTabSelected = onTabSelected, modifier = modifier)
}

/**
 * A row of tabs over [state]'s pages. The pill follows the pages as they are swiped, stretching like a drop of liquid
 * between two tabs, and a tapped tab scrolls the pages to it. The tab the pages settle on is reported through the
 * state (see [rememberMellowTabPagerState]).
 */
@Composable
fun MellowTabBar(
    tabs: List<String>,
    state: MellowTabPagerState,
    modifier: Modifier = Modifier,
) {
    TabBar(
        tabs = tabs,
        selectedIndex = state.pagerState.targetPage,
        pager = state,
        onTabSelected = {},
        modifier = modifier,
    )
}

@Composable
private fun TabBar(
    tabs: List<String>,
    selectedIndex: Int,
    pager: MellowTabPagerState?,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val batterySaver = LocalBatterySaverActive.current
    // Read when the bar recomposes rather than on every frame; a tap reads it afresh.
    val liquid = rememberUpdatedState(!animationsOff(context, batterySaver))
    val selected = rememberUpdatedState(selectedIndex)
    val chips = remember { ChipBounds() }
    val tap = remember { TapAnimation() }
    val indicator = remember(pager) { Indicator(chips, tap, pager, selected, liquid) }
    val scrollState = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }

    FollowIndicator(scrollState, indicator, viewport = { viewport })

    val onTabClick: (Int) -> Unit = { tab ->
        if (pager == null) onTabSelected(tab)
        val from = indicator.position()
        val off = animationsOff(context, batterySaver)
        val generation = tap.start(from = from, to = tab.toFloat(), animate = !off && abs(from - tab) > 0.001f)
        tap.job?.cancel()
        tap.job = scope.launch {
            try {
                coroutineScope {
                    if (pager != null) {
                        val scrolling = launch { if (off) pager.jumpToTab(tab) else pager.animateToTab(tab) }
                        // A finger on the pages takes over from the tap: the pill follows them again.
                        scrolling.invokeOnCompletion { cause ->
                            if (cause is CancellationException) this@coroutineScope.cancel(cause)
                        }
                    }
                    if (tap.isAnimating(generation)) {
                        tap.progress.snapTo(0f)
                        tap.progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = LiquidTabIndicator.tapDurationMillis(tab - from),
                                easing = LinearEasing,
                            ),
                        )
                    }
                }
            } finally {
                tap.finish(generation)
            }
        }
    }

    val muted = MellowTheme.colors.muted
    val pillPath = remember { Path() }
    val clipPath = remember { Path() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scrollState),
    ) {
        // The tabs as they read off the pill, drawn around it, with the pill and the touch targets.
        TabRow(
            chips = chips,
            modifier = Modifier
                .selectableGroup()
                .drawWithContent {
                    if (!drawPill(indicator, pillPath)) {
                        drawContent()
                        return@drawWithContent
                    }
                    clipPath(pillPath, ClipOp.Difference) { this@drawWithContent.drawContent() }
                },
        ) {
            tabs.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier
                        .clip(MellowShapes.Full)
                        .selectable(selected = index == selectedIndex, role = Role.Tab) { onTabClick(index) }
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
        }
        // The same tabs in the selected colour, only where the pill is: a letter half under it is half dark.
        TabRow(
            chips = null,
            modifier = Modifier
                .clearAndSetSemantics {}
                .drawWithContent {
                    if (indicator.span(size.height)) {
                        indicator.outline(clipPath)
                        clipPath(clipPath) { this@drawWithContent.drawContent() }
                    }
                },
        ) {
            tabs.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MellowPalette.Stone950,
                    modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                )
            }
        }
    }
}

/** Draws the pill (1 dp Stone300 rim, Stone200 fill) and leaves its outline in [path]; false while there is none. */
private fun ContentDrawScope.drawPill(indicator: Indicator, path: Path): Boolean {
    if (!indicator.span(size.height)) return false
    indicator.outline(path)
    drawPath(path, MellowPalette.Stone300)
    val border = 1.dp.toPx()
    val radius = (indicator.radius - border).coerceAtLeast(0f)
    drawRoundRect(
        color = MellowPalette.Stone200,
        topLeft = Offset(indicator.left + border, indicator.centerY - radius),
        size = Size((indicator.right - indicator.left - 2 * border).coerceAtLeast(0f), 2 * radius),
        cornerRadius = CornerRadius(radius),
    )
    return true
}

/** Keeps the pill in view: the bar scrolls to centre it, as far as it can scroll. */
@Composable
private fun FollowIndicator(scrollState: ScrollState, indicator: Indicator, viewport: () -> Int) {
    LaunchedEffect(scrollState, indicator) {
        snapshotFlow {
            if (!indicator.span(0f) || viewport() == 0) null
            else LiquidTabIndicator.scrollFor(
                center = (indicator.left + indicator.right) / 2f,
                viewport = viewport(),
                maxScroll = scrollState.maxValue,
            )
        }.collect { target ->
            if (target != null && target != scrollState.value && !scrollState.isScrollInProgress) {
                scrollState.scrollTo(target)
            }
        }
    }
}

/**
 * The tabs laid out in a row: Sp4 at both ends, Sp2 between them, centred vertically. [chips], when given, learns
 * where each tab is.
 */
@Composable
private fun TabRow(chips: ChipBounds?, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val start = MellowSpacing.Sp4.roundToPx()
        val gap = MellowSpacing.Sp2.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val lefts = FloatArray(placeables.size)
        val rights = FloatArray(placeables.size)
        var x = start
        placeables.forEachIndexed { i, placeable ->
            lefts[i] = x.toFloat()
            rights[i] = (x + placeable.width).toFloat()
            x += placeable.width + gap
        }
        val width = if (placeables.isEmpty()) 2 * start else x - gap + start
        chips?.update(lefts, rights, height.toFloat())
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place(lefts[i].toInt(), (height - placeable.height) / 2)
            }
        }
    }
}

/** Where the tabs are in the row. */
@Stable
private class ChipBounds {
    var lefts by mutableStateOf(FloatArray(0), policy = contentEqualPolicy())
        private set
    var rights by mutableStateOf(FloatArray(0), policy = contentEqualPolicy())
        private set
    var height by mutableFloatStateOf(0f)
        private set

    fun update(lefts: FloatArray, rights: FloatArray, height: Float) {
        this.lefts = lefts
        this.rights = rights
        this.height = height
    }
}

private fun contentEqualPolicy() = object : SnapshotMutationPolicy<FloatArray> {
    override fun equivalent(a: FloatArray, b: FloatArray) = a.contentEquals(b)
}

/** A tap's own animation of the pill, from where it was to the tapped tab; the pages may follow on their own. */
@Stable
private class TapAnimation {
    val progress = Animatable(0f)
    var from by mutableFloatStateOf(0f)
        private set
    var to by mutableFloatStateOf(0f)
        private set

    /** Whether the pill draws from [progress] rather than the pages: from the tap until the pages arrive. */
    var active by mutableStateOf(false)
        private set
    private var animating = false
    private var generation = 0
    var job: Job? = null

    fun start(from: Float, to: Float, animate: Boolean): Int {
        this.from = from
        this.to = to
        animating = animate
        active = animate
        return ++generation
    }

    fun isAnimating(generation: Int) = animating && generation == this.generation

    /** The tap of [generation] is over, unless another one has started since. */
    fun finish(generation: Int) {
        if (generation == this.generation) {
            active = false
            animating = false
        }
    }
}

/**
 * Where the pill is: from a tap's animation while there is one, otherwise from the pages' position (or, without
 * pages, the selected tab). Read only while drawing, so the pages moving redraws the bar without recomposing it.
 */
private class Indicator(
    private val chips: ChipBounds,
    private val tap: TapAnimation,
    private val pager: MellowTabPagerState?,
    private val selected: State<Int>,
    private val liquid: State<Boolean>,
) {
    var left = 0f
        private set
    var right = 0f
        private set
    var radius = 0f
        private set
    var centerY = 0f
        private set

    /** The pill's position in tabs, for a tap starting from wherever it is now. */
    fun position(): Float = when {
        tap.active -> tap.from + (tap.to - tap.from) * LiquidTabIndicator.tapTail(tap.progress.value)
        pager != null -> pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction
        else -> selected.value.toFloat()
    }

    /** Works out the pill for a row [rowHeight] tall (0: the chips' own height); false while there are no tabs. */
    fun span(rowHeight: Float): Boolean {
        val lefts = chips.lefts
        val rights = chips.rights
        if (lefts.isEmpty()) return false
        val position: Float
        if (tap.active) {
            val u = tap.progress.value
            left = LiquidTabIndicator.tapLeft(lefts, tap.from, tap.to, u)
            right = LiquidTabIndicator.tapRight(rights, tap.from, tap.to, u)
            position = tap.from + (tap.to - tap.from) * LiquidTabIndicator.tapTail(u)
        } else {
            position = position()
            val isLiquid = liquid.value
            left = LiquidTabIndicator.swipeLeft(lefts, position, isLiquid)
            right = LiquidTabIndicator.swipeRight(rights, position, isLiquid)
        }
        val resting = LiquidTabIndicator.edgeAt(rights, position) - LiquidTabIndicator.edgeAt(lefts, position)
        val halfHeight = chips.height / 2f
        radius = LiquidTabIndicator.radius(
            halfHeight = halfHeight,
            left = left,
            right = right,
            restingWidth = resting,
            pitch = LiquidTabIndicator.pitch(lefts, rights),
        )
        centerY = (if (rowHeight > 0f) rowHeight else chips.height) / 2f
        return true
    }

    /** The pill's outline, as last worked out by [span], into [path]. */
    fun outline(path: Path) {
        path.reset()
        path.addRoundRect(
            RoundRect(
                left = left,
                top = centerY - radius,
                right = right,
                bottom = centerY + radius,
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
}

/** Animations are off: the system's animator scale is 0, or battery saver is on, as for the page turn. */
private fun animationsOff(context: Context, batterySaver: Boolean): Boolean =
    batterySaver || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
