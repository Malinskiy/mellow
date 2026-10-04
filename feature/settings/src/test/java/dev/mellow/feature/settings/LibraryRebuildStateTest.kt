package dev.mellow.feature.settings

import dev.mellow.core.data.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `Last Synced shows the progress of an ordinary sync`() {
        val progress = SyncProgress("artwork", 50, 4575)
        assertEquals(progress, lastSyncedProgress(isSyncing = true, progress, LibraryRebuildState.Idle))
    }

    @Test
    fun `Last Synced leaves the progress of a rebuild to the Rebuild Library row`() {
        val progress = SyncProgress("tracks", 1000, 51132)
        assertNull(lastSyncedProgress(isSyncing = true, progress, LibraryRebuildState.Running))
    }

    @Test
    fun `Last Synced shows no progress when nothing is syncing or nothing is counted yet`() {
        assertNull(lastSyncedProgress(isSyncing = false, SyncProgress("tracks", 1, 2), LibraryRebuildState.Idle))
        assertNull(lastSyncedProgress(isSyncing = true, SyncProgress("tracks", 0, 0), LibraryRebuildState.Idle))
        assertNull(lastSyncedProgress(isSyncing = true, null, LibraryRebuildState.Idle))
    }

    private fun state(syncing: Boolean, pending: Boolean, failed: Boolean) =
        libraryRebuildState(isSyncing = syncing, isRebuildPending = pending, isLastSyncFailed = failed)
}
