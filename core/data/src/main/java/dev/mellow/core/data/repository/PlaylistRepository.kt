package dev.mellow.core.data.repository

import androidx.paging.PagingData
import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(serverId: String): Flow<MellowResult<List<Playlist>>>

    /** The playlist's tracks in playlist order, a page at a time. */
    fun getPagedPlaylistTracks(playlistId: String): Flow<PagingData<Track>>

    /** [limit] tracks of [getPagedPlaylistTracks] from position [offset]. */
    suspend fun getPlaylistTracksSlice(playlistId: String, offset: Int, limit: Int): MellowResult<List<Track>>
    suspend fun countPlaylistTracks(playlistId: String): MellowResult<Int>

    /** [limit] of the playlist's tracks picked at random from all of them, in random order, to shuffle it. */
    suspend fun pickRandomPlaylistTracks(playlistId: String, limit: Int): MellowResult<List<Track>>
    suspend fun getPlaylistById(id: String): MellowResult<Playlist?>
    suspend fun syncPlaylists(serverId: String): MellowResult<Unit>
    suspend fun syncPlaylistTracks(playlistId: String, serverId: String): MellowResult<Unit>
    suspend fun createPlaylist(name: String, serverId: String): MellowResult<String>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String): MellowResult<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): MellowResult<Unit>
}
