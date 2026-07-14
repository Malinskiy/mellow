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
fun GrainientBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF6366F1),
    speed: Float = 1f,
    grainAmount: Float = 0.15f,
    animated: Boolean = true,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrainientAgsl(modifier, baseColor, speed, grainAmount, animated)
    } else {
        GrainientFallback(modifier, baseColor)
    }
}

@Composable
private fun GrainientFallback(modifier: Modifier, baseColor: Color) {
    val light = lerp(baseColor, Color.White, 0.35f)
    val dark = lerp(baseColor, Color.Black, 0.45f)
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(light, baseColor, dark),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GrainientAgsl(
    modifier: Modifier,
    baseColor: Color,
    speed: Float,
    grainAmount: Float,
    animated: Boolean,
) {
    val shader = remember { android.graphics.RuntimeShader(GRAINIENT_AGSL) }
    val shaderBrush = remember { ShaderBrush(shader) }
    var time by remember { mutableFloatStateOf(0f) }

    if (animated) {
        LaunchedEffect(Unit) {
            val randomOffsetNanos = (Math.random() * 60_000_000_000).toLong()
            val startNanos = System.nanoTime() - randomOffsetNanos
            while (true) {
                withInfiniteAnimationFrameMillis {
                    time = (System.nanoTime() - startNanos) / 1_000_000_000f
                }
            }
        }
    }

    val color1 = lerp(baseColor, Color.White, 0.35f)
    val color3 = lerp(baseColor, Color.Black, 0.45f)

    Canvas(modifier = modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time * speed)
        shader.setFloatUniform("grainAmount", grainAmount)
        shader.setColorUniform("color1", color1.toArgb())
        shader.setColorUniform("color2", baseColor.toArgb())
        shader.setColorUniform("color3", color3.toArgb())
        drawRect(brush = shaderBrush)
    }
}

// Noise-driven tri-color gradient with sine warp and film grain.
// Ported from reactbits.dev/backgrounds/grainient GLSL.
@Suppress("ktlint:standard:max-line-length")
private const val GRAINIENT_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float grainAmount;
layout(color) uniform half4 color1;
layout(color) uniform half4 color2;
layout(color) uniform half4 color3;

float2 hash(float2 p) {
    p = float2(dot(p, float2(2127.1, 81.17)), dot(p, float2(1269.5, 283.37)));
    return fract(sin(p) * 43758.5453);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float n = mix(
        mix(dot(-1.0 + 2.0 * hash(i), f),
            dot(-1.0 + 2.0 * hash(i + float2(1.0, 0.0)), f - float2(1.0, 0.0)), u.x),
        mix(dot(-1.0 + 2.0 * hash(i + float2(0.0, 1.0)), f - float2(0.0, 1.0)),
            dot(-1.0 + 2.0 * hash(i + float2(1.0, 1.0)), f - float2(1.0, 1.0)), u.x),
        u.y);
    return 0.5 + 0.5 * n;
}

mat2 Rot(float a) { float s = sin(a); float c = cos(a); return mat2(c, -s, s, c); }

half4 main(float2 fragCoord) {
    float t = time;
    float2 uv = fragCoord / resolution;
    float ratio = resolution.x / resolution.y;
    float2 tuv = uv - 0.5;

    float degree = noise(float2(t * 0.1, tuv.x * tuv.y));
    tuv.y *= 1.0 / ratio;
    tuv *= Rot(radians((degree - 0.5) * 720.0 + 180.0));
    tuv.y *= ratio;

    float freq = 5.0;
    float amp = 30.0;
    float wt = t * 0.2;
    tuv.x += sin(tuv.y * freq + wt) / amp;
    tuv.y += sin(tuv.x * freq * 1.5 + wt) / (amp * 0.5);

    float3 c1 = float3(color1.rgb);
    float3 c2 = float3(color2.rgb);
    float3 c3 = float3(color3.rgb);
    float blendX = tuv.x;
    float3 layer1 = mix(c3, c2, smoothstep(-0.8, 0.8, blendX));
    float3 layer2 = mix(c2, c1, smoothstep(-0.8, 0.8, blendX));
    float3 col = mix(layer1, layer2, smoothstep(0.5, -0.3, tuv.y));

    float2 grainUv = uv * 200.0 + float2(t * 0.05, 0.0);
    float grain = fract(sin(dot(grainUv, float2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) * grainAmount;

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""
