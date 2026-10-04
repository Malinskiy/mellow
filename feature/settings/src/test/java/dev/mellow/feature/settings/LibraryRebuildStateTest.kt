package dev.mellow.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRebuildStateTest {

    @Test
    fun `nothing pending offers a rebuild, whatever else is going on`() {
        assertEquals(LibraryRebuildState.Idle, state(syncing = false, pending = false, failed = false))
        assertEquals(LibraryRebuildState.Idle, state(syncing = true, pending = false, failed = true))
    }

    @Test
    fun `a pending rebuild shows progress while a sync runs`() {
        assertEquals(LibraryRebuildState.Running, state(syncing = true, pending = true, failed = true))
    }

    @Test
    fun `a pending rebuild that isn't running is waiting or has failed`() {
        assertEquals(LibraryRebuildState.Pending, state(syncing = false, pending = true, failed = false))
        assertEquals(LibraryRebuildState.Failed, state(syncing = false, pending = true, failed = true))
    }

    private fun state(syncing: Boolean, pending: Boolean, failed: Boolean) =
        libraryRebuildState(isSyncing = syncing, isRebuildPending = pending, isLastSyncFailed = failed)
}
