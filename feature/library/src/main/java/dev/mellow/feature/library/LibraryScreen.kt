package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.CollapsibleToolbarLayout
import dev.mellow.core.designsystem.component.ConnectionStatusDot
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.rememberCollapsibleToolbarState
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.common.jellyfinImageUrl

data class LibraryPlaylistItem(val id: String, val name: String, val trackCount: Int, val imageId: String?)

private val TABS = listOf("Albums", "Artists", "Tracks", "Genres", "Playlists", "Folders")

data class ArtistItem(val id: String, val name: String, val albumCount: Int, val imageId: String?)

data class TrackItem(val id: String, val title: String, val artist: String, val album: String, val duration: String, val imageId: String?, val albumId: String? = null)

data class AlbumItem(val id: String, val name: String, val artist: String, val imageId: String?)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    albumItems: List<AlbumItem> = emptyList(),
    artists: List<ArtistItem> = emptyList(),
    tracks: List<TrackItem> = emptyList(),
    genres: List<String> = emptyList(),
    playlists: List<LibraryPlaylistItem> = emptyList(),
    serverUrl: String? = null,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    sortLabel: String = "Recently Added",
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSortChanged: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    selectedGenre: String? = null,
    onClearGenre: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(selectedGenre) {
        if (selectedGenre != null) selectedTab = 0
    }

    val toolbarState = rememberCollapsibleToolbarState()
    CollapsibleToolbarLayout(
        state = toolbarState,
        toolbar = {
            Column(modifier = Modifier.background(MellowTheme.colors.background)) {
                LibraryTopBar(
                    isConnected = isConnected,
                    isServerUnreachable = isServerUnreachable,
                    onSettingsClick = onSettingsClick,
                    onSortChanged = onSortChanged,
                    showViewToggle = selectedTab == 0,
                    isGridView = isGridView,
                    onToggleView = { isGridView = !isGridView },
                )
                MellowTabBar(
                    tabs = TABS,
                    selectedIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
                )
                if (selectedGenre != null) {
                    GenreFilterChip(genre = selectedGenre, onClear = onClearGenre)
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()
        val showLoading = isLoading || isSyncing
        when (selectedTab) {
            0 -> if (showLoading && albumItems.isEmpty()) LoadingContent(message = "Syncing albums…")
                 else if (albumItems.isEmpty()) EmptyContent("No albums yet")
                 else if (isGridView) AlbumsPanel(albumItems, serverUrl, onAlbumClick, topPadding)
                 else AlbumsListPanel(albumItems, serverUrl, onAlbumClick, topPadding)
            1 -> if (showLoading && artists.isEmpty()) LoadingContent(message = "Syncing artists…")
                 else if (artists.isEmpty()) EmptyContent("No artists yet")
                 else ArtistsPanel(artists, serverUrl, onArtistClick, topPadding)
            2 -> if (showLoading && tracks.isEmpty()) LoadingContent(message = "Syncing tracks…")
                 else if (tracks.isEmpty()) EmptyContent("No tracks yet")
                 else TracksPanel(tracks, serverUrl, onTrackClick, onTrackMenuClick, topPadding)
            3 -> if (showLoading && genres.isEmpty()) LoadingContent(message = "Syncing genres…")
                 else if (genres.isEmpty()) EmptyContent("No genres yet")
                 else GenresPanel(genres, onGenreClick, topPadding)
            4 -> if (showLoading && playlists.isEmpty()) LoadingContent(message = "Syncing playlists\u2026")
                 else if (playlists.isEmpty()) EmptyContent("No playlists yet")
                 else PlaylistsPanel(playlists, serverUrl, onPlaylistClick, onCreatePlaylist, topPadding)
            5 -> EmptyContent("Coming soon")
        }
    }
}

private val SORT_OPTIONS = listOf("Recently Added", "Name (A-Z)", "Name (Z-A)", "Year")

@Composable
private fun LibraryTopBar(
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onSortChanged: (String) -> Unit = {},
    showViewToggle: Boolean = false,
    isGridView: Boolean = true,
    onToggleView: () -> Unit = {},
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
        )
        Spacer(modifier = Modifier.weight(1f))
        ConnectionStatusDot(
            isConnected = isConnected,
            isServerUnreachable = isServerUnreachable,
        )
        Box(modifier = Modifier.width(MellowSpacing.Sp2))
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector = PhosphorIcons.FunnelSimple,
                    contentDescription = "Sort",
                    tint = MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                SORT_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            showSortMenu = false
                            onSortChanged(option)
                        },
                    )
                }
            }
        }
        if (showViewToggle) {
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isGridView) PhosphorIcons.List else PhosphorIcons.GridFour,
                    contentDescription = if (isGridView) "List view" else "Grid view",
                    tint = MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
private fun SortRow(
    tab: String,
    albumCount: Int = 0,
    artistCount: Int = 0,
    trackCount: Int = 0,
    genreCount: Int = 0,
    sortLabel: String = "Recently Added",
) {
    val counts = mapOf(
        "Albums" to "$albumCount albums",
        "Artists" to "$artistCount artists",
        "Tracks" to "$trackCount tracks",
        "Genres" to "$genreCount genres",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp1),
    ) {
        Text(
            text = counts[tab] ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        ) {
            Text(
                text = "$sortLabel ▾",
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
                    .background(MellowPalette.Stone800, RoundedCornerShape(MellowSpacing.Sp2)),
            ) {
                Icon(
                    imageVector = PhosphorIcons.GridFour,
                    contentDescription = "Grid view",
                    tint = MellowTheme.colors.foreground,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp),
            ) {
                Icon(
                    imageVector = PhosphorIcons.List,
                    contentDescription = "List view",
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumsPanel(albums: List<AlbumItem>, serverUrl: String?, onAlbumClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(top = topPadding + MellowSpacing.Sp3, bottom = MellowSpacing.Sp3, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        items(albums, key = { it.id.ifEmpty { it.name } }) { album ->
            AlbumCard(
                title = album.name,
                artist = album.artist,
                imageUrl = if (serverUrl != null && album.imageId != null) {
                    jellyfinImageUrl(serverUrl, album.imageId)
                } else null,
                onClick = { onAlbumClick(album.id) },
                sharedElementKey = "album_art_library_${album.id}",
            )
        }
    }
}

@Composable
private fun AlbumsListPanel(albums: List<AlbumItem>, serverUrl: String?, onAlbumClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(albums, key = { it.id.ifEmpty { it.name } }) { album ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album.id) }
                    .padding(vertical = MellowSpacing.Sp2),
            ) {
                AsyncImage(
                    model = if (serverUrl != null && album.imageId != null) {
                        jellyfinImageUrl(serverUrl, album.imageId)
                    } else null,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MellowTheme.colors.surface),
                    error = ColorPainter(MellowTheme.colors.surface),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(MellowSpacing.Sp2))
                        .background(MellowTheme.colors.surface),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MellowSpacing.Sp3),
                ) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MellowTheme.colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MellowTheme.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsPanel(artists: List<ArtistItem>, serverUrl: String?, onArtistClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(artists, key = { it.id.ifEmpty { it.name } }) { artist ->
            ArtistRow(
                name = artist.name,
                albumCount = artist.albumCount,
                imageUrl = if (serverUrl != null && artist.imageId != null) {
                    jellyfinImageUrl(serverUrl, artist.imageId)
                } else null,
                onClick = { onArtistClick(artist.id) },
            )
        }
    }
}

@Composable
private fun TracksPanel(
    tracks: List<TrackItem>,
    serverUrl: String?,
    onTrackClick: (String) -> Unit,
    onTrackMenuClick: (String) -> Unit,
    topPadding: Dp = 0.dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                title = track.title,
                subtitle = "${track.artist} · ${track.album}",
                duration = track.duration,
                imageUrl = if (serverUrl != null) {
                    val imgId = track.imageId ?: track.albumId
                    if (imgId != null) jellyfinImageUrl(serverUrl, imgId) else null
                } else null,
                onClick = { onTrackClick(track.id) },
                onMenuClick = { onTrackMenuClick(track.id) },
                showDivider = true,
            )
        }
    }
}

@Composable
private fun GenresPanel(genres: List<String>, onGenreClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding + MellowSpacing.Sp2, bottom = MellowSpacing.Sp2, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(genres, key = { it }) { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGenreClick(genre) }
                    .padding(vertical = MellowSpacing.Sp3),
            )
        }
    }
}

@Composable
private fun PlaylistsPanel(
    playlists: List<LibraryPlaylistItem>,
    serverUrl: String?,
    onPlaylistClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCreatePlaylist: (String) -> Unit,
    topPadding: Dp = 0.dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist.id) }
                    .padding(vertical = MellowSpacing.Sp2),
            ) {
                AsyncImage(
                    model = if (serverUrl != null && playlist.imageId != null) {
                        jellyfinImageUrl(serverUrl, playlist.imageId)
                    } else null,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MellowTheme.colors.surface),
                    error = ColorPainter(MellowTheme.colors.surface),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(MellowSpacing.Sp2))
                        .background(MellowTheme.colors.surface),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MellowSpacing.Sp3),
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MellowTheme.colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlist.trackCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MellowTheme.colors.muted,
                    )
                }
                Icon(
                    imageVector = PhosphorIcons.CaretRight,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun GenreFilterChip(genre: String, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2)
            .background(MellowTheme.colors.surface, RoundedCornerShape(MellowSpacing.Sp4))
            .padding(start = MellowSpacing.Sp3, end = MellowSpacing.Sp1, top = MellowSpacing.Sp1, bottom = MellowSpacing.Sp1),
    ) {
        Text(
            text = genre,
            style = MaterialTheme.typography.labelMedium,
            color = MellowTheme.colors.foreground,
        )
        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
            Icon(
                    imageVector = PhosphorIcons.X,
                contentDescription = "Clear genre filter",
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
