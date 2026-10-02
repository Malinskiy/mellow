package dev.mellow.app.screenshot

import org.junit.Test
import dev.mellow.feature.settings.update.AppUpdateDialog
import dev.mellow.feature.settings.update.AppUpdateHelpDialog
import dev.mellow.feature.settings.update.AppUpdateUiState
import dev.mellow.feature.settings.update.UpdateErrorKind

abstract class UpdateDialogScreenshotTests : ScreenshotCapture() {

    @Test
    fun dialogAvailable_noVerifier() = capture("update-available-noverifier") {
        AppUpdateDialog(
            state = AppUpdateUiState.Available(
                tag = "v1.2.3",
                title = "Mellow v1.2.3",
                notes = "This is a new release with many bug fixes.\n\n- Fixed an issue with playback.\n- Improved UI.",
                htmlUrl = "https://github.com",
                sizeBytes = 10000000,
                verifierPresent = false,
                alreadyDownloaded = false
            ),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogAvailable_withVerifier() = capture("update-available-verifier") {
        AppUpdateDialog(
            state = AppUpdateUiState.Available(
                tag = "v1.2.3",
                title = "Mellow v1.2.3",
                notes = "This is a new release with many bug fixes.\n\n- Fixed an issue with playback.\n- Improved UI.",
                htmlUrl = "https://github.com",
                sizeBytes = 10000000,
                verifierPresent = true,
                alreadyDownloaded = false
            ),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogReadyToInstall() = capture("update-ready") {
        AppUpdateDialog(
            state = AppUpdateUiState.ReadyToInstall(tag = "v1.2.3", verifierPresent = false),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogDownloading() = capture("update-downloading") {
        AppUpdateDialog(
            state = AppUpdateUiState.Downloading(tag = "v1.2.3", progress = 0.42f),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogErrorSignature() = capture("update-error-signature") {
        AppUpdateDialog(
            state = AppUpdateUiState.Error(
                kind = UpdateErrorKind.SignatureMismatch,
                message = null,
                tag = "v1.2.3",
                htmlUrl = null
            ),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogErrorBlocked() = capture("update-error-blocked") {
        AppUpdateDialog(
            state = AppUpdateUiState.Error(
                kind = UpdateErrorKind.InstallBlocked,
                message = null,
                tag = "v1.2.3",
                htmlUrl = null
            ),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun dialogErrorOffline() = capture("update-error-offline") {
        AppUpdateDialog(
            state = AppUpdateUiState.Error(
                kind = UpdateErrorKind.VerifierOffline,
                message = null,
                tag = "v1.2.3",
                htmlUrl = null
            ),
            onDismiss = {}, onUpdate = {}, onInstall = {}, onSkip = {}, onCancelDownload = {},
            onOpenRelease = {}, onOpenDeveloperOptions = {}, onCopyAdbCommand = {}, onMoreInfo = {}, onRetry = {}
        )
    }

    @Test
    fun helpDialog() = capture("update-help-dialog") {
        AppUpdateHelpDialog(
            onDismiss = {},
            onOpenDeveloperOptions = {},
            onCopyAdbCommand = {}
        )
    }
}
