package dev.mellow.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.model.Track
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    serverId: String = "",
    serverUrl: String = "",
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        )

        TextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it, serverId) },
            placeholder = { Text("Search library…", color = MellowPalette.Stone600) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MellowTheme.colors.muted) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("", serverId) }) {
                        Icon(Icons.Filled.Close, "Clear", tint = MellowTheme.colors.muted)
                    }
                }
            },
            shape = MellowShapes.Large,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MellowTheme.colors.surface,
                unfocusedContainerColor = MellowTheme.colors.surface,
                focusedTextColor = MellowTheme.colors.foreground,
                unfocusedTextColor = MellowTheme.colors.foreground,
                cursorColor = MellowTheme.colors.foreground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp4)
                .border(1.dp, MellowTheme.colors.border, MellowShapes.Large),
        )

        Spacer(Modifier.height(MellowSpacing.Sp6))

        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MellowTheme.colors.foreground)
                }
            }
            uiState.results.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "TRACKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MellowTheme.colors.muted,
                            letterSpacing = 0.08.sp,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                        )
                    }
                    itemsIndexed(uiState.results, key = { _, track -> track.id }) { index, track ->
                        val totalSeconds = track.duration.seconds
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        val durationStr = "$minutes:${seconds.toString().padStart(2, '0')}"
                        TrackRow(
                            title = track.name,
                            subtitle = "${track.artistName ?: ""} · ${track.albumName ?: ""}",
                            duration = durationStr,
                            imageUrl = if (serverUrl.isNotEmpty() && track.imageId != null) {
                                dev.mellow.core.common.jellyfinImageUrl(serverUrl, track.imageId!!)
                            } else null,
                            onClick = { onPlayTracks(uiState.results, index) },
                            showDivider = index < uiState.results.lastIndex,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                        )
                    }
                }
            }
            uiState.query.length >= 2 -> {
                EmptyContent("No results found")
            }
            else -> {
                EmptyContent("Search your music library")
            }
        }
    }
}
