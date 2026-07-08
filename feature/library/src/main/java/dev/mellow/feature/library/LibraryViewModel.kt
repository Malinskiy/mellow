package dev.mellow.feature.library

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

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadLibrary(serverId: String) {
        libraryRepository.getAlbums(serverId)
            .onEach { albums -> _uiState.value = _uiState.value.copy(albums = albums, isLoading = false) }
            .launchIn(viewModelScope)

        libraryRepository.getArtists(serverId)
            .onEach { artists -> _uiState.value = _uiState.value.copy(artists = artists) }
            .launchIn(viewModelScope)

        libraryRepository.getRecentTracks(serverId)
            .onEach { tracks -> _uiState.value = _uiState.value.copy(tracks = tracks) }
            .launchIn(viewModelScope)
    }
}
