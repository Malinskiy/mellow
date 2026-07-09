package dev.mellow.core.data.repository

import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(serverId: String): Flow<List<Playlist>>
    fun observePlaylistTracks(playlistId: String): Flow<List<Track>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun syncPlaylists(serverId: String)
    suspend fun syncPlaylistTracks(playlistId: String, serverId: String)
    suspend fun createPlaylist(name: String, serverId: String): String?
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
}
