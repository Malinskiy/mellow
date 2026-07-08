package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.GenreCard
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

private val TABS = listOf("Albums", "Artists", "Tracks", "Genres", "Folders")

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    albums: List<Pair<String, String>> = mockAlbums,
    artists: List<Pair<String, Int>> = mockArtists,
    tracks: List<MockTrack> = mockTracks,
    serverUrl: String? = null,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        LibraryTopBar()

        MellowTabBar(
            tabs = TABS,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
        )

        SortRow(tab = TABS[selectedTab], albumCount = albums.size, artistCount = artists.size, trackCount = tracks.size)

        when (selectedTab) {
            0 -> AlbumsPanel(albums, serverUrl)
            1 -> ArtistsPanel(artists, serverUrl)
            2 -> TracksPanel(tracks, serverUrl)
            3 -> GenresPanel()
            4 -> FoldersPanel()
        }
    }
}

@Composable
private fun LibraryTopBar() {
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
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Sort,
                contentDescription = "Sort",
                tint = MellowTheme.colors.foreground,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = "Server",
                tint = MellowTheme.colors.foreground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SortRow(tab: String, albumCount: Int = 0, artistCount: Int = 0, trackCount: Int = 0) {
    val counts = mapOf(
        "Albums" to "$albumCount albums",
        "Artists" to "$artistCount artists",
        "Tracks" to "$trackCount tracks",
        "Genres" to "8 genres",
        "Folders" to "3 folders",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp1),
    ) {
        Text(
            text = counts[tab] ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Text(
            text = "Recently Added ▾",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
    }
}

@Composable
private fun AlbumsPanel(albums: List<Pair<String, String>>, serverUrl: String?) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        items(albums, key = { it.first }) { (title, artist) ->
            AlbumCard(
                title = title,
                artist = artist,
                imageUrl = null,
                onClick = {},
            )
        }
    }
}

@Composable
private fun ArtistsPanel(artists: List<Pair<String, Int>>, serverUrl: String?) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
    ) {
        items(artists, key = { it.first }) { (name, count) ->
            ArtistRow(
                name = name,
                albumCount = count,
                imageUrl = null,
                onClick = {},
            )
        }
    }
}

@Composable
private fun TracksPanel(tracks: List<MockTrack>, serverUrl: String?) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
    ) {
        items(tracks, key = { it.title }) { track ->
            TrackRow(
                title = track.title,
                subtitle = "${track.artist} · ${track.album}",
                duration = track.duration,
                imageUrl = null,
                onClick = {},
                showDivider = true,
            )
        }
    }
}

@Composable
private fun GenresPanel() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
    ) {
        items(mockGenres, key = { it.first }) { (name, count) ->
            GenreCard(
                name = name,
                count = count,
                onClick = {},
            )
        }
    }
}

@Composable
private fun FoldersPanel() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
    ) {
        items(mockFolders, key = { it.first }) { (name, sub) ->
            TrackRow(
                title = name,
                subtitle = sub,
                duration = "",
                onClick = {},
                showDivider = true,
            )
        }
    }
}

private val mockAlbums = listOf(
    "In Rainbows" to "Radiohead",
    "Currents" to "Tame Impala",
    "Blonde" to "Frank Ocean",
    "To Pimp a Butterfly" to "Kendrick Lamar",
    "Vespertine" to "Bjork",
    "The Dark Side of the Moon" to "Pink Floyd",
    "OK Computer" to "Radiohead",
    "Loveless" to "My Bloody Valentine",
    "Lift Your Skinny Fists" to "Godspeed You! Black Emperor",
    "Remain in Light" to "Talking Heads",
)

private val mockArtists = listOf(
    "Radiohead" to 9,
    "Tame Impala" to 4,
    "Frank Ocean" to 2,
    "Bjork" to 10,
    "Pink Floyd" to 15,
    "Kendrick Lamar" to 5,
    "My Bloody Valentine" to 3,
    "Talking Heads" to 8,
)

data class MockTrack(val title: String, val artist: String, val album: String, val duration: String)

private val mockTracks = listOf(
    MockTrack("15 Step", "Radiohead", "In Rainbows", "3:58"),
    MockTrack("Bodysnatchers", "Radiohead", "In Rainbows", "4:02"),
    MockTrack("Let It Happen", "Tame Impala", "Currents", "7:47"),
    MockTrack("Nikes", "Frank Ocean", "Blonde", "5:14"),
    MockTrack("Alright", "Kendrick Lamar", "To Pimp a Butterfly", "3:39"),
    MockTrack("Pagan Poetry", "Bjork", "Vespertine", "5:14"),
)

private val mockGenres = listOf(
    "Rock" to "42 albums",
    "Electronic" to "28 albums",
    "Hip-Hop" to "19 albums",
    "Jazz" to "15 albums",
    "Classical" to "11 albums",
    "Ambient" to "8 albums",
    "Folk" to "6 albums",
    "Post-Punk" to "4 albums",
)

private val mockFolders = listOf(
    "Music" to "12 folders, 847 files",
    "Audiobooks" to "3 folders, 24 files",
    "Podcasts" to "5 folders, 112 files",
)
