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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

private val TABS = listOf("Tracks", "Albums", "Artists")

@Composable
fun FavoritesScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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

        when (selectedTab) {
            0 -> FavTracksPanel()
            1 -> FavAlbumsPanel()
            2 -> FavArtistsPanel()
        }
    }
}

@Composable
private fun FavTracksPanel() {
    LazyColumn(contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4)) {
        items(favTracks, key = { it.first }) { (title, sub) ->
            TrackRow(
                title = title,
                subtitle = sub,
                duration = "4:50",
                isFavorite = true,
                onFavoriteClick = {},
                onClick = {},
            )
        }
    }
}

@Composable
private fun FavAlbumsPanel() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        items(favAlbums, key = { it.first }) { (title, artist) ->
            AlbumCard(title = title, artist = artist, imageUrl = null, onClick = {})
        }
    }
}

@Composable
private fun FavArtistsPanel() {
    LazyColumn(contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4)) {
        items(favArtists, key = { it.first }) { (name, count) ->
            ArtistRow(name = name, albumCount = count, imageUrl = null, onClick = {})
        }
    }
}

private val favTracks = listOf(
    "Reckoner" to "Radiohead · In Rainbows",
    "Weird Fishes/Arpeggi" to "Radiohead · In Rainbows",
    "Bodysnatchers" to "Radiohead · In Rainbows",
    "Let It Happen" to "Tame Impala · Currents",
    "Nikes" to "Frank Ocean · Blonde",
)

private val favAlbums = listOf(
    "In Rainbows" to "Radiohead",
    "Currents" to "Tame Impala",
    "OK Computer" to "Radiohead",
)

private val favArtists = listOf(
    "Radiohead" to 9,
    "Tame Impala" to 4,
    "Frank Ocean" to 2,
)
