package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class TrackMenuData(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val artistId: String?,
    val imageUrl: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: TrackMenuData,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onStartMix: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrackInfo: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MellowTheme.colors.surfaceElevated,
        contentColor = MellowTheme.colors.foreground,
        dragHandle = {
            Spacer(
                modifier = Modifier
                    .padding(vertical = MellowSpacing.Sp3)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MellowTheme.colors.muted.copy(alpha = 0.4f), MellowShapes.Full),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MellowSpacing.Sp8),
        ) {
            TrackHeader(track)

            HorizontalDivider(
                color = MellowTheme.colors.border,
                modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
            )

            MenuAction(Icons.Filled.SkipNext, "Play Next", onClick = {
                onPlayNext()
                onDismiss()
            })
            MenuAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", onClick = {
                onAddToQueue()
                onDismiss()
            })
            MenuAction(Icons.Filled.PlaylistAdd, "Add to Playlist\u2026", onClick = {
                onAddToPlaylist()
                onDismiss()
            })

            HorizontalDivider(
                color = MellowTheme.colors.border,
                modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
            )

            MenuAction(Icons.Filled.Album, "Go to Album", onClick = {
                onGoToAlbum()
                onDismiss()
            })
            MenuAction(Icons.Filled.Person, "Go to Artist", onClick = {
                onGoToArtist()
                onDismiss()
            })

            HorizontalDivider(
                color = MellowTheme.colors.border,
                modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
            )

            MenuAction(Icons.Outlined.Shuffle, "Start Mix", onClick = {
                onStartMix()
                onDismiss()
            })
            MenuAction(
                icon = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (track.isFavorite) MellowTheme.colors.favorite else MellowTheme.colors.foreground,
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
            )

            HorizontalDivider(
                color = MellowTheme.colors.border,
                modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
            )

            MenuAction(Icons.Filled.Info, "Track Info", onClick = {
                onTrackInfo()
                onDismiss()
            })
        }
    }
}

@Composable
private fun TrackHeader(track: TrackMenuData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
    ) {
        if (track.imageUrl != null) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MellowTheme.colors.surface),
                error = ColorPainter(MellowTheme.colors.surface),
                modifier = Modifier
                    .size(48.dp)
                    .clip(MellowShapes.Small)
                    .background(MellowTheme.colors.surface),
            )
            Spacer(Modifier.width(MellowSpacing.Sp3))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MellowTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} \u00B7 ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MellowTheme.colors.foreground,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(MellowSpacing.Sp4))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MellowTheme.colors.foreground,
        )
    }
}
