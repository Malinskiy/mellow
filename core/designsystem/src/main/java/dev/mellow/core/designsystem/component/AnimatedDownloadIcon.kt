package dev.mellow.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlinx.coroutines.launch

enum class DownloadIconState { Idle, Downloading, Done }

private val DoneGreen = Color(0xFF6EBB8A)

@Composable
fun AnimatedSongDownloadIcon(
    state: DownloadIconState,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val cloudAlpha = remember { Animatable(1f) }
    val cloudScale = remember { Animatable(1f) }
    val ringAlpha = remember { Animatable(0f) }
    val checkScale = remember { Animatable(0f) }
    val checkAlpha = remember { Animatable(0f) }

    LaunchedEffect(state) {
        when (state) {
            DownloadIconState.Idle -> {
                launch { cloudAlpha.animateTo(1f, tween(200)) }
                launch { cloudScale.animateTo(1f, tween(200)) }
                launch { ringAlpha.animateTo(0f, tween(200)) }
                launch { checkAlpha.animateTo(0f, tween(200)) }
                launch { checkScale.snapTo(0f) }
            }
            DownloadIconState.Downloading -> {
                launch { cloudAlpha.animateTo(0f, tween(200)) }
                launch { cloudScale.animateTo(0.5f, tween(200)) }
                launch { ringAlpha.animateTo(1f, tween(300)) }
                launch { checkAlpha.animateTo(0f, tween(100)) }
            }
            DownloadIconState.Done -> {
                launch { cloudAlpha.animateTo(0f, tween(200)) }
                launch { cloudScale.animateTo(0.3f, tween(200)) }
                launch { ringAlpha.animateTo(0f, tween(200)) }
                launch { checkAlpha.animateTo(1f, tween(150)) }
                launch {
                    checkScale.snapTo(0.2f)
                    checkScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
                }
            }
        }
    }

    val mutedColor = MellowTheme.colors.muted
    val trackColor = MellowPalette.Stone800.copy(alpha = 0.2f)
    val progressColor = MellowPalette.Stone300
    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "song-dl-progress",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "song-dl-pulse")
    val pulseRaw by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "song-dl-pulse-alpha",
    )
    val pulseAlpha = if (state == DownloadIconState.Downloading) pulseRaw else 1f

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Icon(
            PhosphorIcons.CloudArrowDown,
            "Download",
            tint = mutedColor,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    alpha = cloudAlpha.value
                    scaleX = cloudScale.value
                    scaleY = cloudScale.value
                },
        )
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ringAlpha.value },
        ) {
            val w = this.size.width
            val h = this.size.height
            val r = 13f * (w / 32f)
            val sw = 2.5f * (w / 32f)
            drawCircle(trackColor, radius = r, style = Stroke(sw))
            drawArc(
                progressColor.copy(alpha = pulseAlpha),
                startAngle = -90f,
                sweepAngle = 360f * smoothProgress,
                useCenter = false,
                topLeft = Offset(w / 2f - r, h / 2f - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Icon(
            PhosphorIcons.CheckCircleFill,
            "Done",
            tint = DoneGreen,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    alpha = checkAlpha.value
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                },
        )
    }
}

@Composable
fun AnimatedAlbumDownloadIndicator(
    state: DownloadIconState,
    progress: Float,
    tracksDone: Int,
    totalTracks: Int,
    modifier: Modifier = Modifier,
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (state == DownloadIconState.Idle) 1f else 0f,
        animationSpec = tween(200),
        label = "icon-alpha",
    )
    val counterAlpha by animateFloatAsState(
        targetValue = if (state == DownloadIconState.Downloading) 1f else 0f,
        animationSpec = tween(200),
        label = "counter-alpha",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (state == DownloadIconState.Downloading) 1f else 0f,
        animationSpec = tween(250),
        label = "ring-alpha",
    )
    val checkScale = remember { Animatable(0f) }
    val checkAlpha = remember { Animatable(0f) }

    LaunchedEffect(state) {
        if (state == DownloadIconState.Done) {
            launch { checkAlpha.animateTo(1f, tween(150)) }
            launch {
                checkScale.snapTo(0.2f)
                checkScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
            }
        } else {
            launch { checkAlpha.snapTo(0f) }
            launch { checkScale.snapTo(0f) }
        }
    }

    val ringTrackColor = MellowPalette.Stone800
    val ringFillColor = when (state) {
        DownloadIconState.Done -> DoneGreen
        else -> MellowPalette.Stone300
    }
    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "album-dl-progress",
    )
    val albumInfiniteTransition = rememberInfiniteTransition(label = "album-dl-pulse")
    val albumPulseRaw by albumInfiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "album-dl-pulse-alpha",
    )
    val albumPulseAlpha = if (state == DownloadIconState.Downloading) albumPulseRaw else 1f

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ringAlpha },
        ) {
            val w = size.width
            val ringR = w * 0.42f
            val ringSw = w * 0.06f
            drawCircle(ringTrackColor, radius = ringR, style = Stroke(ringSw))
            val sweep = if (state == DownloadIconState.Done) 360f else 360f * smoothProgress
            drawArc(
                ringFillColor.copy(alpha = albumPulseAlpha),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(w / 2f - ringR, w / 2f - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(ringSw, cap = StrokeCap.Round),
            )
        }
        Icon(
            PhosphorIcons.DownloadSimple,
            "Download",
            tint = MellowTheme.colors.muted,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = iconAlpha },
        )
        Text(
            "$tracksDone/$totalTracks",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MellowTheme.colors.foreground,
            modifier = Modifier.graphicsLayer { alpha = counterAlpha },
        )
        Icon(
            PhosphorIcons.CheckCircleFill,
            "Done",
            tint = DoneGreen,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    alpha = checkAlpha.value
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                },
        )
    }
}
