package dev.mellow.core.designsystem.component.maintenance

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

private const val CYCLE_MILLIS = 7000
private const val ROTATION_MILLIS = 3000
private const val PULSE_MILLIS = 2000
private const val NUM_GROOVES = 40

/** Tonearm pivot relative to the record centre, in record radii. */
private const val PIVOT_X = 1.1f
private const val PIVOT_Y = -0.8f

/** Record radius at which the tonearm is drawn at its nominal thickness. */
private val NOMINAL_RADIUS = 140.dp
private val PIVOT_CAP = 16.dp

/** Width / height of the record + tonearm drawing: (1 + PIVOT_X) radii plus the pivot cap, over 2 radii. */
private const val RECORD_ASPECT = 1.11f

/** Clear space around the record: this share of the shorter side of its part of the window, at least [MIN_PADDING]. */
private const val PADDING_FRACTION = 0.10f
private val MIN_PADDING = 24.dp

private const val TITLE = "Re-pressing your library\u2026"
private const val SUBLINE = "To keep everything running smoothly"

/**
 * A record being pressed while the library is migrated, with its caption, laid out by [MaintenanceLayout] (around
 * the hinge of a half-folded device, from LocalFoldableState). The record keeps generous padding on every side, so it
 * never nears an edge. A preview for now: the startup screen is [DatabaseMaintenanceScreen].
 *
 * @param fixedTimeFraction freezes the 7 s cycle at this fraction (0..1), for screenshots.
 */
@Composable
fun DatabaseMaintenanceRepressing(
    modifier: Modifier = Modifier,
    fixedTimeFraction: Float? = null,
) {
    val context = LocalContext.current
    val isBatterySaver = LocalBatterySaverActive.current ||
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    val infiniteTransition = rememberInfiniteTransition(label = "repressing")

    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isBatterySaver) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(ROTATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "recordRotation",
    )

    val animatedCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isBatterySaver) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycleProgress",
    )

    val animatedPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val frozen = fixedTimeFraction?.takeUnless { isBatterySaver }
    val rotation = frozen?.let { (it * CYCLE_MILLIS / ROTATION_MILLIS * 360f) % 360f } ?: animatedRotation
    val cycleProgress = frozen ?: animatedCycle
    val pulseAlpha = fixedTimeFraction?.let { pulseAt(it * CYCLE_MILLIS) } ?: animatedPulse

    val armLoweredAmount = if (isBatterySaver) 1f else when {
        cycleProgress < 0.05f -> {
            val t = cycleProgress / 0.05f
            1f - cos(t * Math.PI.toFloat() * 2f) * exp(-t * 3f)
        }
        cycleProgress < 0.9f -> 1f
        cycleProgress < 0.95f -> {
            val t = (cycleProgress - 0.9f) / 0.05f
            1f - t
        }
        else -> 0f
    }

    val grooveFillProgress = if (isBatterySaver) {
        0.5f
    } else if (cycleProgress < 0.9f) {
        (cycleProgress / 0.85f).coerceAtMost(1f)
    } else {
        1f - (cycleProgress - 0.9f) / 0.1f
    }

    Box(modifier = modifier.fillMaxSize().background(MellowTheme.colors.background)) {
        MaintenanceLayout(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            graphicPadding = MIN_PADDING,
            graphicPaddingFraction = PADDING_FRACTION,
            graphicAspectRatio = RECORD_ASPECT,
            graphic = { graphicModifier ->
                RecordAndTonearm(
                    modifier = graphicModifier,
                    rotation = rotation,
                    grooveFillProgress = grooveFillProgress,
                    armLoweredAmount = armLoweredAmount,
                    pulseAlpha = pulseAlpha,
                    isBatterySaver = isBatterySaver,
                )
            },
            caption = { MaintenanceCaption(title = TITLE, subline = SUBLINE) },
        )
    }
}

/** The record and its tonearm, scaled to fit the slot with the pair centred in it. */
@Composable
private fun RecordAndTonearm(
    modifier: Modifier,
    rotation: Float,
    grooveFillProgress: Float,
    armLoweredAmount: Float,
    pulseAlpha: Float,
    isBatterySaver: Boolean,
) {
    val bgColor = MellowTheme.colors.background
    val accentStrong = MellowTheme.colors.accentStrong

    Canvas(modifier = modifier) {
        val recordRadius = fitRecordRadius(size.width, size.height, this)
        if (recordRadius <= 0f) return@Canvas
        val scale = (recordRadius / NOMINAL_RADIUS.toPx()).coerceIn(0.6f, 1.5f)
        val pivotCap = PIVOT_CAP.toPx() * scale
        // Centre the record + tonearm pair, not the record alone, so the pivot never leaves the slot.
        val groupWidth = recordRadius * (1f + PIVOT_X) + pivotCap
        val center = Offset((size.width - groupWidth) / 2f + recordRadius, size.height / 2f)
        val labelRadius = recordRadius * 0.35f
        val holeRadius = recordRadius * 0.03f

        drawCircle(
            color = Color(0xFF0F0F0F),
            radius = recordRadius,
            center = center,
        )

        // Grooves are pressed from the rim inwards, as a record plays: groove i (0 = outermost) appears once the
        // fill reaches it, and the stylus sits on the groove being pressed.
        val grooveSpacing = (recordRadius - labelRadius) / NUM_GROOVES
        for (i in 0 until NUM_GROOVES) {
            val grooveRel = i.toFloat() / NUM_GROOVES
            if (grooveRel <= grooveFillProgress) {
                val r = recordRadius - (i + 0.5f) * grooveSpacing
                val baseAlpha = ((grooveFillProgress - grooveRel) / 0.05f).coerceAtMost(1f)
                val alpha = if (isBatterySaver) baseAlpha * pulseAlpha else baseAlpha
                val lightness = 0.15f + (i % 3) * 0.03f
                drawCircle(
                    color = Color.White.copy(alpha = alpha * lightness),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        if (!isBatterySaver) {
            withTransform({
                rotate(rotation, center)
            }) {
                val sheenBrush = Brush.sweepGradient(
                    0.0f to Color.White.copy(alpha = 0f),
                    0.1f to Color.White.copy(alpha = 0.05f),
                    0.15f to Color.White.copy(alpha = 0f),
                    0.4f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = 0.05f),
                    0.6f to Color.White.copy(alpha = 0f),
                    1.0f to Color.White.copy(alpha = 0f),
                    center = center,
                )
                drawCircle(
                    brush = sheenBrush,
                    radius = recordRadius,
                    center = center,
                )
            }
        }

        drawCircle(
            color = MellowPalette.Stone700,
            radius = labelRadius,
            center = center,
        )
        drawCircle(
            color = MellowPalette.Stone500,
            radius = labelRadius * 0.8f,
            center = center,
            style = Stroke(width = 2.dp.toPx() * scale),
        )
        drawCircle(
            color = bgColor,
            radius = holeRadius,
            center = center,
        )

        // Tonearm: the stylus is where the circle of the current groove meets the circle the arm sweeps around its
        // pivot, so it always rides the groove being pressed. Lifted, it rests just off the rim.
        val armBase = center + Offset(recordRadius * PIVOT_X, recordRadius * PIVOT_Y)
        val armLength = recordRadius * 1.15f
        val playRadius = recordRadius - (grooveFillProgress.coerceIn(0f, 1f) * NUM_GROOVES)
            .coerceAtMost(NUM_GROOVES - 0.5f) * grooveSpacing - grooveSpacing * 0.5f
        val restRadius = recordRadius * 1.12f
        val lowered = armLoweredAmount.coerceIn(0f, 1f)
        val tipRadius = restRadius + (playRadius - restRadius) * lowered
        val tip = stylusPosition(center, armBase, armLength, tipRadius)
        val lift = 6.dp.toPx() * scale * (1f - lowered)
        val armWidth = 6.dp.toPx() * scale

        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = armBase + Offset(lift, lift),
            end = tip + Offset(lift, lift),
            strokeWidth = armWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MellowPalette.Stone300,
            start = armBase,
            end = tip,
            strokeWidth = armWidth,
            cap = StrokeCap.Round,
        )
        // Headshell: a short wider piece along the arm, ending at the stylus.
        val dir = (tip - armBase) / (tip - armBase).getDistance()
        drawLine(
            color = MellowPalette.Stone200,
            start = tip - dir * (recordRadius * 0.16f),
            end = tip,
            strokeWidth = 10.dp.toPx() * scale,
            cap = StrokeCap.Round,
        )
        drawCircle(color = accentStrong, radius = 3.dp.toPx() * scale, center = tip)
        drawCircle(color = MellowPalette.Stone200, radius = pivotCap, center = armBase)
        drawCircle(color = MellowPalette.Stone800, radius = pivotCap / 2f, center = armBase)
    }
}

/** Largest record radius whose record + tonearm fits a [width] x [height] slot (the layout supplies the padding). */
private fun fitRecordRadius(width: Float, height: Float, density: Density): Float {
    val nominal = with(density) { NOMINAL_RADIUS.toPx() }
    val cap = with(density) { PIVOT_CAP.toPx() }
    var radius = min(height / 2f, width / (1f + PIVOT_X + cap / nominal))
    val scaledCap = cap * (radius / nominal).coerceIn(0.6f, 1.5f)
    if (radius * (1f + PIVOT_X) + scaledCap > width) radius = (width - scaledCap) / (1f + PIVOT_X)
    return radius.coerceAtLeast(0f)
}

private fun pulseAt(millis: Float): Float {
    val phase = (millis / PULSE_MILLIS) % 2f
    val t = if (phase < 1f) phase else 2f - phase
    return 0.4f + 0.4f * t
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun DatabaseMaintenanceRepressingPreview() {
    MellowTheme(darkTheme = true) {
        DatabaseMaintenanceRepressing(fixedTimeFraction = 0.45f)
    }
}

/**
 * Where the stylus is when it touches the circle of [radius] around [center], on an arm of [armLength] pivoting at
 * [pivot]: the intersection of the two circles on the record's pivot side.
 */
private fun stylusPosition(center: Offset, pivot: Offset, armLength: Float, radius: Float): Offset {
    val toPivot = pivot - center
    val d = toPivot.getDistance()
    val u = toPivot / d
    val a = (d * d - armLength * armLength + radius * radius) / (2f * d)
    val h = sqrt((radius * radius - a * a).coerceAtLeast(0f))
    // Perpendicular to the centre-pivot line, turned towards the bottom of the screen.
    val perp = Offset(-u.y, u.x)
    return center + u * a + perp * h
}
