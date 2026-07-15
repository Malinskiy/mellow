package dev.mellow.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onSignIn: (serverUrl: String, username: String, password: String) -> Unit = { _, _, _ -> },
    isLoading: Boolean = false,
    error: String? = null,
    trustSelfSigned: Boolean = false,
    onTrustSelfSignedChange: (Boolean) -> Unit = {},
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }

    val caretRotation by animateFloatAsState(
        targetValue = if (showAdvancedSettings) 180f else 0f,
        label = "caretRotation",
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MellowPalette.Stone500,
        unfocusedBorderColor = MellowTheme.colors.border,
        focusedContainerColor = MellowTheme.colors.surface,
        unfocusedContainerColor = MellowTheme.colors.surface,
        focusedTextColor = MellowTheme.colors.foreground,
        unfocusedTextColor = MellowTheme.colors.foreground,
        cursorColor = MellowTheme.colors.foreground,
        focusedLabelColor = MellowTheme.colors.muted,
        unfocusedLabelColor = MellowTheme.colors.muted,
    )

    @Suppress("DEPRECATION")
    CompositionLocalProvider(LocalAutofillHighlightColor provides Color.Transparent) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MellowSpacing.Sp6),
        ) {
            Spacer(Modifier.height(80.dp))

            Text(
                text = "Mellow",
                style = MaterialTheme.typography.displayLarge,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "Connect to your Jellyfin server",
                style = MaterialTheme.typography.bodyMedium,
                color = MellowTheme.colors.muted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = MellowSpacing.Sp2),
            )

            Spacer(Modifier.height(MellowSpacing.Sp12))

            Text("Server address", style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.muted)
            Spacer(Modifier.height(MellowSpacing.Sp2))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = { Text("https://jellyfin.example.com", color = MellowPalette.Stone600) },
                shape = MellowShapes.Medium,
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MellowSpacing.Sp5))

            Text("Username", style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.muted)
            Spacer(Modifier.height(MellowSpacing.Sp2))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("Username", color = MellowPalette.Stone600) },
                shape = MellowShapes.Medium,
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MellowSpacing.Sp5))

            Text("Password", style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.muted)
            Spacer(Modifier.height(MellowSpacing.Sp2))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password", color = MellowPalette.Stone600) },
                shape = MellowShapes.Medium,
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            if (error != null) {
                Text(
                    text = error,
                    color = MellowPalette.Red500,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = MellowSpacing.Sp3),
                )
            }

            Button(
                onClick = { onSignIn(serverUrl, username, password) },
                enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank(),
                shape = MellowShapes.Medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MellowPalette.Stone200,
                    contentColor = MellowPalette.Stone950,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MellowPalette.Stone950,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Sign In", style = MaterialTheme.typography.labelLarge)
                }
            }

            TextButton(
                onClick = { showAdvancedSettings = !showAdvancedSettings },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MellowSpacing.Sp3),
            ) {
                Text("Advanced Settings", color = MellowTheme.colors.muted)
                Spacer(Modifier.size(MellowSpacing.Sp2))
                Icon(
                    PhosphorIcons.CaretDown,
                    contentDescription = null,
                    tint = MellowTheme.colors.muted,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(caretRotation),
                )
            }

            AnimatedVisibility(visible = showAdvancedSettings) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrustSelfSignedChange(!trustSelfSigned) }
                        .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                ) {
                    Icon(
                        PhosphorIcons.WarningCircle,
                        contentDescription = null,
                        tint = MellowTheme.colors.muted,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = MellowSpacing.Sp3),
                    ) {
                        Text(
                            "Trust self-signed certificates",
                            style = MaterialTheme.typography.titleMedium,
                            color = MellowTheme.colors.foreground,
                        )
                        Text(
                            "Allow connections to servers with self-signed SSL certificates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MellowTheme.colors.muted,
                        )
                    }
                    Switch(
                        checked = trustSelfSigned,
                        onCheckedChange = onTrustSelfSignedChange,
                    )
                }
            }

            Spacer(Modifier.height(MellowSpacing.Sp12))
        }
    }
    }
}
