package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    imageUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MellowSpacing.MiniPlayerHeight)
            .shadow(elevation = 8.dp, shape = MellowShapes.Large)
            .clip(MellowShapes.Large)
            .background(MellowTheme.colors.surfaceElevated)
            .border(1.dp, MellowTheme.colors.border, MellowShapes.Large)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MellowShapes.Small)
                    .background(MellowTheme.colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.MusicNote,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MellowSpacing.Sp3),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MellowTheme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedPlayPauseIcon(
                isPlaying = isPlaying,
                onToggle = onPlayPauseClick,
                iconSize = 22.dp,
                tint = MellowTheme.colors.foreground,
            )

            IconButton(onClick = onNextClick) {
                Icon(
                    imageVector = PhosphorIcons.SkipForward,
                    contentDescription = "Next",
                    tint = MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp3)
                .height(2.dp)
                .clip(MellowShapes.Full)
                .background(MellowTheme.colors.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MellowTheme.colors.accent),
            )
        }
    }
}
