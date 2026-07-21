package dev.mellow.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_preferences",
)

@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.syncDataStore

    val isForceOffline: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_FORCE_OFFLINE] ?: false
    }

    val autoSyncIntervalHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[AUTO_SYNC_INTERVAL_HOURS] ?: DEFAULT_SYNC_INTERVAL_HOURS
    }

    val lastSyncTimestamp: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIMESTAMP] ?: 0L
    }

    val syncCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SYNC_COUNT] ?: 0
    }

    val albumRevision: Flow<Int> = dataStore.data.map { preferences ->
        preferences[ALBUM_REVISION] ?: 0
    }

    val artistRevision: Flow<Int> = dataStore.data.map { preferences ->
        preferences[ARTIST_REVISION] ?: 0
    }

    val trackRevision: Flow<Int> = dataStore.data.map { preferences ->
        preferences[TRACK_REVISION] ?: 0
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

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun incrementSyncCount() {
        dataStore.edit { preferences ->
            val current = preferences[SYNC_COUNT] ?: 0
            preferences[SYNC_COUNT] = current + 1
        }
    }

    suspend fun setAlbumRevision(revision: Int) {
        dataStore.edit { preferences ->
            preferences[ALBUM_REVISION] = revision
        }
    }

    suspend fun setArtistRevision(revision: Int) {
        dataStore.edit { preferences ->
            preferences[ARTIST_REVISION] = revision
        }
    }

    suspend fun setTrackRevision(revision: Int) {
        dataStore.edit { preferences ->
            preferences[TRACK_REVISION] = revision
        }
    }

    companion object {
        private val IS_FORCE_OFFLINE = booleanPreferencesKey("is_force_offline")
        private val AUTO_SYNC_INTERVAL_HOURS = intPreferencesKey("auto_sync_interval_hours")
        private val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        private val SYNC_COUNT = intPreferencesKey("sync_count")
        private val ALBUM_REVISION = intPreferencesKey("album_data_revision")
        private val ARTIST_REVISION = intPreferencesKey("artist_data_revision")
        private val TRACK_REVISION = intPreferencesKey("track_data_revision")
        const val DEFAULT_SYNC_INTERVAL_HOURS = 6
        // bump per-table when its mapper logic changes to force full re-sync for that table only
        const val CURRENT_ALBUM_REVISION = 2
        const val CURRENT_ARTIST_REVISION = 0
        const val CURRENT_TRACK_REVISION = 0
    }
}
