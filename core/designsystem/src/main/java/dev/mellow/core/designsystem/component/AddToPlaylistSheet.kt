package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class PlaylistPickerItem(
    val id: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<PlaylistPickerItem>,
    onSelect: (playlistId: String) -> Unit,
    onDismiss: () -> Unit,
    onCreateNew: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            Text(
                "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
            ) {
                Icon(
                    PhosphorIcons.Plus,
                    contentDescription = null,
                    tint = MellowTheme.colors.accentStrong,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(MellowSpacing.Sp4))
                Text(
                    "Create New Playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MellowTheme.colors.accentStrong,
                )
            }

            if (playlists.isNotEmpty()) {
                HorizontalDivider(
                    color = MellowTheme.colors.border,
                    modifier = Modifier.padding(vertical = MellowSpacing.Sp1),
                )
            }

            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(playlist.id)
                                onDismiss()
                            }
                            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                    ) {
                        Icon(
                            PhosphorIcons.MusicNote,
                            contentDescription = null,
                            tint = MellowTheme.colors.muted,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(MellowSpacing.Sp4))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MellowTheme.colors.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistInlineDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                onCreateNew(name)
            },
        )
    }
}

@Composable
private fun CreatePlaylistInlineDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MellowTheme.colors.surfaceElevated,
        titleContentColor = MellowTheme.colors.foreground,
        textContentColor = MellowTheme.colors.foreground,
        title = { Text("New Playlist") },
        text = {
            androidx.compose.material3.TextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = {
                    Text("Playlist name", color = MellowTheme.colors.muted)
                },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
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
            androidx.compose.material3.TextButton(
                onClick = { onCreate(playlistName.trim()) },
                enabled = playlistName.isNotBlank(),
            ) {
                Text(
                    "Create",
                    color = if (playlistName.isNotBlank()) MellowTheme.colors.foreground else MellowTheme.colors.muted,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = MellowTheme.colors.muted)
            }
        },
    )
}
