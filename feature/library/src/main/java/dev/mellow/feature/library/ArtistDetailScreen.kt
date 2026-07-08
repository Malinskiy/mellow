package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + MellowSpacing.Sp16),
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        item {
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
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
                }
            }
        }

        item { ArtistHero() }

        item {
            SectionHeader("Top Tracks", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
        }

        itemsIndexed(mockTopTracks, key = { _, t -> t.first }) { index, (title, duration) ->
            TrackRow(
                title = title,
                subtitle = "Radiohead",
                duration = duration,
                trackNumber = "${index + 1}",
                onClick = {},
                showDivider = index < mockTopTracks.lastIndex,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
            )
        }

        item {
            SectionHeader("Discography", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            ) {
                items(mockDiscography, key = { it.first }) { (title, year) ->
                    AlbumCard(
                        title = title,
                        artist = year,
                        imageUrl = null,
                        onClick = {},
                        modifier = Modifier.size(width = 150.dp, height = 200.dp),
                    )
                }
            }
        }

        item {
            SectionHeader("Similar Artists", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
            ) {
                items(mockSimilar, key = { it }) { name ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MellowTheme.colors.surface),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MellowTheme.colors.foreground,
                            modifier = Modifier.padding(top = MellowSpacing.Sp2),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
    ) {
        AsyncImage(
            model = null,
            contentDescription = "Artist photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MellowTheme.colors.surface),
        )
        Spacer(Modifier.height(MellowSpacing.Sp5))
        Text("Radiohead", style = MaterialTheme.typography.displaySmall, color = MellowTheme.colors.foreground)
        Text("9 albums · 127 tracks", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted, modifier = Modifier.padding(top = MellowSpacing.Sp2))

        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MellowSpacing.Sp5),
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
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MellowTheme.colors.foreground, modifier = modifier)
}

private val mockTopTracks = listOf(
    "Reckoner" to "4:50",
    "Weird Fishes/Arpeggi" to "5:18",
    "15 Step" to "3:58",
    "Everything in Its Right Place" to "4:11",
    "Idioteque" to "5:09",
)

private val mockDiscography = listOf(
    "In Rainbows" to "2007",
    "Kid A" to "2000",
    "OK Computer" to "1997",
    "A Moon Shaped Pool" to "2016",
    "The Bends" to "1995",
)

private val mockSimilar = listOf("Thom Yorke", "Atoms for Peace", "Portishead", "Massive Attack", "Sigur Rós")
