package dev.mellow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import java.time.Duration
import javax.inject.Inject

data class HomeUiState(
    val recentlyPlayed: List<HomeAlbumItem> = emptyList(),
    val recentlyAdded: List<HomeAlbumItem> = emptyList(),
    val favoriteTracks: List<HomeTrackItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val albumCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _favTrackModels = MutableStateFlow<List<Track>>(emptyList())
    val favTrackModels: StateFlow<List<Track>> = _favTrackModels.asStateFlow()

    private var loadedServerId: String? = null

    fun loadHome(serverId: String) {
        if (serverId.isEmpty() || serverId == loadedServerId) return
        loadedServerId = serverId

        combine(
            libraryRepository.getAlbums(serverId),
            libraryRepository.getFavoriteTracks(serverId),
            libraryRepository.getRecentlyPlayedAlbums(serverId),
            libraryRepository.getMostPlayedAlbums(serverId),
            libraryRepository.getFavoriteAlbums(serverId),
        ) { albums, favTracks, recentlyPlayedAlbums, mostPlayedAlbums, favoriteAlbums ->
            val recentlyAdded = albums
                .sortedByDescending { it.id }
                .take(20)
                .map { it.toHomeAlbumItem() }

            // Build recentlyPlayed: most-played first (for Quick Picks .take(6)),
            // then recently-played, deduplicated. Falls back to favorites then recently added.
            val mostPlayedIds = mostPlayedAlbums.map { it.id }.toSet()
            val recentlyPlayed = if (mostPlayedAlbums.isNotEmpty() || recentlyPlayedAlbums.isNotEmpty()) {
                val combined = mostPlayedAlbums.take(6) +
                    recentlyPlayedAlbums.filterNot { it.id in mostPlayedIds }
                combined.map { it.toHomeAlbumItem() }
            } else if (favoriteAlbums.isNotEmpty()) {
                favoriteAlbums.take(6).map { it.toHomeAlbumItem() }
            } else {
                emptyList()
            }

            val genres = albums
                .flatMap { it.genres }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(15)
                .map { it.key }

            _favTrackModels.value = favTracks.take(5)

            _uiState.value = HomeUiState(
                recentlyPlayed = recentlyPlayed,
                recentlyAdded = recentlyAdded,
                favoriteTracks = favTracks.take(5).map { it.toHomeTrackItem() },
                genres = genres,
                albumCount = albums.size,
                isLoading = false,
            )
        }.launchIn(viewModelScope)
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
)

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
