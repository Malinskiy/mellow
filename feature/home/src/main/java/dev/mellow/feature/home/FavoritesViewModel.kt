package dev.mellow.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private var loadedServerId: String? = null
    private var syncCompleted = false

    fun loadFavorites(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId

        combine(
            libraryRepository.getFavoriteTracks(serverId),
            libraryRepository.getFavoriteAlbums(serverId),
            libraryRepository.getFavoriteArtists(serverId),
        ) { tracks, albums, artists ->
            val hasData = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
            _uiState.value = FavoritesUiState(
                tracks = tracks,
                albums = albums,
                artists = artists,
                isLoading = !hasData && !syncCompleted,
            )
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
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
    }
}
