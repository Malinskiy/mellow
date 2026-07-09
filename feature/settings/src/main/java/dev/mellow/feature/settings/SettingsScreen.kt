package dev.mellow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.network.ConnectionState

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    serverUrl: String = "",
    connectionState: ConnectionState = ConnectionState.Offline,
    lastSyncTimestamp: Long = 0L,
    isSyncing: Boolean = false,
    syncProgress: SyncProgress? = null,
    isCleaningUp: Boolean = false,
    isForceOffline: Boolean = false,
    autoSyncIntervalHours: Int = 6,
    onSyncNow: () -> Unit = {},
    onCleanup: () -> Unit = {},
    onForceOfflineChange: (Boolean) -> Unit = {},
    onAutoSyncIntervalChange: (Int) -> Unit = {},
    downloadQuality: String = "original",
    wifiOnly: Boolean = true,
    storageCap: Long = 10L * 1024 * 1024 * 1024,
    autoCleanupDays: Int = 30,
    totalDownloadedBytes: Long = 0L,
    onDownloadQualityChange: (String) -> Unit = {},
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onStorageCapChange: (Long) -> Unit = {},
    onAutoCleanupChange: (Int) -> Unit = {},
    onClearAllDownloads: () -> Unit = {},
) {
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var showStorageCapPicker by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showIntervalPicker) {
        SyncIntervalPickerDialog(
            currentInterval = autoSyncIntervalHours,
            onSelect = { hours ->
                onAutoSyncIntervalChange(hours)
                showIntervalPicker = false
            },
            onDismiss = { showIntervalPicker = false },
        )
    }

    if (showQualityPicker) {
        DownloadQualityPickerDialog(
            currentQuality = downloadQuality,
            onSelect = { quality ->
                onDownloadQualityChange(quality)
                showQualityPicker = false
            },
            onDismiss = { showQualityPicker = false },
        )
    }

    if (showStorageCapPicker) {
        StorageCapPickerDialog(
            currentCap = storageCap,
            onSelect = { bytes ->
                onStorageCapChange(bytes)
                showStorageCapPicker = false
            },
            onDismiss = { showStorageCapPicker = false },
        )
    }

    if (showClearConfirmation) {
        ClearAllConfirmationDialog(
            totalBytes = totalDownloadedBytes,
            onConfirm = onClearAllDownloads,
            onDismiss = { showClearConfirmation = false },
        )
    }

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

        SettingsSection("Sync")
        ConnectionStatusRow(connectionState)
        LastSyncedRow(lastSyncTimestamp, isSyncing, syncProgress, onSyncNow)
        CleanupRow(isCleaningUp, onCleanup)
        SettingsRow(
            icon = Icons.Filled.Sync,
            title = "Auto-Sync Frequency",
            value = formatSyncInterval(autoSyncIntervalHours),
            onClick = { showIntervalPicker = true },
        )
        SettingsToggleRow(
            icon = Icons.Filled.Sync,
            title = "Force Offline Mode",
            subtitle = "Play downloaded music only",
            checked = isForceOffline,
            onCheckedChange = onForceOfflineChange,
        )
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Server")
        SettingsRow(Icons.Filled.Dns, "Jellyfin Server", serverUrl.ifEmpty { "Not connected" })
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Playback")
        SettingsRow(Icons.Filled.PlayCircle, "Audio Quality", "Original / FLAC")
        SettingsRow(Icons.Filled.PlayCircle, "Transcoding", "When needed · 320 kbps")
        SettingsRow(Icons.Filled.PlayCircle, "Gapless Playback", "Enabled")
        SettingsRow(Icons.Filled.PlayCircle, "ReplayGain", "Album mode")
        HorizontalDivider(color = MellowTheme.colors.border)

        SettingsSection("Downloads & Offline")
        SettingsRow(
            icon = Icons.Filled.Download,
            title = "Download Quality",
            value = formatQualityLabel(downloadQuality),
            onClick = { showQualityPicker = true },
        )
        SettingsToggleRow(
            icon = Icons.Filled.Download,
            title = "Wi-Fi Only",
            subtitle = "Only download over Wi-Fi",
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange,
        )
        StorageBar(usedBytes = totalDownloadedBytes, capBytes = storageCap)
        SettingsRow(
            icon = Icons.Filled.Download,
            title = "Storage Cap",
            value = formatStorageCap(storageCap),
            onClick = { showStorageCapPicker = true },
        )
        SettingsToggleRow(
            icon = Icons.Filled.Download,
            title = "Auto-Cleanup",
            subtitle = "Remove downloads not played in 30 days",
            checked = autoCleanupDays > 0,
            onCheckedChange = { enabled -> onAutoCleanupChange(if (enabled) 30 else 0) },
        )
        ClearAllDownloadsRow(
            totalBytes = totalDownloadedBytes,
            onClick = { showClearConfirmation = true },
        )
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
private fun StorageBar(usedBytes: Long, capBytes: Long) {
    val isUnlimited = capBytes == Long.MAX_VALUE
    val fraction = if (!isUnlimited && capBytes > 0) {
        (usedBytes.toFloat() / capBytes).coerceIn(0f, 1f)
    } else if (isUnlimited) {
        0f
    } else {
        0f
    }
    val freeBytes = if (isUnlimited) 0L else (capBytes - usedBytes).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (isUnlimited) {
                    "${formatBytes(usedBytes)} used"
                } else {
                    "${formatBytes(usedBytes)} of ${formatBytes(capBytes)} used"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
            if (!isUnlimited) {
                Text(
                    "${formatBytes(freeBytes)} free",
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                )
            }
        }
        Spacer(Modifier.height(MellowSpacing.Sp2))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MellowShapes.Full),
            color = MellowTheme.colors.accentStrong,
            trackColor = MellowTheme.colors.surface,
        )
    }
}

@Composable
private fun ClearAllDownloadsRow(totalBytes: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = totalBytes > 0, onClick = onClick)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(
            Icons.Filled.Delete,
            null,
            tint = if (totalBytes > 0) MellowPalette.Red500 else MellowTheme.colors.muted,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text(
                "Clear All Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = if (totalBytes > 0) MellowPalette.Red500 else MellowTheme.colors.muted,
            )
            if (totalBytes > 0) {
                Text(
                    "Free up ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun DownloadQualityPickerDialog(
    currentQuality: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "original" to "Original (FLAC)",
        "high" to "High (320 kbps MP3)",
        "medium" to "Medium (128 kbps Opus)",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Quality") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = MellowSpacing.Sp2),
                    ) {
                        RadioButton(selected = value == currentQuality, onClick = { onSelect(value) })
                        Spacer(Modifier.width(MellowSpacing.Sp2))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StorageCapPickerDialog(
    currentCap: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val gb = 1024L * 1024 * 1024
    val options = listOf(
        5 * gb to "5 GB",
        10 * gb to "10 GB",
        20 * gb to "20 GB",
        50 * gb to "50 GB",
        Long.MAX_VALUE to "Unlimited",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Cap") },
        text = {
            Column {
                options.forEach { (bytes, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(bytes) }
                            .padding(vertical = MellowSpacing.Sp2),
                    ) {
                        RadioButton(selected = bytes == currentCap, onClick = { onSelect(bytes) })
                        Spacer(Modifier.width(MellowSpacing.Sp2))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ClearAllConfirmationDialog(
    totalBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear All Downloads") },
        text = { Text("This will remove all downloaded tracks and free up ${formatBytes(totalBytes)}.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Clear All", color = MellowPalette.Red500) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConnectionStatusRow(connectionState: ConnectionState) {
    val (label, color) = when (connectionState) {
        ConnectionState.Connected -> "Connected" to Color(0xFF22C55E)
        ConnectionState.ServerUnreachable -> "Server Unreachable" to Color(0xFFF59E0B)
        ConnectionState.Offline -> "Offline" to MellowTheme.colors.muted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(Icons.Filled.Sync, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text("Connection Status", style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun LastSyncedRow(
    timestamp: Long,
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    onSyncNow: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(Icons.Filled.Sync, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text("Last Synced", style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
            if (isSyncing && syncProgress != null && syncProgress.total > 0) {
                Text(
                    "${syncProgress.phase}… ${syncProgress.current}/${syncProgress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                )
            } else {
                Text(
                    formatRelativeTime(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                )
            }
        }
        if (isSyncing && syncProgress != null && syncProgress.total > 0) {
            val progress = syncProgress.current.toFloat() / syncProgress.total
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(
                onClick = onSyncNow,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Sync Now", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CleanupRow(isCleaningUp: Boolean, onCleanup: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(Icons.Filled.Sync, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text("Clean Up Library", style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
            Text(
                "Remove items deleted from server",
                style = MaterialTheme.typography.bodySmall,
                color = MellowTheme.colors.muted,
            )
        }
        if (isCleaningUp) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(
                onClick = onCleanup,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Clean Up", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Icon(icon, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MellowSpacing.Sp3),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SyncIntervalPickerDialog(
    currentInterval: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(0 to "Manual only", 1 to "Every hour", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Every 24 hours")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Sync Frequency") },
        text = {
            Column {
                options.forEach { (hours, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hours) }
                            .padding(vertical = MellowSpacing.Sp2),
                    ) {
                        RadioButton(selected = hours == currentInterval, onClick = { onSelect(hours) })
                        Spacer(Modifier.width(MellowSpacing.Sp2))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1_000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        hours == 1L -> "1 hour ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "Yesterday"
        else -> "$days days ago"
    }
}

private fun formatSyncInterval(hours: Int): String = when (hours) {
    0 -> "Manual only"
    1 -> "Every hour"
    else -> "Every $hours hours"
}

private fun formatQualityLabel(quality: String): String = when (quality) {
    "original" -> "Original (FLAC)"
    "high" -> "High (320 kbps MP3)"
    "medium" -> "Medium (128 kbps Opus)"
    else -> quality
}

private fun formatStorageCap(bytes: Long): String {
    if (bytes == Long.MAX_VALUE) return "Unlimited"
    val gb = bytes / (1024.0 * 1024 * 1024)
    return "${gb.toInt()} GB"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes / (1024.0 * 1024 * 1024)
    val mb = bytes / (1024.0 * 1024)
    return if (gb >= 1.0) {
        "%.1f GB".format(gb)
    } else {
        "%.1f MB".format(mb)
    }
}
