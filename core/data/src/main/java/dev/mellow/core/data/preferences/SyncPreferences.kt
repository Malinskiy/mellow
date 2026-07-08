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

    companion object {
        private val IS_FORCE_OFFLINE = booleanPreferencesKey("is_force_offline")
        private val AUTO_SYNC_INTERVAL_HOURS = intPreferencesKey("auto_sync_interval_hours")
        private val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        const val DEFAULT_SYNC_INTERVAL_HOURS = 6
    }
}
