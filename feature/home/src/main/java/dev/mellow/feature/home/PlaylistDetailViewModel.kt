package dev.mellow.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.common.MellowResult
import dev.mellow.core.common.QUEUE_WINDOW_SIZE
import dev.mellow.core.common.queueWindow
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    /** The playlist's tracks, a page at a time: a playlist can be too long to hold at once. */
    val tracks: Flow<PagingData<Track>> = playlistRepository.getPagedPlaylistTracks(playlistId)
        .cachedIn(viewModelScope)

    private var syncedServerId: String? = null

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            when (val result = playlistRepository.getPlaylistById(playlistId)) {
                is MellowResult.Success -> _uiState.value = _uiState.value.copy(playlist = result.data)
                else -> {}
            }
        }
    }

    /**
     * What to queue to play the playlist from the track at [index] (the track [trackId], if given), and where that
     * track is in it: the playlist's tracks around it, at most [QUEUE_WINDOW_SIZE] of them. `null` if there's
     * nothing to play.
     */
    suspend fun tracksToPlay(index: Int, trackId: String?): Pair<List<Track>, Int>? {
        val count = (playlistRepository.countPlaylistTracks(playlistId) as? MellowResult.Success)?.data ?: return null
        return queueWindow(
            index = index,
            count = count,
            trackId = trackId,
            idOf = Track::id,
            loadSlice = { offset, limit ->
                (playlistRepository.getPlaylistTracksSlice(playlistId, offset, limit) as? MellowResult.Success)?.data
            },
            loadTrack = { moved -> (libraryRepository.getTrack(moved) as? MellowResult.Success)?.data },
        )
    }

    /**
     * The playlist shuffled: at most [QUEUE_WINDOW_SIZE] of its tracks picked at random from all of them, so a
     * shuffle never reads the whole playlist.
     */
    suspend fun shuffledTracks(): List<Track> {
        val picked = playlistRepository.pickRandomPlaylistTracks(playlistId, QUEUE_WINDOW_SIZE)
        return (picked as? MellowResult.Success)?.data ?: emptyList()
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
