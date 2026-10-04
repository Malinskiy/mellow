package dev.mellow.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_preferences",
)

/** Which library a recorded sync is for: one server, as one user. */
fun librarySyncScope(serverId: String, userId: String): String = "$serverId/$userId"

/** What the last successful library sync recorded, read when a sync starts. */
data class LibrarySyncState(
    /** The server and user the recorded sync was for, as `serverId/userId`. */
    val scope: String?,
    /** When the last successful sync started, or 0 if none is recorded. */
    val lastStartedAt: Long,
    val albumRevision: Int,
    val artistRevision: Int,
    val trackRevision: Int,
    /** The pending full pass, if there is one. */
    val fullPassRequest: Long?,
) {
    /** Whether a sync of [scope] has to be a full pass rather than fetching what changed. */
    fun needsFullPass(scope: String): Boolean =
        fullPassRequest != null ||
            lastStartedAt == 0L ||
            this.scope != scope ||
            albumRevision < SyncPreferences.CURRENT_ALBUM_REVISION ||
            artistRevision < SyncPreferences.CURRENT_ARTIST_REVISION ||
            trackRevision < SyncPreferences.CURRENT_TRACK_REVISION
}

/** Sync settings and bookkeeping. Tests pass their own [DataStore]; the app uses the one named `sync_preferences`. */
@Singleton
class SyncPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.syncDataStore)

    val isForceOffline: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_FORCE_OFFLINE] ?: false
    }

    val autoSyncIntervalHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[AUTO_SYNC_INTERVAL_HOURS] ?: DEFAULT_SYNC_INTERVAL_HOURS
    }

    /** When the last successful library sync finished, or 0 if none has. */
    val lastSyncTimestamp: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIMESTAMP] ?: 0L
    }

    val syncCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SYNC_COUNT] ?: 0
    }

    val librarySyncState: Flow<LibrarySyncState> = dataStore.data.map { preferences ->
        LibrarySyncState(
            scope = preferences[LAST_SYNC_SCOPE],
            lastStartedAt = preferences[LAST_SYNC_STARTED_AT] ?: 0L,
            albumRevision = preferences[ALBUM_REVISION] ?: 0,
            artistRevision = preferences[ARTIST_REVISION] ?: 0,
            trackRevision = preferences[TRACK_REVISION] ?: 0,
            fullPassRequest = preferences[FULL_PASS_REQUESTED_AT],
        )
    }

    /**
     * Whether the library of the [activeServer] needs a full pass that hasn't completed yet, by the rule a sync starts
     * with ([LibrarySyncState.needsFullPass]): before its first sync (also the first after an update from a version
     * that didn't record syncs this way), after a data revision bump, for another server or user, or when the user
     * asked to rebuild the library. False while no server is active.
     */
    fun isFullPassPending(activeServer: Flow<Server?>): Flow<Boolean> =
        combine(activeServer, librarySyncState) { server, state ->
            server != null && state.needsFullPass(librarySyncScope(server.id, server.userId))
        }.distinctUntilChanged()

    /** When the last library sync attempt failed, or 0 if the last attempt succeeded. */
    val lastSyncFailedAt: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SYNC_FAILED_AT] ?: 0L
    }

    suspend fun setForceOffline(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_FORCE_OFFLINE] = enabled
        }
    }

    suspend fun setAutoSyncIntervalHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[AUTO_SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun readLibrarySyncState(): LibrarySyncState = librarySyncState.first()

    /**
     * Keeps a full pass pending until one completes, so an interrupted pass is retried as a full pass. Returns the
     * request the pass that is starting will complete.
     */
    suspend fun markFullPassPending(): Long {
        var request = 0L
        dataStore.edit { preferences ->
            request = preferences[FULL_PASS_REQUESTED_AT]
                ?: System.currentTimeMillis().also { preferences[FULL_PASS_REQUESTED_AT] = it }
        }
        return request
    }

    /** Makes the next library sync a full pass. A pass that is already running doesn't complete this request. */
    suspend fun requestFullPass() {
        dataStore.edit { preferences ->
            val previous = preferences[FULL_PASS_REQUESTED_AT] ?: 0L
            preferences[FULL_PASS_REQUESTED_AT] = maxOf(System.currentTimeMillis(), previous + 1)
            preferences.remove(LAST_SYNC_FAILED_AT)
        }
    }

    /**
     * Records a library sync of [scope] that completed every step. The next sync fetches what changed since shortly
     * before [startedAt]. A full pass also records the current data revisions and completes [fullPassRequest], unless
     * a newer request came in while it ran.
     */
    suspend fun recordSyncSucceeded(scope: String, startedAt: Long, completedAt: Long, fullPassRequest: Long?) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_SCOPE] = scope
            preferences[LAST_SYNC_STARTED_AT] = startedAt
            preferences[LAST_SYNC_TIMESTAMP] = completedAt
            preferences[SYNC_COUNT] = (preferences[SYNC_COUNT] ?: 0) + 1
            preferences.remove(LAST_SYNC_FAILED_AT)
            if (fullPassRequest != null) {
                preferences[ALBUM_REVISION] = CURRENT_ALBUM_REVISION
                preferences[ARTIST_REVISION] = CURRENT_ARTIST_REVISION
                preferences[TRACK_REVISION] = CURRENT_TRACK_REVISION
                if (preferences[FULL_PASS_REQUESTED_AT] == fullPassRequest) {
                    preferences.remove(FULL_PASS_REQUESTED_AT)
                }
            }
        }
    }

    /** Records a library sync attempt that didn't complete. Nothing about the last successful sync changes. */
    suspend fun recordSyncFailed(failedAt: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_FAILED_AT] = failedAt
        }
    }

    companion object {
        private val IS_FORCE_OFFLINE = booleanPreferencesKey("is_force_offline")
        private val AUTO_SYNC_INTERVAL_HOURS = intPreferencesKey("auto_sync_interval_hours")
        private val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        private val LAST_SYNC_STARTED_AT = longPreferencesKey("last_sync_started_at")
        private val LAST_SYNC_SCOPE = stringPreferencesKey("last_sync_scope")
        private val LAST_SYNC_FAILED_AT = longPreferencesKey("last_sync_failed_at")
        private val FULL_PASS_REQUESTED_AT = longPreferencesKey("full_pass_requested_at")
        private val SYNC_COUNT = intPreferencesKey("sync_count")
        private val ALBUM_REVISION = intPreferencesKey("album_data_revision")
        private val ARTIST_REVISION = intPreferencesKey("artist_data_revision")
        private val TRACK_REVISION = intPreferencesKey("track_data_revision")
        const val DEFAULT_SYNC_INTERVAL_HOURS = 6
        // bump per-table when its mapper logic changes to force a full library pass
        const val CURRENT_ALBUM_REVISION = 5
        const val CURRENT_ARTIST_REVISION = 2
        const val CURRENT_TRACK_REVISION = 3
    }
}
