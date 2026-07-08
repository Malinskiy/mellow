package dev.mellow.feature.home

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    fun loadFavorites(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId

        libraryRepository.getFavoriteTracks(serverId)
            .onEach { tracks -> _uiState.value = _uiState.value.copy(tracks = tracks, isLoading = false) }
            .launchIn(viewModelScope)

        libraryRepository.getFavoriteAlbums(serverId)
            .onEach { albums -> _uiState.value = _uiState.value.copy(albums = albums, isLoading = false) }
            .launchIn(viewModelScope)

        libraryRepository.getFavoriteArtists(serverId)
            .onEach { artists -> _uiState.value = _uiState.value.copy(artists = artists, isLoading = false) }
            .launchIn(viewModelScope)
    }
}
