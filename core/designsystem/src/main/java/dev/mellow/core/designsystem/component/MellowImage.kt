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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MellowImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = PhosphorIcons.MusicNote,
    fallbackIconSize: Dp = 32.dp,
) {
    if (model == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(fallbackIconSize),
            )
        }
    } else {
        var isError by remember(model) { mutableStateOf(false) }
        var isLoading by remember(model) { mutableStateOf(true) }

        if (isError) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(fallbackIconSize),
                )
            }
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
