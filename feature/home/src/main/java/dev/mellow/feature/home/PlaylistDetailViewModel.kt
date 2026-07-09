package dev.mellow.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var syncedServerId: String? = null

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            val playlist = playlistRepository.getPlaylistById(playlistId)
            _uiState.value = _uiState.value.copy(playlist = playlist)
        }

        playlistRepository.observePlaylistTracks(playlistId)
            .onEach { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks, isLoading = false)
            }
            .launchIn(viewModelScope)
    }

    fun syncTracks(serverId: String) {
        if (serverId.isEmpty() || serverId == syncedServerId) return
        syncedServerId = serverId
        viewModelScope.launch {
            playlistRepository.syncPlaylistTracks(playlistId, serverId)
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }
}
