package dev.mellow.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val HEART_OUTLINE_PATH =
    "M128,216S28,160,28,92A52,52,0,0,1,128,72h0A52,52,0,0,1,228,92C228,160,128,216,128,216Z"

private const val HEART_FILL_PATH =
    "M240,94c0,70-103.79,126.66-108.21,129a8,8,0,0,1-7.58,0C119.79,220.66,16,164,16,94A60,60,0,0,1,128,56h0A60,60,0,0,1,240,94Z"

private val HeartColor = Color(0xFFE8564A)

@Composable
fun AnimatedHeartIcon(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val scope = rememberCoroutineScope()
    val isFirstRun = remember { mutableStateOf(true) }
    val fillScale = remember { Animatable(if (isFavorite) 1f else 0f) }
    val fillAlpha = remember { Animatable(if (isFavorite) 1f else 0f) }
    val outlineAlpha = remember { Animatable(if (isFavorite) 0f else 1f) }
    val particles = remember { List(6) { Animatable(0f) } }
    val mutedColor = MellowTheme.colors.muted

    LaunchedEffect(isFavorite) {
        if (isFirstRun.value) {
            isFirstRun.value = false
            return@LaunchedEffect
        }
        if (isFavorite) {
            launch { outlineAlpha.animateTo(0f, tween(200)) }
            launch { fillAlpha.animateTo(1f, tween(150)) }
            launch {
                fillScale.snapTo(0.2f)
                fillScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
            }
            particles.forEach { p ->
                launch {
                    p.snapTo(0f)
                    p.animateTo(1f, tween(450))
                }
            }
        } else {
            launch { fillScale.animateTo(0.4f, tween(250)) }
            launch { fillAlpha.animateTo(0f, tween(250)) }
            launch { outlineAlpha.animateTo(1f, tween(250)) }
            particles.forEach { p -> launch { p.snapTo(0f) } }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(iconSize * 1.8f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() },
    ) {
        Canvas(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { alpha = outlineAlpha.value },
        ) {
            drawSvgPath(
                HEART_OUTLINE_PATH,
                mutedColor,
                style = Stroke(
                    width = size.width * 0.08f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        Canvas(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = fillAlpha.value
                    scaleX = fillScale.value
                    scaleY = fillScale.value
                },
        ) {
            drawSvgPath(HEART_FILL_PATH, HeartColor)
        }
        Canvas(modifier = Modifier.size(iconSize * 1.8f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = size.width * 0.45f
            val dotRadius = 4.dp.toPx().coerceAtMost(size.width * 0.06f)
            for (i in 0 until 6) {
                val p = particles.getOrElse(i) { return@Canvas }.value
                if (p <= 0f) continue
                val angle = (i * 60.0) * (Math.PI / 180.0)
                val dist = maxRadius * p
                val px = cx + (cos(angle) * dist).toFloat()
                val py = cy + (sin(angle) * dist).toFloat()
                val pAlpha = (1f - p).coerceIn(0f, 1f)
                drawCircle(HeartColor.copy(alpha = pAlpha), radius = dotRadius, center = Offset(px, py))
            }
        }
    }
}

private fun DrawScope.drawSvgPath(
    pathData: String,
    color: Color,
    style: DrawStyle? = null,
) {
    val sx = size.width / 256f
    val sy = size.height / 256f
    val path = Path()
    PathParser().parsePathString(pathData).toPath(path)
    path.transform(Matrix().apply { scale(sx, sy) })
    if (style != null) {
        drawPath(path, color, style = style)
    } else {
        drawPath(path, color)
    }
}
