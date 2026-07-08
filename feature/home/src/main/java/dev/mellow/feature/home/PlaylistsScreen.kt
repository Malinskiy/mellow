package dev.mellow.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.theme.MellowPalette
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
                shape = CircleShape,
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

            EmptyContent("No playlists yet")
        }
    }
}
