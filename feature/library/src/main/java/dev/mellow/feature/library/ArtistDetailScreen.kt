package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.designsystem.component.ArtworkBackground
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.AdaptiveTrackGrid
import dev.mellow.core.designsystem.component.MellowImage
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.LocalMiniPlayerPadding
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass

data class ArtistTrack(val id: String, val title: String, val duration: String, val albumName: String, val imageUrl: String? = null)

data class ArtistAlbum(val id: String, val name: String, val year: Int?, val imageId: String?)

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    artistName: String = "",
    artistImageUrl: String? = null,
    albumCount: Int = 0,
    totalTrackCount: Int = 0,
    overview: String? = null,
    topTracks: List<ArtistTrack> = emptyList(),
    albums: List<ArtistAlbum> = emptyList(),
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    serverUrl: String? = null,
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onShare: () -> Unit = {},
    onAddAllToQueue: () -> Unit = {},
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val isExpanded = LocalWindowWidthClass.current != WindowWidthClass.Compact

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        when {
            isLoading -> LoadingContent(message = "Loading artist…")
            error != null -> ErrorContent(message = error, onRetry = onRetry)
            else -> {
                if (isExpanded) {
                    ArtistDetailExpanded(
                        onBack = onBack,
                        artistName = artistName,
                        artistImageUrl = artistImageUrl,
                        albumCount = albumCount,
                        totalTrackCount = totalTrackCount,
                        overview = overview,
                        topTracks = topTracks,
                        albums = albums,
                        onAlbumClick = onAlbumClick,
                        onTrackClick = onTrackClick,
                        onTrackMenuClick = onTrackMenuClick,
                        serverUrl = serverUrl,
                        isFavorite = isFavorite,
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle,
                        onFavoriteClick = onFavoriteClick,
                        onMore = { showMoreMenu = true },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + MellowSpacing.Sp16),
                        modifier = Modifier.fillMaxSize(),
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
                                    Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
                                }
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(PhosphorIcons.DotsThreeVertical, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        item {
                            ArtistHero(
                                artistName = artistName,
                                artistImageUrl = artistImageUrl,
                                albumCount = albumCount,
                                trackCount = totalTrackCount,
                                isFavorite = isFavorite,
                                onPlayAll = onPlayAll,
                                onShuffle = onShuffle,
                                onFavoriteClick = onFavoriteClick,
                            )
                        }

                        if (topTracks.isNotEmpty()) {
                            item {
                                SectionHeader("Top Tracks", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
                            }

                            itemsIndexed(topTracks, key = { _, t -> t.id }) { index, track ->
                                TrackRow(
                                    title = track.title,
                                    subtitle = track.albumName,
                                    duration = track.duration,
                                    trackNumber = "${index + 1}",
                                    imageUrl = track.imageUrl,
                                    onClick = { onTrackClick(track.id) },
                                    onMenuClick = { onTrackMenuClick(track.id) },
                                    showDivider = index < topTracks.lastIndex,
                                )
                            }
                        }

                        if (albums.isNotEmpty()) {
                            item {
                                SectionHeader("Discography", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
                            }

                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                                    horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumCard(
                                            title = album.name,
                                            artist = album.year?.toString() ?: "",
                                            imageUrl = if (serverUrl != null && album.imageId != null) {
                                                jellyfinImageUrl(serverUrl, album.imageId)
                                            } else null,
                                            onClick = { onAlbumClick(album.id) },
                                            modifier = Modifier.size(width = 150.dp, height = 200.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMoreMenu) {
        ArtistMoreMenu(
            onDismiss = { showMoreMenu = false },
            onPlayAll = {
                onPlayAll()
                showMoreMenu = false
            },
            onAddAllToQueue = {
                onAddAllToQueue()
                showMoreMenu = false
            },
            onShare = {
                onShare()
                showMoreMenu = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistMoreMenu(
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onShare: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MellowSpacing.Sp8),
        ) {
            ArtistMenuAction(PhosphorIcons.Play, "Play All", onClick = onPlayAll)
            ArtistMenuAction(PhosphorIcons.Queue, "Add All to Queue", onClick = onAddAllToQueue)
            ArtistMenuAction(PhosphorIcons.ShareNetwork, "Share", onClick = onShare)
        }
    }
}

@Composable
private fun ArtistMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(icon, label, tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(MellowSpacing.Sp4))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MellowTheme.colors.foreground)
    }
}

private val ARTIST_TABS = listOf("Top Tracks", "Discography")

@Composable
private fun ArtistDetailExpanded(
    onBack: () -> Unit,
    artistName: String,
    artistImageUrl: String?,
    albumCount: Int,
    totalTrackCount: Int,
    overview: String?,
    topTracks: List<ArtistTrack>,
    albums: List<ArtistAlbum>,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onTrackMenuClick: (String) -> Unit,
    serverUrl: String?,
    isFavorite: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMore: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight(),
        ) {
            ArtworkBackground(
                artworkKey = artistImageUrl,
                imageUrl = artistImageUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 70.dp,
                imageAlpha = 0.25f,
                overlayColors = emptyList(),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
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
                    IconButton(onClick = onMore) {
                        Icon(PhosphorIcons.DotsThreeVertical, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (LocalWindowWidthClass.current == WindowWidthClass.Medium) {
                                Modifier.padding(bottom = LocalMiniPlayerPadding.current)
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    MellowImage(
                        model = artistImageUrl,
                        contentDescription = "Artist image",
                        fallbackIcon = PhosphorIcons.User,
                        fallbackIconSize = 48.dp,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = MellowSpacing.Sp12)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(MellowTheme.colors.surface),
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp5))
                    Text(
                        artistName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MellowTheme.colors.foreground,
                    )
                    if (albumCount > 0 || totalTrackCount > 0) {
                        val parts = mutableListOf<String>()
                        if (albumCount > 0) parts.add("$albumCount albums")
                        if (totalTrackCount > 0) parts.add("$totalTrackCount tracks")
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MellowTheme.colors.muted,
                            modifier = Modifier.padding(top = MellowSpacing.Sp2),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = MellowSpacing.Sp5),
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
                    }
                    if (!overview.isNullOrBlank()) {
                        Text(
                            overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MellowTheme.colors.muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = MellowSpacing.Sp4, start = MellowSpacing.Sp5, end = MellowSpacing.Sp5),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MellowTheme.colors.border)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            MellowTabBar(
                tabs = ARTIST_TABS,
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(top = MellowSpacing.Sp3, bottom = MellowSpacing.Sp3),
            )

            when (selectedTab) {
                0 -> {
                    AdaptiveTrackGrid(
                        items = topTracks,
                        key = { it.id },
                        contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + LocalMiniPlayerPadding.current),
                        modifier = Modifier.fillMaxSize(),
                    ) { index, track ->
                        TrackRow(
                            title = track.title,
                            subtitle = track.albumName,
                            duration = track.duration,
                            trackNumber = "${index + 1}",
                            imageUrl = track.imageUrl,
                            onClick = { onTrackClick(track.id) },
                            onMenuClick = { onTrackMenuClick(track.id) },
                            showDivider = false,
                        )
                    }
                }
                1 -> {
                    // Discography grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(
                            start = MellowSpacing.Sp4,
                            end = MellowSpacing.Sp4,
                            bottom = MellowSpacing.Sp16 + LocalMiniPlayerPadding.current,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItems(albums, key = { it.id }) { album ->
                            AlbumCard(
                                title = album.name,
                                artist = album.year?.toString() ?: "",
                                imageUrl = if (serverUrl != null && album.imageId != null) {
                                    jellyfinImageUrl(serverUrl, album.imageId)
                                } else null,
                                onClick = { onAlbumClick(album.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(
    artistName: String,
    artistImageUrl: String?,
    albumCount: Int,
    trackCount: Int = 0,
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
    ) {
        MellowImage(
            model = artistImageUrl,
            contentDescription = "Artist image",
            fallbackIcon = PhosphorIcons.User,
            fallbackIconSize = 48.dp,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MellowTheme.colors.surface),
        )
        Spacer(Modifier.height(MellowSpacing.Sp5))
        Text(artistName, style = MaterialTheme.typography.displaySmall, color = MellowTheme.colors.foreground)
        if (albumCount > 0 || trackCount > 0) {
            val parts = mutableListOf<String>()
            if (albumCount > 0) parts.add("$albumCount albums")
            if (trackCount > 0) parts.add("$trackCount tracks")
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
                modifier = Modifier.padding(top = MellowSpacing.Sp2),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MellowSpacing.Sp5),
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
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MellowTheme.colors.foreground, modifier = modifier)
}
