package dev.mellow.core.data.repository

import android.util.Log
import dev.mellow.core.data.mapper.toModel
import dev.mellow.core.data.mapper.toPlaylistEntity
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import dev.mellow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val serverDao: ServerDao,
    private val jellyfinDataSource: JellyfinDataSource,
) : PlaylistRepository {

    companion object {
        private const val TAG = "PlaylistRepository"
    }

    override fun observePlaylists(serverId: String): Flow<List<Playlist>> =
        playlistDao.observePlaylists(serverId).map { entities -> entities.map { it.toModel() } }

    override fun observePlaylistTracks(playlistId: String): Flow<List<Track>> =
        playlistDao.getPlaylistTracks(playlistId).map { entities -> entities.map { it.toModel() } }

    override suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toModel()

    override suspend fun syncPlaylists(serverId: String) {
        try {
            val server = serverDao.getActiveServer() ?: return
            val userId = UUID.fromString(server.userId)
            val items = jellyfinDataSource.getPlaylists(userId)
            if (items.isNotEmpty()) {
                playlistDao.upsertPlaylists(items.map { it.toPlaylistEntity(serverId) })
            }
            Log.d(TAG, "syncPlaylists: ${items.size} playlists synced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync playlists", e)
        }
    }

    override suspend fun syncPlaylistTracks(playlistId: String, serverId: String) {
        try {
            val server = serverDao.getActiveServer() ?: return
            val userId = UUID.fromString(server.userId)
            val items = jellyfinDataSource.getPlaylistItems(
                playlistId = UUID.fromString(playlistId),
                userId = userId,
            )
            if (items.isNotEmpty()) {
                trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
                playlistDao.clearPlaylistTracks(playlistId)
                playlistDao.insertPlaylistTracks(
                    items.mapIndexed { index, dto ->
                        PlaylistTrackCrossRef(
                            playlistId = playlistId,
                            trackId = dto.id.toString(),
                            position = index,
                            addedAt = System.currentTimeMillis(),
                        )
                    },
                )
            }
            Log.d(TAG, "syncPlaylistTracks: ${items.size} tracks synced for $playlistId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync playlist tracks for $playlistId", e)
        }
    }

    override suspend fun createPlaylist(name: String, serverId: String): String? {
        return try {
            val server = serverDao.getActiveServer() ?: return null
            val userId = UUID.fromString(server.userId)
            val newId = jellyfinDataSource.createPlaylist(name, userId) ?: return null

            playlistDao.upsert(
                PlaylistEntity(
                    id = newId,
                    serverId = serverId,
                    name = name,
                    sortName = name,
                    trackCount = 0,
                    durationMs = 0L,
                    imageTag = null,
                    isFavorite = false,
                    isLocal = false,
                    lastSynced = System.currentTimeMillis(),
                ),
            )
            Log.d(TAG, "createPlaylist: created '$name' with id=$newId")
            newId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create playlist '$name'", e)
            null
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String) {
        val existingTracks = playlistDao.getPlaylistById(playlistId)?.trackCount ?: 0
        playlistDao.insertPlaylistTracks(
            listOf(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = existingTracks,
                    addedAt = System.currentTimeMillis(),
                ),
            ),
        )

        try {
            val server = serverDao.getActiveServer() ?: return
            val userId = UUID.fromString(server.userId)
            jellyfinDataSource.addToPlaylist(
                playlistId = UUID.fromString(playlistId),
                trackIds = listOf(UUID.fromString(trackId)),
                userId = userId,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add track $trackId to playlist $playlistId on server", e)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)

        try {
            jellyfinDataSource.removeFromPlaylist(
                playlistId = playlistId,
                entryIds = listOf(trackId),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove track $trackId from playlist $playlistId on server", e)
        }
    }
}
