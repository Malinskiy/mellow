package dev.mellow.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.designsystem.component.AlbumCard
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class ArtistTrack(val id: String, val title: String, val duration: String, val albumName: String, val imageUrl: String? = null)

data class ArtistAlbum(val id: String, val name: String, val year: Int?, val imageId: String?)

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    artistName: String = "",
    artistImageUrl: String? = null,
    albumCount: Int = 0,
    overview: String? = null,
    topTracks: List<ArtistTrack> = emptyList(),
    albums: List<ArtistAlbum> = emptyList(),
    isLoading: Boolean = false,
    isSyncing: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    serverUrl: String? = null,
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        when {
            isLoading -> LoadingContent(message = "Loading artist…")
            error != null -> ErrorContent(message = error, onRetry = onRetry)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = MellowSpacing.Sp16 + MellowSpacing.Sp16),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MellowTheme.colors.foreground)
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.MoreVert, "More", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    item {
                        ArtistHero(
                            artistName = artistName,
                            artistImageUrl = artistImageUrl,
                            albumCount = albumCount,
                            trackCount = topTracks.size,
                            isFavorite = isFavorite,
                            onPlayAll = onPlayAll,
                            onShuffle = onShuffle,
                            onFavoriteClick = onFavoriteClick,
                        )
                    }

                    if (topTracks.isNotEmpty()) {
                        item {
                            SectionHeader("Top Tracks", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
                        }

                        itemsIndexed(topTracks, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                title = track.title,
                                subtitle = track.albumName,
                                duration = track.duration,
                                trackNumber = "${index + 1}",
                                imageUrl = track.imageUrl,
                                onClick = { onTrackClick(track.id) },
                                onMenuClick = { onTrackMenuClick(track.id) },
                                showDivider = index < topTracks.lastIndex,
                                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                            )
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item {
                            SectionHeader("Discography", modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp4))
                        }

                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
                                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    AlbumCard(
                                        title = album.name,
                                        artist = album.year?.toString() ?: "",
                                        imageUrl = if (serverUrl != null && album.imageId != null) {
                                            jellyfinImageUrl(serverUrl, album.imageId)
                                        } else null,
                                        onClick = { onAlbumClick(album.id) },
                                        modifier = Modifier.size(width = 150.dp, height = 200.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(
    artistName: String,
    artistImageUrl: String?,
    albumCount: Int,
    trackCount: Int = 0,
    isFavorite: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp4),
    ) {
        AsyncImage(
            model = artistImageUrl,
            contentDescription = "Artist image",
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MellowTheme.colors.surface),
            error = ColorPainter(MellowTheme.colors.surface),
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MellowTheme.colors.surface),
        )
        Spacer(Modifier.height(MellowSpacing.Sp5))
        Text(artistName, style = MaterialTheme.typography.displaySmall, color = MellowTheme.colors.foreground)
        if (albumCount > 0 || trackCount > 0) {
            val parts = mutableListOf<String>()
            if (albumCount > 0) parts.add("$albumCount albums")
            if (trackCount > 0) parts.add("$trackCount tracks")
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
                modifier = Modifier.padding(top = MellowSpacing.Sp2),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MellowSpacing.Sp5),
        ) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onPlayAll,
                modifier = Modifier
                    .size(52.dp)
                    .background(MellowPalette.Stone200, MellowShapes.Full),
            ) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = MellowPalette.Stone950, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (isFavorite) MellowTheme.colors.favorite else MellowTheme.colors.muted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MellowTheme.colors.foreground, modifier = modifier)
}
