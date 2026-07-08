package dev.mellow.core.data.repository

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
    suspend fun getArtist(artistId: String): Artist?
    fun getArtistTracks(artistId: String): Flow<List<Track>>
    fun getRecentTracks(serverId: String): Flow<List<Track>>
    suspend fun search(serverId: String, query: String): List<Track>
    fun getFavoriteTracks(serverId: String): Flow<List<Track>>
    fun getFavoriteAlbums(serverId: String): Flow<List<Album>>
    fun getFavoriteArtists(serverId: String): Flow<List<Artist>>
    suspend fun syncLibrary(serverId: String)
}
