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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + MellowSpacing.Sp16),
        ) {
            item { AlbumDetailTopBar(onBack) }
            item { AlbumHero() }
            itemsIndexed(mockAlbumTracks, key = { _, t -> t.title }) { index, track ->
                TrackRow(
                    title = track.title,
                    subtitle = "",
                    duration = track.duration,
                    trackNumber = if (track.isPlaying) null else "${index + 1}",
                    isPlaying = track.isPlaying,
                    isFavorite = track.isFavorite,
                    onFavoriteClick = {},
                    onMenuClick = {},
                    onClick = {},
                    showDivider = index < mockAlbumTracks.lastIndex,
                    modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                )
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
private fun AlbumHero() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MellowPalette.Stone800)
                .blur(60.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
        ) {
            AsyncImage(
                model = null,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(240.dp)
                    .aspectRatio(1f)
                    .clip(MellowShapes.Large)
                    .background(MellowTheme.colors.surface),
            )

            Spacer(Modifier.height(MellowSpacing.Sp5))
            Text("In Rainbows", style = MaterialTheme.typography.displaySmall, color = MellowTheme.colors.foreground)
            Text("Radiohead", style = MaterialTheme.typography.titleLarge, color = MellowTheme.colors.accentStrong, modifier = Modifier.padding(top = MellowSpacing.Sp1))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                modifier = Modifier.padding(top = MellowSpacing.Sp2),
            ) {
                Text("2007", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                Text("10 tracks", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
                Text("42:34", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
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

private data class AlbumTrack(val title: String, val duration: String, val isPlaying: Boolean = false, val isFavorite: Boolean = false)

private val mockAlbumTracks = listOf(
    AlbumTrack("15 Step", "3:58", isPlaying = true),
    AlbumTrack("Bodysnatchers", "4:02", isFavorite = true),
    AlbumTrack("Nude", "4:15"),
    AlbumTrack("Weird Fishes/Arpeggi", "5:18", isFavorite = true),
    AlbumTrack("All I Need", "3:49"),
    AlbumTrack("Faust Arp", "2:10"),
    AlbumTrack("Reckoner", "4:50", isFavorite = true),
    AlbumTrack("House of Cards", "5:28"),
    AlbumTrack("Jigsaw Falling into Place", "4:09"),
    AlbumTrack("Videotape", "4:24"),
)
