package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    message: String = "Loading…",
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MellowTheme.colors.foreground, strokeWidth = 2.dp)
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
        }
    }
}

@Composable
fun EmptyContent(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MellowSpacing.Sp8),
        ) {
            Icon(icon, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MellowTheme.colors.muted)
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MellowSpacing.Sp8),
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MellowPalette.Red500, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MellowTheme.colors.muted)
            Spacer(Modifier.height(MellowSpacing.Sp4))
            Button(
                onClick = onRetry,
                shape = MellowShapes.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MellowPalette.Stone200,
                    contentColor = MellowPalette.Stone950,
                ),
            ) {
                Text("Retry")
            }
        }
    }
}
