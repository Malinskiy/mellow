package dev.mellow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private var loadedServerId: String? = null

    fun retry() {
        val id = loadedServerId ?: return
        loadedServerId = null
        _uiState.value = PlaylistsUiState()
        loadPlaylists(id)
    }

    fun loadPlaylists(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId

        playlistRepository.observePlaylists(serverId)
            .onEach { result ->
                val playlists = (result as? MellowResult.Success)?.data ?: return@onEach
                _uiState.value = PlaylistsUiState(playlists = playlists, isLoading = false)
            }
            .catch { e ->
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            playlistRepository.syncPlaylists(serverId)
        }
    }

    fun createPlaylist(name: String) {
        val serverId = loadedServerId ?: return
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, serverId)
        }
    }
}
