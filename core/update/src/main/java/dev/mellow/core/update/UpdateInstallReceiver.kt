package dev.mellow.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the status of the session [ApkInstaller] committed and publishes it to [InstallStateHolder]. When the
 * system needs the user's confirmation it launches the confirmation UI (this may fire more than once; launching again
 * is harmless). Declared in the app manifest with `exported="false"`; only the explicit PendingIntent reaches it.
 */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var installStateHolder: InstallStateHolder

    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        if (extras == null) {
            Log.w(LOG_TAG, "Install status broadcast without extras")
            return
        }
        val state = InstallResultMapper.map(extras)
        val message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = extras.getInt(PackageInstaller.EXTRA_SESSION_ID, InstallStateHolder.NO_SESSION)
        if (sessionId != installStateHolder.activeSessionId) {
            // A session abandoned to make room for a retry, or one committed by a previous process: not ours now.
            val active = installStateHolder.activeSessionId
            Log.i(LOG_TAG, "Ignoring status for session $sessionId (active $active): $state")
            return
        }
        when (state) {
            InstallState.PendingUserAction -> {
                val confirmation = extras.intentExtraCompat()
                if (confirmation == null) {
                    Log.w(LOG_TAG, "Install needs user action but carried no intent; message=$message")
                    installStateHolder.publish(InstallState.Failure(PackageInstaller.STATUS_FAILURE, message))
                    return
                }
                try {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: RuntimeException) {
                    Log.w(LOG_TAG, "Could not show the install confirmation", e)
                    installStateHolder.publish(InstallState.Failure(PackageInstaller.STATUS_FAILURE, e.message))
                    return
                }
            }
            is InstallState.Cancelled ->
                Log.w(LOG_TAG, "Install cancelled: code=${state.code} message=$message")
            is InstallState.VerificationBlocked ->
                Log.w(
                    LOG_TAG,
                    "Install blocked by developer verification: reason=${state.reason} " +
                        "hasContextIntent=${state.contextIntent != null} message=$message",
                )
            is InstallState.Failure ->
                Log.w(LOG_TAG, "Install failed: code=${state.code} message=$message")
            InstallState.Idle, InstallState.Success -> Unit
        }
        installStateHolder.publish(state, sessionId)
    }
}
