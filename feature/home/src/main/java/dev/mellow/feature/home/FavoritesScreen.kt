package dev.mellow.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import dev.mellow.core.common.jellyfinImageUrl
import androidx.compose.ui.Alignment
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.CollapsibleToolbarLayout
import dev.mellow.core.designsystem.component.ConnectionStatusDot
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.rememberCollapsibleToolbarState
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import java.time.Duration

private val TABS = listOf("Tracks", "Albums", "Artists")

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    serverId: String = "",
    serverUrl: String? = null,
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val viewModel: FavoritesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(serverId) {
        viewModel.loadFavorites(serverId)
    }

    val toolbarState = rememberCollapsibleToolbarState()
    CollapsibleToolbarLayout(
        state = toolbarState,
        toolbar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MellowTheme.colors.background)
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
            ) {
                Text(
                    "Favorites",
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
        },
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            MellowTabBar(
            tabs = TABS,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
        )

        if (state.isLoading) {
            LoadingContent()
        } else {
            val totalCount = state.tracks.size + state.albums.size + state.artists.size
            if (totalCount > 0) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp1),
                ) {
                    Text(
                        "$totalCount favorites",
                        style = MaterialTheme.typography.bodySmall,
                        color = MellowTheme.colors.muted,
                    )
                    Button(
                        onClick = onShuffleAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MellowPalette.Stone200,
                            contentColor = MellowPalette.Stone950,
                        ),
                        shape = MellowShapes.Full,
                        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                    ) {
                        Icon(PhosphorIcons.Shuffle, null, modifier = Modifier.size(16.dp))
                        Text(
                            "Shuffle All",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = MellowSpacing.Sp2),
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (state.tracks.isEmpty()) {
                        EmptyContent("No favorite tracks yet")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                                TrackRow(
                                    title = track.name,
                                    subtitle = "${track.artistName ?: ""} · ${track.albumName ?: ""}",
                                    duration = formatFavDuration(track.duration),
                                    imageUrl = if (serverUrl != null && track.imageId != null) {
                                        jellyfinImageUrl(serverUrl, track.imageId!!)
                                    } else null,
                                    isFavorite = true,
                                    onClick = { onTrackClick(track.id) },
                                    onMenuClick = { onTrackMenuClick(track.id) },
                                    showDivider = index < state.tracks.lastIndex,
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (state.albums.isEmpty()) {
                        EmptyContent("No favorite albums yet")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                            verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                AlbumCard(
                                    title = album.name,
                                    artist = album.artistName ?: "",
                                    imageUrl = if (serverUrl != null && album.imageId != null) {
                                        jellyfinImageUrl(serverUrl, album.imageId!!)
                                    } else null,
                                    onClick = { onAlbumClick(album.id) },
                                )
                            }
                        }
                    }
                }
                2 -> {
                    if (state.artists.isEmpty()) {
                        EmptyContent("No favorite artists yet")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(state.artists, key = { _, a -> a.id }) { _, artist ->
                                ArtistRow(
                                    name = artist.name,
                                    albumCount = artist.albumCount,
                                    imageUrl = if (serverUrl != null && artist.imageId != null) {
                                        jellyfinImageUrl(serverUrl, artist.imageId!!)
                                    } else null,
                                    onClick = { onArtistClick(artist.id) },
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

private fun formatFavDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
