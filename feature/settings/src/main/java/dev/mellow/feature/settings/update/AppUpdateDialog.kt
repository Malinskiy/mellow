package dev.mellow.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.mellow.core.designsystem.component.MellowDialog
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.MellowSpacing

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onMoreInfo: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is AppUpdateUiState.Available -> {
            MellowDialog(
                onDismissRequest = onDismiss,
                title = "Update available",
                dismissLabel = "Later",
                confirmLabel = if (state.alreadyDownloaded) "Install" else "Update",
                onConfirm = if (state.alreadyDownloaded) onInstall else onUpdate,
                content = {
                    Column {
                        Text(
                            text = if (state.tag in state.title) state.title else "${state.title} (${state.tag})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MellowTheme.colors.foreground,
                            modifier = Modifier.padding(bottom = MellowSpacing.Sp2)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = state.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MellowTheme.colors.foreground,
                                    maxLines = 12,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        TextButton(onClick = onOpenRelease, modifier = Modifier.padding(top = MellowSpacing.Sp1)) {
                            Text("View on GitHub")
                        }
                        if (state.verifierPresent) {
                            Spacer(modifier = Modifier.height(MellowSpacing.Sp2))
                            Text(
                                text = "Mellow isn't registered with Google's developer verification. If Android blocks " +
                                    "the install, you'll need to enable installs from unverified developers once " +
                                    "(Developer options) — Mellow will guide you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MellowTheme.colors.muted
                            )
                        }
                        Spacer(modifier = Modifier.height(MellowSpacing.Sp2))
                        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                            Text("Skip this version", color = MellowTheme.colors.muted)
                        }
                    }
                }
            )
        }
        is AppUpdateUiState.Downloading -> {
            MellowDialog(
                onDismissRequest = onCancelDownload,
                title = "Downloading ${state.tag}",
                dismissLabel = "Cancel",
                content = {
                    Column(modifier = Modifier.padding(vertical = MellowSpacing.Sp2)) {
                        if (state.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(MellowSpacing.Sp2))
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MellowTheme.colors.muted,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            )
        }
        is AppUpdateUiState.Verifying -> {
            MellowDialog(
                onDismissRequest = onDismiss,
                title = "Verifying update",
                description = "Checking the download and its signature.",
                dismissLabel = "Hide",
                content = {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            )
        }
        is AppUpdateUiState.Installing -> {
            MellowDialog(
                onDismissRequest = onDismiss,
                title = "Installing ${state.tag}",
                description = "Android will ask you to confirm. Mellow restarts when the update is done.",
                dismissLabel = "Hide",
                content = {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            )
        }
        is AppUpdateUiState.ReadyToInstall -> {
            MellowDialog(
                onDismissRequest = onDismiss,
                title = "Update ready",
                description = "Mellow ${state.tag} is downloaded and verified.",
                dismissLabel = "Later",
                confirmLabel = "Install",
                onConfirm = onInstall
            )
        }
        is AppUpdateUiState.Error -> {
            when (state.kind) {
                UpdateErrorKind.InstallBlocked -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Android blocked this update",
                        dismissLabel = "Close",
                        confirmLabel = if (state.isRetryableBlock && state.tag != null) "Try again" else null,
                        onConfirm = if (state.isRetryableBlock && state.tag != null) onInstall else null,
                        content = {
                            Column {
                                if (state.isRetryableBlock) {
                                    Text(
                                        text = "Android couldn't finish checking the app. Try again; if it keeps failing:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MellowTheme.colors.foreground,
                                        modifier = Modifier.padding(bottom = MellowSpacing.Sp2)
                                    )
                                }
                                Text(
                                    text = advancedFlowSteps(state.tag),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MellowTheme.colors.foreground
                                )
                                Spacer(modifier = Modifier.height(MellowSpacing.Sp4))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                                        Text("Open developer options")
                                    }
                                    TextButton(onClick = onCopyAdbCommand, modifier = Modifier.fillMaxWidth()) {
                                        Text("Copy adb command")
                                    }
                                    if (state.contextIntent != null) {
                                        TextButton(onClick = onMoreInfo, modifier = Modifier.fillMaxWidth()) {
                                            Text("More info")
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                UpdateErrorKind.VerifierOffline -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Verification Failed",
                        description = "Android couldn't verify the app because it's offline. Connect to the internet and tap Install again.",
                        dismissLabel = "Close",
                        confirmLabel = "Retry",
                        onConfirm = onInstall
                    )
                }
                UpdateErrorKind.SignatureMismatch -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Signature Mismatch",
                        description = "This build was signed with a different key than the installed app. Install manually from GitHub.",
                        dismissLabel = "Close",
                        confirmLabel = "Open on GitHub",
                        onConfirm = onOpenRelease
                    )
                }
                UpdateErrorKind.InstallFailed -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Couldn't install the update",
                        description = listOfNotNull(
                            "Android didn't install this update.",
                            state.message,
                        ).joinToString(" "),
                        dismissLabel = "Close",
                        confirmLabel = if (state.tag != null) "Try again" else null,
                        onConfirm = if (state.tag != null) onInstall else null,
                    )
                }
                UpdateErrorKind.NetworkError, UpdateErrorKind.RateLimited -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Couldn't check for updates",
                        description = state.message ?: "Something went wrong. Try again later.",
                        dismissLabel = "Close",
                    )
                }
                UpdateErrorKind.DownloadFailed -> {
                    MellowDialog(
                        onDismissRequest = onDismiss,
                        title = "Download failed",
                        description = state.message ?: "Something went wrong. Try again later.",
                        dismissLabel = "Close",
                        confirmLabel = "Retry",
                        onConfirm = onRetry,
                    )
                }
            }
        }
        AppUpdateUiState.Idle, AppUpdateUiState.Checking, AppUpdateUiState.UpToDate -> {
            // Nothing to show in dialog format
        }
    }
}

@Composable
fun AppUpdateHelpDialog(
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onCopyAdbCommand: () -> Unit,
) {
    MellowDialog(
        onDismissRequest = onDismiss,
        title = "Unverified developer",
        dismissLabel = "Close",
        content = {
            Column {
                Text(
                    text = "Mellow isn't registered with Google's developer verification. On certified devices, Android " +
                        "requires a one-time setup to install updates:\n\n" + advancedFlowSteps(tag = null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MellowTheme.colors.foreground
                )
                Spacer(modifier = Modifier.height(MellowSpacing.Sp4))
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                        Text("Open developer options")
                    }
                    TextButton(onClick = onCopyAdbCommand, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy adb command")
                    }
                }
            }
        }
    )
}

/** The three advanced-flow steps plus the ADB escape hatch; shared by the blocked dialog and the help dialog. */
internal fun advancedFlowSteps(tag: String?): String {
    val apk = if (tag != null) "mellow-$tag.apk" else "app-release.apk"
    return "1. Open Developer options → turn on Install apps from unverified developers (name may differ by device). " +
        "If Developer options is missing: Settings › About phone › tap Build number 7 times.\n" +
        "2. Confirm, restart your phone, and wait 24 hours (one-time).\n" +
        "3. Come back here and tap Install. Choose \"indefinitely\" when asked, otherwise updates stop working " +
        "after 7 days.\n\n" +
        "Alternative: install with adb install -r $apk from a computer (no wait)."
}
