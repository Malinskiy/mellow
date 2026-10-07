package dev.mellow.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.app.navigation.TrackLinkTargets
import dev.mellow.app.navigation.trackLinkTargets
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.preferences.DisplayPreferences
import dev.mellow.core.data.preferences.DownloadPreferences
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.DownloadRepository
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.LyricsDao
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.LyricsEntity
import dev.mellow.core.designsystem.component.TrackMenuDownload
import dev.mellow.core.model.Track
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.player.MellowPlayer
import dev.mellow.sync.SyncScheduler
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val displayPreferences: DisplayPreferences,
    private val syncScheduler: SyncScheduler,
    private val jellyfinDataSource: JellyfinDataSource,
    private val libraryRepository: LibraryRepository,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val lyricsDao: LyricsDao,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.CHECKING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _serverId = MutableStateFlow<String?>(null)
    val serverId: StateFlow<String?> = _serverId.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    val lowPowerMode: StateFlow<Boolean> = displayPreferences.lowPowerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadedOnly: StateFlow<Boolean> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val connectionState: StateFlow<ConnectionState> = networkStateObserver.connectionState

    fun toggleDownloadedOnly() {
        viewModelScope.launch { displayPreferences.setDownloadedOnly(!downloadedOnly.value) }
    }

    val isSyncing: StateFlow<Boolean> = syncScheduler.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncProgress: StateFlow<SyncProgress?> = syncScheduler.observeSyncProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The active server's next library sync is a full pass (a rebuild) that hasn't completed yet, including before the
     * first sync after an update.
     */
    val isRebuildPending: StateFlow<Boolean> = syncPreferences.isFullPassPending(userRepository.observeActiveServer())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isLastSyncFailed: StateFlow<Boolean> = syncPreferences.lastSyncFailedAt
        .map { it > 0L }
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
            syncScheduler.cancelRemovedWork()
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

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
            _serverId.value = null
            _serverUrl.value = null
            _authState.value = AuthState.LOGGED_OUT
        }
    }

    fun syncNow() {
        val id = _serverId.value ?: return
        viewModelScope.launch { syncScheduler.syncNow(id) }
    }

    fun rebuildLibrary() {
        val id = _serverId.value ?: return
        viewModelScope.launch { syncScheduler.rebuildNow(id) }
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

    fun startMix(trackId: String) {
        viewModelScope.launch {
            val serverId = _serverId.value ?: return@launch
            when (val result = libraryRepository.getInstantMix(serverId, trackId)) {
                is MellowResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        player.playTracks(result.data)
                    }
                }
                is MellowResult.Error -> {}
                is MellowResult.Loading -> {}
            }
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

    suspend fun getArtistsForTrack(trackId: String): List<ArtistEntity> {
        return trackDao.getArtistsForTrack(trackId)
    }

    /** What the expanded player can open for [track]: its album and its artists, when they're in the library. */
    suspend fun linkTargetsOf(track: Track): TrackLinkTargets = trackLinkTargets(
        albumId = track.albumId,
        fallbackArtistId = track.resolvedArtistId ?: track.artistId,
        hasArtists = { trackDao.getArtistsForTrack(track.id).isNotEmpty() },
        albumExists = { (libraryRepository.getAlbum(it) as? MellowResult.Success)?.data != null },
        artistExists = { (libraryRepository.getArtist(it) as? MellowResult.Success)?.data != null },
    )

    /** The library's track [trackId], for lists that only keep the tracks on screen. */
    suspend fun getTrack(trackId: String): Track? =
        (libraryRepository.getTrack(trackId) as? MellowResult.Success)?.data

    suspend fun getArtistsForAlbum(albumId: String): List<ArtistEntity> {
        return albumDao.getArtistsForAlbum(albumId)
    }

    suspend fun countAlbumsByArtistCrossRef(artistId: String): Int {
        return albumDao.countAlbumsByArtistCrossRef(artistId)
    }

    fun isTrackDownloaded(trackId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadDao.isDownloaded(trackId)
    }

    /** The track menu's download entry for [trackId], live while the menu is open. */
    fun observeTrackMenuDownload(trackId: String): Flow<TrackMenuDownload> = combine(
        downloadRepository.observeDownload(trackId).map { (it as? MellowResult.Success)?.data },
        networkStateObserver.connectionState,
        downloadPreferences.storageCap,
        downloadRepository.getTotalDownloadedBytes().map { (it as? MellowResult.Success)?.data ?: 0L },
    ) { download, connection, cap, used ->
        trackMenuDownload(download, connection, storageFull = isStorageFull(used, cap))
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            val server = userRepository.getActiveServer() ?: return@launch
            val quality = downloadPreferences.downloadQuality.first()
            downloadRepository.downloadTrack(track, server.id, quality)
        }
    }

    fun cancelTrackDownload(trackId: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(trackId) }
    }

    fun removeTrackDownload(trackId: String) {
        viewModelScope.launch { downloadRepository.removeDownload(trackId) }
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
