package dev.mellow.app.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import dev.mellow.core.designsystem.component.AnimatedAlbumDownloadIndicator
import dev.mellow.core.designsystem.component.AnimatedHeartIcon
import dev.mellow.core.designsystem.component.AnimatedPlayPauseButton
import dev.mellow.core.designsystem.component.AnimatedPlayPauseIcon
import dev.mellow.core.designsystem.component.AnimatedSongDownloadIcon
import dev.mellow.core.designsystem.component.DownloadIconState
import dev.mellow.core.designsystem.component.GrainientBackground
import dev.mellow.core.designsystem.component.IridescenceBackground
import dev.mellow.core.designsystem.component.PixelBlastPlaceholder
import dev.mellow.core.designsystem.component.Shimmer
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun DevIconComparisonScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
        ) {
            IconButton(onClick = onBack) {
                Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
            }
            Text(
                "Dev Tools",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader("Background Shaders") }
            item { BackgroundShaderDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Loading Placeholders")
            }
            item { LoadingPlaceholderDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Play / Pause — Filled")
            }
            item { PlayPauseFilledDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Play / Pause — Outlined")
            }
            item { PlayPauseOutlinedDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Heart / Favorite")
            }
            item { HeartFavoriteDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Song Download Status")
            }
            item { SongDownloadDemo() }
            item {
                Spacer(Modifier.height(MellowSpacing.Sp6))
                SectionHeader("Album Download")
            }
            item { AlbumDownloadDemo() }
            item { Spacer(Modifier.height(MellowSpacing.Sp16)) }
        }
    }
}

private val presetColors = listOf(
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFEF4444),
    Color(0xFFF97316),
    Color(0xFF22C55E),
    Color(0xFF06B6D4),
    Color(0xFF3B82F6),
)

@Composable
private fun BackgroundShaderDemo() {
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var animated by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp6),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Color", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Spacer(Modifier.weight(1f))
            Text("Animate", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            Spacer(Modifier.size(MellowSpacing.Sp2))
            Switch(checked = animated, onCheckedChange = { animated = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2)) {
            presetColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (color == selectedColor) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier,
                        )
                        .clickable { selectedColor = color },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShaderTile("Grainient", Modifier.weight(1f)) {
                GrainientBackground(
                    modifier = Modifier.fillMaxSize(),
                    baseColor = selectedColor,
                    animated = animated,
                )
            }
            ShaderTile("Iridescence", Modifier.weight(1f)) {
                IridescenceBackground(
                    modifier = Modifier.fillMaxSize(),
                    color = selectedColor,
                    animated = animated,
                )
            }
        }

        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            "Multi-color iridescence (album palette)",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val secondary = presetColors[(presetColors.indexOf(selectedColor) + 3) % presetColors.size]
            ShaderTile("Dual-color", Modifier.weight(1f)) {
                IridescenceBackground(
                    modifier = Modifier.fillMaxSize(),
                    color = selectedColor,
                    secondaryColor = secondary,
                    animated = animated,
                )
            }
            ShaderTile("Simple fallback", Modifier.weight(1f)) {
                val light = androidx.compose.ui.graphics.lerp(selectedColor, Color.White, 0.35f)
                val dark = androidx.compose.ui.graphics.lerp(selectedColor, Color.Black, 0.45f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(light, selectedColor, dark),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun ShaderTile(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MellowShapes.Medium),
        ) {
            content()
        }
        Spacer(Modifier.height(MellowSpacing.Sp1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
    }
}

@Composable
private fun LoadingPlaceholderDemo() {
    val pixelColor = MellowTheme.colors.foreground.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp6),
        verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp4),
    ) {
        Text(
            "Cycles load \u2192 image \u2192 reset with random delay",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LoadingDemoTile(
                label = "Shimmer",
                placeholder = { mod -> Shimmer(mod) },
                modifier = Modifier.weight(1f),
            )
            LoadingDemoTile(
                label = "Pixel Blast",
                placeholder = { mod ->
                    PixelBlastPlaceholder(modifier = mod, color = pixelColor)
                },
                modifier = Modifier.weight(1f),
            )
            LoadingDemoTile(
                label = "Pixel Blast (dense)",
                placeholder = { mod ->
                    PixelBlastPlaceholder(modifier = mod, color = pixelColor, patternDensity = 0.9f)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LoadingDemoTile(
    label: String,
    placeholder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (isActive) {
            isLoading = true
            delay(Random.nextLong(800, 3000))
            isLoading = false
            delay(Random.nextLong(2000, 3500))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MellowShapes.Medium)
                .background(MellowTheme.colors.surface),
        ) {
            if (isLoading) {
                placeholder(Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(MellowPalette.Stone700, MellowPalette.Stone500),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.MusicNote,
                        contentDescription = null,
                        tint = MellowPalette.Stone300,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(MellowSpacing.Sp1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
    }
}

@Composable
private fun PlayPauseFilledDemo() {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isPlaying) "Playing — tap to pause" else "Paused — tap to play",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Small" to 40.dp, "Default" to 64.dp, "Large" to 72.dp).forEach { (label, btnSize) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedPlayPauseButton(
                        isPlaying = isPlaying,
                        onToggle = { isPlaying = !isPlaying },
                        buttonSize = btnSize,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun PlayPauseOutlinedDemo() {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isPlaying) "Playing — tap to pause" else "Paused — tap to play",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Small" to 20.dp, "Default" to 24.dp, "Large" to 32.dp).forEach { (label, iconSz) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        onToggle = { isPlaying = !isPlaying },
                        iconSize = iconSz,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun HeartFavoriteDemo() {
    var isFavorite by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        Text(
            if (isFavorite) "Favorited — tap to remove" else "Not favorited — tap to add",
            style = MaterialTheme.typography.bodySmall,
            color = MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Track row" to 22.dp, "Default" to 32.dp, "Now Playing" to 44.dp).forEach { (label, iconSize) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedHeartIcon(
                        isFavorite = isFavorite,
                        onToggle = { isFavorite = !isFavorite },
                        iconSize = iconSize,
                    )
                    Spacer(Modifier.height(MellowSpacing.Sp1))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
                }
            }
        }
    }
}

private enum class SongDlState { Idle, Downloading, Done }

@Composable
private fun SongDownloadDemo() {
    var row1State by remember { mutableStateOf(SongDlState.Idle) }
    var row1Progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        SongDownloadRow("Track 1 — Idle \u2192 Animate", row1State, row1Progress)
        HorizontalDivider(color = MellowTheme.colors.border)
        SongDownloadRow("Track 2 — Downloading", SongDlState.Downloading, 0.63f)
        HorizontalDivider(color = MellowTheme.colors.border)
        SongDownloadRow("Track 3 — Done", SongDlState.Done, 1f)
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        if (row1State != SongDlState.Idle) {
                            row1State = SongDlState.Idle
                            row1Progress = 0f
                            return@clickable
                        }
                        scope.launch {
                            row1State = SongDlState.Downloading
                            for (i in 1..20) {
                                delay(80)
                                row1Progress = i / 20f
                            }
                            row1State = SongDlState.Done
                            row1Progress = 1f
                        }
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text(
                    if (row1State == SongDlState.Idle) "Simulate download" else "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = MellowTheme.colors.foreground,
                )
            }
        }
    }
}

@Composable
private fun SongDownloadRow(title: String, state: SongDlState, progress: Float) {
    val dlState = when (state) {
        SongDlState.Idle -> DownloadIconState.Idle
        SongDlState.Downloading -> DownloadIconState.Downloading
        SongDlState.Done -> DownloadIconState.Done
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        AnimatedSongDownloadIcon(state = dlState, progress = progress, modifier = Modifier.size(32.dp))
    }
}

private enum class AlbumDlState { Idle, Downloading, Done }

@Composable
private fun AlbumDownloadDemo() {
    var state by remember { mutableStateOf(AlbumDlState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var tracksDone by remember { mutableIntStateOf(0) }
    val totalTracks = 12
    val scope = rememberCoroutineScope()

    val dlState = when (state) {
        AlbumDlState.Idle -> DownloadIconState.Idle
        AlbumDlState.Downloading -> DownloadIconState.Downloading
        AlbumDlState.Done -> DownloadIconState.Done
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MellowSpacing.Sp6),
    ) {
        AnimatedAlbumDownloadIndicator(
            state = dlState,
            progress = progress,
            tracksDone = tracksDone,
            totalTracks = totalTracks,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(MellowSpacing.Sp2))
        Text(
            when (state) {
                AlbumDlState.Idle -> "Tap Simulate to start"
                AlbumDlState.Downloading -> "$tracksDone/$totalTracks tracks"
                AlbumDlState.Done -> "Downloaded"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state == AlbumDlState.Done) MellowTheme.colors.success else MellowTheme.colors.muted,
        )
        Spacer(Modifier.height(MellowSpacing.Sp4))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        if (state != AlbumDlState.Idle) return@clickable
                        scope.launch {
                            state = AlbumDlState.Downloading
                            for (i in 1..totalTracks) {
                                delay(120)
                                tracksDone = i
                                progress = i.toFloat() / totalTracks
                            }
                            state = AlbumDlState.Done
                        }
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text("Simulate", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.foreground)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MellowTheme.colors.surface)
                    .clickable {
                        state = AlbumDlState.Idle
                        progress = 0f
                        tracksDone = 0
                    }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.foreground)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MellowTheme.colors.muted,
        modifier = Modifier.padding(start = MellowSpacing.Sp4, top = MellowSpacing.Sp4, bottom = MellowSpacing.Sp2),
    )
}
