package dev.mellow.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.preferences.DownloadPreferences
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.repository.DownloadRepository
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.core.model.Album
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumDownloadEvent {
    data class StorageCapExceeded(val usedBytes: Long, val capBytes: Long) : AlbumDownloadEvent
}

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

    private val _albumId = MutableStateFlow(savedStateHandle.get<String>("albumId") ?: "")

    fun setAlbumId(id: String) {
        if (id.isNotEmpty() && id != _albumId.value) {
            _uiState.value = AlbumDetailUiState()
            _albumId.value = id
        }
    }

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    val albumDownloadState: StateFlow<AlbumDownloadState> = _albumId
        .flatMapLatest { id ->
            if (id.isEmpty()) {
                flowOf(AlbumDownloadState("", 0, 0, 0L, 0L, AlbumDownloadState.Status.NONE))
            } else {
                downloadRepository.observeAlbumDownloads(id).map { result ->
                    when (result) {
                        is MellowResult.Success -> result.data
                        else -> AlbumDownloadState(id, 0, 0, 0L, 0L, AlbumDownloadState.Status.NONE)
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AlbumDownloadState("", 0, 0, 0L, 0L, AlbumDownloadState.Status.NONE),
        )

    private val _downloadEvents = Channel<AlbumDownloadEvent>(Channel.BUFFERED)
    val downloadEvents = _downloadEvents.receiveAsFlow()

    val liveProgress: StateFlow<Map<String, Float>> = downloadRepository.liveProgress

    val trackDownloadStates: StateFlow<Map<String, DownloadState?>> = _uiState
        .map { state -> state.tracks.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { trackIds ->
            if (trackIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    trackIds.map { id ->
                        downloadRepository.observeDownload(id).map { result ->
                            val state = when (result) {
                                is MellowResult.Success -> result.data
                                else -> null
                            }
                            id to state
                        }
                    },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        _albumId
            .flatMapLatest { id ->
                if (id.isEmpty()) flowOf(null)
                else libraryRepository.observeAlbum(id)
            }
            .onEach { result ->
                when (result) {
                    null -> {}
                    is MellowResult.Success -> {
                        val album = result.data
                        if (album != null) {
                            _uiState.value = _uiState.value.copy(album = album, isLoading = false)
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Album not found")
                        }
                    }
                    is MellowResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = result.exception.message)
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)

        _albumId
            .flatMapLatest { id ->
                if (id.isEmpty()) flowOf(null)
                else libraryRepository.getAlbumTracks(id)
            }
            .onEach { result ->
                when (result) {
                    null -> _uiState.value = _uiState.value.copy(tracks = emptyList())
                    is MellowResult.Success -> _uiState.value = _uiState.value.copy(tracks = result.data)
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    fun downloadAlbum() {
        viewModelScope.launch {
            val tracks = _uiState.value.tracks
            if (tracks.isEmpty()) return@launch

            val cap = downloadPreferences.storageCap.first()
            if (cap != Long.MAX_VALUE) {
                val usedResult = downloadRepository.getTotalDownloadedBytes().first()
                val used = (usedResult as? MellowResult.Success)?.data ?: 0L
                if (used >= cap) {
                    _downloadEvents.send(AlbumDownloadEvent.StorageCapExceeded(used, cap))
                    return@launch
                }
            }

            val server = userRepository.getActiveServer() ?: return@launch
            val quality = downloadPreferences.downloadQuality.first()
            downloadRepository.downloadAlbum(_albumId.value, tracks, server.id, quality)
        }
    }

    fun downloadAlbumForced() {
        viewModelScope.launch {
            val tracks = _uiState.value.tracks
            if (tracks.isEmpty()) return@launch
            val server = userRepository.getActiveServer() ?: return@launch
            val quality = downloadPreferences.downloadQuality.first()
            downloadRepository.downloadAlbum(_albumId.value, tracks, server.id, quality)
        }
    }

    fun updateStorageCap(newCap: Long) {
        viewModelScope.launch {
            downloadPreferences.setStorageCap(newCap)
        }
    }

    fun removeAlbumDownloads() {
        viewModelScope.launch {
            downloadRepository.removeAlbumDownloads(_albumId.value)
        }
    }

    fun retry() {
        val currentId = _albumId.value
        if (currentId.isNotEmpty()) {
            _uiState.value = AlbumDetailUiState()
            _albumId.value = ""
            _albumId.value = currentId
        }
    }
}
