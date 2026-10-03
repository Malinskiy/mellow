package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.roundToInt

/**
 * Artwork with a placeholder while loading and [fallbackIcon] when there is no image or it fails to load.
 *
 * Pass `fallbackIconSize = null` to size the icon from the image's bounds instead: artwork that animates between
 * screens (shared elements) then shows the same icon at every size, without a jump when the animation ends.
 */
@Composable
fun MellowImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = PhosphorIcons.MusicNote,
    fallbackIconSize: Dp? = 32.dp,
) {
    if (model == null) {
        FallbackIcon(fallbackIcon, fallbackIconSize, modifier)
    } else {
        var isError by remember(model) { mutableStateOf(false) }
        var isLoading by remember(model) { mutableStateOf(true) }

        if (isError) {
            FallbackIcon(fallbackIcon, fallbackIconSize, modifier)
        } else {
            Box(modifier = modifier) {
                if (isLoading) {
                    PixelBlastPlaceholder(
                        modifier = Modifier.matchParentSize(),
                        color = MellowTheme.colors.foreground.copy(alpha = 0.15f),
                    )
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .memoryCacheKey(model.toString())
                        .placeholderMemoryCacheKey(model.toString())
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { isLoading = false },
                    onError = { isError = true },
                )
            }
        }
    }
}

@Composable
private fun FallbackIcon(icon: ImageVector, size: Dp?, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = MellowTheme.colors.muted,
            modifier = if (size != null) Modifier.size(size) else Modifier.proportionalIconSize(),
        )
    }
}

/**
 * A quarter of the available size, 16–56 dp. Measured in a layout modifier rather than with BoxWithConstraints:
 * during a shared element transition the layout pass sees the animated size, while subcomposition only sees the
 * final one.
 */
private fun Modifier.proportionalIconSize(): Modifier = layout { measurable, constraints ->
    val available = minOf(constraints.maxWidth, constraints.maxHeight)
    val side = if (available == Constraints.Infinity) {
        MAX_ICON.roundToPx()
    } else {
        (available * 0.25f).roundToInt().coerceIn(MIN_ICON.roundToPx(), MAX_ICON.roundToPx())
    }
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

private val MIN_ICON = 16.dp
private val MAX_ICON = 56.dp
