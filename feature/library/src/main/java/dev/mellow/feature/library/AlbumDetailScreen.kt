package dev.mellow.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class AlbumDetailTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val duration: String,
    val trackNumber: Int?,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
)

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    albumName: String = "In Rainbows",
    artistName: String = "Radiohead",
    albumImageUrl: String? = null,
    year: Int? = 2007,
    expectedTrackCount: Int = 10,
    tracks: List<AlbumDetailTrack> = mockAlbumTracks,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
) {
    val tracksLoading = tracks.isEmpty() && (isSyncing || expectedTrackCount > 0)
    val displayTrackCount = if (tracks.isNotEmpty()) tracks.size else expectedTrackCount

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
                            albumName = albumName,
                            artistName = artistName,
                            imageUrl = albumImageUrl,
                            year = year,
                            trackCount = displayTrackCount,
                            totalDuration = formatTotalDuration(tracks),
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
                            TrackRow(
                                title = track.title,
                                subtitle = "",
                                duration = track.duration,
                                trackNumber = if (track.isPlaying) null else "${track.trackNumber ?: (index + 1)}",
                                isPlaying = track.isPlaying,
                                isFavorite = track.isFavorite,
                                onFavoriteClick = {},
                                onMenuClick = {},
                                onClick = { onTrackClick(track.id) },
                                showDivider = index < tracks.lastIndex,
                                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MellowTheme.colors.foreground)
        }
        Row {
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Share, "Share", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AlbumHero(
    albumName: String,
    artistName: String,
    imageUrl: String?,
    year: Int?,
    trackCount: Int,
    totalDuration: String,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Album art for $albumName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(240.dp)
                    .aspectRatio(1f)
                    .clip(MellowShapes.Large)
                    .background(MellowTheme.colors.surface),
            )

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

            Spacer(Modifier.height(MellowSpacing.Sp5))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(52.dp)
                        .background(MellowPalette.Stone200, MellowShapes.Full),
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = MellowPalette.Stone950, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.FavoriteBorder, "Favorite", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Download, "Download", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
                }
            }
        }
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

private val mockAlbumTracks = listOf(
    AlbumDetailTrack("mock-1", "15 Step", "Radiohead", "3:58", 1, isPlaying = true),
    AlbumDetailTrack("mock-2", "Bodysnatchers", "Radiohead", "4:02", 2, isFavorite = true),
    AlbumDetailTrack("mock-3", "Nude", "Radiohead", "4:15", 3),
    AlbumDetailTrack("mock-4", "Weird Fishes/Arpeggi", "Radiohead", "5:18", 4, isFavorite = true),
    AlbumDetailTrack("mock-5", "All I Need", "Radiohead", "3:49", 5),
    AlbumDetailTrack("mock-6", "Faust Arp", "Radiohead", "2:10", 6),
    AlbumDetailTrack("mock-7", "Reckoner", "Radiohead", "4:50", 7, isFavorite = true),
    AlbumDetailTrack("mock-8", "House of Cards", "Radiohead", "5:28", 8),
    AlbumDetailTrack("mock-9", "Jigsaw Falling into Place", "Radiohead", "4:09", 9),
    AlbumDetailTrack("mock-10", "Videotape", "Radiohead", "4:24", 10),
)
