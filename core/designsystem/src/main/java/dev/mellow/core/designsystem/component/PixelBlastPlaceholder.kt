package dev.mellow.core.designsystem.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun PixelBlastPlaceholder(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    patternDensity: Float = 0.7f,
    pixelSizeJitter: Float = 1.15f,
    speed: Float = 1f,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PixelBlastAgsl(modifier, color, patternDensity, pixelSizeJitter, speed)
    } else {
        Shimmer(modifier)
    }
}

@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )

    Box(
        modifier = modifier.drawBehind {
            val bandWidth = size.width * 0.4f
            val x = progress * size.width

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x + bandWidth, size.height),
                ),
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PixelBlastAgsl(
    modifier: Modifier,
    color: Color,
    patternDensity: Float,
    pixelSizeJitter: Float,
    speed: Float,
) {
    val shader = remember { android.graphics.RuntimeShader(PIXEL_BLAST_AGSL) }
    val shaderBrush = remember { ShaderBrush(shader) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val randomOffsetNanos = (Math.random() * 60_000_000_000).toLong()
        val startNanos = System.nanoTime() - randomOffsetNanos
        while (true) {
            withInfiniteAnimationFrameMillis {
                time = (System.nanoTime() - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time * speed)
        shader.setFloatUniform("cellSize", 8.dp.toPx())
        shader.setFloatUniform("density", patternDensity)
        shader.setFloatUniform("sizeJitter", pixelSizeJitter)
        shader.setColorUniform("pixelColor", color.toArgb())
        drawRect(brush = shaderBrush)
    }
}

// FBM noise + Bayer dithering + circle SDF — runs entirely on GPU.
// AGSL syntax: half4 main(float2 fragCoord), top-left origin, float2/half4 types.
@Suppress("ktlint:standard:max-line-length")
private const val PIXEL_BLAST_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float cellSize;
uniform float density;
uniform float sizeJitter;
layout(color) uniform half4 pixelColor;

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
        mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(float2 p, float t) {
    float v = 0.0;
    float a = 1.0;
    float totalA = 0.0;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p + float2(t * 1.5, t * 1.05));
        p *= 1.25;
        totalA += a;
    }
    return v / totalA;
}

float bayer2(float2 a) { a = floor(a); return fract(a.x / 2.0 + a.y * a.y * 0.75); }
float bayer4(float2 a) { return bayer2(a * 0.5) * 0.25 + bayer2(a); }
float bayer8(float2 a) { return bayer4(a * 0.5) * 0.25 + bayer2(a); }

half4 main(float2 fragCoord) {
    float2 cellUV = fragCoord / cellSize;
    float2 cellId = floor(cellUV);
    float2 localUV = fract(cellUV) - 0.5;

    float2 gridSize = ceil(resolution / cellSize);
    float2 noiseSample = cellId / gridSize * 2.5;

    float phase = hash21(cellId) * 6.28;
    float localT = time + sin(phase + time * 0.3) * 0.4;

    float n = fbm(noiseSample, localT);
    float feed = n - 0.5 + (density - 0.5) * 0.6;

    float bayer = bayer8(cellId) - 0.5;
    float alpha = smoothstep(0.35, 0.65, feed + bayer);

    float h = hash21(cellId * float2(127.0, 311.0));
    float jScale = 1.0 + (h - 0.5) * (sizeJitter - 1.0) * 2.0;
    float radius = 0.38 * clamp(jScale, 0.5, 1.4);
    float circle = smoothstep(radius + 0.015, radius - 0.015, length(localUV));

    return half4(pixelColor.rgb, pixelColor.a * alpha * circle);
}
"""
