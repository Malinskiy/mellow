package dev.mellow.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch

private const val PLAY_FILL_PATH =
    "M232.4,114.49,88.32,26.35a16,16,0,0,0-16.2-.3A15.86,15.86,0,0,0,64,40.87V215.13a15.94,15.94,0,0,0,8.12,13.81,16,16,0,0,0,16.2-.3L232.4,141.51a15.81,15.81,0,0,0,0-27Z"

private const val PAUSE_FILL_PATH =
    "M216,48V208a16,16,0,0,1-16,16H160a16,16,0,0,1-16-16V48a16,16,0,0,1,16-16h40A16,16,0,0,1,216,48ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Z"

private val CrossfadeSpec = tween<Float>(280, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))

@Composable
fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
) {
    val scope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }
    val rippleScale = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    val t by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(280, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "pp-transform",
    )
    val iconSize = buttonSize * 0.5f
    val fgColor = MellowTheme.colors.foreground
    val bgColor = MellowTheme.colors.background

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(buttonSize * 1.3f),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (rippleAlpha.value > 0f) {
                    val buttonRadius = size.minDimension / 2.6f
                    drawCircle(
                        fgColor,
                        radius = buttonRadius * 1.08f * rippleScale.value,
                        style = Stroke(width = 2.dp.toPx()),
                        alpha = rippleAlpha.value,
                    )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }
                .clip(CircleShape)
                .background(fgColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    scope.launch {
                        pressScale.snapTo(0.92f)
                        pressScale.animateTo(1f, tween(150))
                    }
                    scope.launch {
                        rippleScale.snapTo(0.95f)
                        rippleAlpha.snapTo(0.6f)
                        val rippleEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
                        launch { rippleScale.animateTo(1.25f, tween(500, easing = rippleEasing)) }
                        launch { rippleAlpha.animateTo(0f, tween(500, easing = rippleEasing)) }
                    }
                    onToggle()
                },
        ) {
            Crossfade(targetState = isPlaying, animationSpec = CrossfadeSpec, label = "pp") { playing ->
                if (playing) {
                    Canvas(
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                scaleX = 0.5f + 0.5f * t
                                scaleY = 0.5f + 0.5f * t
                                rotationZ = 90f * (1f - t)
                            },
                    ) {
                        drawSvgPath(PAUSE_FILL_PATH, bgColor)
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                scaleX = 1f - 0.5f * t
                                scaleY = 1f - 0.5f * t
                                rotationZ = -90f * t
                            },
                    ) {
                        drawSvgPath(PLAY_FILL_PATH, bgColor)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = MellowTheme.colors.foreground,
) {
    val t by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(280, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "pp-icon-transform",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(iconSize * 1.8f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Crossfade(targetState = isPlaying, animationSpec = CrossfadeSpec, label = "pp-icon") { playing ->
            if (playing) {
                Canvas(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            scaleX = 0.5f + 0.5f * t
                            scaleY = 0.5f + 0.5f * t
                            rotationZ = 90f * (1f - t)
                        },
                ) {
                    val s = size.width / 256f
                    val sw = 20f * s
                    drawLine(tint, Offset(88f * s, 48f * s), Offset(88f * s, 208f * s), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(tint, Offset(168f * s, 48f * s), Offset(168f * s, 208f * s), strokeWidth = sw, cap = StrokeCap.Round)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            scaleX = 1f - 0.5f * t
                            scaleY = 1f - 0.5f * t
                            rotationZ = -90f * t
                        },
                ) {
                    val s = size.width / 256f
                    val sw = 20f * s
                    val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val path = Path().apply {
                        moveTo(72f * s, 39f * s)
                        lineTo(216f * s, 128f * s)
                        lineTo(72f * s, 217f * s)
                        close()
                    }
                    drawPath(path, tint, style = style)
                }
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
