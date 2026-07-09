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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "download_preferences",
)

@Singleton
class DownloadPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.downloadDataStore

    val downloadQuality: Flow<String> = dataStore.data.map { preferences ->
        preferences[DOWNLOAD_QUALITY] ?: DEFAULT_QUALITY
    }

    val wifiOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[WIFI_ONLY] ?: true
    }

    val storageCap: Flow<Long> = dataStore.data.map { preferences ->
        preferences[STORAGE_CAP] ?: DEFAULT_STORAGE_CAP_BYTES
    }

    val autoCleanupDays: Flow<Int> = dataStore.data.map { preferences ->
        preferences[AUTO_CLEANUP_DAYS] ?: DEFAULT_AUTO_CLEANUP_DAYS
    }

    suspend fun setDownloadQuality(quality: String) {
        dataStore.edit { preferences ->
            preferences[DOWNLOAD_QUALITY] = quality
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[WIFI_ONLY] = enabled
        }
    }

    suspend fun setStorageCap(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[STORAGE_CAP] = bytes
        }
    }

    suspend fun setAutoCleanupDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[AUTO_CLEANUP_DAYS] = days
        }
    }

    companion object {
        private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val STORAGE_CAP = longPreferencesKey("storage_cap")
        private val AUTO_CLEANUP_DAYS = intPreferencesKey("auto_cleanup_days")

        const val DEFAULT_QUALITY = "original"
        const val DEFAULT_STORAGE_CAP_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB
        const val DEFAULT_AUTO_CLEANUP_DAYS = 30
    }
}
