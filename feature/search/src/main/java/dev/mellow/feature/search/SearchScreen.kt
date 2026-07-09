package dev.mellow.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.designsystem.component.ArtistRow
import dev.mellow.core.designsystem.component.ConnectionStatusDot
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
    isConnected: Boolean = false,
    isServerUnreachable: Boolean = false,
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
            )
            Spacer(modifier = Modifier.weight(1f))
            ConnectionStatusDot(
                isConnected = isConnected,
                isServerUnreachable = isServerUnreachable,
            )
        }

        TextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it, serverId) },
            placeholder = { Text("Albums, artists, tracks…", color = MellowPalette.Stone600) },
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

        Spacer(Modifier.height(MellowSpacing.Sp4))

        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MellowTheme.colors.foreground)
                }
            }
            uiState.hasResults -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val topResult = uiState.topResult
                    if (topResult != null) {
                        item { SectionHeader("Top Result") }
                        item {
                            TopResultRow(
                                result = topResult,
                                serverUrl = serverUrl,
                                onClick = {
                                    when (topResult) {
                                        is SearchResult.ArtistResult -> onArtistClick(topResult.artist.id)
                                        is SearchResult.AlbumResult -> onAlbumClick(topResult.album.id)
                                        is SearchResult.TrackResult -> {
                                            val idx = uiState.tracks.indexOfFirst { it.id == topResult.track.id }
                                            if (idx >= 0) onPlayTracks(uiState.tracks, idx)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (uiState.tracks.isNotEmpty()) {
                        item { SectionHeader("Tracks") }
                        itemsIndexed(
                            uiState.tracks.take(5),
                            key = { _, track -> "track_${track.id}" },
                        ) { index, track ->
                            TrackRow(
                                title = track.name,
                                subtitle = "${track.artistName ?: ""} · ${track.albumName ?: ""}",
                                duration = formatDuration(track),
                                imageUrl = trackImageUrl(serverUrl, track),
                                onClick = { onPlayTracks(uiState.tracks, index) },
                                onMenuClick = { onTrackMenuClick(track.id) },
                                showDivider = index < uiState.tracks.take(5).lastIndex,
                                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                            )
                        }
                    }

                    if (uiState.albums.isNotEmpty()) {
                        item { SectionHeader("Albums") }
                        items(uiState.albums, key = { "album_${it.id}" }) { album ->
                            ResultRow(
                                title = album.name,
                                subtitle = "${album.artistName ?: ""} · ${album.year ?: ""}",
                                imageUrl = if (serverUrl.isNotEmpty() && album.imageId != null) {
                                    jellyfinImageUrl(serverUrl, album.imageId!!)
                                } else null,
                                typeTag = "Album",
                                onClick = { onAlbumClick(album.id) },
                            )
                        }
                    }

                    if (uiState.artists.isNotEmpty()) {
                        item { SectionHeader("Artists") }
                        items(uiState.artists, key = { "artist_${it.id}" }) { artist ->
                            ResultRow(
                                title = artist.name,
                                subtitle = "${artist.albumCount} albums",
                                imageUrl = if (serverUrl.isNotEmpty() && artist.imageId != null) {
                                    jellyfinImageUrl(serverUrl, artist.imageId!!)
                                } else null,
                                typeTag = "Artist",
                                isRound = true,
                                onClick = { onArtistClick(artist.id) },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(MellowSpacing.Sp16)) }
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MellowTheme.colors.muted,
        letterSpacing = 0.08.sp,
        modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    )
}

@Composable
private fun TopResultRow(result: SearchResult, serverUrl: String, onClick: () -> Unit) {
    val (title, subtitle, imageUrl, isRound) = when (result) {
        is SearchResult.ArtistResult -> {
            val a = result.artist
            val img = if (serverUrl.isNotEmpty() && a.imageId != null) jellyfinImageUrl(serverUrl, a.imageId!!) else null
            listOf(a.name, "Artist · ${a.albumCount} albums", img, true)
        }
        is SearchResult.AlbumResult -> {
            val a = result.album
            val img = if (serverUrl.isNotEmpty() && a.imageId != null) jellyfinImageUrl(serverUrl, a.imageId!!) else null
            listOf(a.name, "Album · ${a.artistName ?: ""}", img, false)
        }
        is SearchResult.TrackResult -> {
            val t = result.track
            val img = if (serverUrl.isNotEmpty() && t.imageId != null) jellyfinImageUrl(serverUrl, t.imageId!!) else null
            listOf(t.name, "Track · ${t.artistName ?: ""}", img, false)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        AsyncImage(
            model = imageUrl as? String,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MellowTheme.colors.surface),
            error = ColorPainter(MellowTheme.colors.surface),
            modifier = Modifier
                .size(48.dp)
                .clip(if (isRound as Boolean) CircleShape else MellowShapes.Small)
                .background(MellowTheme.colors.surface),
        )
        Spacer(Modifier.width(MellowSpacing.Sp3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title as String,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MellowTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle as String,
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    typeTag: String,
    isRound: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MellowTheme.colors.surface),
            error = ColorPainter(MellowTheme.colors.surface),
            modifier = Modifier
                .size(48.dp)
                .clip(if (isRound) CircleShape else MellowShapes.Small)
                .background(MellowTheme.colors.surface),
        )
        Spacer(Modifier.width(MellowSpacing.Sp3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MellowTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
        }
        Text(
            typeTag,
            style = MaterialTheme.typography.labelSmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier
                .border(1.dp, MellowTheme.colors.border, MellowShapes.Full)
                .padding(horizontal = MellowSpacing.Sp2, vertical = 2.dp),
        )
    }
}

private fun formatDuration(track: Track): String {
    val totalSeconds = track.duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun trackImageUrl(serverUrl: String, track: Track): String? =
    if (serverUrl.isNotEmpty() && track.imageId != null) jellyfinImageUrl(serverUrl, track.imageId!!) else null
