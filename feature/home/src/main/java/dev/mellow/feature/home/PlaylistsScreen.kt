package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class PlaylistItem(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?,
)

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistItem> = emptyList(),
    onPlaylistClick: (String) -> Unit = {},
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

            if (playlists.isEmpty()) {
                EmptyContent("No playlists yet")
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            name = playlist.name,
                            trackCount = playlist.trackCount,
                            imageUrl = playlist.imageUrl,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                }
            }
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
private fun PlaylistRow(
    name: String,
    trackCount: Int,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MellowShapes.Small)
                    .background(MellowTheme.colors.surface),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MellowShapes.Small)
                    .background(MellowTheme.colors.surface),
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.width(MellowSpacing.Sp3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MellowTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$trackCount tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
        }
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
