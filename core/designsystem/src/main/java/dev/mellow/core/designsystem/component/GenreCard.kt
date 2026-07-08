package dev.mellow.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun GenreCard(
    name: String,
    count: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MellowTheme.colors.surface,
        shape = MellowShapes.Medium,
        modifier = modifier
            .border(1.dp, MellowTheme.colors.border, MellowShapes.Medium)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(MellowSpacing.Sp4)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MellowTheme.colors.foreground,
            )
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                color = MellowTheme.colors.muted,
                modifier = Modifier.padding(top = MellowSpacing.Sp1),
            )
        }
    }
}
