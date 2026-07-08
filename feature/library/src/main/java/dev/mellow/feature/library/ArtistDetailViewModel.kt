package dev.mellow.feature.library

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val artistId: String = savedStateHandle["artistId"] ?: ""

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtistDetail()
    }

    private fun loadArtistDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val artist = libraryRepository.getArtist(artistId)
                if (artist != null) {
                    _uiState.value = _uiState.value.copy(artist = artist, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Artist not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load artist",
                )
            }
        }

        libraryRepository.getArtistAlbums(artistId)
            .onEach { albums -> _uiState.value = _uiState.value.copy(albums = albums) }
            .launchIn(viewModelScope)

        libraryRepository.getArtistTracks(artistId)
            .onEach { tracks -> _uiState.value = _uiState.value.copy(topTracks = tracks) }
            .launchIn(viewModelScope)
    }

    fun retry() {
        loadArtistDetail()
    }
}
