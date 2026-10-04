package dev.mellow.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.mellow.core.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var preferences: SyncPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        preferences = SyncPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "sync.preferences_pb") },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a full pass shows as pending before the first sync, also right after an update`() = runTest {
        // Nothing recorded yet: a fresh install, or an update from a version that didn't record sync starts.
        assertTrue(isPending(ACTIVE))
        assertFalse("nothing to rebuild while logged out", isPending(null))
    }

    @Test
    fun `a full pass is needed until one succeeds for this server and user`() = runTest {
        assertTrue(preferences.readLibrarySyncState().needsFullPass(SCOPE))

        val request = preferences.markFullPassPending()
        preferences.recordSyncSucceeded(SCOPE, startedAt = 100, completedAt = 200, fullPassRequest = request)

        val state = preferences.readLibrarySyncState()
        assertFalse(state.needsFullPass(SCOPE))
        assertTrue(state.needsFullPass("other server/user"))
        assertEquals(100L, state.lastStartedAt)
        assertEquals(200L, preferences.lastSyncTimestamp.first())
        assertFalse(isPending(ACTIVE))
        assertTrue("another server's library", isPending(ACTIVE.copy(id = "other server")))
        assertTrue("another user's library", isPending(ACTIVE.copy(userId = "other user")))
    }

    @Test
    fun `an outdated data revision needs a full pass`() = runTest {
        val state = LibrarySyncState(
            scope = SCOPE,
            lastStartedAt = 100,
            albumRevision = SyncPreferences.CURRENT_ALBUM_REVISION,
            artistRevision = SyncPreferences.CURRENT_ARTIST_REVISION,
            trackRevision = SyncPreferences.CURRENT_TRACK_REVISION - 1,
            fullPassRequest = null,
        )

        assertTrue(state.needsFullPass(SCOPE))
        assertFalse(state.copy(trackRevision = SyncPreferences.CURRENT_TRACK_REVISION).needsFullPass(SCOPE))
    }

    @Test
    fun `a pending full pass stays pending through failures and changes-only syncs`() = runTest {
        preferences.recordSyncSucceeded(SCOPE, startedAt = 100, completedAt = 200, fullPassRequest = 0)
        preferences.requestFullPass()

        preferences.recordSyncFailed(300)
        assertTrue(isPending(ACTIVE))
        assertEquals(300L, preferences.lastSyncFailedAt.first())

        preferences.recordSyncSucceeded(SCOPE, startedAt = 400, completedAt = 500, fullPassRequest = null)
        assertTrue(isPending(ACTIVE))
        assertTrue(preferences.readLibrarySyncState().needsFullPass(SCOPE))
        assertEquals(0L, preferences.lastSyncFailedAt.first())
    }

    @Test
    fun `a rebuild requested while a pass runs is not completed by that pass`() = runTest {
        val running = preferences.markFullPassPending()
        preferences.requestFullPass()

        preferences.recordSyncSucceeded(SCOPE, startedAt = 100, completedAt = 200, fullPassRequest = running)

        assertTrue(isPending(ACTIVE))
    }

    @Test
    fun `a failed attempt changes nothing the next sync relies on`() = runTest {
        val request = preferences.markFullPassPending()
        preferences.recordSyncSucceeded(SCOPE, startedAt = 100, completedAt = 200, fullPassRequest = request)
        val before = preferences.readLibrarySyncState()

        preferences.recordSyncFailed(300)

        assertEquals(before, preferences.readLibrarySyncState())
        assertEquals(200L, preferences.lastSyncTimestamp.first())
    }

    private suspend fun isPending(activeServer: Server?): Boolean =
        preferences.isFullPassPending(flowOf(activeServer)).first()

    private companion object {
        val ACTIVE = Server("server", "Home", "https://jellyfin.example", userId = "user", accessToken = "token")
        val SCOPE = librarySyncScope(ACTIVE.id, ACTIVE.userId)
    }
}
