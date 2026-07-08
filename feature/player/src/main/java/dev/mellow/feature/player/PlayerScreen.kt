package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.component.QualityBadge
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    trackName: String = "",
    artistName: String = "",
    albumName: String = "",
    albumImageUrl: String? = null,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isFavorite: Boolean = false,
    onCollapse: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MellowPalette.Stone800)
                .blur(80.dp),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            NowPlayingTopBar(albumName, onCollapse, onQueueClick)
            AlbumArt(albumImageUrl, modifier = Modifier.weight(1f))
            TrackInfo(trackName, artistName, isFavorite)
            ProgressBar(progress, positionMs, durationMs, onSeekTo)
            PlaybackControls(isPlaying, onPlayPauseClick, onSkipPreviousClick, onSkipNextClick)
            BottomActions()
        }
    }
}

@Composable
private fun NowPlayingTopBar(albumName: String, onCollapse: () -> Unit, onQueueClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Filled.KeyboardArrowDown, "Collapse", tint = MellowTheme.colors.foreground)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
            Text(albumName.ifEmpty { "Unknown" }, style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.foreground)
        }
        IconButton(onClick = onQueueClick) {
            Icon(Icons.Outlined.QueueMusic, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AlbumArt(albumImageUrl: String?, modifier: Modifier = Modifier) {
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
            modifier = Modifier
                .width(320.dp)
                .aspectRatio(1f)
                .clip(MellowShapes.Large)
                .background(MellowTheme.colors.surface),
        )
    }
}

@Composable
private fun TrackInfo(trackName: String, artistName: String, isFavorite: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                trackName.ifEmpty { "No track" },
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
            )
            Text(
                artistName.ifEmpty { "Unknown artist" },
                style = MaterialTheme.typography.titleLarge,
                color = MellowTheme.colors.accentStrong,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MellowTheme.colors.favorite else MellowTheme.colors.muted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, positionMs: Long, durationMs: Long, onSeekTo: (Long) -> Unit) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekProgress else progress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp5),
    ) {
        Slider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = { value ->
                isSeeking = true
                seekProgress = value
            },
            onValueChangeFinished = {
                isSeeking = false
                val seekMs = (seekProgress * durationMs).toLong()
                onSeekTo(seekMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = MellowTheme.colors.foreground,
                activeTrackColor = MellowTheme.colors.foreground,
                inactiveTrackColor = MellowPalette.Stone700,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
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
private fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp5),
    ) {
        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Shuffle, "Shuffle", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .size(64.dp)
                .background(MellowTheme.colors.foreground, MellowShapes.Full),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MellowTheme.colors.background,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = onSkipNextClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipNext, "Next", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Repeat, "Repeat", tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun BottomActions() {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp2)
            .padding(bottom = MellowSpacing.Sp8),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhoneAndroid, "Device", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text("This device", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lyrics, "Lyrics", tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QualityBadge(codec = "FLAC")
            Spacer(Modifier.height(4.dp))
            Text("Lossless", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
