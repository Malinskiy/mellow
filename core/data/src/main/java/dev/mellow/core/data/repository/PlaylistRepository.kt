package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(serverId: String): Flow<MellowResult<List<Playlist>>>
    fun observePlaylistTracks(playlistId: String): Flow<MellowResult<List<Track>>>
    suspend fun getPlaylistById(id: String): MellowResult<Playlist?>
    suspend fun syncPlaylists(serverId: String): MellowResult<Unit>
    suspend fun syncPlaylistTracks(playlistId: String, serverId: String): MellowResult<Unit>
    suspend fun createPlaylist(name: String, serverId: String): MellowResult<String>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String): MellowResult<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): MellowResult<Unit>
}
