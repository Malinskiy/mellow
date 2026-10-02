package dev.mellow.core.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import dev.mellow.core.update.InstallResultMapper.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON
import dev.mellow.core.update.InstallResultMapper.REASON_DEVELOPER_BLOCKED
import dev.mellow.core.update.InstallResultMapper.REASON_NETWORK_UNAVAILABLE
import dev.mellow.core.update.InstallResultMapper.REASON_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallResultMapperTest {

    private fun extras(status: Int?, block: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        if (status != null) putInt(PackageInstaller.EXTRA_STATUS, status)
        block()
    }

    @Test
    fun `pending user action`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_PENDING_USER_ACTION) {
                putParcelable(Intent.EXTRA_INTENT, Intent("android.content.pm.action.CONFIRM_INSTALL"))
            },
        )

        assertEquals(InstallState.PendingUserAction, state)
    }

    @Test
    fun `success`() {
        assertEquals(InstallState.Success, InstallResultMapper.map(extras(PackageInstaller.STATUS_SUCCESS)))
    }

    @Test
    fun `aborted without a verification reason is a user cancel`() {
        val state = InstallResultMapper.map(extras(PackageInstaller.STATUS_FAILURE_ABORTED))

        assertEquals(InstallState.Cancelled(PackageInstaller.STATUS_FAILURE_ABORTED), state)
    }

    @Test
    fun `the status message never changes the classification`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED) {
                putString(PackageInstaller.EXTRA_STATUS_MESSAGE, "Blocked by developer verification")
            },
        )

        assertEquals(InstallState.Cancelled(PackageInstaller.STATUS_FAILURE_ABORTED), state)
    }

    @Test
    fun `aborted with reason developer blocked`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED) {
                putInt(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, REASON_DEVELOPER_BLOCKED)
            },
        )

        assertEquals(InstallState.VerificationBlocked(reason = 2, contextIntent = null), state)
    }

    @Test
    fun `aborted with reason network unavailable`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED) {
                putInt(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, REASON_NETWORK_UNAVAILABLE)
            },
        )

        assertEquals(InstallState.VerificationBlocked(reason = 1, contextIntent = null), state)
    }

    @Test
    fun `aborted with reason unknown`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED) {
                putInt(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, REASON_UNKNOWN)
            },
        )

        assertEquals(InstallState.VerificationBlocked(reason = 0, contextIntent = null), state)
    }

    @Test
    fun `aborted with a reason carries the OS context intent when present`() {
        val context = Intent("android.settings.SOME_VERIFICATION_INFO").putExtra("detail", 7)
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED) {
                putInt(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, REASON_DEVELOPER_BLOCKED)
                putParcelable(Intent.EXTRA_INTENT, context)
            },
        )

        val blocked = state as InstallState.VerificationBlocked
        assertEquals(REASON_DEVELOPER_BLOCKED, blocked.reason)
        assertSame(context, blocked.contextIntent)
    }

    @Test
    fun `the extra name and reason values match the platform reference`() {
        assertEquals(
            "android.content.pm.extra.DEVELOPER_VERIFICATION_FAILURE_REASON",
            EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON,
        )
        assertEquals(listOf(0, 1, 2), listOf(REASON_UNKNOWN, REASON_NETWORK_UNAVAILABLE, REASON_DEVELOPER_BLOCKED))
    }

    @Test
    fun `incompatible is a failure with the platform message`() {
        val state = InstallResultMapper.map(
            extras(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                putString(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
            },
        )

        assertEquals(
            InstallState.Failure(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
            state,
        )
    }

    @Test
    fun `other failures keep their status`() {
        assertEquals(
            InstallState.Failure(PackageInstaller.STATUS_FAILURE_STORAGE, null),
            InstallResultMapper.map(extras(PackageInstaller.STATUS_FAILURE_STORAGE)),
        )
        assertEquals(
            InstallState.Failure(PackageInstaller.STATUS_FAILURE_BLOCKED, null),
            InstallResultMapper.map(extras(PackageInstaller.STATUS_FAILURE_BLOCKED)),
        )
    }

    @Test
    fun `a missing status is a generic failure`() {
        assertEquals(
            InstallState.Failure(PackageInstaller.STATUS_FAILURE, null),
            InstallResultMapper.map(extras(status = null)),
        )
    }
}
