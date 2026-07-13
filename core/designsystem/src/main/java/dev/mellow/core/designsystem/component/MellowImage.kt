package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
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
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            fallbackIcon,
                            contentDescription = null,
                            tint = MellowTheme.colors.muted,
                            modifier = Modifier.size(fallbackIconSize),
                        )
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}
