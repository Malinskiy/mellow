package dev.mellow.core.designsystem.component

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

@Composable
fun IridescenceBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF6366F1),
    secondaryColor: Color? = null,
    speed: Float = 0.2f,
    animated: Boolean = true,
    edgeFade: Float = 0f,
    backgroundColor: Color = Color.Transparent,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        IridescenceAgsl(modifier, color, secondaryColor, speed, animated, edgeFade, backgroundColor)
    } else {
        IridescenceFallback(modifier, color, secondaryColor)
    }
}

@Composable
private fun IridescenceFallback(modifier: Modifier, color: Color, secondaryColor: Color? = null) {
    val c2 = secondaryColor ?: color
    val light = lerp(color, Color.White, 0.4f)
    val dark = lerp(c2, Color.Black, 0.3f)
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.sweepGradient(
                    colors = listOf(light, color, dark, c2, light),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                ),
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun IridescenceAgsl(
    modifier: Modifier,
    color: Color,
    secondaryColor: Color?,
    speed: Float,
    animated: Boolean,
    edgeFade: Float,
    backgroundColor: Color,
) {
    val shader = remember { android.graphics.RuntimeShader(IRIDESCENCE_AGSL) }
    val shaderBrush = remember { ShaderBrush(shader) }
    var time by remember { mutableFloatStateOf(0f) }

    if (animated) {
        LaunchedEffect(Unit) {
            val startNanos = System.nanoTime()
            while (true) {
                withInfiniteAnimationFrameMillis {
                    time = (System.nanoTime() - startNanos) / 1_000_000_000f
                }
            }
        }
    }

    val c2 = secondaryColor ?: color

    Canvas(modifier = modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time * speed)
        shader.setColorUniform("baseColor", color.toArgb())
        shader.setColorUniform("secondColor", c2.toArgb())
        shader.setFloatUniform("hasSecond", if (secondaryColor != null) 1f else 0f)
        shader.setFloatUniform("edgeFade", edgeFade)
        shader.setColorUniform("bgColor", backgroundColor.toArgb())
        drawRect(brush = shaderBrush)
    }
}

// Chaotic oscillator drives spatial blending between palette colors.
// Output constrained to baseColor↔secondColor. Edges lerp toward bgColor.
@Suppress("ktlint:standard:max-line-length")
private const val IRIDESCENCE_AGSL = """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 baseColor;
layout(color) uniform half4 secondColor;
uniform float hasSecond;
uniform float edgeFade;
layout(color) uniform half4 bgColor;

half4 main(float2 fragCoord) {
    float mr = min(resolution.x, resolution.y);
    float2 uv = (fragCoord * 2.0 - resolution.xy) / mr;

    float d = -time * 0.5;
    float a = 0.0;
    for (float i = 0.0; i < 8.0; i += 1.0) {
        a += cos(i - d - a * uv.x);
        d += sin(uv.y * i + a);
    }
    d += time * 0.5;

    float blend1 = 0.5 + 0.5 * cos(d * 0.5 + a * 0.3);
    float blend2 = 0.5 + 0.5 * cos(uv.x * d * 0.3 + uv.y * a * 0.3);
    float rawBlend = mix(blend1, blend2, 0.5);
    float blend = rawBlend * rawBlend * 0.6 * hasSecond;

    float3 col = mix(float3(baseColor.rgb), float3(secondColor.rgb), blend);

    float brightness = 0.85 + 0.15 * cos(a * 0.7 + d * 0.3);
    col *= brightness;

    if (edgeFade > 0.0) {
        float2 norm = fragCoord / resolution;
        float2 edgeDist = min(norm, 1.0 - norm);
        float fade = smoothstep(0.0, edgeFade, min(edgeDist.x, edgeDist.y));
        col = mix(float3(bgColor.rgb), col, fade);
    }

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""
