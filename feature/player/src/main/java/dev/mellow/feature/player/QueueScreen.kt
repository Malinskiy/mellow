package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class QueueTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val imageUrl: String?,
)

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: QueueTrack? = null,
    upNext: List<QueueTrack> = emptyList(),
    shuffleEnabled: Boolean = false,
    onTrackClick: (Int) -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onClearClick: () -> Unit = {},
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MellowTheme.colors.foreground)
            }
            Text(
                "Queue",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShuffleClick) {
                Icon(
                    Icons.Filled.Shuffle,
                    "Shuffle",
                    tint = if (shuffleEnabled) MellowTheme.colors.accentStrong else MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onClearClick) {
                Icon(Icons.Filled.ClearAll, "Clear", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
        }

        if (nowPlaying == null && upNext.isEmpty()) {
            EmptyContent("Queue is empty")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = MellowSpacing.Sp16),
            ) {
                if (nowPlaying != null) {
                    item {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MellowTheme.colors.muted,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                        )
                    }
                    item {
                        TrackRow(
                            title = nowPlaying.title,
                            subtitle = "${nowPlaying.artist} · ${nowPlaying.album}",
                            duration = nowPlaying.duration,
                            imageUrl = nowPlaying.imageUrl,
                            isPlaying = true,
                            onClick = {},
                            showDivider = true,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                        )
                    }
                }

                if (upNext.isNotEmpty()) {
                    item {
                        Text(
                            "UP NEXT · ${upNext.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MellowTheme.colors.muted,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
                        )
                    }
                    itemsIndexed(upNext, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                        TrackRow(
                            title = track.title,
                            subtitle = "${track.artist} · ${track.album}",
                            duration = track.duration,
                            imageUrl = track.imageUrl,
                            onClick = { onTrackClick(index) },
                            showDivider = index < upNext.lastIndex,
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
                        )
                    }
                }
            }
        }
    }
}
