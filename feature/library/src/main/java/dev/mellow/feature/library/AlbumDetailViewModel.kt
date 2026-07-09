package dev.mellow.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.preferences.DownloadPreferences
import dev.mellow.core.data.repository.DownloadRepository
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.core.model.Album
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences,
    private val userRepository: UserRepositoryImpl,
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    val albumDownloadState: StateFlow<AlbumDownloadState> =
        downloadRepository.observeAlbumDownloads(albumId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AlbumDownloadState(albumId, 0, 0, 0L, 0L, AlbumDownloadState.Status.NONE),
            )

    val trackDownloadStates: StateFlow<Map<String, DownloadState?>> = _uiState
        .map { state -> state.tracks.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { trackIds ->
            if (trackIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    trackIds.map { id ->
                        downloadRepository.observeDownload(id).map { state -> id to state }
                    },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        loadAlbumDetail()
    }

    private fun loadAlbumDetail() {
        libraryRepository.observeAlbum(albumId)
            .onEach { album ->
                if (album != null) {
                    _uiState.value = _uiState.value.copy(album = album, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Album not found")
                }
            }
            .launchIn(viewModelScope)

        libraryRepository.getAlbumTracks(albumId)
            .onEach { tracks -> _uiState.value = _uiState.value.copy(tracks = tracks) }
            .launchIn(viewModelScope)
    }

    fun downloadAlbum() {
        viewModelScope.launch {
            val tracks = _uiState.value.tracks
            if (tracks.isEmpty()) return@launch
            val server = userRepository.getActiveServer() ?: return@launch
            val quality = downloadPreferences.downloadQuality.first()
            downloadRepository.downloadAlbum(albumId, tracks, server.id, quality)
        }
    }

    fun removeAlbumDownloads() {
        viewModelScope.launch {
            downloadRepository.removeAlbumDownloads(albumId)
        }
    }

    fun retry() {
        loadAlbumDetail()
    }
}
