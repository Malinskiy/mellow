package dev.mellow.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val totalTrackCount: Int = 0,
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
        libraryRepository.observeArtist(artistId)
            .onEach { result ->
                when (result) {
                    is MellowResult.Success -> {
                        val artist = result.data
                        if (artist != null) {
                            _uiState.value = _uiState.value.copy(artist = artist, isLoading = false)
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Artist not found")
                        }
                    }
                    is MellowResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = result.exception.message)
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val artist = libraryRepository.observeArtist(artistId)
                .mapNotNull { (it as? MellowResult.Success)?.data }
                .first()

            libraryRepository.getArtistAlbumsById(artist.id)
                .onEach { result ->
                    when (result) {
                        is MellowResult.Success -> _uiState.value = _uiState.value.copy(albums = result.data)
                        else -> {}
                    }
                }
                .launchIn(viewModelScope)

            libraryRepository.getArtistTracksById(artist.id)
                .onEach { result ->
                    when (result) {
                        is MellowResult.Success -> _uiState.value = _uiState.value.copy(topTracks = result.data)
                        else -> {}
                    }
                }
                .launchIn(viewModelScope)

            when (val trackCountResult = libraryRepository.countArtistTracksById(artist.id)) {
                is MellowResult.Success -> _uiState.value = _uiState.value.copy(totalTrackCount = trackCountResult.data)
                else -> {}
            }
        }
    }

    fun retry() {
        loadArtistDetail()
    }
}
