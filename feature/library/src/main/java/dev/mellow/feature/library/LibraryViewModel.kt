package dev.mellow.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.common.MellowResult
import dev.mellow.core.common.QUEUE_WINDOW_BEFORE
import dev.mellow.core.common.QUEUE_WINDOW_SIZE
import dev.mellow.core.common.formatTrackDuration
import dev.mellow.core.data.preferences.DisplayPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.LibrarySort
import dev.mellow.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryUiState(
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** What the user chose to see: whose library, in which order, and the genre the albums are narrowed to. */
private data class LibrarySelection(val serverId: String, val sort: LibrarySort, val genre: String?)

/** What the library's lists show: the [LibrarySelection], all tracks or only downloaded ones. */
private data class LibraryQuery(
    val serverId: String,
    val sort: LibrarySort,
    val genre: String?,
    val downloadedOnly: Boolean,
)

/**
 * The library's tabs. Albums, artists and tracks come a page at a time, so no tab holds the whole library; each
 * reloads when its order, the genre or the downloaded-only filter changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    displayPreferences: DisplayPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val selection = MutableStateFlow<LibrarySelection?>(null)

    /** `null` until the preference is read, so the lists don't load unfiltered first. */
    private val downloadedOnlyPreference: StateFlow<Boolean?> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val query: Flow<LibraryQuery> =
        combine(selection.filterNotNull(), downloadedOnlyPreference.filterNotNull()) { s, downloadedOnly ->
            LibraryQuery(s.serverId, s.sort, s.genre, downloadedOnly)
        }

    val albums: Flow<PagingData<AlbumItem>> = query
        .distinctUntilChanged()
        .flatMapLatest { q -> libraryRepository.getPagedAlbums(q.serverId, q.sort, q.genre, q.downloadedOnly) }
        .map { page -> page.map { AlbumItem(it.id, it.name, it.artistName ?: "", it.imageId) } }
        .cachedIn(viewModelScope)

    val artists: Flow<PagingData<ArtistItem>> = query
        // Artists only sort by name; every other order keeps their sort-name order, and the genre doesn't apply.
        .map { q -> q.copy(sort = q.sort.takeIf { it in NAME_SORTS } ?: LibrarySort.RecentlyAdded, genre = null) }
        .distinctUntilChanged()
        .flatMapLatest { q -> libraryRepository.getPagedArtists(q.serverId, q.sort, q.downloadedOnly) }
        .map { page -> page.map { ArtistItem(it.id, it.name, it.albumCount, it.imageId) } }
        .cachedIn(viewModelScope)

    val tracks: Flow<PagingData<TrackItem>> = query
        .map { q -> q.copy(genre = null) }
        .distinctUntilChanged()
        .flatMapLatest { q -> libraryRepository.getPagedTracks(q.serverId, q.sort, q.downloadedOnly) }
        .map { page -> page.map { it.toTrackItem() } }
        .cachedIn(viewModelScope)

    private var genresJob: Job? = null

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        observeGenres()
    }

    /** Shows [serverId]'s library in [sort] order, with the albums narrowed to [genre] if it's set. */
    fun loadLibrary(serverId: String, sort: LibrarySort = LibrarySort.RecentlyAdded, genre: String? = null) {
        selection.value = LibrarySelection(serverId, sort, genre)
        if (genresJob == null) observeGenres()
    }

    /**
     * What to queue when the track [trackId] at [index] of the tracks tab, a list of [count] tracks, is played, and
     * where that track is in it. The database seeks from the track instead of trusting the possibly stale position.
     */
    suspend fun tracksToPlay(index: Int, trackId: String, count: Int): Pair<List<Track>, Int>? {
        val shown = selection.value ?: return null
        val downloadedOnly = downloadedOnlyPreference.value ?: return null
        val result = libraryRepository.getTracksWindow(
            shown.serverId,
            shown.sort,
            downloadedOnly,
            trackId,
            QUEUE_WINDOW_BEFORE,
            QUEUE_WINDOW_SIZE,
        )
        val window = (result as? MellowResult.Success)?.data ?: return null
        val position = window.indexOfFirst { it.id == trackId }
        if (position >= 0) return window to position
        val track = (libraryRepository.getTrack(trackId) as? MellowResult.Success)?.data ?: return null
        return listOf(track) to 0
    }

    /**
     * Up to [QUEUE_WINDOW_SIZE] tracks picked at random from the whole library, in random order, for the Tracks tab's
     * shuffle; only downloaded ones while the list shows only downloads. Empty before the library is loaded.
     */
    suspend fun shuffledTracks(): List<Track> {
        val shown = selection.value ?: return emptyList()
        val downloadedOnly = downloadedOnlyPreference.value ?: return emptyList()
        val picked = libraryRepository.pickRandomTracks(shown.serverId, downloadedOnly, QUEUE_WINDOW_SIZE)
        return (picked as? MellowResult.Success)?.data ?: emptyList()
    }

    private fun observeGenres() {
        genresJob?.cancel()
        genresJob = query
            .map { q -> q.serverId to q.downloadedOnly }
            .distinctUntilChanged()
            .flatMapLatest { (serverId, downloadedOnly) -> libraryRepository.getGenres(serverId, downloadedOnly) }
            .onEach { result ->
                when (result) {
                    is MellowResult.Success ->
                        _uiState.value = _uiState.value.copy(genres = result.data, isLoading = false, error = null)
                    is MellowResult.Error ->
                        _uiState.value = _uiState.value.copy(error = result.exception.message, isLoading = false)
                    MellowResult.Loading -> {}
                }
            }
            .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
            .launchIn(viewModelScope)
    }

    private companion object {
        val NAME_SORTS = setOf(LibrarySort.NameAscending, LibrarySort.NameDescending)
    }
}

private fun Track.toTrackItem() = TrackItem(
    id = id,
    title = name,
    artist = artistName ?: "",
    album = albumName ?: "",
    duration = formatTrackDuration(duration),
    imageId = imageId,
    albumId = albumId,
)
