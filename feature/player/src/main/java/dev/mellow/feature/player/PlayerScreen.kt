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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onCollapse: () -> Unit = {},
    onQueueClick: () -> Unit = {},
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
            NowPlayingTopBar(onCollapse, onQueueClick)
            AlbumArt(modifier = Modifier.weight(1f))
            TrackInfo()
            ProgressBar()
            PlaybackControls()
            BottomActions()
        }
    }
}

@Composable
private fun NowPlayingTopBar(onCollapse: () -> Unit, onQueueClick: () -> Unit) {
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
            Text("In Rainbows", style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.foreground)
        }
        IconButton(onClick = onQueueClick) {
            Icon(Icons.Outlined.QueueMusic, "Queue", tint = MellowTheme.colors.foreground, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AlbumArt(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp8),
    ) {
        AsyncImage(
            model = null,
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
private fun TrackInfo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Reckoner", style = MaterialTheme.typography.headlineLarge, color = MellowTheme.colors.foreground)
            Text("Radiohead", style = MaterialTheme.typography.titleLarge, color = MellowTheme.colors.accentStrong, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Favorite, "Favorite", tint = MellowTheme.colors.favorite, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ProgressBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp6, vertical = MellowSpacing.Sp5),
    ) {
        LinearProgressIndicator(
            progress = { 0.35f },
            color = MellowTheme.colors.foreground,
            trackColor = MellowPalette.Stone700,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MellowShapes.Full),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MellowSpacing.Sp2),
        ) {
            Text("1:42", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Text("4:50", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
        }
    }
}

@Composable
private fun PlaybackControls() {
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
        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = MellowTheme.colors.foreground, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(
            onClick = {},
            modifier = Modifier
                .size(64.dp)
                .background(MellowTheme.colors.foreground, MellowShapes.Full),
        ) {
            Icon(Icons.Filled.Pause, "Pause", tint = MellowTheme.colors.background, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(MellowSpacing.Sp8))
        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
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
