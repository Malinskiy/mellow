package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.QualityBadge
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    artModifier: Modifier = Modifier,
    trackName: String = "",
    artistName: String = "",
    albumName: String = "",
    albumImageUrl: String? = null,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    error: String? = null,
    onCollapse: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onPlayDownloadedClick: () -> Unit = {},
    codec: String? = null,
) {
    val windowWidthClass = LocalWindowWidthClass.current

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
                    .graphicsLayer { alpha = 0.35f },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MellowTheme.colors.background.copy(alpha = 0.5f),
                                MellowTheme.colors.background.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)),
        ) {
        when (windowWidthClass) {
            WindowWidthClass.Expanded -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        NowPlayingTopBar(albumName, onCollapse, onQueueClick)
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(3f, fill = false)
                                .padding(horizontal = MellowSpacing.Sp8),
                        ) {
                            AsyncImage(
                                model = albumImageUrl,
                                contentDescription = "Album art",
                                contentScale = ContentScale.Crop,
                                modifier = artModifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(MellowShapes.Large)
                                    .background(MellowTheme.colors.surface),
                            )
                        }
                        PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                        PlayerProgressBar(progress, positionMs, durationMs, onSeekTo)
                        PlayerPlaybackControls(
                            isPlaying = isPlaying,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipPreviousClick = onSkipPreviousClick,
                            onSkipNextClick = onSkipNextClick,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                        )
                        PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick, reducedBottomPadding = true)
                        Spacer(Modifier.weight(1f))
                    }
                    QueuePanel()
                }
            }
            WindowWidthClass.Medium -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = MellowSpacing.Sp8,
                                end = MellowSpacing.Sp4,
                            ),
                    ) {
                        NowPlayingCollapseButton(onCollapse)
                        AsyncImage(
                            model = albumImageUrl,
                            contentDescription = "Album art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxHeight(0.75f)
                                .aspectRatio(1f)
                                .clip(MellowShapes.Large)
                                .background(MellowTheme.colors.surface),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        if (albumName.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
                                modifier = Modifier.padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp1),
                            ) {
                                Text(
                                    "Playing from",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MellowTheme.colors.muted,
                                    letterSpacing = 0.1.sp,
                                )
                                Text(
                                    albumName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MellowTheme.colors.foreground,
                                )
                            }
                        }
                        PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                        PlayerProgressBar(progress, positionMs, durationMs, onSeekTo, compactVerticalPadding = true)
                        PlayerPlaybackControls(
                            isPlaying = isPlaying,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipPreviousClick = onSkipPreviousClick,
                            onSkipNextClick = onSkipNextClick,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                            compactVerticalPadding = true,
                        )
                        PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick, compactVerticalPadding = true)
                    }
                }
            }
            WindowWidthClass.Compact -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NowPlayingTopBar(albumName, onCollapse, onQueueClick)
                    AlbumArt(albumImageUrl, artModifier = artModifier, modifier = Modifier.weight(1f))
                    PlayerTrackInfo(trackName, artistName, isFavorite, isDownloaded, onFavoriteClick)
                    PlayerProgressBar(progress, positionMs, durationMs, onSeekTo)
                    PlayerPlaybackControls(
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipPreviousClick = onSkipPreviousClick,
                        onSkipNextClick = onSkipNextClick,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        onShuffleClick = onShuffleClick,
                        onRepeatClick = onRepeatClick,
                    )
                    PlayerBottomActions(codec = codec, onLyricsClick = onLyricsClick)
                }
            }
        }
        }

        if (error != null) {
            PlaybackErrorOverlay(
                error = error,
                onRetryClick = onRetryClick,
                onPlayDownloadedClick = onPlayDownloadedClick,
            )
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    error: String,
    onRetryClick: () -> Unit,
    onPlayDownloadedClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background.copy(alpha = 0.92f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MellowSpacing.Sp8),
        ) {
            Icon(
                PhosphorIcons.CloudSlash,
                contentDescription = null,
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Text(
                "Can't reach server",
                style = MaterialTheme.typography.headlineSmall,
                color = MellowTheme.colors.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(MellowSpacing.Sp2))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MellowTheme.colors.muted,
            )
            Spacer(Modifier.height(MellowSpacing.Sp6))
            Button(
                onClick = onRetryClick,
                shape = MellowShapes.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MellowTheme.colors.foreground,
                    contentColor = MellowTheme.colors.background,
                ),
            ) {
                Text("Retry")
            }
            Spacer(Modifier.height(MellowSpacing.Sp3))
            OutlinedButton(
                onClick = onPlayDownloadedClick,
                shape = MellowShapes.Full,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MellowTheme.colors.foreground,
                ),
            ) {
                Icon(
                    PhosphorIcons.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MellowSpacing.Sp2))
                Text("Play downloaded music")
            }
        }
    }
}

@Composable
fun NowPlayingTopBar(albumName: String, onCollapse: () -> Unit, onQueueClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onCollapse) {
            Icon(PhosphorIcons.CaretDown, "Collapse", tint = MellowTheme.colors.foreground)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            Text(albumName.ifEmpty { "Unknown" }, style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.foreground)
        }
        IconButton(onClick = onQueueClick) {
            Icon(PhosphorIcons.Queue, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NowPlayingCollapseButton(onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MellowSpacing.Sp2),
        contentAlignment = Alignment.TopStart,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(PhosphorIcons.CaretDown, "Collapse", tint = MellowTheme.colors.foreground)
        }
    }
}

@Composable
private fun AlbumArt(albumImageUrl: String?, artSize: Dp = 320.dp, artModifier: Modifier = Modifier, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp8),
    ) {
        AsyncImage(
            model = albumImageUrl,
            contentDescription = "Album art",
            contentScale = ContentScale.Crop,
            modifier = artModifier
                .width(artSize)
                .aspectRatio(1f)
                .clip(MellowShapes.Large)
                .background(MellowTheme.colors.surface),
        )
    }
}

@Composable
fun PlayerTrackInfo(
    trackName: String,
    artistName: String,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    onFavoriteClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trackName.ifEmpty { "No track" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MellowTheme.colors.foreground,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDownloaded) {
                    Spacer(Modifier.width(MellowSpacing.Sp2))
                    Icon(
                        PhosphorIcons.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MellowPalette.Green500,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                artistName.ifEmpty { "Unknown artist" },
                style = MaterialTheme.typography.titleLarge,
                color = MellowTheme.colors.accentStrong,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        AnimatedHeartIcon(
            isFavorite = isFavorite,
            onToggle = onFavoriteClick,
            iconSize = 24.dp,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressBar(progress: Float, positionMs: Long, durationMs: Long, onSeekTo: (Long) -> Unit, compactVerticalPadding: Boolean = false) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekProgress else progress

    LaunchedEffect(progress) {
        if (isSeeking && kotlin.math.abs(progress - seekProgress) < 0.02f) {
            isSeeking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = if (compactVerticalPadding) MellowSpacing.Sp1 else MellowSpacing.Sp5),
    ) {
        Slider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = { value ->
                isSeeking = true
                seekProgress = value
            },
            onValueChangeFinished = {
                val seekMs = (seekProgress * durationMs).toLong()
                onSeekTo(seekMs)
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(2.dp, CircleShape)
                        .background(MellowTheme.colors.foreground, CircleShape),
                )
            },
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MellowPalette.Stone700),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MellowTheme.colors.foreground),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val displayMs = if (isSeeking) (seekProgress * durationMs).toLong() else positionMs
            Text(formatMs(displayMs), style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Text(formatMs(durationMs), style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
        }
    }
}

@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    compactVerticalPadding: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = if (compactVerticalPadding) MellowSpacing.Sp1 else MellowSpacing.Sp5),
    ) {
        IconButton(onClick = onShuffleClick, modifier = Modifier.size(36.dp)) {
            Icon(
                PhosphorIcons.Shuffle,
                "Shuffle",
                tint = if (shuffleEnabled) MellowTheme.colors.accentStrong else MellowTheme.colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(44.dp)) {
            Icon(PhosphorIcons.SkipBack, "Previous", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        AnimatedPlayPauseButton(
            isPlaying = isPlaying,
            onToggle = onPlayPauseClick,
            buttonSize = 64.dp,
        )
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = onSkipNextClick, modifier = Modifier.size(44.dp)) {
            Icon(PhosphorIcons.SkipForward, "Next", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = onRepeatClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (repeatMode == 1) PhosphorIcons.RepeatOnce else PhosphorIcons.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != 0) MellowTheme.colors.accentStrong else MellowTheme.colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun PlayerBottomActions(codec: String? = null, onLyricsClick: () -> Unit = {}, reducedBottomPadding: Boolean = false, compactVerticalPadding: Boolean = false) {
    val qualityLabel = when (codec?.lowercase()) {
        "flac", "alac", "wav", "pcm" -> "Lossless"
        "aac", "mp3", "opus", "vorbis", "ogg" -> "Lossy"
        else -> codec?.uppercase() ?: "Unknown"
    }

    if (compactVerticalPadding) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp5, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp1)) {
                Icon(PhosphorIcons.DeviceMobile, "Device", tint = MellowTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text("This device", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp1),
                modifier = Modifier.clickable(onClick = onLyricsClick),
            ) {
                Icon(PhosphorIcons.TextAa, "Lyrics", tint = MellowTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            QualityBadge(codec = codec?.uppercase() ?: "\u2014")
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp2)
                .padding(bottom = if (reducedBottomPadding) MellowSpacing.Sp4 else MellowSpacing.Sp8),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PhosphorIcons.DeviceMobile, "Device", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text("This device", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onLyricsClick),
            ) {
                Icon(PhosphorIcons.TextAa, "Lyrics", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QualityBadge(codec = codec?.uppercase() ?: "\u2014")
                Spacer(Modifier.height(4.dp))
                Text(qualityLabel, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun QueuePanel() {
    val borderColor = MellowTheme.colors.border
    Column(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .background(MellowTheme.colors.background.copy(alpha = 0.3f)),
    ) {
        Text(
            "Up Next",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                "Queue",
                fontSize = 13.sp,
                color = MellowTheme.colors.foreground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .drawBehind {
                        drawLine(
                            color = borderColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    },
            )
            Spacer(Modifier.width(MellowSpacing.Sp4))
            Text(
                "Lyrics",
                fontSize = 13.sp,
                color = MellowTheme.colors.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(MellowSpacing.Sp4),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Queue available from queue screen",
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
