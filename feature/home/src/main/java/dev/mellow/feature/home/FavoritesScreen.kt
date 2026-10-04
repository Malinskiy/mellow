package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.mellow.core.common.artworkUri
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import androidx.compose.ui.Alignment
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.CollapsibleToolbarLayout
import dev.mellow.core.designsystem.component.ConnectionCloudIcon
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.rememberCollapsibleToolbarState
import dev.mellow.core.designsystem.component.AdaptiveTrackGrid
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import java.time.Duration

private val TABS = listOf("Tracks", "Albums", "Artists")

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    serverId: String = "",
    serverUrl: String? = null,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (index: Int, trackId: String) -> Unit = { _, _ -> },
    onTrackMenuClick: (String) -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val viewModel: FavoritesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(serverId) {
        viewModel.loadFavorites(serverId)
    }

    FavoritesContent(
        tracks = tracks,
        albums = albums,
        artists = artists,
        isLoading = state.isLoading,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        serverUrl = serverUrl,
        isConnected = isConnected,
        isServerUnreachable = isServerUnreachable,
        isFilterActive = isFilterActive,
        onToggleFilter = onToggleFilter,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onTrackClick = onTrackClick,
        onTrackMenuClick = onTrackMenuClick,
        onShuffleAll = onShuffleAll,
        onSettingsClick = onSettingsClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/** The favorites tabs. [isLoading]: the favorites are being fetched and there are none to show yet. */
@Composable
fun FavoritesContent(
    tracks: LazyPagingItems<Track> = emptyPagingItems(),
    albums: LazyPagingItems<Album> = emptyPagingItems(),
    artists: LazyPagingItems<Artist> = emptyPagingItems(),
    isLoading: Boolean = false,
    error: String? = null,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    serverUrl: String? = null,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (index: Int, trackId: String) -> Unit = { _, _ -> },
    onTrackMenuClick: (String) -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isExpanded = LocalWindowWidthClass.current != WindowWidthClass.Compact
    val toolbarState = rememberCollapsibleToolbarState()
    CollapsibleToolbarLayout(
        state = toolbarState,
         toolbar = {
            Column(modifier = Modifier.background(MellowTheme.colors.background)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                ) {
                    Text(
                        "Favorites",
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
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = PhosphorIcons.Gear,
                            contentDescription = "Settings",
                            tint = MellowTheme.colors.foreground,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                MellowTabBar(
                    tabs = TABS,
                    selectedIndex = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()
        if (isLoading && tracks.itemCount == 0 && albums.itemCount == 0) {
            LoadingContent()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> PagedContent(tracks, "No favorite tracks yet", "Couldn't load favorite tracks") {
                    AdaptiveTrackGrid(
                        itemCount = tracks.itemCount,
                        key = tracks.itemKey { it.id },
                        contentPadding = PaddingValues(top = topPadding),
                        modifier = Modifier.fillMaxSize(),
                    ) { index, _ ->
                        // Null while its page loads: a blank row holds its place.
                        val track = tracks[index]
                        TrackRow(
                            title = track?.name ?: "",
                            subtitle = if (track != null) "${track.artistName ?: ""} · ${track.albumName ?: ""}" else "",
                            duration = if (track != null) formatFavDuration(track.duration) else "",
                            imageUrl = if (serverUrl != null && track != null) {
                                val imgId = track.imageId ?: track.albumId
                                if (imgId != null) artworkUri(imgId) else null
                            } else null,
                            isFavorite = track != null,
                            onClick = { if (track != null) onTrackClick(index, track.id) },
                            onMenuClick = { if (track != null) onTrackMenuClick(track.id) },
                            showDivider = false,
                        )
                    }
                }
                1 -> PagedContent(albums, "No favorite albums yet", "Couldn't load favorite albums") {
                    val albumGridMinSize = if (isExpanded) 180.dp else 160.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = albumGridMinSize),
                        contentPadding = PaddingValues(top = topPadding, start = MellowSpacing.Sp4, end = MellowSpacing.Sp4),
                        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(albums.itemCount, key = albums.itemKey { it.id }) { index ->
                            val album = albums[index]
                            val imageId = album?.imageId
                            AlbumCard(
                                title = album?.name ?: "",
                                artist = album?.artistName ?: "",
                                imageUrl = if (serverUrl != null && imageId != null) artworkUri(imageId) else null,
                                onClick = { if (album != null) onAlbumClick(album.id) },
                                sharedElementKey = album?.let { "album_art_favorites_${it.id}" },
                            )
                        }
                    }
                }
                2 -> PagedContent(artists, "No favorite artists yet", "Couldn't load favorite artists") {
                    AdaptiveTrackGrid(
                        itemCount = artists.itemCount,
                        key = artists.itemKey { it.id },
                        contentPadding = PaddingValues(top = topPadding),
                        modifier = Modifier.fillMaxSize(),
                    ) { index, columns ->
                        val artist = artists[index]
                        val imageId = artist?.imageId
                        ArtistRow(
                            name = artist?.name ?: "",
                            albumCount = artist?.albumCount ?: 0,
                            imageUrl = if (serverUrl != null && imageId != null) artworkUri(imageId) else null,
                            onClick = { if (artist != null) onArtistClick(artist.id) },
                            showChevron = columns == 1,
                        )
                    }
                }
            }
            val totalCount = tracks.itemCount + albums.itemCount + artists.itemCount
            if (totalCount > 0) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onShuffleAll,
                    containerColor = MellowTheme.colors.foreground,
                    contentColor = MellowTheme.colors.background,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MellowSpacing.Sp4),
                ) {
                    Icon(PhosphorIcons.Shuffle, contentDescription = "Shuffle all", modifier = Modifier.size(24.dp))
                }
            }
        }
        }
    }
}

private fun formatFavDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
