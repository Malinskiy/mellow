package dev.mellow.feature.settings.update

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.common.MellowResult
import dev.mellow.core.update.ApkDownloader
import dev.mellow.core.update.ApkInstaller
import dev.mellow.core.update.AppUpdateRepository
import dev.mellow.core.update.DownloadException
import dev.mellow.core.update.DownloadedUpdateStore
import dev.mellow.core.update.GitHubRateLimitedException
import dev.mellow.core.update.InstallResultMapper
import dev.mellow.core.update.InstallState
import dev.mellow.core.update.InstallStateHolder
import dev.mellow.core.update.UpdateCheck
import dev.mellow.core.update.VerifierDetector
import dev.mellow.core.update.VerifyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UpdateErrorKind {
    NetworkError, RateLimited, DownloadFailed, SignatureMismatch, InstallFailed, InstallBlocked, VerifierOffline
}

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data object UpToDate : AppUpdateUiState
    data class Available(
        val tag: String,
        val title: String,
        val notes: String,
        val htmlUrl: String,
        val sizeBytes: Long,
        val verifierPresent: Boolean,
        val alreadyDownloaded: Boolean,
    ) : AppUpdateUiState
    data class Downloading(val tag: String, val progress: Float) : AppUpdateUiState
    data object Verifying : AppUpdateUiState
    data class ReadyToInstall(val tag: String, val verifierPresent: Boolean) : AppUpdateUiState
    data class Installing(val tag: String) : AppUpdateUiState
    data class Error(
        val kind: UpdateErrorKind,
        val message: String?,
        val tag: String?,
        val htmlUrl: String?,
        val contextIntent: Intent? = null,
        val isRetryableBlock: Boolean = false,
    ) : AppUpdateUiState
}

sealed interface AppUpdateEvent {
    data class OpenUrl(val url: String) : AppUpdateEvent
    data object OpenDeveloperOptions : AppUpdateEvent
    data class CopyToClipboard(val text: String) : AppUpdateEvent
    data class OpenUnknownSources(val intent: Intent) : AppUpdateEvent
    data class OpenIntent(val intent: Intent) : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val installStateHolder: InstallStateHolder,
    private val verifierDetector: VerifierDetector,
    private val downloadedStore: DownloadedUpdateStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _dialogDismissed = MutableStateFlow(false)

    /**
     * Whether the host should show [AppUpdateDialog] for the current [uiState]. "Later"/"Hide" only hide the dialog;
     * the state itself (the Settings badge, a running download, a pending install) survives. Any state change
     * shows the dialog again.
     */
    val dialogVisible: StateFlow<Boolean> = combine(_uiState, _dialogDismissed) { state, dismissed ->
        !dismissed && state !is AppUpdateUiState.Idle && state !is AppUpdateUiState.Checking &&
            state !is AppUpdateUiState.UpToDate
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The cached newer release, if any, independent of the dialog's life cycle (drives the Settings badge). */
    val cachedUpdate: StateFlow<UpdateCheck.Available?> = repository.availableUpdate
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _event = Channel<AppUpdateEvent>()
    val event = _event.receiveAsFlow()

    val autoCheckEnabled: StateFlow<Boolean> = repository.autoCheckEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val devApiBaseUrlOverride: StateFlow<String?> = repository.devApiBaseUrlOverride
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether Google's developer-verification component is on this device; drives guidance only. */
    val verifierPresent: Boolean by lazy { verifierDetector.isDeveloperVerifierPresent() }

    private var downloadJob: Job? = null

    /** Set when the user was sent to the "install unknown apps" switch; [onResumed] continues the install. */
    private var awaitingInstallPermission = false

    /** Last tag the user acted on; survives states that do not carry one (e.g. a simulated block). */
    private var lastTag: String? = null
    private var lastHtmlUrl: String? = null

    init {
        viewModelScope.launch {
            installStateHolder.state.collect { state -> handleInstallState(state) }
        }
    }

    private fun setState(state: AppUpdateUiState) {
        if (state != _uiState.value) _dialogDismissed.value = false
        _uiState.value = state
    }

    /** `viewModelScope.launch` that turns unexpected failures (DataStore IO, file system) into a logged, idle state. */
    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Update operation failed", e)
                setState(AppUpdateUiState.Idle)
            }
        }
    }

    private fun handleInstallState(state: InstallState) {
        when (state) {
            InstallState.Idle, InstallState.PendingUserAction -> Unit
            InstallState.Success -> {
                // Only meaningful while this process is still the old build; the system replaces it shortly.
                installStateHolder.reset()
            }
            is InstallState.Cancelled -> {
                installStateHolder.reset()
                val tag = lastTag
                setState(
                    if (tag != null) AppUpdateUiState.ReadyToInstall(tag, verifierPresent) else AppUpdateUiState.Idle,
                )
            }
            is InstallState.VerificationBlocked -> {
                installStateHolder.reset()
                setState(
                    if (state.reason == InstallResultMapper.REASON_NETWORK_UNAVAILABLE) {
                        AppUpdateUiState.Error(
                            kind = UpdateErrorKind.VerifierOffline,
                            message = null,
                            tag = lastTag,
                            htmlUrl = lastHtmlUrl,
                        )
                    } else {
                        AppUpdateUiState.Error(
                            kind = UpdateErrorKind.InstallBlocked,
                            message = null,
                            tag = lastTag,
                            htmlUrl = lastHtmlUrl,
                            contextIntent = state.contextIntent,
                            isRetryableBlock = state.reason == InstallResultMapper.REASON_UNKNOWN,
                        )
                    },
                )
            }
            is InstallState.Failure -> {
                installStateHolder.reset()
                setState(
                    AppUpdateUiState.Error(
                        kind = UpdateErrorKind.InstallFailed,
                        message = state.message?.takeIf { it.length <= MAX_DETAIL_LENGTH },
                        tag = lastTag,
                        htmlUrl = lastHtmlUrl,
                    ),
                )
            }
        }
    }

    fun checkOnStart() {
        if (_uiState.value !is AppUpdateUiState.Idle) return
        safeLaunch {
            repository.cleanupAfterUpdate()

            val downloaded = downloadedStore.validate()
            if (downloaded != null) {
                val available = repository.availableUpdate.first()
                val apk = available?.downloaded
                if (available != null && !available.skipped && apk != null) {
                    remember(available)
                    setState(AppUpdateUiState.ReadyToInstall(apk.tag, verifierPresent))
                    return@safeLaunch
                }
            }

            setState(AppUpdateUiState.Checking)
            handleCheckResult(repository.check(force = false), force = false)
        }
    }

    fun checkNow() {
        if (isBusy()) {
            _dialogDismissed.value = false
            return
        }
        safeLaunch {
            setState(AppUpdateUiState.Checking)
            handleCheckResult(repository.check(force = true), force = true)
        }
    }

    private fun handleCheckResult(result: MellowResult<UpdateCheck>, force: Boolean) {
        when (result) {
            is MellowResult.Success -> when (val check = result.data) {
                UpdateCheck.UpToDate ->
                    setState(if (force) AppUpdateUiState.UpToDate else AppUpdateUiState.Idle)
                is UpdateCheck.Available -> {
                    remember(check)
                    setState(if (check.skipped && !force) AppUpdateUiState.Idle else availableState(check))
                }
            }
            is MellowResult.Error -> {
                if (!force) {
                    setState(AppUpdateUiState.Idle)
                    return
                }
                val rateLimited = result.exception is GitHubRateLimitedException
                setState(
                    AppUpdateUiState.Error(
                        kind = if (rateLimited) UpdateErrorKind.RateLimited else UpdateErrorKind.NetworkError,
                        message = if (rateLimited) {
                            "GitHub is rate-limiting update checks. Try again in an hour."
                        } else {
                            "Couldn't reach GitHub. Check your connection and try again."
                        },
                        tag = null,
                        htmlUrl = null,
                    ),
                )
            }
            MellowResult.Loading -> Unit
        }
    }

    fun download() {
        val tag = currentTag() ?: return
        if (installPending()) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            setState(AppUpdateUiState.Downloading(tag, INDETERMINATE))

            // The release was cached by the check that produced this state; no network needed here.
            val available = try {
                repository.availableUpdate.first()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Could not read the cached release", e)
                null
            }
            if (available == null || available.release.tagName != tag) {
                setState(
                    AppUpdateUiState.Error(
                        kind = UpdateErrorKind.DownloadFailed,
                        message = "This update is no longer available.",
                        tag = tag,
                        htmlUrl = lastHtmlUrl,
                    ),
                )
                return@launch
            }
            remember(available)

            try {
                downloader.download(available.asset, tag).collect { progress ->
                    val fraction = if (progress.totalBytes > 0) {
                        progress.bytesRead.toFloat() / progress.totalBytes.toFloat()
                    } else {
                        INDETERMINATE
                    }
                    setState(AppUpdateUiState.Downloading(tag, fraction))
                }
                setState(AppUpdateUiState.Verifying)
                setState(verifiedState(downloader.verifyAndRecord(tag), tag))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                setState(
                    AppUpdateUiState.Error(
                        kind = UpdateErrorKind.DownloadFailed,
                        message = describe(e),
                        tag = tag,
                        htmlUrl = available.release.htmlUrl,
                    ),
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Download of $tag failed", e)
                setState(
                    AppUpdateUiState.Error(
                        kind = UpdateErrorKind.DownloadFailed,
                        message = "The download failed. Check your connection and try again.",
                        tag = tag,
                        htmlUrl = available.release.htmlUrl,
                    ),
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        setState(AppUpdateUiState.Idle)
    }

    fun install() {
        val tag = currentTag() ?: return
        if (installPending()) {
            // The system sheet for the previous attempt is still open (the user hid our dialog); just show it again.
            setState(AppUpdateUiState.Installing(tag))
            return
        }
        safeLaunch { installInternal(tag) }
    }

    /** Called from the host on `ON_RESUME`; continues an install the unknown-sources switch interrupted. */
    fun onResumed() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        if (!installer.canRequestPackageInstalls()) return
        val tag = (_uiState.value as? AppUpdateUiState.ReadyToInstall)?.tag ?: return
        safeLaunch { installInternal(tag) }
    }

    private suspend fun installInternal(tag: String) {
        setState(AppUpdateUiState.Verifying)
        val verified = downloader.verifyAndRecord(tag)
        if (verified !is VerifyResult.Ok) {
            setState(verifiedState(verified, tag))
            return
        }
        if (!installer.canRequestPackageInstalls()) {
            awaitingInstallPermission = true
            setState(AppUpdateUiState.ReadyToInstall(tag, verifierPresent))
            _event.send(AppUpdateEvent.OpenUnknownSources(installer.unknownSourcesSettingsIntent()))
            return
        }
        setState(AppUpdateUiState.Installing(tag))
        val result = installer.install(downloadedStore.apkFile(tag))
        if (result is MellowResult.Error) {
            setState(
                AppUpdateUiState.Error(
                    kind = UpdateErrorKind.InstallFailed,
                    message = "Couldn't start the installer.",
                    tag = tag,
                    htmlUrl = lastHtmlUrl,
                ),
            )
        }
    }

    /** Download, verification, or an install the system has not answered yet. */
    private fun isBusy(): Boolean {
        val current = _uiState.value
        return current is AppUpdateUiState.Downloading || current is AppUpdateUiState.Verifying ||
            current is AppUpdateUiState.Installing || installPending()
    }

    private fun installPending(): Boolean = installStateHolder.state.value is InstallState.PendingUserAction

    private fun verifiedState(result: VerifyResult, tag: String): AppUpdateUiState = when (result) {
        is VerifyResult.Ok -> AppUpdateUiState.ReadyToInstall(tag, verifierPresent)
        VerifyResult.SignatureMismatch -> AppUpdateUiState.Error(
            kind = UpdateErrorKind.SignatureMismatch,
            message = null,
            tag = tag,
            htmlUrl = lastHtmlUrl,
        )
        VerifyResult.NotAnApk, VerifyResult.WrongPackage -> AppUpdateUiState.Error(
            kind = UpdateErrorKind.DownloadFailed,
            message = "The downloaded file isn't a Mellow update.",
            tag = tag,
            htmlUrl = lastHtmlUrl,
        )
        VerifyResult.NotNewer -> AppUpdateUiState.Error(
            kind = UpdateErrorKind.DownloadFailed,
            message = "The downloaded build isn't newer than the installed one.",
            tag = tag,
            htmlUrl = lastHtmlUrl,
        )
    }

    private fun availableState(check: UpdateCheck.Available): AppUpdateUiState =
        if (check.downloaded != null) {
            AppUpdateUiState.ReadyToInstall(check.release.tagName, verifierPresent)
        } else {
            AppUpdateUiState.Available(
                tag = check.release.tagName,
                title = check.release.name,
                notes = check.release.body.asPlainReleaseNotes(),
                htmlUrl = check.release.htmlUrl,
                sizeBytes = check.asset.sizeBytes,
                verifierPresent = verifierPresent,
                alreadyDownloaded = false,
            )
        }

    fun skipVersion() {
        val tag = (_uiState.value as? AppUpdateUiState.Available)?.tag ?: return
        safeLaunch {
            repository.skipVersion(tag)
            setState(AppUpdateUiState.Idle)
        }
    }

    /** Hides the dialog; the underlying state (download, pending install, available release) is kept. */
    fun dismiss() {
        _dialogDismissed.value = true
    }

    /** Re-opens the dialog for the current state, or for the cached release when nothing is in progress. */
    fun showDialog() {
        val current = _uiState.value
        if (current is AppUpdateUiState.Idle || current is AppUpdateUiState.UpToDate) {
            val cached = cachedUpdate.value ?: return
            remember(cached)
            setState(availableState(cached))
        }
        _dialogDismissed.value = false
    }

    fun setAutoCheck(enabled: Boolean) {
        safeLaunch { repository.setAutoCheckEnabled(enabled) }
    }

    fun openRelease() {
        val url = (_uiState.value as? AppUpdateUiState.Available)?.htmlUrl
            ?: (_uiState.value as? AppUpdateUiState.Error)?.htmlUrl
            ?: lastHtmlUrl
            ?: return
        viewModelScope.launch { _event.send(AppUpdateEvent.OpenUrl(url)) }
    }

    fun openDeveloperOptions() {
        viewModelScope.launch { _event.send(AppUpdateEvent.OpenDeveloperOptions) }
    }

    fun copyAdbCommand() {
        val file = lastTag?.let { "mellow-$it.apk" } ?: "app-release.apk"
        viewModelScope.launch { _event.send(AppUpdateEvent.CopyToClipboard("adb install -r $file")) }
    }

    fun openMoreInfo(intent: Intent) {
        viewModelScope.launch { _event.send(AppUpdateEvent.OpenIntent(intent)) }
    }

    fun setDevApiBaseUrlOverride(url: String?) {
        safeLaunch { repository.setDevApiBaseUrlOverride(url) }
    }

    /** Debug-only: feeds a developer-verification block through the real install-state path. */
    fun simulateVerificationBlock() {
        installStateHolder.publish(
            InstallState.VerificationBlocked(
                reason = InstallResultMapper.REASON_DEVELOPER_BLOCKED,
                contextIntent = null,
            ),
        )
    }

    private fun currentTag(): String? = when (val current = _uiState.value) {
        is AppUpdateUiState.Available -> current.tag
        is AppUpdateUiState.ReadyToInstall -> current.tag
        is AppUpdateUiState.Installing -> current.tag
        is AppUpdateUiState.Error -> current.tag
        else -> null
    }

    private fun remember(available: UpdateCheck.Available) {
        lastTag = available.release.tagName
        lastHtmlUrl = available.release.htmlUrl
    }

    private fun describe(e: DownloadException): String = when (val reason = e.reason) {
        is DownloadException.Reason.Http -> "GitHub answered with HTTP ${reason.code}."
        DownloadException.Reason.Io -> "The download was interrupted. Check your connection and try again."
        is DownloadException.Reason.SizeMismatch -> "The download was incomplete. Try again."
        is DownloadException.Reason.ChecksumMismatch -> "The downloaded file is corrupted. Try again."
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }

    private companion object {
        const val LOG_TAG = "MellowUpdate"
        const val INDETERMINATE = -1f
        const val MAX_DETAIL_LENGTH = 120
    }
}

private val MARKDOWN_HEADING = Regex("""^#{1,6}\s*""", RegexOption.MULTILINE)
private val MARKDOWN_BULLET = Regex("""^\s*[-*]\s+""", RegexOption.MULTILINE)
private val MARKDOWN_EMPHASIS = Regex("""\*\*|__|`""")
private val MARKDOWN_LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
private val BLANK_LINES = Regex("""\n{3,}""")

/** GitHub release bodies are Markdown; the dialog shows plain text, so drop the most common markup. */
internal fun String.asPlainReleaseNotes(): String = this
    .replace("\r\n", "\n")
    .replace(MARKDOWN_LINK) { it.groupValues[1] }
    .replace(MARKDOWN_HEADING, "")
    .replace(MARKDOWN_BULLET, "• ")
    .replace(MARKDOWN_EMPHASIS, "")
    .replace(BLANK_LINES, "\n\n")
    .trim()
