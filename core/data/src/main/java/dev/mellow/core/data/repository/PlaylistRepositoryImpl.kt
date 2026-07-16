package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
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
import kotlinx.coroutines.flow.catch
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

    override fun observePlaylists(serverId: String): Flow<MellowResult<List<Playlist>>> =
        playlistDao.observePlaylists(serverId).map { entities ->
            MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Playlist>>
        }.catch { emit(MellowResult.Error(it)) }

    override fun observePlaylistTracks(playlistId: String): Flow<MellowResult<List<Track>>> =
        playlistDao.getPlaylistTracks(playlistId).map { entities ->
            MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>>
        }.catch { emit(MellowResult.Error(it)) }

    override suspend fun getPlaylistById(id: String): MellowResult<Playlist?> =
        try {
            MellowResult.Success(playlistDao.getPlaylistById(id)?.toModel())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun syncPlaylists(serverId: String): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)
            val items = jellyfinDataSource.getPlaylists(userId)
            if (items.isNotEmpty()) {
                playlistDao.upsertPlaylists(items.map { it.toPlaylistEntity(serverId) })
            }
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun syncPlaylistTracks(playlistId: String, serverId: String): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
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
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun createPlaylist(name: String, serverId: String): MellowResult<String> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return MellowResult.Error(IllegalStateException("No active server"))
            val userId = UUID.fromString(server.userId)
            val newId = jellyfinDataSource.createPlaylist(name, userId)
                ?: return MellowResult.Error(IllegalStateException("Failed to create playlist"))

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
            MellowResult.Success(newId)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun addTrackToPlaylist(
        playlistId: String,
        trackId: String,
        serverId: String,
    ): MellowResult<Unit> {
        return try {
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

            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)
            jellyfinDataSource.addToPlaylist(
                playlistId = UUID.fromString(playlistId),
                trackIds = listOf(UUID.fromString(trackId)),
                userId = userId,
            )
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun removeTrackFromPlaylist(
        playlistId: String,
        trackId: String,
    ): MellowResult<Unit> {
        return try {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)

            jellyfinDataSource.removeFromPlaylist(
                playlistId = playlistId,
                entryIds = listOf(trackId),
            )
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }
}
