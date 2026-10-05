package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.mellow.core.designsystem.icon.PhosphorIcons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val download: TrackMenuDownload,
)

/** What the track menu's download entry offers. */
enum class TrackMenuDownload {
    /** Not on the device; the entry downloads it. */
    Available,

    /** Queued or downloading; the entry cancels it. */
    Downloading,

    /** On the device; the entry removes it. */
    Downloaded,

    /** Not on the device and the server can't be reached; the entry is disabled. */
    Offline,

    /** Not on the device and downloads have reached the storage limit; the entry is disabled. */
    StorageFull,
}

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
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onTrackInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
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
        TrackContextMenuContent(
            track = track,
            onDismiss = onDismiss,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onGoToAlbum = onGoToAlbum,
            onGoToArtist = onGoToArtist,
            onStartMix = onStartMix,
            onToggleFavorite = onToggleFavorite,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onRemoveDownload = onRemoveDownload,
            onTrackInfo = onTrackInfo,
        )
    }
}

/** The track menu's header and actions, the body of [TrackContextMenu]'s sheet. */
@Composable
fun TrackContextMenuContent(
    track: TrackMenuData,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onStartMix: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onTrackInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = MellowSpacing.Sp8),
    ) {
        TrackHeader(track)

        HorizontalDivider(
            color = MellowTheme.colors.border,
            modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
        )

        MenuAction(PhosphorIcons.SkipForward, "Play Next", onClick = {
            onPlayNext()
            onDismiss()
        })
        MenuAction(PhosphorIcons.Queue, "Add to Queue", onClick = {
            onAddToQueue()
            onDismiss()
        })
        MenuAction(PhosphorIcons.ListPlus, "Add to Playlist\u2026", onClick = {
            onAddToPlaylist()
            onDismiss()
        })

        HorizontalDivider(
            color = MellowTheme.colors.border,
            modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
        )

        MenuAction(PhosphorIcons.VinylRecord, "Go to Album", onClick = {
            onGoToAlbum()
            onDismiss()
        })
        MenuAction(
            PhosphorIcons.User,
            if ("," in track.artist) "Go to Artist\u2026" else "Go to Artist",
            onClick = {
                onGoToArtist()
                onDismiss()
            },
        )

        HorizontalDivider(
            color = MellowTheme.colors.border,
            modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
        )

        MenuAction(PhosphorIcons.Shuffle, "Start Mix", onClick = {
            onStartMix()
            onDismiss()
        })
        MenuAction(
            icon = if (track.isFavorite) PhosphorIcons.HeartFill else PhosphorIcons.Heart,
            label = if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
            tint = if (track.isFavorite) MellowTheme.colors.favorite else MellowTheme.colors.foreground,
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
        )
        DownloadAction(
            download = track.download,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onRemoveDownload = onRemoveDownload,
            onDismiss = onDismiss,
        )

        HorizontalDivider(
            color = MellowTheme.colors.border,
            modifier = Modifier.padding(vertical = MellowSpacing.Sp2),
        )

        MenuAction(PhosphorIcons.Info, "Track Info", onClick = {
            onTrackInfo()
            onDismiss()
        })
    }
}

@Composable
private fun DownloadAction(
    download: TrackMenuDownload,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (download) {
        TrackMenuDownload.Available -> MenuAction(
            icon = PhosphorIcons.DownloadSimple,
            label = "Download",
            onClick = {
                onDownload()
                onDismiss()
            },
            modifier = modifier,
        )
        TrackMenuDownload.Downloading -> MenuAction(
            icon = PhosphorIcons.X,
            label = "Cancel download",
            onClick = {
                onCancelDownload()
                onDismiss()
            },
            modifier = modifier,
        )
        TrackMenuDownload.Downloaded -> MenuAction(
            icon = PhosphorIcons.Trash,
            label = "Remove download",
            onClick = {
                onRemoveDownload()
                onDismiss()
            },
            modifier = modifier,
        )
        TrackMenuDownload.Offline -> MenuAction(
            icon = PhosphorIcons.DownloadSimple,
            label = "Download",
            onClick = {},
            modifier = modifier,
            enabled = false,
            supportingText = "Offline",
        )
        TrackMenuDownload.StorageFull -> MenuAction(
            icon = PhosphorIcons.DownloadSimple,
            label = "Download",
            onClick = {},
            modifier = modifier,
            enabled = false,
            supportingText = "Storage limit reached",
        )
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MellowShapes.Small)
                .background(MellowTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            MellowImage(
                model = track.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackIconSize = 22.dp,
            )
        }
        Spacer(Modifier.width(MellowSpacing.Sp3))
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
    modifier: Modifier = Modifier,
    tint: Color = MellowTheme.colors.foreground,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    val muted = MellowTheme.colors.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the label next to it says what the action is.
            contentDescription = null,
            tint = if (enabled) tint else muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(MellowSpacing.Sp4))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MellowTheme.colors.foreground else muted,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
