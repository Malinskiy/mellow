package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MellowDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissLabel: String = "Cancel",
    confirmLabel: String? = null,
    confirmColor: Color = MellowPalette.Stone950,
    confirmBackground: Color = MellowPalette.Stone200,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(MellowShapes.ExtraLarge)
                .background(MellowTheme.colors.surfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.04f), MellowShapes.ExtraLarge),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        letterSpacing = (-0.01).sp,
                    ),
                    color = MellowTheme.colors.foreground,
                )
                if (description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MellowTheme.colors.muted,
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(20.dp))
                    content()
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            ) {
                Text(
                    dismissLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = MellowTheme.colors.muted,
                    modifier = Modifier
                        .clip(MellowShapes.Full)
                        .clickable(onClick = onDismissRequest)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
                if (onConfirm != null && confirmLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = if (confirmEnabled) confirmColor else MellowPalette.Stone500,
                        modifier = Modifier
                            .clip(MellowShapes.Full)
                            .then(
                                if (confirmEnabled) {
                                    Modifier
                                        .background(confirmBackground)
                                        .clickable(onClick = onConfirm)
                                } else {
                                    Modifier.background(MellowPalette.Stone700)
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MellowRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MellowShapes.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) MellowPalette.Stone300 else MellowPalette.Stone600,
                    shape = CircleShape,
                ),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MellowPalette.Stone300),
                )
            }
        }
        Spacer(Modifier.width(MellowSpacing.Sp4))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MellowTheme.colors.foreground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MellowPalette.Stone500,
                )
            }
        }
    }
}
