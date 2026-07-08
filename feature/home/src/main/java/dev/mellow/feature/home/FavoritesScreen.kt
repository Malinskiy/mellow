package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.hilt.navigation.compose.hiltViewModel
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import java.time.Duration

private val TABS = listOf("Tracks", "Albums", "Artists")

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    serverId: String = "",
    serverUrl: String? = null,
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
) {
    val viewModel: FavoritesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(serverId) {
        viewModel.loadFavorites(serverId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        )

        MellowTabBar(
            tabs = TABS,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
        )

        if (state.isLoading) {
            LoadingContent()
        } else {
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
                                    subtitle = track.artistName ?: "",
                                    duration = formatFavDuration(track.duration),
                                    imageUrl = if (serverUrl != null && track.imageId != null) {
                                        jellyfinImageUrl(serverUrl, track.imageId!!)
                                    } else null,
                                    isFavorite = true,
                                    onClick = { onTrackClick(track.id) },
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
                            columns = GridCells.Fixed(2),
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

private fun formatFavDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
