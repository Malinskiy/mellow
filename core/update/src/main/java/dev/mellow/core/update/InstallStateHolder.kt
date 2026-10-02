package dev.mellow.core.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of the last `PackageInstaller` session commit, as reported to [UpdateInstallReceiver]. */
sealed interface InstallState {
    data object Idle : InstallState

    /** The system confirmation UI has been launched. */
    data object PendingUserAction : InstallState

    /** The update was installed; the system restarts the app. */
    data object Success : InstallState

    /** `STATUS_FAILURE_ABORTED` without a verification reason: the user dismissed the install sheet. */
    data class Cancelled(val code: Int) : InstallState

    /**
     * `STATUS_FAILURE_ABORTED` with a developer-verification reason (see [InstallResultMapper]). [contextIntent] is
     * the OS-provided intent with more context, when one was attached.
     */
    data class VerificationBlocked(val reason: Int, val contextIntent: Intent?) : InstallState

    /** Any other status. [message] is the platform's free-form text, for display and logs only. */
    data class Failure(val code: Int, val message: String?) : InstallState
}

@Singleton
class InstallStateHolder @Inject constructor() {

    private val mutableState = MutableStateFlow<InstallState>(InstallState.Idle)

    val state: StateFlow<InstallState> = mutableState.asStateFlow()

    /** The session [ApkInstaller] last committed; results for any other session are stale and ignored. */
    @Volatile
    var activeSessionId: Int = NO_SESSION
        private set

    /** Marks [sessionId] as the one whose results matter and clears any earlier outcome. */
    fun begin(sessionId: Int) {
        activeSessionId = sessionId
        mutableState.value = InstallState.Idle
    }

    /**
     * Publishes [state] for [sessionId]. Returns false (and publishes nothing) when the session is not the active
     * one — e.g. a session abandoned to make room for a retry, or one from a previous process. Pass
     * [NO_SESSION] for states this process produced itself.
     */
    fun publish(state: InstallState, sessionId: Int = NO_SESSION): Boolean {
        if (sessionId != NO_SESSION && sessionId != activeSessionId) return false
        mutableState.value = state
        return true
    }

    fun reset() {
        mutableState.value = InstallState.Idle
    }

    companion object {
        const val NO_SESSION = -1
    }
}
