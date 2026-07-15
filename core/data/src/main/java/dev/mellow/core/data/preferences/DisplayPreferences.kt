package dev.mellow.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "display_preferences",
)

@Singleton
class DisplayPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.displayDataStore

    val lowPowerMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LOW_POWER_MODE] ?: false
    }

    suspend fun setLowPowerMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LOW_POWER_MODE] = enabled
        }
    }

    companion object {
        private val LOW_POWER_MODE = booleanPreferencesKey("low_power_mode")
    }
}
