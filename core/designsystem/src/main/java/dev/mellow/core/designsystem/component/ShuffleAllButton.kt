package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowTheme

/** The floating "Shuffle all" button over a list: Favorites and the Library's Tracks tab. */
@Composable
fun ShuffleAllButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MellowTheme.colors.foreground,
        contentColor = MellowTheme.colors.background,
        modifier = modifier,
    ) {
        Icon(PhosphorIcons.Shuffle, contentDescription = "Shuffle all", modifier = Modifier.size(24.dp))
    }
}
