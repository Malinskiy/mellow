package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) {
    Scaffold(
        containerColor = MellowTheme.colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = MellowPalette.Stone200,
                contentColor = MellowPalette.Stone950,
                shape = MellowShapes.Large,
            ) {
                Icon(Icons.Filled.Add, "Create playlist")
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
            ) {
                items(mockPlaylists, key = { it.name }) { playlist ->
                    PlaylistCard(playlist)
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: MockPlaylist) {
    Column(modifier = Modifier.clickable {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MellowShapes.Medium)
                .border(1.dp, MellowTheme.colors.border, MellowShapes.Medium),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(
                            listOf(
                                MellowPalette.Stone800,
                                MellowPalette.Stone700,
                                MellowPalette.Stone600,
                                MellowPalette.Stone800,
                            )[it]
                        ),
                )
            }
        }
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(top = MellowSpacing.Sp2),
        )
        Text(
            "${playlist.trackCount} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier.padding(top = MellowSpacing.Sp1),
        )
    }
}

private data class MockPlaylist(val name: String, val trackCount: Int)

private val mockPlaylists = listOf(
    MockPlaylist("Chill Vibes", 42),
    MockPlaylist("Workout", 28),
    MockPlaylist("Focus", 35),
    MockPlaylist("Late Night", 19),
    MockPlaylist("Road Trip", 67),
    MockPlaylist("Classics", 53),
)
