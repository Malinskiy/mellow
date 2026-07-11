package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: String? = null,
    imageUrl: String? = null,
    isFavorite: Boolean = false,
    isPlaying: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (isPlaying) MellowTheme.colors.accentStrong else MellowTheme.colors.foreground

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        ) {
            if (trackNumber != null) {
                Text(
                    text = trackNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) MellowTheme.colors.accentStrong else MellowTheme.colors.muted,
                    modifier = Modifier.width(24.dp),
                    maxLines = 1,
                )
            }

            if (imageUrl != null) {
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
                Box(modifier = Modifier.width(MellowSpacing.Sp3))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MellowTheme.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp2),
            )

            if (trailingContent != null) {
                trailingContent()
            }

            if (onFavoriteClick != null) {
                AnimatedHeartIcon(
                    isFavorite = isFavorite,
                    onToggle = onFavoriteClick,
                    iconSize = 16.dp,
                )
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = PhosphorIcons.DotsThreeVertical,
                        contentDescription = "More options",
                        tint = MellowTheme.colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(color = MellowTheme.colors.border, thickness = 1.dp)
        }
    }
}
