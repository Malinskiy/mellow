package dev.mellow.feature.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.BoxWithConstraints
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
import dev.mellow.core.designsystem.component.AdaptiveTrackGrid
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.LocalNavAnimatedVisibilityScope
import dev.mellow.core.designsystem.component.LocalSharedTransitionScope
import dev.mellow.core.designsystem.component.CollapsibleToolbarLayout
import dev.mellow.core.designsystem.component.ConnectionCloudIcon
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.component.rememberCollapsibleToolbarState
import dev.mellow.core.designsystem.theme.LocalMiniPlayerPadding
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import kotlin.math.ceil


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
    val albumId: String? = null,
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
    error: String? = null,
    onRetry: () -> Unit = {},
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
    onAlbumClick: (String, String) -> Unit = { _, _ -> },
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isExpanded = LocalWindowWidthClass.current != WindowWidthClass.Compact
    val toolbarState = rememberCollapsibleToolbarState()
    CollapsibleToolbarLayout(
        state = toolbarState,
        toolbar = {
            HomeTopBar(
                isConnected = isConnected,
                isServerUnreachable = isServerUnreachable,
                error = error,
                onRetry = onRetry,
                isFilterActive = isFilterActive,
                onToggleFilter = onToggleFilter,
                onSettingsClick = onSettingsClick,
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) { contentPadding ->
        val toolbarTopPadding = contentPadding.calculateTopPadding()
        val isEmpty = quickPicks.isEmpty() && recentlyPlayed.isEmpty() && recentlyAdded.isEmpty() && favoriteTracks.isEmpty() && genres.isEmpty()
        if (isEmpty) {
            dev.mellow.core.designsystem.component.EmptyContent("Add music to your Jellyfin library to get started")
        } else {
        LazyColumn(
            contentPadding = PaddingValues(
                top = (toolbarTopPadding - MellowSpacing.Sp5).coerceAtLeast(0.dp),
                bottom = MellowSpacing.Sp8 + LocalMiniPlayerPadding.current,
            ),
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
                    if (isExpanded) {
                        val rpMinSize = if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) 180.dp else 150.dp
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MellowSpacing.Sp4),
                        ) {
                            val columns = (maxWidth / rpMinSize).toInt().coerceAtLeast(1)
                            val maxItems = (columns * 2).coerceAtMost(recentlyPlayed.size)
                            val clippedItems = recentlyPlayed.take(maxItems / columns * columns)
                            val rows = clippedItems.size / columns
                            val gridHeight = (60.dp * rows) + (MellowSpacing.Sp3 * (rows - 1).coerceAtLeast(0))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = rpMinSize),
                                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                                verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                                userScrollEnabled = false,
                                modifier = Modifier.height(gridHeight),
                            ) {
                                items(clippedItems, key = { it.id }) { album ->
                                    CompactAlbumCard(
                                        title = album.name,
                                        artist = album.artist,
                                        imageUrl = if (serverUrl != null && album.imageId != null) {
                                            jellyfinImageUrl(serverUrl, album.imageId)
                                        } else null,
                                        onClick = { onAlbumClick(album.id, "recent") },
                                        sharedElementKey = "album_art_recent_${album.id}",
                                    )
                                }
                            }
                        }
                    } else {
                        AlbumCarousel(
                            albums = recentlyPlayed,
                            serverUrl = serverUrl,
                            onAlbumClick = { id -> onAlbumClick(id, "recent") },
                            sharedKeyPrefix = "recent",
                        )
                    }
                }
            }

            if (recentlyAdded.isNotEmpty()) {
                item {
                    SectionHeader("Recently Added")
                }
                item {
                    if (isExpanded) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MellowSpacing.Sp4),
                        ) {
                            val raMinSize = if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) 200.dp else 150.dp
                            val columns = (maxWidth / raMinSize).toInt().coerceAtLeast(1)
                            val maxAdded = (columns * 2).coerceAtMost(recentlyAdded.size)
                            val clippedAdded = recentlyAdded.take(maxAdded / columns * columns)
                            val rows = clippedAdded.size / columns
                            val columnWidth = (maxWidth - MellowSpacing.Sp3 * (columns - 1)) / columns
                            val rowHeight = columnWidth + 50.dp
                            val gridHeight = (rowHeight * rows) + (MellowSpacing.Sp3 * (rows - 1).coerceAtLeast(0))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = raMinSize),
                                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                                verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                                userScrollEnabled = false,
                                modifier = Modifier.height(gridHeight),
                            ) {
                                items(clippedAdded, key = { it.id }) { album ->
                                    AlbumCard(
                                        title = album.name,
                                        artist = album.artist,
                                        imageUrl = if (serverUrl != null && album.imageId != null) {
                                            jellyfinImageUrl(serverUrl, album.imageId)
                                        } else null,
                                        onClick = { onAlbumClick(album.id, "added") },
                                        sharedElementKey = "album_art_added_${album.id}",
                                    )
                                }
                            }
                        }
                    } else {
                        AlbumCarousel(
                            albums = recentlyAdded,
                            serverUrl = serverUrl,
                            onAlbumClick = { id -> onAlbumClick(id, "added") },
                            sharedKeyPrefix = "added",
                        )
                    }
                }
            }

            if (favoriteTracks.isNotEmpty()) {
                item {
                    SectionHeader("Your Favorites")
                }
                item {
                    AdaptiveTrackGrid(
                        items = favoriteTracks.take(5),
                        key = { it.id },
                        nested = true,
                    ) { _, track ->
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
                            showDivider = false,
                        )
                    }
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
}

@Composable
private fun HomeTopBar(
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    error: String? = null,
    onRetry: () -> Unit = {},
    isFilterActive: Boolean = false,
    onToggleFilter: () -> Unit = {},
    onSettingsClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MellowTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = MellowSpacing.Sp4, end = MellowSpacing.Sp4, top = MellowSpacing.Sp3, bottom = MellowSpacing.Sp1),
    ) {
        Text(
            text = "Mellow",
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
            top = MellowSpacing.Sp5,
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
    val gridMinSize = if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) 180.dp else 150.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4),
    ) {
        val columns = (maxWidth / gridMinSize).toInt().coerceAtLeast(1)
        val maxItems = (columns * 3).coerceAtMost(albums.size)
        val clippedAlbums = albums.take(maxItems / columns * columns)
        val rows = clippedAlbums.size / columns
        val gridHeight = (60.dp * rows) + (MellowSpacing.Sp3 * (rows - 1).coerceAtLeast(0))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = gridMinSize),
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            userScrollEnabled = false,
            modifier = Modifier.height(gridHeight),
        ) {
            items(clippedAlbums, key = { it.id }) { album ->
                CompactAlbumCard(
                    title = album.name,
                    artist = album.artist,
                    imageUrl = if (serverUrl != null && album.imageId != null) {
                        jellyfinImageUrl(serverUrl, album.imageId)
                    } else null,
                    onClick = { onAlbumClick(album.id) },
                    sharedElementKey = "album_art_quick_${album.id}",
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun CompactAlbumCard(
    title: String,
    artist: String,
    imageUrl: String?,
    onClick: () -> Unit,
    sharedElementKey: String? = null,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

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
                .then(
                    if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = sharedElementKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                                clipInOverlayDuringTransition = OverlayClip(MellowShapes.AlbumArt),
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(MellowShapes.AlbumArt)
                .background(MellowTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                coil3.compose.AsyncImage(
                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageUrl)
                        .memoryCacheKey(imageUrl)
                        .placeholderMemoryCacheKey(coil3.memory.MemoryCache.Key(imageUrl))
                        .build(),
                    contentDescription = "Album art",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.compose.material3.Icon(
                    dev.mellow.core.designsystem.icon.PhosphorIcons.MusicNote,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(24.dp),
                )
            }
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
                sharedElementKey = if (sharedKeyPrefix.isNotEmpty()) "album_art_${sharedKeyPrefix}_${album.id}" else null,
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
