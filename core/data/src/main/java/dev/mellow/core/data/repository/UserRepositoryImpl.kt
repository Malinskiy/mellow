package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.model.Server
import dev.mellow.core.network.JellyfinClientWrapper
import dev.mellow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val jellyfinClient: JellyfinClientWrapper,
    private val jellyfinDataSource: JellyfinDataSource,
    private val serverDao: ServerDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
) : UserRepository {

    override suspend fun authenticate(serverUrl: String, username: String, password: String): MellowResult<Server> {
        return try {
            jellyfinClient.connect(serverUrl, DeviceInfo(id = UUID.randomUUID().toString(), name = "Mellow"))
            val result = jellyfinDataSource.authenticate(username, password)
            jellyfinClient.authenticate(result.accessToken)

            val server = Server(
                id = result.serverId,
                name = result.serverName,
                url = serverUrl,
                userId = result.userId,
                accessToken = result.accessToken,
            )

            serverDao.deactivateAll()
            serverDao.upsert(
                ServerEntity(
                    id = server.id,
                    name = server.name,
                    url = server.url,
                    userId = server.userId,
                    accessToken = server.accessToken,
                    isActive = true,
                    lastConnected = System.currentTimeMillis(),
                )
            )
            MellowResult.Success(server)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun getServers(): MellowResult<List<Server>> =
        try {
            MellowResult.Success(serverDao.getServers().map { it.toModel() })
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    suspend fun getActiveServer(): Server? =
        serverDao.getActiveServer()?.toModel()

    fun observeActiveServer(): Flow<Server?> =
        serverDao.observeActiveServer().map { it?.toModel() }

    suspend fun restoreSession(): Boolean {
        val server = serverDao.getActiveServer() ?: return false
        jellyfinClient.connect(server.url, DeviceInfo(id = UUID.randomUUID().toString(), name = "Mellow"))
        jellyfinClient.authenticate(server.accessToken)
        return true
    }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return MellowResult.Error(IllegalStateException("No active server"))
            jellyfinDataSource.setFavorite(
                userId = UUID.fromString(server.userId),
                itemId = UUID.fromString(itemId),
                isFavorite = isFavorite,
            )
            trackDao.setFavorite(itemId, isFavorite)
            albumDao.setFavorite(itemId, isFavorite)
            artistDao.setFavorite(itemId, isFavorite)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackStarted(itemId: String): MellowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackStarted(UUID.fromString(itemId))
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long): MellowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackProgress(UUID.fromString(itemId), positionMs * 10_000)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackStopped(itemId: String, positionMs: Long): MellowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackStopped(UUID.fromString(itemId), positionMs * 10_000)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun logout(): MellowResult<Unit> {
        return try {
            serverDao.deactivateAll()
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    private fun ServerEntity.toModel() = Server(
        id = id,
        name = name,
        url = url,
        userId = userId,
        accessToken = accessToken,
    )
}
