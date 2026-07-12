package dev.mellow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Track
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import java.time.Duration
import javax.inject.Inject

data class HomeUiState(
    val quickPicks: List<HomeAlbumItem> = emptyList(),
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
    private val shuffleSeed = System.nanoTime()
    private var cachedQuickPicks: List<HomeAlbumItem>? = null
    private var cachedFavTracks: List<Track>? = null

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
                .sortedByDescending { it.dateAdded }
                .take(20)
                .map { it.toHomeAlbumItem() }

            val mostPlayedIds = mostPlayedAlbums.map { it.id }.toSet()
            val recentlyPlayed = if (recentlyPlayedAlbums.isNotEmpty()) {
                recentlyPlayedAlbums.take(12).map { it.toHomeAlbumItem() }
            } else {
                emptyList()
            }

            val quickPickPool = cachedQuickPicks ?: run {
                val picks = if (mostPlayedAlbums.isNotEmpty() || recentlyPlayedAlbums.isNotEmpty()) {
                    val combined = mostPlayedAlbums +
                        recentlyPlayedAlbums.filterNot { it.id in mostPlayedIds }
                    combined.shuffled(Random(shuffleSeed)).take(12).map { it.toHomeAlbumItem() }
                } else if (favoriteAlbums.isNotEmpty()) {
                    favoriteAlbums.shuffled(Random(shuffleSeed)).take(12).map { it.toHomeAlbumItem() }
                } else {
                    emptyList()
                }
                cachedQuickPicks = picks
                picks
            }

            val genres = albums
                .flatMap { it.genres }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(15)
                .map { it.key }

            val shuffledFavs = cachedFavTracks ?: run {
                val picks = favTracks.shuffled(Random(shuffleSeed)).take(5)
                cachedFavTracks = picks
                picks
            }
            _favTrackModels.value = shuffledFavs

            _uiState.value = HomeUiState(
                quickPicks = quickPickPool,
                recentlyPlayed = recentlyPlayed,
                recentlyAdded = recentlyAdded,
                favoriteTracks = shuffledFavs.map { it.toHomeTrackItem() },
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
    albumId = albumId,
)

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
