package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.AnimatedPlayPauseIcon
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class LyricsLine(
    val startMs: Long,
    val text: String,
)

@Composable
fun LyricsScreen(
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    trackName: String = "",
    artistName: String = "",
    albumImageUrl: String? = null,
    lyrics: List<LyricsLine> = emptyList(),
    isLoadingLyrics: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isPlaying: Boolean = false,
    onClose: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
) {
    val activeIndex by remember(lyrics, positionMs) {
        derivedStateOf {
            if (lyrics.isEmpty()) -1
            else lyrics.indexOfLast { it.startMs <= positionMs }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetIndex = (activeIndex + 2).coerceAtMost(lyrics.lastIndex)
            listState.animateScrollToItem(targetIndex, scrollOffset = -200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.background(MellowTheme.colors.background)),
    ) {
        if (!embedded && albumImageUrl != null) {
            AsyncImage(
                model = albumImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(120.dp)
                    .graphicsLayer { alpha = 0.3f },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MellowTheme.colors.background.copy(alpha = 0.6f),
                                MellowTheme.colors.background.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)),
        ) {
            LyricsTopBar(
                trackName = trackName,
                artistName = artistName,
                albumImageUrl = albumImageUrl,
                onClose = onClose,
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoadingLyrics -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MellowTheme.colors.foreground,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    lyrics.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                PhosphorIcons.MusicNote,
                                contentDescription = null,
                                tint = MellowTheme.colors.muted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(MellowSpacing.Sp4))
                            Text(
                                "No lyrics available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MellowTheme.colors.muted,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = MellowSpacing.Sp6),
                            verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = MellowSpacing.Sp8,
                                bottom = MellowSpacing.Sp16,
                            ),
                        ) {
                            itemsIndexed(lyrics, key = { idx, _ -> idx }) { index, line ->
                                val isActive = index == activeIndex
                                val isPast = index < activeIndex
                                val alpha = when {
                                    isActive -> 1f
                                    isPast -> 0.4f
                                    else -> 0.6f
                                }
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = if (isActive) 24.sp else 20.sp,
                                    ),
                                    color = MellowTheme.colors.foreground.copy(alpha = alpha),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeekTo(line.startMs) }
                                        .padding(vertical = MellowSpacing.Sp1),
                                )
                            }
                        }
                    }
                }
            }

            LyricsMiniControls(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onSeekTo = onSeekTo,
                onPlayPauseClick = onPlayPauseClick,
                onSkipPreviousClick = onSkipPreviousClick,
                onSkipNextClick = onSkipNextClick,
            )
        }
    }
}

@Composable
private fun LyricsTopBar(
    trackName: String,
    artistName: String,
    albumImageUrl: String?,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onClose) {
            Icon(PhosphorIcons.CaretDown, "Close", tint = MellowTheme.colors.foreground)
        }

        Spacer(Modifier.width(MellowSpacing.Sp2))

        if (albumImageUrl != null) {
            AsyncImage(
                model = albumImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(MellowShapes.Small)
                    .background(MellowTheme.colors.surface),
            )
            Spacer(Modifier.width(MellowSpacing.Sp3))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                trackName.ifEmpty { "No track" },
                style = MaterialTheme.typography.titleMedium,
                color = MellowTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artistName.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LyricsMiniControls(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val displayProgress = if (isSeeking) seekProgress else progress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6)
            .padding(bottom = MellowSpacing.Sp6),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(36.dp)) {
                Icon(PhosphorIcons.SkipBack, "Previous", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(MellowSpacing.Sp4))

            Slider(
                value = displayProgress.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    seekProgress = value
                },
                onValueChangeFinished = {
                    isSeeking = false
                    onSeekTo((seekProgress * durationMs).toLong())
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .shadow(1.dp, CircleShape)
                            .background(MellowTheme.colors.foreground, CircleShape),
                    )
                },
                track = { sliderState ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MellowPalette.Stone700),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MellowTheme.colors.foreground),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(MellowSpacing.Sp4))

            AnimatedPlayPauseIcon(
                isPlaying = isPlaying,
                onToggle = onPlayPauseClick,
                iconSize = 20.dp,
                tint = MellowTheme.colors.foreground,
            )

            Spacer(Modifier.width(MellowSpacing.Sp4))

            IconButton(onClick = onSkipNextClick, modifier = Modifier.size(36.dp)) {
                Icon(PhosphorIcons.SkipForward, "Next", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp12),
        ) {
            val displayMs = if (isSeeking) (seekProgress * durationMs).toLong() else positionMs
            Text(formatLyricsMs(displayMs), style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            Text(formatLyricsMs(durationMs), style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
        }
    }
}

private fun formatLyricsMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
