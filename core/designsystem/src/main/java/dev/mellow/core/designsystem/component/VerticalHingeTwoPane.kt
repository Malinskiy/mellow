package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalHingeTwoPane(
    leftPaneWidth: Dp,
    hingeWidth: Dp = 16.dp,
    modifier: Modifier = Modifier,
    leftContent: @Composable BoxScope.() -> Unit,
    rightContent: @Composable BoxScope.() -> Unit,
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(leftPaneWidth)
                .fillMaxHeight(),
            content = leftContent,
        )
        Spacer(modifier = Modifier.width(hingeWidth))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            content = rightContent,
        )
    }
}
