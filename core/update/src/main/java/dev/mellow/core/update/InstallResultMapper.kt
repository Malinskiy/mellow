package dev.mellow.core.update

import android.content.pm.PackageInstaller
import android.os.Bundle

/** Turns the extras of a `PackageInstaller` status broadcast into an [InstallState]. Pure; no side effects. */
object InstallResultMapper {

    /**
     * Int extra on `STATUS_FAILURE_ABORTED` when Android's developer verification blocked the install. Declared
     * locally because the platform constants were added in API 36.1, above this module's `compileSdk`. See
     * https://developer.android.com/reference/android/content/pm/PackageInstaller#EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON
     * — for installers targeting API 36 or lower its absence on `STATUS_FAILURE_ABORTED` means the user cancelled.
     */
    const val EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON =
        "android.content.pm.extra.DEVELOPER_VERIFICATION_FAILURE_REASON"

    /** `DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN`: e.g. the verifier timed out. */
    const val REASON_UNKNOWN = 0

    /** `DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE`: the verifier could not reach Google. */
    const val REASON_NETWORK_UNAVAILABLE = 1

    /** `DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED`: the developer is not registered/allowed. */
    const val REASON_DEVELOPER_BLOCKED = 2

    fun map(extras: Bundle): InstallState =
        when (val status = extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallState.PendingUserAction
            PackageInstaller.STATUS_SUCCESS -> InstallState.Success
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                if (extras.containsKey(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON)) {
                    InstallState.VerificationBlocked(
                        reason = extras.getInt(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, REASON_UNKNOWN),
                        contextIntent = extras.intentExtraCompat(),
                    )
                } else {
                    InstallState.Cancelled(status)
                }
            else -> InstallState.Failure(status, extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
}
