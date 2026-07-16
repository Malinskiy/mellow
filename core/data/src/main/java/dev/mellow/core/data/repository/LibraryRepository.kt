package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    fun getArtists(serverId: String): Flow<MellowResult<List<Artist>>>
    fun getAlbumTracks(albumId: String): Flow<MellowResult<List<Track>>>
    fun getArtistAlbums(artistName: String): Flow<MellowResult<List<Album>>>
    suspend fun getAlbum(albumId: String): MellowResult<Album?>
    fun observeAlbum(albumId: String): Flow<MellowResult<Album?>>
    suspend fun getArtist(artistId: String): MellowResult<Artist?>
    fun observeArtist(artistId: String): Flow<MellowResult<Artist?>>
    fun getArtistTracks(artistName: String): Flow<MellowResult<List<Track>>>
    suspend fun countArtistAlbums(artistName: String): MellowResult<Int>
    suspend fun countArtistTracks(artistName: String): MellowResult<Int>
    fun getRecentTracks(serverId: String): Flow<MellowResult<List<Track>>>
    suspend fun search(serverId: String, query: String): MellowResult<List<Track>>
    suspend fun searchAlbums(serverId: String, query: String): MellowResult<List<Album>>
    suspend fun searchArtists(serverId: String, query: String): MellowResult<List<Artist>>
    fun getRecentSearches(serverId: String): Flow<MellowResult<List<String>>>
    suspend fun saveRecentSearch(serverId: String, query: String): MellowResult<Unit>
    suspend fun deleteRecentSearch(serverId: String, query: String): MellowResult<Unit>
    suspend fun clearRecentSearches(serverId: String): MellowResult<Unit>
    fun getFavoriteTracks(serverId: String): Flow<MellowResult<List<Track>>>
    fun getFavoriteAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    fun getFavoriteArtists(serverId: String): Flow<MellowResult<List<Artist>>>
    fun getRecentlyPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    fun getMostPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    suspend fun syncHomeScreenPriority(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Set<String>>
    suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Unit>
    suspend fun syncFavorites(serverId: String): MellowResult<Unit>
    suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Unit>
    suspend fun getInstantMix(serverId: String, trackId: String): MellowResult<List<Track>>
}
