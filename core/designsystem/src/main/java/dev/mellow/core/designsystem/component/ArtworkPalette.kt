package dev.mellow.core.designsystem.component

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val colorCount: Int,
)

private object ArtworkPaletteCache {
    val cache = LruCache<String, ArtworkPalette>(50)
}

@Composable
fun rememberArtworkPalette(
    artworkKey: String?,
    imageUrl: String?,
): ArtworkPalette? {
    if (artworkKey == null || imageUrl == null) return null

    val cached = ArtworkPaletteCache.cache.get(artworkKey)
    var palette by remember(artworkKey) { mutableStateOf(cached) }

    if (cached != null) return cached

    val context = LocalContext.current

    LaunchedEffect(artworkKey) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(200, 200)
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect

        val extracted = withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val quantized = QuantizerCelebi.quantize(pixels, 128)

            val topByPopulation = quantized.entries
                .sortedByDescending { it.value }
                .take(10)
            Log.d("ArtworkPalette", "=== $artworkKey ===")
            Log.d("ArtworkPalette", "Bitmap: ${bitmap.width}x${bitmap.height}, pixels: ${pixels.size}")
            Log.d("ArtworkPalette", "Quantized colors: ${quantized.size}")
            topByPopulation.forEachIndexed { i, (color, pop) ->
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                Log.d("ArtworkPalette", "  #$i: rgb($r,$g,$b) hex=#${"%06X".format(color and 0xFFFFFF)} pop=$pop")
            }

            val scored = Score.score(quantized, 4, 0xFF4285F4.toInt(), false)

            scored.forEachIndexed { i, color ->
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                Log.d("ArtworkPalette", "  Score #$i: rgb($r,$g,$b) hex=#${"%06X".format(color and 0xFFFFFF)}")
            }

            if (scored.isEmpty()) return@withContext null

            val colorCount = scored.size
            val primary = Color(scored[0])
            val secondary = if (scored.size > 1) Color(scored[1]) else {
                Color(
                    red = (primary.red * 0.5f).coerceIn(0f, 1f),
                    green = (primary.green * 0.5f).coerceIn(0f, 1f),
                    blue = (primary.blue * 0.5f).coerceIn(0f, 1f),
                )
            }
            val tertiary = if (scored.size > 2) Color(scored[2]) else secondary
            val accent = if (scored.size > 3) Color(scored[3]) else tertiary

            ArtworkPalette(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                accent = accent,
                colorCount = colorCount,
            )
        } ?: return@LaunchedEffect

        ArtworkPaletteCache.cache.put(artworkKey, extracted)
        palette = extracted
    }

    return palette
}
