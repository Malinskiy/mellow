package dev.mellow.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchResult {
    data class TrackResult(val track: Track) : SearchResult
    data class AlbumResult(val album: Album) : SearchResult
    data class ArtistResult(val artist: Artist) : SearchResult
}

data class SearchUiState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val topResult: SearchResult? = null,
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasResults: Boolean
        get() = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var recentSearchesJob: Job? = null
    private var currentServerId: String? = null

    fun loadRecentSearches(serverId: String) {
        if (serverId == currentServerId) return
        currentServerId = serverId
        recentSearchesJob?.cancel()
        recentSearchesJob = viewModelScope.launch {
            libraryRepository.getRecentSearches(serverId).collect { searches ->
                _uiState.value = _uiState.value.copy(recentSearches = searches)
            }
        }
    }

    fun onQueryChanged(query: String, serverId: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = SearchUiState(
                query = query,
                recentSearches = _uiState.value.recentSearches,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)

            try {
                val tracksDeferred = async { libraryRepository.search(serverId, query) }
                val albumsDeferred = async { libraryRepository.searchAlbums(serverId, query) }
                val artistsDeferred = async { libraryRepository.searchArtists(serverId, query) }

                val tracks = tracksDeferred.await()
                val albums = albumsDeferred.await()
                val artists = artistsDeferred.await().map { artist ->
                    val count = libraryRepository.countArtistAlbums(artist.name)
                    artist.copy(albumCount = count)
                }

                val queryLower = query.lowercase()
                val topResult = pickTopResult(queryLower, tracks, albums, artists)

                _uiState.value = _uiState.value.copy(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    topResult = topResult,
                    isSearching = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Search failed",
                    isSearching = false,
                )
            }
        }
    }

    fun onResultInteracted() {
        val serverId = currentServerId ?: return
        val query = _uiState.value.query
        if (query.length < 2) return
        viewModelScope.launch {
            libraryRepository.saveRecentSearch(serverId, query)
        }
    }

    fun onDeleteRecentSearch(query: String) {
        val serverId = currentServerId ?: return
        viewModelScope.launch {
            libraryRepository.deleteRecentSearch(serverId, query)
        }
    }

    fun onClearRecentSearches() {
        val serverId = currentServerId ?: return
        viewModelScope.launch {
            libraryRepository.clearRecentSearches(serverId)
        }
    }

    private fun pickTopResult(
        query: String,
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
    ): SearchResult? {
        data class Candidate(val name: String, val result: SearchResult)

        val candidates = mutableListOf<Candidate>()
        artists.forEach { candidates.add(Candidate(it.name.lowercase(), SearchResult.ArtistResult(it))) }
        albums.forEach { candidates.add(Candidate(it.name.lowercase(), SearchResult.AlbumResult(it))) }
        tracks.take(3).forEach { candidates.add(Candidate(it.name.lowercase(), SearchResult.TrackResult(it))) }

        if (candidates.isEmpty()) return null

        val exact = candidates.find { it.name == query }
        if (exact != null) return exact.result

        val startsWith = candidates.filter { it.name.startsWith(query) }
            .minByOrNull { it.name.length }
        if (startsWith != null) return startsWith.result

        return candidates.minByOrNull { it.name.length }?.result
    }
}
