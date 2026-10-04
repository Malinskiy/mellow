package dev.mellow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import javax.inject.Inject

data class HomeUiState(
    val quickPicks: List<HomeAlbumItem> = emptyList(),
    val recentlyPlayed: List<HomeAlbumItem> = emptyList(),
    val recentlyAdded: List<HomeAlbumItem> = emptyList(),
    val favoriteTracks: List<HomeTrackItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: dev.mellow.core.data.repository.DownloadRepository,
    displayPreferences: dev.mellow.core.data.preferences.DisplayPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _favTrackModels = MutableStateFlow<List<Track>>(emptyList())
    val favTrackModels: StateFlow<List<Track>> = _favTrackModels.asStateFlow()

    private val _downloadedOnly: StateFlow<Boolean> = displayPreferences.downloadedOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedServerId: String? = null
    private var cachedQuickPicks: List<HomeAlbumItem>? = null
    private var cachedRecentlyPlayed: List<HomeAlbumItem>? = null
    private var cachedRecentlyAdded: List<HomeAlbumItem>? = null
    private var cachedFavTracks: List<Track>? = null

    fun retry() {
        val id = loadedServerId ?: return
        loadedServerId = null
        clearCaches()
        _uiState.value = _uiState.value.copy(error = null, isLoading = true)
        loadHome(id)
    }

    private fun clearCaches() {
        cachedQuickPicks = null
        cachedRecentlyPlayed = null
        cachedRecentlyAdded = null
        cachedFavTracks = null
    }

    fun loadHome(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId
        clearCaches()

        // Every row reads only the albums and tracks it shows, never the whole library.
        combine(
            libraryRepository.getRecentlyAddedAlbums(serverId, limit = ROW_SIZE),
            libraryRepository.getRandomFavoriteTracks(serverId, limit = FAVORITE_TRACKS_SIZE),
            libraryRepository.getRecentlyPlayedAlbums(serverId),
            libraryRepository.getRandomAlbums(serverId, limit = 2 * ROW_SIZE),
            libraryRepository.getTopGenres(serverId, limit = GENRES_SIZE),
        ) { recentlyAddedResult, favTracksResult, recentlyPlayedResult, randomAlbumsResult, genresResult ->
            val recentlyAddedAlbums = (recentlyAddedResult as? MellowResult.Success)?.data ?: emptyList()
            val favTracks = (favTracksResult as? MellowResult.Success)?.data ?: emptyList()
            val recentlyPlayedAlbums = (recentlyPlayedResult as? MellowResult.Success)?.data ?: emptyList()
            val randomAlbums = (randomAlbumsResult as? MellowResult.Success)?.data ?: emptyList()
            val genres = (genresResult as? MellowResult.Success)?.data ?: emptyList()

            val recentlyAdded = cachedRecentlyAdded ?: run {
                val items = recentlyAddedAlbums.map { it.toHomeAlbumItem() }
                if (items.size >= 3) cachedRecentlyAdded = items
                items
            }

            val recentlyPlayed = cachedRecentlyPlayed ?: run {
                val items = if (recentlyPlayedAlbums.isNotEmpty()) {
                    recentlyPlayedAlbums.take(12).map { it.toHomeAlbumItem() }
                } else {
                    emptyList()
                }
                if (items.size >= 3) cachedRecentlyPlayed = items
                items
            }

            val recentlyPlayedIds = recentlyPlayed.map { it.id }.toSet()
            val quickPickPool = cachedQuickPicks ?: run {
                // Random albums of the whole library; twice a row's worth, so a row is left once the recently
                // played ones are taken out.
                val picks = randomAlbums
                    .filterNot { it.id in recentlyPlayedIds }
                    .take(ROW_SIZE)
                    .map { it.toHomeAlbumItem() }
                if (picks.size >= 4) cachedQuickPicks = picks
                picks
            }

            val shuffledFavs = cachedFavTracks ?: run {
                // Already a random pick of the favorites.
                val picks = favTracks
                if (picks.isNotEmpty()) cachedFavTracks = picks
                picks
            }
            _favTrackModels.value = shuffledFavs

            unfilteredHome = HomeUiState(
                quickPicks = quickPickPool,
                recentlyPlayed = recentlyPlayed,
                recentlyAdded = recentlyAdded,
                favoriteTracks = shuffledFavs.map { it.toHomeTrackItem() },
                genres = genres,
                isLoading = false,
            )
            emitFiltered()
        }.catch { e ->
            _uiState.value = _uiState.value.copy(
                error = e.message ?: "Failed to load home",
                isLoading = false,
            )
        }.launchIn(viewModelScope)

        _downloadedOnly.onEach { emitFiltered() }.launchIn(viewModelScope)
    }

    private var unfilteredHome = HomeUiState()
    private var cachedDlAlbumIds: Set<String>? = null
    private var cachedDlTrackIds: Set<String>? = null

    private suspend fun emitFiltered() {
        val state = unfilteredHome
        if (state.isLoading) { _uiState.value = state; return }
        if (!_downloadedOnly.value) { _uiState.value = state; return }
        val dlAlbumIds = cachedDlAlbumIds ?: run {
            ((downloadRepository.getDownloadedAlbumIds() as? MellowResult.Success)?.data ?: emptySet())
                .also { cachedDlAlbumIds = it }
        }
        val dlTrackIds = cachedDlTrackIds ?: run {
            ((downloadRepository.getDownloadedTrackIds() as? MellowResult.Success)?.data ?: emptySet())
                .also { cachedDlTrackIds = it }
        }
        _uiState.value = state.copy(
            quickPicks = state.quickPicks.filter { it.id in dlAlbumIds },
            recentlyPlayed = state.recentlyPlayed.filter { it.id in dlAlbumIds },
            recentlyAdded = state.recentlyAdded.filter { it.id in dlAlbumIds },
            favoriteTracks = state.favoriteTracks.filter { it.id in dlTrackIds },
        )
    }

    private companion object {
        /** Albums in each album row. */
        const val ROW_SIZE = 12
        const val FAVORITE_TRACKS_SIZE = 5
        const val GENRES_SIZE = 15
    }
}

private fun Album.toHomeAlbumItem() = HomeAlbumItem(
    id = id,
    name = name,
    artist = artistName ?: "",
    imageId = imageId,
)

private fun Track.toHomeTrackItem() = HomeTrackItem(
    id = id,
    title = name,
    artist = artistName ?: "",
    album = albumName ?: "",
    duration = formatDuration(duration),
    imageId = imageId,
    albumId = albumId,
)

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
