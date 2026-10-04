package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.mellow.core.designsystem.component.MellowImage
import dev.mellow.core.designsystem.component.AdaptiveTrackGrid
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.CollapsibleToolbarLayout
import dev.mellow.core.designsystem.component.ConnectionCloudIcon
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.rememberCollapsibleToolbarState
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import dev.mellow.core.common.artworkUri
import dev.mellow.core.model.LibrarySort
import kotlinx.coroutines.flow.flowOf

data class LibraryPlaylistItem(val id: String, val name: String, val trackCount: Int, val imageId: String?)

private val TABS = listOf("Albums", "Artists", "Tracks", "Genres", "Playlists")

data class ArtistItem(val id: String, val name: String, val albumCount: Int, val imageId: String?)

data class TrackItem(val id: String, val title: String, val artist: String, val album: String, val duration: String, val imageId: String?, val albumId: String? = null)

data class AlbumItem(val id: String, val name: String, val artist: String, val imageId: String?)

/**
 * The library's tabs. Albums, artists and tracks are paged lists that load as they scroll; genres and playlists are
 * short lists.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    albumItems: LazyPagingItems<AlbumItem> = emptyPagingItems(),
    artists: LazyPagingItems<ArtistItem> = emptyPagingItems(),
    tracks: LazyPagingItems<TrackItem> = emptyPagingItems(),
    genres: List<String> = emptyList(),
    playlists: List<LibraryPlaylistItem> = emptyList(),
    serverUrl: String? = null,
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
    sortLabel: String = "Recently Added",
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (index: Int, trackId: String) -> Unit = { _, _ -> },
    onTrackMenuClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSortChanged: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    selectedGenre: String? = null,
    onClearGenre: () -> Unit = {},
    initialTab: Int = 0,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var isGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(selectedGenre) {
        if (selectedGenre != null) selectedTab = 0
    }

    // The album grid needs room for two columns; anything narrower (small phones, split screen) shows the list.
    BoxWithConstraints(modifier = modifier) {
        val albumGridFits = maxWidth >= albumGridMinSize() * 2 + ALBUM_GRID_SPACING + ALBUM_GRID_PADDING * 2
        val toolbarState = rememberCollapsibleToolbarState()
        CollapsibleToolbarLayout(
            state = toolbarState,
            toolbar = {
                Column(modifier = Modifier.background(MellowTheme.colors.background)) {
                    LibraryTopBar(
                        isConnected = isConnected,
                        isServerUnreachable = isServerUnreachable,
                        error = error,
                        onRetry = onRetry,
                        isFilterActive = isFilterActive,
                        onToggleFilter = onToggleFilter,
                        onSettingsClick = onSettingsClick,
                        onSortChanged = onSortChanged,
                        showViewToggle = selectedTab == 0 && albumGridFits,
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
            modifier = Modifier
                .fillMaxSize()
                .background(MellowTheme.colors.background),
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()
            val showLoading = isLoading || isSyncing
            when (selectedTab) {
                0 -> PagedTab(albumItems, showLoading, "Syncing albums…", "No albums yet", "Couldn't load albums") {
                    if (isGridView && albumGridFits) AlbumsPanel(albumItems, serverUrl, onAlbumClick, topPadding)
                    else AlbumsListPanel(albumItems, serverUrl, onAlbumClick, topPadding)
                }
                1 -> PagedTab(artists, showLoading, "Syncing artists…", "No artists yet", "Couldn't load artists") {
                    ArtistsPanel(artists, serverUrl, onArtistClick, topPadding)
                }
                2 -> PagedTab(tracks, showLoading, "Syncing tracks…", "No tracks yet", "Couldn't load tracks") {
                    TracksPanel(tracks, serverUrl, onTrackClick, onTrackMenuClick, topPadding)
                }
                3 -> if (showLoading && genres.isEmpty()) LoadingContent(message = "Syncing genres…")
                     else if (genres.isEmpty()) EmptyContent("No genres yet")
                     else GenresPanel(genres, onGenreClick, topPadding)
                4 -> if (showLoading && playlists.isEmpty()) LoadingContent(message = "Syncing playlists\u2026")
                     else if (playlists.isEmpty()) EmptyContent("No playlists yet")
                     else PlaylistsPanel(playlists, serverUrl, onPlaylistClick, onCreatePlaylist, topPadding)
            }
        }
    }
}

/** A paged tab: its list once it has items, otherwise loading, an error to retry, or why it's empty. */
@Composable
private fun <T : Any> PagedTab(
    items: LazyPagingItems<T>,
    showLoading: Boolean,
    loadingMessage: String,
    emptyMessage: String,
    errorMessage: String,
    content: @Composable () -> Unit,
) {
    val refresh = items.loadState.refresh
    when {
        items.itemCount > 0 -> content()
        refresh is LoadState.Error -> ErrorContent(message = errorMessage, onRetry = items::retry)
        showLoading || refresh is LoadState.Loading -> LoadingContent(message = loadingMessage)
        else -> EmptyContent(emptyMessage)
    }
}

/**
 * A list with no items and nothing to load, for a tab the caller has no list for. The load states say so: without
 * them the list would look like it's still loading.
 */
@Composable
private fun <T : Any> emptyPagingItems(): LazyPagingItems<T> =
    remember {
        val loaded = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
        flowOf(PagingData.empty<T>(loaded))
    }.collectAsLazyPagingItems()

private val SORT_OPTIONS = linkedMapOf(
    "Recently Added" to LibrarySort.RecentlyAdded,
    "Name (A-Z)" to LibrarySort.NameAscending,
    "Name (Z-A)" to LibrarySort.NameDescending,
    "Year" to LibrarySort.Year,
)

/** The order an option of the library's sort menu stands for. */
fun librarySortFor(label: String): LibrarySort = SORT_OPTIONS[label] ?: LibrarySort.RecentlyAdded

@Composable
private fun LibraryTopBar(
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
        )
        Spacer(modifier = Modifier.weight(1f))
        ConnectionCloudIcon(
            isConnected = isConnected,
            isServerUnreachable = isServerUnreachable,
            error = error,
            onRetry = onRetry,
            isFilterActive = isFilterActive,
            onToggleFilter = onToggleFilter,
        )
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
                SORT_OPTIONS.keys.forEach { option ->
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
                    imageVector = if (isGridView) PhosphorIcons.Rows else PhosphorIcons.SquaresFour,
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
                    imageVector = PhosphorIcons.SquaresFour,
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
                    imageVector = PhosphorIcons.Rows,
                    contentDescription = "List view",
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun albumGridMinSize(): Dp = if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) 180.dp else 160.dp

private val ALBUM_GRID_PADDING = MellowSpacing.Sp4
private val ALBUM_GRID_SPACING = MellowSpacing.Sp3

@Composable
private fun AlbumsPanel(albums: LazyPagingItems<AlbumItem>, serverUrl: String?, onAlbumClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = albumGridMinSize()),
        contentPadding = PaddingValues(top = topPadding + MellowSpacing.Sp3, bottom = MellowSpacing.Sp3, start = ALBUM_GRID_PADDING, end = ALBUM_GRID_PADDING),
        horizontalArrangement = Arrangement.spacedBy(ALBUM_GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        items(albums.itemCount, key = albums.itemKey { it.id.ifEmpty { it.name } }) { index ->
            // Null while its page loads: a blank card holds its place.
            val album = albums[index]
            AlbumCard(
                title = album?.name ?: "",
                artist = album?.artist ?: "",
                imageUrl = if (serverUrl != null && album?.imageId != null) {
                    artworkUri(album.imageId)
                } else null,
                onClick = { if (album != null) onAlbumClick(album.id) },
                sharedElementKey = album?.let { "album_art_library_${it.id}" },
            )
        }
    }
}

@Composable
private fun AlbumsListPanel(albums: LazyPagingItems<AlbumItem>, serverUrl: String?, onAlbumClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
    ) {
        items(albums.itemCount, key = albums.itemKey { it.id.ifEmpty { it.name } }) { index ->
            val album = albums[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (album != null) onAlbumClick(album.id) }
                    .padding(vertical = MellowSpacing.Sp2),
            ) {
                MellowImage(
                    model = if (serverUrl != null && album?.imageId != null) {
                        artworkUri(album.imageId)
                    } else null,
                    contentDescription = album?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(MellowSpacing.Sp2))
                        .background(MellowTheme.colors.surface),
                    fallbackIconSize = 24.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MellowSpacing.Sp3),
                ) {
                    Text(
                        text = album?.name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MellowTheme.colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album?.artist ?: "",
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
private fun ArtistsPanel(artists: LazyPagingItems<ArtistItem>, serverUrl: String?, onArtistClick: (String) -> Unit, topPadding: Dp = 0.dp) {
    AdaptiveTrackGrid(
        itemCount = { artists.itemCount },
        key = artists.itemKey { it.id.ifEmpty { it.name } },
        contentPadding = PaddingValues(top = topPadding),
        modifier = Modifier.fillMaxSize(),
    ) { index, columns ->
        val artist = artists[index]
        ArtistRow(
            name = artist?.name ?: "",
            albumCount = artist?.albumCount ?: 0,
            imageUrl = if (serverUrl != null && artist?.imageId != null) {
                artworkUri(artist.imageId)
            } else null,
            onClick = { if (artist != null) onArtistClick(artist.id) },
            showChevron = columns == 1,
        )
    }
}

@Composable
private fun TracksPanel(
    tracks: LazyPagingItems<TrackItem>,
    serverUrl: String?,
    onTrackClick: (index: Int, trackId: String) -> Unit,
    onTrackMenuClick: (String) -> Unit,
    topPadding: Dp = 0.dp,
) {
    AdaptiveTrackGrid(
        itemCount = { tracks.itemCount },
        key = tracks.itemKey { it.id },
        contentPadding = PaddingValues(top = topPadding),
        modifier = Modifier.fillMaxSize(),
    ) { index, _ ->
        val track = tracks[index]
        TrackRow(
            title = track?.title ?: "",
            subtitle = if (track != null) "${track.artist} · ${track.album}" else "",
            duration = track?.duration ?: "",
            imageUrl = if (serverUrl != null && track != null) {
                val imgId = track.imageId ?: track.albumId
                if (imgId != null) artworkUri(imgId) else null
            } else null,
            onClick = { if (track != null) onTrackClick(index, track.id) },
            onMenuClick = { if (track != null) onTrackMenuClick(track.id) },
            showDivider = false,
        )
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
                MellowImage(
                    model = if (serverUrl != null && playlist.imageId != null) {
                        artworkUri(playlist.imageId)
                    } else null,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(MellowSpacing.Sp2))
                        .background(MellowTheme.colors.surface),
                    fallbackIconSize = 24.dp,
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
