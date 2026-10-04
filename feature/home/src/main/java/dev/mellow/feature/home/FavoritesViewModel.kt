package dev.mellow.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.common.MellowResult
import dev.mellow.core.common.QUEUE_WINDOW_SIZE
import dev.mellow.core.common.queueWindow
import dev.mellow.core.data.preferences.DisplayPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [isLoading]: the favorites are being fetched from the server. */
data class FavoritesUiState(
    val isLoading: Boolean = true,
)

/** The favorite tracks, albums and artists, each a page at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    displayPreferences: DisplayPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val serverId = MutableStateFlow<String?>(null)

    /** `null` until the preference is read, so the lists don't load unfiltered first. */
    private val downloadedOnlyPreference: StateFlow<Boolean?> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val source: Flow<Pair<String, Boolean>> =
        combine(serverId.filterNotNull(), downloadedOnlyPreference.filterNotNull()) { id, downloadedOnly ->
            id to downloadedOnly
        }.distinctUntilChanged()

    val tracks: Flow<PagingData<Track>> = source
        .flatMapLatest { (id, downloadedOnly) -> libraryRepository.getPagedFavoriteTracks(id, downloadedOnly) }
        .cachedIn(viewModelScope)

    val albums: Flow<PagingData<Album>> = source
        .flatMapLatest { (id, downloadedOnly) -> libraryRepository.getPagedFavoriteAlbums(id, downloadedOnly) }
        .cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> = source
        .flatMapLatest { (id, downloadedOnly) -> libraryRepository.getPagedFavoriteArtists(id, downloadedOnly) }
        .cachedIn(viewModelScope)

    private var loadedServerId: String? = null

    fun retry() {
        val id = loadedServerId ?: return
        syncFavorites(id)
    }

    fun loadFavorites(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId
        this.serverId.value = serverId
        syncFavorites(serverId)
    }

    /**
     * What to queue when the favorite track [trackId] at [index] of the list is played, and where that track is in
     * it: the favorites around it as the list shows them (downloaded ones only, if that's what it shows), at most
     * [QUEUE_WINDOW_SIZE] of them. `null` if there's nothing to play.
     */
    suspend fun tracksToPlay(index: Int, trackId: String): Pair<List<Track>, Int>? {
        val id = serverId.value ?: return null
        val downloadedOnly = downloadedOnlyPreference.value ?: return null
        val count = (libraryRepository.countFavoriteTracks(id, downloadedOnly) as? MellowResult.Success)?.data
            ?: return null
        return queueWindow(
            index = index,
            count = count,
            trackId = trackId,
            idOf = Track::id,
            loadSlice = { offset, limit ->
                (libraryRepository.getFavoriteTracksSlice(id, downloadedOnly, offset, limit) as? MellowResult.Success)
                    ?.data
            },
            loadTrack = { moved -> (libraryRepository.getTrack(moved) as? MellowResult.Success)?.data },
        )
    }

    /**
     * The favorites shuffled, as the list shows them: at most [QUEUE_WINDOW_SIZE] picked at random from all of
     * them, so a shuffle never reads every favorite.
     */
    suspend fun shuffledTracks(): List<Track> {
        val id = serverId.value ?: return emptyList()
        val downloadedOnly = downloadedOnlyPreference.value ?: return emptyList()
        val picked = libraryRepository.pickRandomFavoriteTracks(id, downloadedOnly, QUEUE_WINDOW_SIZE)
        return (picked as? MellowResult.Success)?.data ?: emptyList()
    }

    private fun syncFavorites(serverId: String) {
        _uiState.value = FavoritesUiState(isLoading = true)
        viewModelScope.launch {
            try {
                libraryRepository.syncFavorites(serverId)
            } catch (e: Exception) {
                Log.w(TAG, "Favorites sync failed (API may be unavailable)", e)
            } finally {
                _uiState.value = FavoritesUiState(isLoading = false)
            }
        }
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
    }
}
