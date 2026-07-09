package dev.mellow.core.data.repository

import dev.mellow.core.data.SyncProgress
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getAlbums(serverId: String): Flow<List<Album>>
    fun getArtists(serverId: String): Flow<List<Artist>>
    fun getAlbumTracks(albumId: String): Flow<List<Track>>
    fun getArtistAlbums(artistId: String): Flow<List<Album>>
    suspend fun getAlbum(albumId: String): Album?
    fun observeAlbum(albumId: String): Flow<Album?>
    suspend fun getArtist(artistId: String): Artist?
    fun observeArtist(artistId: String): Flow<Artist?>
    fun getArtistTracks(artistId: String): Flow<List<Track>>
    fun getRecentTracks(serverId: String): Flow<List<Track>>
    suspend fun search(serverId: String, query: String): List<Track>
    suspend fun searchAlbums(serverId: String, query: String): List<Album>
    suspend fun searchArtists(serverId: String, query: String): List<Artist>
    fun getFavoriteTracks(serverId: String): Flow<List<Track>>
    fun getFavoriteAlbums(serverId: String): Flow<List<Album>>
    fun getFavoriteArtists(serverId: String): Flow<List<Artist>>
    fun getRecentlyPlayedAlbums(serverId: String): Flow<List<Album>>
    fun getMostPlayedAlbums(serverId: String): Flow<List<Album>>
    suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit = {})
    suspend fun syncFavorites(serverId: String)
    suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit = {})
}
