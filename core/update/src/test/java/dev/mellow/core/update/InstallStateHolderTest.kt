package dev.mellow.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStateHolderTest {

    private val holder = InstallStateHolder()

    @Test
    fun `begin clears the previous outcome and tracks the session`() {
        holder.publish(InstallState.Cancelled(3))
        holder.begin(7)
        assertEquals(InstallState.Idle, holder.state.value)
        assertEquals(7, holder.activeSessionId)
    }

    @Test
    fun `results for the active session are published`() {
        holder.begin(7)
        assertTrue(holder.publish(InstallState.PendingUserAction, sessionId = 7))
        assertEquals(InstallState.PendingUserAction, holder.state.value)
    }

    @Test
    fun `results for another session are dropped`() {
        holder.begin(7)
        holder.publish(InstallState.PendingUserAction, sessionId = 7)
        assertFalse(holder.publish(InstallState.Cancelled(3), sessionId = 6))
        assertEquals(InstallState.PendingUserAction, holder.state.value)
    }

    @Test
    fun `a fresh process ignores results of sessions it did not commit`() {
        assertFalse(holder.publish(InstallState.Success, sessionId = 42))
        assertEquals(InstallState.Idle, holder.state.value)
    }

    @Test
    fun `locally produced states need no session`() {
        holder.begin(7)
        assertTrue(holder.publish(InstallState.VerificationBlocked(reason = 2, contextIntent = null)))
    }
}
