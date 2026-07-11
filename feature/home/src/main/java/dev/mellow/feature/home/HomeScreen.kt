package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ConnectionStatusDot
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme


data class HomeAlbumItem(
    val id: String,
    val name: String,
    val artist: String,
    val imageId: String?,
)

data class HomeTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val imageId: String?,
)

@Composable
fun HomeScreen(
    quickPicks: List<HomeAlbumItem> = emptyList(),
    recentlyPlayed: List<HomeAlbumItem> = emptyList(),
    recentlyAdded: List<HomeAlbumItem> = emptyList(),
    favoriteTracks: List<HomeTrackItem> = emptyList(),
    genres: List<String> = emptyList(),
    serverUrl: String? = null,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    onAlbumClick: (String, String) -> Unit = { _, _ -> },
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        HomeTopBar(
            isConnected = isConnected,
            isServerUnreachable = isServerUnreachable,
            onSettingsClick = onSettingsClick,
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = MellowSpacing.Sp8),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (quickPicks.isNotEmpty()) {
                item {
                    SectionHeader("Quick Picks")
                }
                item {
                    QuickPicksGrid(
                        albums = quickPicks,
                        serverUrl = serverUrl,
                        onAlbumClick = { id -> onAlbumClick(id, "quick") },
                    )
                }
            }

            if (recentlyPlayed.isNotEmpty()) {
                item {
                    SectionHeader("Recently Played")
                }
                item {
                    AlbumCarousel(
                        albums = recentlyPlayed,
                        serverUrl = serverUrl,
                        onAlbumClick = { id -> onAlbumClick(id, "recent") },
                        sharedKeyPrefix = "recent",
                    )
                }
            }

            if (recentlyAdded.isNotEmpty()) {
                item {
                    SectionHeader("Recently Added")
                }
                item {
                    AlbumCarousel(
                        albums = recentlyAdded,
                        serverUrl = serverUrl,
                        onAlbumClick = { id -> onAlbumClick(id, "added") },
                        sharedKeyPrefix = "added",
                    )
                }
            }

            if (favoriteTracks.isNotEmpty()) {
                item {
                    SectionHeader("Your Favorites")
                }
                itemsIndexed(
                    favoriteTracks.take(5),
                    key = { _, track -> "fav_${track.id}" },
                ) { index, track ->
                    TrackRow(
                        title = track.title,
                        subtitle = "${track.artist} · ${track.album}",
                        duration = track.duration,
                        imageUrl = if (serverUrl != null && track.imageId != null) {
                            jellyfinImageUrl(serverUrl, track.imageId)
                        } else null,
                        onClick = { onTrackClick(track.id) },
                        onMenuClick = { onTrackMenuClick(track.id) },
                        showDivider = index < favoriteTracks.take(5).lastIndex,
                    )
                }
            }

            if (genres.isNotEmpty()) {
                item {
                    SectionHeader("Browse by Genre")
                }
                item {
                    GenreChipsRow(
                        genres = genres,
                        onGenreClick = onGenreClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    onSettingsClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            text = "Mellow",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
        )
        Spacer(modifier = Modifier.weight(1f))
        ConnectionStatusDot(
            isConnected = isConnected,
            isServerUnreachable = isServerUnreachable,
        )
        Box(modifier = Modifier.width(MellowSpacing.Sp2))
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = PhosphorIcons.Gear,
                contentDescription = "Settings",
                tint = MellowTheme.colors.foreground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MellowTheme.colors.foreground,
        modifier = Modifier.padding(
            start = MellowSpacing.Sp4,
            end = MellowSpacing.Sp4,
            top = MellowSpacing.Sp6,
            bottom = MellowSpacing.Sp3,
        ),
    )
}

@Composable
private fun QuickPicksGrid(
    albums: List<HomeAlbumItem>,
    serverUrl: String?,
    onAlbumClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        userScrollEnabled = false,
        modifier = Modifier.height((68.dp * 3) + (MellowSpacing.Sp3 * 2)),
    ) {
        items(albums, key = { it.id }) { album ->
            CompactAlbumCard(
                title = album.name,
                artist = album.artist,
                imageUrl = if (serverUrl != null && album.imageId != null) {
                    jellyfinImageUrl(serverUrl, album.imageId)
                } else null,
                onClick = { onAlbumClick(album.id) },
            )
        }
    }
}

@Composable
private fun CompactAlbumCard(
    title: String,
    artist: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(MellowShapes.Small)
            .background(MellowTheme.colors.surface)
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .height(60.dp)
                .width(60.dp)
                .clip(MellowShapes.Small)
                .background(MellowTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                dev.mellow.core.designsystem.icon.PhosphorIcons.MusicNote,
                contentDescription = null,
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(24.dp),
            )
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = "Album art",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
                style = MaterialTheme.typography.labelMedium,
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
    }
}

@Composable
private fun AlbumCarousel(
    albums: List<HomeAlbumItem>,
    serverUrl: String?,
    onAlbumClick: (String) -> Unit,
    sharedKeyPrefix: String = "",
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                title = album.name,
                artist = album.artist,
                imageUrl = if (serverUrl != null && album.imageId != null) {
                    jellyfinImageUrl(serverUrl, album.imageId)
                } else null,
                onClick = { onAlbumClick(album.id) },
                modifier = Modifier.width(130.dp),

            )
        }
    }
}

@Composable
private fun GenreChipsRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MellowSpacing.Sp4),
    ) {
        genres.forEach { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.labelMedium,
                color = MellowTheme.colors.foreground,
                modifier = Modifier
                    .clip(MellowShapes.Full)
                    .border(1.dp, MellowTheme.colors.border, MellowShapes.Full)
                    .clickable { onGenreClick(genre) }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            )
        }
    }
}
