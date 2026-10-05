package dev.mellow.core.designsystem.component.maintenance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.theme.DevicePosture
import dev.mellow.core.designsystem.theme.FoldableState
import dev.mellow.core.designsystem.theme.LocalFoldableState
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A window at least this much wider than tall puts the graphic and the caption side by side. */
private const val SIDE_BY_SIDE_ASPECT = 1.25f

/** A hinge closer than this share of the window's shorter side to an edge isn't laid out around. */
private const val MIN_HINGE_PART = 0.25f

/** The caption's font size relative to the graphic's slot (dp to sp), between titleMedium and [MAX_CAPTION_SP]. */
private const val CAPTION_SIZE_FRACTION = 0.06f

/** The largest caption: Material's headlineMedium size. Mellow's own headlineMedium (18 sp) is small next to a big
 * graphic. */
private const val MAX_CAPTION_SP = 28f

/** Clear space kept around the group, relative to the shorter side of its part of the window; at least 24 dp. */
private const val OUTER_MARGIN_FRACTION = 0.05f
private val MIN_OUTER_MARGIN = 24.dp

/** The group spans at most this share of the window along its stacking axis: never edge to edge. */
private const val MAX_GROUP_SHARE = 0.92f

/** A [MaintenanceCaption]'s subline, relative to its title. */
private const val SUBLINE_SIZE_RATIO = 0.72f

/** Between a [MaintenanceCaption]'s title and its subline, relative to the title's size (sp to dp). */
private const val SUBLINE_GAP_RATIO = 0.3f

/** The narrowest graphic slot [MaintenanceLayout] takes, as width over height. */
private const val MIN_ASPECT = 0.1f

/** Between the graphic's slot and the caption. */
private val CAPTION_GAP = 16.dp

/** Side by side, the caption takes at most this share of the width. */
private const val SIDE_CAPTION_SHARE = 0.4f

/** How [MaintenanceLayout] arranges its graphic and caption. */
internal enum class MaintenanceShape {
    /** Portrait or near square: the graphic above the caption, centred together. */
    Stacked,

    /** Wide: the graphic left of the caption, centred together. */
    SideBySide,

    /** Half folded, hinge across: the graphic above the hinge, the caption below it. */
    Tabletop,

    /** Half folded, hinge upright: the graphic left of the hinge, the caption right of it. */
    Book,
}

/**
 * [MaintenanceLayout]'s spacing, in px: at least [minMargin] kept clear around the group, [gap] between graphic and
 * caption.
 */
internal data class MaintenanceSpacing(val minMargin: Float, val gap: Float) {
    /** Clear space around the group in a [part] of the window. */
    fun margin(part: Size): Float = max(minMargin, min(part.width, part.height) * OUTER_MARGIN_FRACTION)
}

/** Where [MaintenanceLayout] puts its [graphic] slot and its caption, in px. */
internal data class MaintenancePlan(val shape: MaintenanceShape, val graphic: Rect, val captionTopLeft: Offset)

/** The hinge to lay out around: only while the device is half folded, or when its fold splits the screen. */
val FoldableState.layoutHinge: Rect?
    get() = hingeBounds.takeIf { hasFold && (posture != DevicePosture.Flat || isSeparating) }

/** The shape for an [area] (px) with a [hinge] in the area's coordinates, if any. */
internal fun maintenanceShape(area: Size, hinge: Rect?): MaintenanceShape {
    if (hinge != null) {
        val minPart = min(area.width, area.height) * MIN_HINGE_PART
        val across = hinge.width >= hinge.height
        if (across && hinge.top > minPart && hinge.bottom < area.height - minPart) return MaintenanceShape.Tabletop
        if (!across && hinge.left > minPart && hinge.right < area.width - minPart) return MaintenanceShape.Book
    }
    return if (area.width >= area.height * SIDE_BY_SIDE_ASPECT) MaintenanceShape.SideBySide else MaintenanceShape.Stacked
}

/** How wide the caption may get before it wraps. */
internal fun captionMaxWidth(shape: MaintenanceShape, area: Size, hinge: Rect?, spacing: MaintenanceSpacing): Float =
    when (shape) {
        MaintenanceShape.SideBySide -> area.width * SIDE_CAPTION_SHARE
        MaintenanceShape.Book -> {
            val right = Size(area.width - (hinge?.right ?: 0f), area.height)
            right.width - 2 * spacing.margin(right)
        }
        else -> area.width - 2 * spacing.margin(area)
    }.coerceAtLeast(0f)

/** The part of an [area] (px) the graphic is laid out in: above or left of the hinge when folded, else all of it. */
internal fun graphicPart(shape: MaintenanceShape, area: Size, hinge: Rect?): Size = when (shape) {
    MaintenanceShape.Tabletop -> Size(area.width, hinge?.top ?: (area.height / 2f))
    MaintenanceShape.Book -> Size(hinge?.left ?: (area.width / 2f), area.height)
    else -> area
}

/** The graphic's padding (px): [fraction] of the shorter side of its [part] of the window, at least [minPadding]. */
internal fun graphicPaddingPx(part: Size, minPadding: Float, fraction: Float): Float =
    max(minPadding, part.minDimension * fraction)

/** The caption's font size (sp) for a graphic slot whose shorter side is [slotDp], between [minSp] and [maxSp]. */
internal fun captionFontSize(slotDp: Float, minSp: Float, maxSp: Float): Float =
    (slotDp * CAPTION_SIZE_FRACTION).coerceIn(minSp, maxSp)

/** A [MaintenanceCaption]'s subline size (sp) under a title of [titleSp], never below [minSp]. */
internal fun sublineFontSize(titleSp: Float, minSp: Float): Float = max(minSp, titleSp * SUBLINE_SIZE_RATIO)

/**
 * Where the graphic, [aspect] times as wide as tall, and a [caption] of that size go in an [area] (px), as one centred
 * group.
 */
internal fun maintenancePlan(
    shape: MaintenanceShape,
    area: Size,
    hinge: Rect?,
    caption: Size,
    spacing: MaintenanceSpacing,
    aspect: Float = 1f,
): MaintenancePlan {
    return when (shape) {
        MaintenanceShape.Stacked -> {
            val beside = spacing.gap + caption.height
            val tall = min(area.height - 2 * spacing.margin(area), area.height * MAX_GROUP_SHARE) - beside
            val graphic = Size(area.width, tall).fit(aspect)
            val top = (area.height - (graphic.height + beside)) / 2f
            MaintenancePlan(
                shape = shape,
                graphic = Rect(Offset((area.width - graphic.width) / 2f, top), graphic),
                captionTopLeft = Offset((area.width - caption.width) / 2f, top + graphic.height + spacing.gap),
            )
        }
        MaintenanceShape.SideBySide -> {
            val margin = spacing.margin(area)
            val beside = spacing.gap + caption.width
            val wide = min(area.width - 2 * margin, area.width * MAX_GROUP_SHARE) - beside
            val graphic = Size(wide, min(area.height - 2 * margin, area.height * MAX_GROUP_SHARE)).fit(aspect)
            val left = (area.width - (graphic.width + beside)) / 2f
            MaintenancePlan(
                shape = shape,
                graphic = Rect(Offset(left, (area.height - graphic.height) / 2f), graphic),
                captionTopLeft = Offset(left + graphic.width + spacing.gap, (area.height - caption.height) / 2f),
            )
        }
        MaintenanceShape.Tabletop -> {
            val hingeTop = hinge?.top ?: (area.height / 2f)
            val hingeBottom = hinge?.bottom ?: hingeTop
            val above = Size(area.width, hingeTop)
            val margin = spacing.margin(above)
            val graphic = Size(above.width - 2 * margin, above.height - 2 * margin).fit(aspect)
            val below = (hingeBottom + area.height) / 2f
            MaintenancePlan(
                shape = shape,
                graphic = Rect(Offset((area.width - graphic.width) / 2f, (hingeTop - graphic.height) / 2f), graphic),
                captionTopLeft = Offset((area.width - caption.width) / 2f, below - caption.height / 2f),
            )
        }
        MaintenanceShape.Book -> {
            val hingeLeft = hinge?.left ?: (area.width / 2f)
            val hingeRight = hinge?.right ?: hingeLeft
            val left = Size(hingeLeft, area.height)
            val margin = spacing.margin(left)
            val graphic = Size(left.width - 2 * margin, left.height - 2 * margin).fit(aspect)
            val right = (hingeRight + area.width) / 2f
            MaintenancePlan(
                shape = shape,
                graphic = Rect(Offset((hingeLeft - graphic.width) / 2f, (area.height - graphic.height) / 2f), graphic),
                captionTopLeft = Offset(right - caption.width / 2f, (area.height - caption.height) / 2f),
            )
        }
    }
}

/** The largest size [aspect] times as wide as tall within this one; none if this one is empty. */
private fun Size.fit(aspect: Float): Size {
    val height = max(0f, min(width / aspect, height))
    return Size(height * aspect, height)
}

private enum class MaintenanceSlot { Graphic, Caption }

/**
 * A maintenance screen's layout: a [graphic] and its [caption], one group centred in the available space.
 * Portrait or near square, the graphic sits above the caption; wide, beside it. Half folded, the hinge splits them:
 * the graphic above it (tabletop) or left of it (book), the caption on the other side. The caption's text style
 * scales with the graphic, from titleMedium up to 28 sp; a [MaintenanceCaption] adds a muted subline under it.
 *
 * @param graphicPadding space kept clear inside the graphic's slot, around what it draws.
 * @param graphicPaddingFraction at least this share of the shorter side of the graphic's part of the window (the
 *   pane above or left of the hinge, when folded) is kept clear instead, if that's more than [graphicPadding].
 * @param graphicAspectRatio the graphic slot's width over its height: square by default.
 * @param hingeBounds the hinge to lay out around, in window coordinates; null for none.
 * @param graphic draws the graphic with the modifier it's given, which fills its slot, padded.
 */
@Composable
fun MaintenanceLayout(
    modifier: Modifier = Modifier,
    graphicPadding: Dp,
    graphicPaddingFraction: Float = 0f,
    graphicAspectRatio: Float = 1f,
    hingeBounds: Rect? = LocalFoldableState.current.layoutHinge,
    graphic: @Composable (Modifier) -> Unit,
    caption: @Composable () -> Unit,
) {
    // Where the layout sits in the window, to bring the hinge into its coordinates.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val smallest = MaterialTheme.typography.titleMedium
    val aspect = graphicAspectRatio.coerceAtLeast(MIN_ASPECT)

    SubcomposeLayout(modifier = modifier.onPlaced { origin = it.positionInWindow() }) { constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val area = Size(width.toFloat(), height.toFloat())
        val hinge = hingeBounds?.translate(-origin)
        val shape = maintenanceShape(area, hinge)
        val spacing = MaintenanceSpacing(minMargin = MIN_OUTER_MARGIN.toPx(), gap = CAPTION_GAP.toPx())
        val maxCaptionWidth = captionMaxWidth(shape, area, hinge, spacing).roundToInt()

        // The caption's size follows the slot's, and the slot's the caption's: size the slot for a large caption first.
        val roughCaption = Size(maxCaptionWidth.toFloat(), MAX_CAPTION_SP.sp.toPx() * 1.5f)
        val roughSlot = maintenancePlan(shape, area, hinge, roughCaption, spacing, aspect).graphic.minDimension
        val fontSize = captionFontSize(roughSlot.toDp().value, smallest.fontSize.value, MAX_CAPTION_SP).sp
        val style = smallest.copy(
            fontSize = fontSize,
            textAlign = if (shape == MaintenanceShape.SideBySide) TextAlign.Start else TextAlign.Center,
        )
        val captionPlaceable = subcompose(MaintenanceSlot.Caption) {
            Box { ProvideTextStyle(style) { caption() } }
        }.first().measure(Constraints(maxWidth = maxCaptionWidth))

        val captionSize = Size(captionPlaceable.width.toFloat(), captionPlaceable.height.toFloat())
        val plan = maintenancePlan(shape, area, hinge, captionSize, spacing, aspect)
        val padding = graphicPaddingPx(graphicPart(shape, area, hinge), graphicPadding.toPx(), graphicPaddingFraction)
        val graphicPlaceable = subcompose(MaintenanceSlot.Graphic) {
            Box { graphic(Modifier.padding(padding.toDp()).fillMaxSize()) }
        }.first().measure(Constraints.fixed(plan.graphic.width.roundToInt(), plan.graphic.height.roundToInt()))

        layout(width, height) {
            graphicPlaceable.place(plan.graphic.topLeft.round())
            captionPlaceable.place(plan.captionTopLeft.round())
        }
    }
}

/**
 * A two-line caption for [MaintenanceLayout]: a [title] in the layout's caption style and, under it, a smaller muted
 * [subline].
 */
@Composable
fun MaintenanceCaption(
    title: String,
    subline: String,
    modifier: Modifier = Modifier,
) {
    val titleStyle = LocalTextStyle.current
    val titleSp = if (titleStyle.fontSize.isSp) titleStyle.fontSize.value else MAX_CAPTION_SP
    val sublineSize = sublineFontSize(titleSp, MaterialTheme.typography.bodySmall.fontSize.value).sp
    val alignment = if (titleStyle.textAlign == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(text = title, color = MellowTheme.colors.foreground)
        Spacer(Modifier.height((titleSp * SUBLINE_GAP_RATIO).dp))
        Text(
            text = subline,
            color = MellowTheme.colors.muted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = sublineSize, textAlign = titleStyle.textAlign),
        )
    }
}
