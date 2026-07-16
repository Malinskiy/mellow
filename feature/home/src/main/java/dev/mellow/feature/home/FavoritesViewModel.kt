package dev.mellow.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: dev.mellow.core.data.repository.DownloadRepository,
    displayPreferences: dev.mellow.core.data.preferences.DisplayPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val _downloadedOnly: StateFlow<Boolean> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedServerId: String? = null
    private var syncCompleted = false

    fun retry() {
        val id = loadedServerId ?: return
        loadedServerId = null
        syncCompleted = false
        _uiState.value = FavoritesUiState()
        loadFavorites(id)
    }

    fun loadFavorites(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId

        combine(
            libraryRepository.getFavoriteTracks(serverId),
            libraryRepository.getFavoriteAlbums(serverId),
            libraryRepository.getFavoriteArtists(serverId),
        ) { tracksResult, albumsResult, artistsResult ->
            val tracks = (tracksResult as? MellowResult.Success)?.data ?: emptyList()
            val albums = (albumsResult as? MellowResult.Success)?.data ?: emptyList()
            val artists = (artistsResult as? MellowResult.Success)?.data ?: emptyList()
            unfilteredFavTracks = tracks
            unfilteredFavAlbums = albums
            unfilteredFavArtists = artists
            val hasData = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
            syncCompleted = syncCompleted || hasData
            emitFiltered()
        }.catch { e ->
            _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            try {
                libraryRepository.syncFavorites(serverId)
            } catch (e: Exception) {
                Log.w(TAG, "Favorites sync failed (API may be unavailable)", e)
            } finally {
                syncCompleted = true
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }

        _downloadedOnly.onEach { emitFiltered() }.launchIn(viewModelScope)
    }

    private var unfilteredFavTracks: List<Track> = emptyList()
    private var unfilteredFavAlbums: List<Album> = emptyList()
    private var unfilteredFavArtists: List<Artist> = emptyList()
    private var cachedDlAlbumIds: Set<String>? = null
    private var cachedDlArtistNames: Set<String>? = null
    private var cachedDlTrackIds: Set<String>? = null

    private suspend fun emitFiltered() {
        if (!_downloadedOnly.value) {
            _uiState.value = FavoritesUiState(
                tracks = unfilteredFavTracks,
                albums = unfilteredFavAlbums,
                artists = unfilteredFavArtists,
                isLoading = unfilteredFavTracks.isEmpty() && unfilteredFavAlbums.isEmpty() && !syncCompleted,
            )
            return
        }
        val dlAlbumIds = cachedDlAlbumIds ?: run {
            ((downloadRepository.getDownloadedAlbumIds() as? MellowResult.Success)?.data ?: emptySet())
                .also { cachedDlAlbumIds = it }
        }
        val dlArtistNames = cachedDlArtistNames ?: run {
            ((downloadRepository.getDownloadedArtistNames() as? MellowResult.Success)?.data ?: emptySet())
                .also { cachedDlArtistNames = it }
        }
        val dlTrackIds = cachedDlTrackIds ?: run {
            ((downloadRepository.getDownloadedTrackIds() as? MellowResult.Success)?.data ?: emptySet())
                .also { cachedDlTrackIds = it }
        }
        _uiState.value = FavoritesUiState(
            tracks = unfilteredFavTracks.filter { it.id in dlTrackIds },
            albums = unfilteredFavAlbums.filter { it.id in dlAlbumIds },
            artists = unfilteredFavArtists.filter { it.name in dlArtistNames },
            isLoading = false,
        )
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
    }
}
