package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    currentAlbumName: String = "",
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onTrackClick: (Int) -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onClearClick: () -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onRemoveTrack: (Int) -> Unit = {},
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
                Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
            }
            Text(
                "Queue",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShuffleClick) {
                Icon(
                    PhosphorIcons.Shuffle,
                    "Shuffle",
                    tint = if (shuffleEnabled) MellowTheme.colors.accentStrong else MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRepeatClick) {
                Icon(
                    imageVector = if (repeatMode == 1) PhosphorIcons.RepeatOnce else PhosphorIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != 0) MellowTheme.colors.accentStrong else MellowTheme.colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onClearClick) {
                Icon(PhosphorIcons.Broom, "Clear", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
        }

        if (nowPlaying == null && upNext.isEmpty()) {
            EmptyContent("Queue is empty")
        } else {
            val hapticFeedback = LocalHapticFeedback.current
            val headerCount = (if (nowPlaying != null) 2 else 0) + 1
            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                onMoveTrack(from.index - headerCount, to.index - headerCount)
            }

            LazyColumn(
                state = lazyListState,
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
                        Box(
                            modifier = Modifier
                                .padding(horizontal = MellowSpacing.Sp2)
                                .background(MellowTheme.colors.surfaceElevated, MellowShapes.Medium)
                                .padding(horizontal = MellowSpacing.Sp2),
                        ) {
                            TrackRow(
                                title = nowPlaying.title,
                                subtitle = "${nowPlaying.artist} \u00B7 ${nowPlaying.album}",
                                duration = nowPlaying.duration,
                                imageUrl = nowPlaying.imageUrl,
                                isPlaying = true,
                                onClick = {},
                                showDivider = false,
                            )
                        }
                    }
                }

                if (upNext.isNotEmpty()) {
                    item {
                        val label = if (currentAlbumName.isNotEmpty()) {
                            "NEXT UP FROM: $currentAlbumName \u00B7 ${upNext.size} tracks"
                        } else {
                            "UP NEXT \u00B7 ${upNext.size} tracks"
                        }
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MellowTheme.colors.muted,
                            modifier = Modifier.padding(
                                start = MellowSpacing.Sp4,
                                end = MellowSpacing.Sp4,
                                top = MellowSpacing.Sp4,
                                bottom = MellowSpacing.Sp2,
                            ),
                        )
                    }
                    itemsIndexed(upNext, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                        ReorderableItem(reorderableState, key = "${track.id}_$index") { isDragging ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = MellowSpacing.Sp2)
                                    .then(
                                        if (isDragging) {
                                            Modifier
                                                .graphicsLayer {
                                                    shadowElevation = 8f
                                                    scaleX = 1.02f
                                                    scaleY = 1.02f
                                                }
                                                .background(MellowTheme.colors.surfaceElevated)
                                        } else {
                                            Modifier
                                        }
                                    ),
                            ) {
                                Icon(
                                    PhosphorIcons.DotsSixVertical,
                                    contentDescription = "Reorder",
                                    tint = if (isDragging) MellowTheme.colors.foreground else MellowPalette.Stone600,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .draggableHandle(
                                            onDragStarted = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                        ),
                                )
                                Spacer(Modifier.width(MellowSpacing.Sp1))
                                TrackRow(
                                    title = track.title,
                                    subtitle = "${track.artist} \u00B7 ${track.album}",
                                    duration = track.duration,
                                    imageUrl = track.imageUrl,
                                    onClick = { onTrackClick(index) },
                                    showDivider = index < upNext.lastIndex,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onRemoveTrack(index) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        PhosphorIcons.X,
                                        contentDescription = "Remove from queue",
                                        tint = MellowPalette.Stone600,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(MellowSpacing.Sp2))
                            }
                        }
                    }
                }
            }
        }
    }
}
