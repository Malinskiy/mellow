package dev.mellow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MellowTheme.colors.foreground)
            }
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = MellowTheme.colors.foreground)
        }

        SettingsSection("Server")
        SettingsRow(Icons.Filled.Dns, "Home Media Server", "media.home.lan:8096")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Playback")
        SettingsRow(Icons.Filled.PlayCircle, "Audio Quality", "Original / FLAC")
        SettingsRow(Icons.Filled.PlayCircle, "Transcoding", "When needed · 320 kbps")
        SettingsRow(Icons.Filled.PlayCircle, "Gapless Playback", "Enabled")
        SettingsRow(Icons.Filled.PlayCircle, "ReplayGain", "Album mode")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Downloads")
        SettingsRow(Icons.Filled.Download, "Download Quality", "Opus 128k")
        SettingsRow(Icons.Filled.Download, "Wi-Fi Only", "Enabled")
        SettingsRow(Icons.Filled.Download, "Storage", "2.4 GB used")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Appearance")
        SettingsRow(Icons.Filled.Palette, "Theme", "Dark")
        SettingsRow(Icons.Filled.Palette, "Dynamic Colors", "From album art")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Android Auto")
        SettingsRow(Icons.Filled.DirectionsCar, "Content Tabs", "Recent, Albums, Artists, Playlists")
        SettingsRow(Icons.Filled.DirectionsCar, "Grid Size", "Large artwork")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("About")
        SettingsRow(Icons.Filled.Info, "Version", "0.1.0")
        SettingsRow(Icons.Filled.Info, "Licenses", "")
        Spacer(Modifier.height(MellowSpacing.Sp16))
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MellowTheme.colors.muted,
        modifier = Modifier.padding(start = MellowSpacing.Sp4, top = MellowSpacing.Sp6, bottom = MellowSpacing.Sp2),
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(icon, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
    }
}
