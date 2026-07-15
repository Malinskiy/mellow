package dev.mellow.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "network_preferences",
)

@Singleton
class NetworkPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.networkDataStore

    val trustSelfSigned: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[TRUST_SELF_SIGNED] ?: false
    }

    suspend fun setTrustSelfSigned(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRUST_SELF_SIGNED] = enabled
        }
    }

    /**
     * Reads the current trust-self-signed value synchronously.
     * Only use from non-suspend contexts (OkHttpClient construction).
     */
    fun isTrustSelfSignedSync(): Boolean = runBlocking {
        trustSelfSigned.first()
    }

    companion object {
        private val TRUST_SELF_SIGNED = booleanPreferencesKey("trust_self_signed")
    }
}
