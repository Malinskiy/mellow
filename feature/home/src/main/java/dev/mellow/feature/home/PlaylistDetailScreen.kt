package dev.mellow.feature.home

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
import dev.mellow.core.designsystem.component.AdaptiveTrackGrid
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class PlaylistDetailTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val duration: String,
    val imageUrl: String?,
)

/** A playlist's tracks, a page at a time. [isLoading] shows the loading state whatever the list's state. */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playlistName: String = "",
    tracks: LazyPagingItems<PlaylistDetailTrack> = emptyPagingItems(),
    isLoading: Boolean = false,
    onTrackClick: (index: Int, trackId: String) -> Unit = { _, _ -> },
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onTrackMenuClick: (String) -> Unit = {},
    onRemoveTrack: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    PhosphorIcons.ArrowLeft,
                    "Back",
                    tint = MellowTheme.colors.foreground,
                )
            }
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MellowTheme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
        }

        if (tracks.itemCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text(
                    "${tracks.itemCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShuffle) {
                    Icon(
                        PhosphorIcons.Shuffle,
                        "Shuffle",
                        tint = MellowTheme.colors.muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                AnimatedPlayPauseButton(
                    isPlaying = false,
                    onToggle = onPlayAll,
                    buttonSize = 44.dp,
                )
            }
        }

        val refresh = tracks.loadState.refresh
        when {
            isLoading || (tracks.itemCount == 0 && refresh is LoadState.Loading) -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MellowTheme.colors.foreground,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(MellowSpacing.Sp3))
                        Text(
                            "Loading tracks\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MellowTheme.colors.muted,
                        )
                    }
                }
            }
            tracks.itemCount == 0 && refresh is LoadState.Error ->
                ErrorContent(message = "Couldn't load this playlist", onRetry = tracks::retry)
            tracks.itemCount == 0 -> EmptyContent("No tracks in this playlist")
            else -> {
                AdaptiveTrackGrid(
                    itemCount = tracks.itemCount,
                    key = tracks.itemKey { it.id },
                    contentPadding = PaddingValues(bottom = MellowSpacing.Sp16),
                ) { index, _ ->
                    // Null while its page loads: a blank row holds its place.
                    val track = tracks[index]
                    TrackRow(
                        title = track?.title ?: "",
                        subtitle = track?.artistName ?: "",
                        duration = track?.duration ?: "",
                        imageUrl = track?.imageUrl,
                        onClick = { if (track != null) onTrackClick(index, track.id) },
                        onMenuClick = { if (track != null) onTrackMenuClick(track.id) },
                        showDivider = false,
                    )
                }
            }
        }
    }
}
