package dev.mellow.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.sqrt

enum class BackgroundMode {
    Auto,
    Blur,
    Iridescence,
    Grainient,
}

private fun colorDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat() * 255f
}

@Composable
fun ArtworkBackground(
    artworkKey: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    mode: BackgroundMode = BackgroundMode.Auto,
    blurRadius: Dp = 120.dp,
    imageAlpha: Float = 0.35f,
    edgeFade: Float = 0f,
    overlayColors: List<Pair<Float, Float>> = listOf(0.5f to 0f, 0.85f to 1f),
) {
    val supportsShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val needsPalette = when (mode) {
        BackgroundMode.Blur -> false
        BackgroundMode.Auto -> supportsShader
        BackgroundMode.Iridescence -> supportsShader
        BackgroundMode.Grainient -> supportsShader
    }
    val palette = if (needsPalette) rememberArtworkPalette(artworkKey, imageUrl) else null
    val isBatterySaver = LocalBatterySaverActive.current
    val background = MellowTheme.colors.background

    val resolvedMode = when (mode) {
        BackgroundMode.Auto -> if (supportsShader) BackgroundMode.Grainient else BackgroundMode.Blur
        BackgroundMode.Iridescence -> if (supportsShader) BackgroundMode.Iridescence else BackgroundMode.Blur
        BackgroundMode.Grainient -> if (supportsShader) BackgroundMode.Grainient else BackgroundMode.Blur
        BackgroundMode.Blur -> BackgroundMode.Blur
    }

    Box(modifier = modifier) {
        when {
            resolvedMode == BackgroundMode.Grainient && palette != null -> {
                val useIridescenceFallback = colorDistance(palette.primary, palette.secondary) < 50f
                if (useIridescenceFallback) {
                    IridescenceBackground(
                        color = palette.primary,
                        secondaryColor = palette.secondary,
                        animated = !isBatterySaver,
                        speed = 0.2f,
                        edgeFade = edgeFade,
                        backgroundColor = background,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = imageAlpha * 1.5f },
                    )
                } else {
                    GrainientBackground(
                        baseColor = palette.primary,
                        grainAmount = 0f,
                        animated = !isBatterySaver,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = imageAlpha * 1.5f },
                    )
                }
            }
            resolvedMode == BackgroundMode.Iridescence && palette != null -> {
                IridescenceBackground(
                    color = palette.primary,
                    secondaryColor = palette.secondary,
                    animated = !isBatterySaver,
                    speed = 0.2f,
                    edgeFade = edgeFade,
                    backgroundColor = background,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageAlpha * 1.5f },
                )
            }
            resolvedMode == BackgroundMode.Blur && imageUrl != null -> {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius)
                        .graphicsLayer { alpha = imageAlpha },
                )
            }
        }

        if (overlayColors.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            *overlayColors.map { (alpha, fraction) ->
                                fraction to background.copy(alpha = alpha)
                            }.toTypedArray(),
                        ),
                    ),
            )
        }
    }
}
