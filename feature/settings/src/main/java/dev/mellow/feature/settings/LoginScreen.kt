package dev.mellow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MellowSpacing.Sp6),
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = "Melowdy",
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

        DiscoveredServer()

        DividerWithText("or enter manually")

        Spacer(Modifier.height(MellowSpacing.Sp5))

        Text("Server address", style = MaterialTheme.typography.labelMedium, color = MellowTheme.colors.muted)
        Spacer(Modifier.height(MellowSpacing.Sp2))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            placeholder = { Text("https://jellyfin.example.com", color = MellowPalette.Stone600) },
            shape = MellowShapes.Medium,
            colors = textFieldColors,
            singleLine = true,
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
            Text("Sign In", style = MaterialTheme.typography.labelLarge)
        }

        TextButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MellowSpacing.Sp3),
        ) {
            Text("Advanced Settings", color = MellowTheme.colors.muted)
        }

        Spacer(Modifier.height(MellowSpacing.Sp12))
    }
}

@Composable
private fun DiscoveredServer() {
    Column {
        Text(
            text = "FOUND ON NETWORK",
            style = MaterialTheme.typography.labelSmall,
            color = MellowTheme.colors.muted,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        )
        Spacer(Modifier.height(MellowSpacing.Sp3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MellowTheme.colors.border, MellowShapes.Medium)
                .background(MellowTheme.colors.surface, MellowShapes.Medium)
                .clickable {}
                .padding(MellowSpacing.Sp3),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(MellowPalette.Stone800, MellowShapes.Small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MellowTheme.colors.accentStrong,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MellowSpacing.Sp3),
            ) {
                Text("Home Media Server", style = MaterialTheme.typography.titleMedium, color = MellowTheme.colors.foreground)
                Text("192.168.1.100:8096", style = MaterialTheme.typography.bodySmall, color = MellowTheme.colors.muted)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MellowTheme.colors.online, MellowShapes.Full),
            )
        }
    }
}

@Composable
private fun DividerWithText(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = MellowSpacing.Sp5),
    ) {
        HorizontalDivider(color = MellowTheme.colors.border, modifier = Modifier.weight(1f))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MellowPalette.Stone600,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp3),
        )
        HorizontalDivider(color = MellowTheme.colors.border, modifier = Modifier.weight(1f))
    }
}
