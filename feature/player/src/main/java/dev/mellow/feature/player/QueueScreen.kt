package dev.mellow.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import dev.mellow.core.designsystem.component.TrackRow
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.ClearAll, "Clear", tint = MellowTheme.colors.foreground, modifier = Modifier.size(20.dp))
            }
        }

        Text(
            "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        )

        TrackRow(
            title = "Reckoner",
            subtitle = "Radiohead · In Rainbows",
            duration = "4:50",
            isPlaying = true,
            onClick = {},
            showDivider = true,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
        )

        Text(
            "UP NEXT",
            style = MaterialTheme.typography.labelSmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
        )

        LazyColumn(
            contentPadding = PaddingValues(start = MellowSpacing.Sp4, end = MellowSpacing.Sp4, bottom = MellowSpacing.Sp16),
        ) {
            itemsIndexed(mockQueue, key = { _, t -> t.first }) { index, (title, info) ->
                TrackRow(
                    title = title,
                    subtitle = info,
                    duration = mockQueueDurations[index],
                    onClick = {},
                    onMenuClick = {},
                    showDivider = index < mockQueue.lastIndex,
                )
            }
        }
    }
}

private val mockQueue = listOf(
    "House of Cards" to "Radiohead · In Rainbows",
    "Jigsaw Falling into Place" to "Radiohead · In Rainbows",
    "Videotape" to "Radiohead · In Rainbows",
    "Let It Happen" to "Tame Impala · Currents",
    "Nikes" to "Frank Ocean · Blonde",
    "Alright" to "Kendrick Lamar · To Pimp a Butterfly",
)

private val mockQueueDurations = listOf("5:28", "4:09", "4:24", "7:47", "5:14", "3:39")
