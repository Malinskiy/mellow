package dev.mellow.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: dev.mellow.core.data.repository.DownloadRepository,
    displayPreferences: dev.mellow.core.data.preferences.DisplayPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _downloadedOnly: StateFlow<Boolean> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedServerId: String? = null

    fun retry() {
        val id = loadedServerId ?: return
        loadedServerId = null
        _uiState.value = LibraryUiState()
        loadLibrary(id)
    }

    fun loadLibrary(serverId: String) {
        if (loadedServerId == serverId) return
        loadedServerId = serverId

        libraryRepository.getAlbums(serverId)
            .onEach { albums ->
                unfilteredAlbums = albums
                emitFiltered()
            }
            .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
            .launchIn(viewModelScope)

        libraryRepository.getArtists(serverId)
            .onEach { artists ->
                unfilteredArtists = artists
                emitFiltered()
            }
            .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
            .launchIn(viewModelScope)

        libraryRepository.getRecentTracks(serverId)
            .onEach { tracks ->
                unfilteredTracks = tracks
                emitFiltered()
            }
            .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
            .launchIn(viewModelScope)

        _downloadedOnly.onEach { emitFiltered() }.launchIn(viewModelScope)
    }

    private var unfilteredAlbums: List<Album> = emptyList()
    private var unfilteredArtists: List<Artist> = emptyList()
    private var unfilteredTracks: List<Track> = emptyList()
    private var cachedDlAlbumIds: Set<String>? = null
    private var cachedDlArtistNames: Set<String>? = null
    private var cachedDlTrackIds: Set<String>? = null

    private suspend fun emitFiltered() {
        if (!_downloadedOnly.value) {
            _uiState.value = _uiState.value.copy(
                albums = unfilteredAlbums,
                artists = unfilteredArtists,
                tracks = unfilteredTracks,
                isLoading = false,
            )
            return
        }
        val dlAlbumIds = cachedDlAlbumIds ?: downloadRepository.getDownloadedAlbumIds().also { cachedDlAlbumIds = it }
        val dlArtistNames = cachedDlArtistNames ?: downloadRepository.getDownloadedArtistNames().also { cachedDlArtistNames = it }
        val dlTrackIds = cachedDlTrackIds ?: downloadRepository.getDownloadedTrackIds().also { cachedDlTrackIds = it }
        _uiState.value = _uiState.value.copy(
            albums = unfilteredAlbums.filter { it.id in dlAlbumIds },
            artists = unfilteredArtists.filter { it.name in dlArtistNames },
            tracks = unfilteredTracks.filter { it.id in dlTrackIds },
            isLoading = false,
        )
    }
}
