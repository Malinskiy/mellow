package dev.mellow.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    onCreatePlaylist: (String) -> Unit = {},
) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MellowTheme.colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
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

    if (showDialog) {
        CreatePlaylistDialog(
            onDismiss = { showDialog = false },
            onCreate = { name ->
                showDialog = false
                onCreatePlaylist(name)
            },
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MellowTheme.colors.surfaceElevated,
        titleContentColor = MellowTheme.colors.foreground,
        textContentColor = MellowTheme.colors.foreground,
        title = { Text("New Playlist") },
        text = {
            TextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = {
                    Text("Playlist name", color = MellowTheme.colors.muted)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MellowTheme.colors.foreground,
                    unfocusedTextColor = MellowTheme.colors.foreground,
                    focusedContainerColor = MellowTheme.colors.surface,
                    unfocusedContainerColor = MellowTheme.colors.surface,
                    cursorColor = MellowTheme.colors.foreground,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(playlistName.trim()) },
                enabled = playlistName.isNotBlank(),
            ) {
                Text("Create", color = if (playlistName.isNotBlank()) MellowTheme.colors.foreground else MellowTheme.colors.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MellowTheme.colors.muted)
            }
        },
    )
}
