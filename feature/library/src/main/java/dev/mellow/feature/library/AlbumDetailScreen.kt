package dev.mellow.feature.library

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.LocalNavAnimatedVisibilityScope
import dev.mellow.core.designsystem.component.LocalSharedTransitionScope
import dev.mellow.core.designsystem.component.AnimatedAlbumDownloadIndicator
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.AnimatedSongDownloadIcon
import dev.mellow.core.designsystem.component.DownloadIconState
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent

import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.model.AlbumDownloadState

enum class TrackDownloadIndicator {
    NONE,
    DOWNLOADED,
    DOWNLOADING,
    NOT_DOWNLOADED,
}

data class AlbumDetailTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val duration: String,
    val trackNumber: Int?,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val downloadIndicator: TrackDownloadIndicator = TrackDownloadIndicator.NONE,
)

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    albumId: String = "",
    sharedElementSource: String = "library",
    albumName: String = "",
    artistName: String = "",
    albumImageUrl: String? = null,
    year: Int? = null,
    expectedTrackCount: Int = 0,
    tracks: List<AlbumDetailTrack> = emptyList(),
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onTrackFavoriteClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    downloadStatus: AlbumDownloadState.Status = AlbumDownloadState.Status.NONE,
    downloadProgress: Float = 0f,
    downloadedCount: Int = 0,
    totalDownloadCount: Int = 0,
    downloadInfoText: String? = null,
    onDownloadClick: () -> Unit = {},
    onRemoveDownloadsClick: () -> Unit = {},
    isOffline: Boolean = false,
) {
    val tracksLoading = tracks.isEmpty() && (isSyncing || expectedTrackCount > 0)
    val displayTrackCount = if (tracks.isNotEmpty()) tracks.size else expectedTrackCount
    val showDownloadIndicators = downloadStatus != AlbumDownloadState.Status.NONE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        when {
            isLoading -> LoadingContent(message = "Loading album…")
            error != null -> ErrorContent(message = error, onRetry = onRetry)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + MellowSpacing.Sp16),
                ) {
                    item { AlbumDetailTopBar(onBack) }
                    item {
                        AlbumHero(
                            albumId = albumId,
                            sharedElementSource = sharedElementSource,
                            albumName = albumName,
                            artistName = artistName,
                            imageUrl = albumImageUrl,
                            year = year,
                            trackCount = displayTrackCount,
                            totalDuration = formatTotalDuration(tracks),
                            isFavorite = isFavorite,
                            onPlayAll = onPlayAll,
                            onShuffle = onShuffle,
                            onFavoriteClick = onFavoriteClick,
                            downloadStatus = downloadStatus,
                            downloadProgress = downloadProgress,
                            downloadedCount = downloadedCount,
                            totalDownloadCount = totalDownloadCount,
                            downloadInfoText = downloadInfoText,
                            onDownloadClick = onDownloadClick,
                            onRemoveDownloadsClick = onRemoveDownloadsClick,
                        )
                    }
                    if (tracksLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MellowSpacing.Sp8),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MellowTheme.colors.foreground,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.height(MellowSpacing.Sp3))
                                    Text(
                                        "Loading tracks…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MellowTheme.colors.muted,
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                            val isNotDownloadedOffline = isOffline &&
                                showDownloadIndicators &&
                                track.downloadIndicator != TrackDownloadIndicator.DOWNLOADED
                            TrackRow(
                                title = track.title,
                                subtitle = "",
                                duration = track.duration,
                                trackNumber = if (track.isPlaying) null else "${track.trackNumber ?: (index + 1)}",
                                isPlaying = track.isPlaying,
                                isFavorite = track.isFavorite,
                                onFavoriteClick = { onTrackFavoriteClick(track.id) },
                                onMenuClick = { onTrackMenuClick(track.id) },
                                onClick = {
                                    if (!isNotDownloadedOffline) onTrackClick(track.id)
                                },
                                showDivider = index < tracks.lastIndex,
                                trailingContent = if (showDownloadIndicators) {
                                    {
                                        AnimatedSongDownloadIcon(
                                            state = when (track.downloadIndicator) {
                                                TrackDownloadIndicator.DOWNLOADED -> DownloadIconState.Done
                                                TrackDownloadIndicator.DOWNLOADING -> DownloadIconState.Downloading
                                                else -> DownloadIconState.Idle
                                            },
                                            progress = if (track.downloadIndicator == TrackDownloadIndicator.DOWNLOADING) 0.5f else 0f,
                                            size = 28.dp,
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = if (isNotDownloadedOffline) 0.5f else 1f
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun AlbumDetailTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onBack) {
            Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
        }
        Row {
            IconButton(onClick = {}) {
                Icon(PhosphorIcons.ShareNetwork, "Share", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {}) {
                Icon(PhosphorIcons.DotsThreeVertical, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun AlbumHero(
    albumId: String,
    sharedElementSource: String = "library",
    albumName: String,
    artistName: String,
    imageUrl: String?,
    year: Int?,
    trackCount: Int,
    totalDuration: String,
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    downloadStatus: AlbumDownloadState.Status = AlbumDownloadState.Status.NONE,
    downloadProgress: Float = 0f,
    downloadedCount: Int = 0,
    totalDownloadCount: Int = 0,
    downloadInfoText: String? = null,
    onDownloadClick: () -> Unit = {},
    onRemoveDownloadsClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MellowTheme.colors.surface),
                error = ColorPainter(MellowTheme.colors.surface),
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.35f }
                    .blur(60.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MellowPalette.Stone800)
                    .blur(60.dp),
            )
        }

        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
        val sharedElementKey = "album_art_${sharedElementSource}_$albumId"

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
        ) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .aspectRatio(1f)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = sharedElementKey),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(MellowShapes.Large),
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clip(MellowShapes.Large)
                    .background(MellowTheme.colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.MusicNote,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(48.dp),
                )
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(MellowSpacing.Sp5))
            Text(
                albumName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.02).em,
                ),
                color = MellowTheme.colors.foreground,
                textAlign = TextAlign.Center,
            )
            Text(
                artistName,
                style = MaterialTheme.typography.titleLarge,
                color = MellowTheme.colors.accentStrong,
                modifier = Modifier.padding(top = MellowSpacing.Sp1),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                modifier = Modifier.padding(top = MellowSpacing.Sp2),
            ) {
                if (year != null) {
                    Text("$year", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                }
                Text("$trackCount tracks", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                if (totalDuration.isNotEmpty()) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                    Text(totalDuration, style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                }
            }
            if (downloadInfoText != null) {
                Text(
                    downloadInfoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                    modifier = Modifier.padding(top = MellowSpacing.Sp1),
                )
            }

            Spacer(Modifier.height(MellowSpacing.Sp5))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(PhosphorIcons.Shuffle, "Shuffle", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
                }
                AnimatedPlayPauseButton(
                    isPlaying = false,
                    onToggle = onPlayAll,
                    buttonSize = 52.dp,
                )
                AnimatedHeartIcon(
                    isFavorite = isFavorite,
                    onToggle = onFavoriteClick,
                    iconSize = 22.dp,
                )
                AlbumDownloadButton(
                    status = downloadStatus,
                    downloadProgress = downloadProgress,
                    downloadedCount = downloadedCount,
                    totalDownloadCount = totalDownloadCount,
                    onDownloadClick = onDownloadClick,
                    onRemoveDownloadsClick = onRemoveDownloadsClick,
                )
            }
        }
    }
}

@Composable
private fun AlbumDownloadButton(
    status: AlbumDownloadState.Status,
    downloadProgress: Float,
    downloadedCount: Int,
    totalDownloadCount: Int,
    onDownloadClick: () -> Unit,
    onRemoveDownloadsClick: () -> Unit,
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Downloads") },
            text = { Text("Remove all downloaded tracks for this album?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveDownloadsClick()
                    showRemoveDialog = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            },
        )
    }

    val dlState = when (status) {
        AlbumDownloadState.Status.NONE -> DownloadIconState.Idle
        AlbumDownloadState.Status.DOWNLOADING -> DownloadIconState.Downloading
        AlbumDownloadState.Status.COMPLETED -> DownloadIconState.Done
        AlbumDownloadState.Status.PARTIAL -> DownloadIconState.Downloading
    }

    Box(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            when (status) {
                AlbumDownloadState.Status.NONE -> onDownloadClick()
                AlbumDownloadState.Status.COMPLETED -> showRemoveDialog = true
                AlbumDownloadState.Status.PARTIAL -> onDownloadClick()
                else -> {}
            }
        },
    ) {
        AnimatedAlbumDownloadIndicator(
            state = dlState,
            progress = downloadProgress,
            tracksDone = downloadedCount,
            totalTracks = totalDownloadCount,
            modifier = Modifier.size(36.dp),
        )
    }
}

private fun formatTotalDuration(tracks: List<AlbumDetailTrack>): String {
    var totalSeconds = 0L
    for (track in tracks) {
        val parts = track.duration.split(":")
        if (parts.size == 2) {
            totalSeconds += (parts[0].toLongOrNull() ?: 0) * 60 + (parts[1].toLongOrNull() ?: 0)
        }
    }
    if (totalSeconds == 0L) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
