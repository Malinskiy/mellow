package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MellowTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp2),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MellowSpacing.Sp4),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MellowPalette.Stone950 else MellowTheme.colors.muted,
                modifier = Modifier
                    .clip(MellowShapes.Full)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(MellowPalette.Stone200)
                                .border(1.dp, MellowPalette.Stone300, MellowShapes.Full)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            )
        }
    }
}
