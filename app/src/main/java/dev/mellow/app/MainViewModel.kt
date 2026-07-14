package dev.mellow.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.LyricsDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.LyricsEntity
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.player.MellowPlayer
import dev.mellow.sync.SyncScheduler
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthState { CHECKING, LOGGED_IN, LOGGED_OUT }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepositoryImpl,
    val player: MellowPlayer,
    private val networkStateObserver: NetworkStateObserver,
    private val syncPreferences: SyncPreferences,
    private val syncScheduler: SyncScheduler,
    private val jellyfinDataSource: JellyfinDataSource,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val lyricsDao: LyricsDao,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.CHECKING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _serverId = MutableStateFlow<String?>(null)
    val serverId: StateFlow<String?> = _serverId.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = networkStateObserver.connectionState

    val isSyncing: StateFlow<Boolean> = syncScheduler.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncProgress: StateFlow<SyncProgress?> = syncScheduler.observeSyncProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isCleaningUp: StateFlow<Boolean> = syncScheduler.observeCleanupState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lastSyncTimestamp: StateFlow<Long> = syncPreferences.lastSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val isForceOffline: StateFlow<Boolean> = syncPreferences.isForceOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoSyncIntervalHours: StateFlow<Int> = syncPreferences.autoSyncIntervalHours
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncPreferences.DEFAULT_SYNC_INTERVAL_HOURS,
        )

    init {
        player.connect()
        viewModelScope.launch {
            syncPreferences.isForceOffline.collect { offline ->
                networkStateObserver.setOfflineMode(offline)
            }
        }
        viewModelScope.launch {
            val restored = userRepository.restoreSession()
            if (restored) {
                val server = userRepository.getActiveServer()
                _serverId.value = server?.id
                _serverUrl.value = server?.url
                _authState.value = AuthState.LOGGED_IN
                networkStateObserver.markConnected()
                server?.id?.let { id ->
                    syncScheduler.schedulePeriodicSync(id)
                    syncScheduler.syncNow(id)
                }
            } else {
                _authState.value = AuthState.LOGGED_OUT
            }
        }
    }

    fun onLoggedIn(serverId: String) {
        viewModelScope.launch {
            val server = userRepository.getActiveServer()
            _serverId.value = serverId
            _serverUrl.value = server?.url
            _authState.value = AuthState.LOGGED_IN
            networkStateObserver.markConnected()
            syncScheduler.schedulePeriodicSync(serverId)
            syncScheduler.syncNow(serverId)
        }
    }

    fun syncNow() {
        val id = _serverId.value ?: return
        syncScheduler.syncNow(id)
    }

    fun cleanupLibrary() {
        val id = _serverId.value ?: return
        syncScheduler.cleanupNow(id)
    }

    fun setForceOffline(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setForceOffline(enabled)
            networkStateObserver.setOfflineMode(enabled)
        }
    }

    fun setAutoSyncInterval(hours: Int) {
        viewModelScope.launch {
            syncPreferences.setAutoSyncIntervalHours(hours)
            val id = _serverId.value ?: return@launch
            syncScheduler.schedulePeriodicSync(id)
        }
    }

    fun toggleFavorite(itemId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            userRepository.setFavorite(itemId, !currentlyFavorite)
        }
    }

    suspend fun fetchLyrics(trackId: String): List<JellyfinDataSource.LyricsResult> {
        val cached = lyricsDao.getLyrics(trackId)
        if (cached != null) {
            return parseLyricsData(cached.lyricsData)
        }

        val results = try {
            jellyfinDataSource.getLyrics(UUID.fromString(trackId))
        } catch (e: Exception) {
            emptyList()
        }

        if (results.isNotEmpty()) {
            val serverId = _serverId.value ?: return results
            val json = org.json.JSONArray()
            for (r in results) {
                json.put(org.json.JSONObject().apply {
                    put("startMs", r.startMs)
                    put("text", r.text)
                })
            }
            lyricsDao.upsert(
                LyricsEntity(
                    trackId = trackId,
                    serverId = serverId,
                    lyricsData = json.toString(),
                    lastSynced = System.currentTimeMillis(),
                ),
            )
        }

        return results
    }

    private fun parseLyricsData(data: String): List<JellyfinDataSource.LyricsResult> {
        return try {
            val arr = org.json.JSONArray(data)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val text = obj.optString("text", "")
                if (text.isEmpty()) return@mapNotNull null
                JellyfinDataSource.LyricsResult(
                    startMs = obj.optLong("startMs", -1L),
                    text = text,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isTrackDownloaded(trackId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadDao.isDownloaded(trackId)
    }

    fun observeTrackFavorite(trackId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return trackDao.observeIsFavorite(trackId).map { it ?: false }
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String) {
        playlistRepository.addTrackToPlaylist(playlistId, trackId, serverId)
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
