package dev.mellow.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbumDetail()
    }

    private fun loadAlbumDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val album = libraryRepository.getAlbum(albumId)
                if (album != null) {
                    _uiState.value = _uiState.value.copy(album = album, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Album not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load album",
                )
            }
        }

        libraryRepository.getAlbumTracks(albumId)
            .onEach { tracks -> _uiState.value = _uiState.value.copy(tracks = tracks) }
            .launchIn(viewModelScope)
    }

    fun retry() {
        loadAlbumDetail()
    }
}
