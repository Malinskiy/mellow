package dev.mellow.core.data.repository

import dev.mellow.core.database.dao.ServerDao
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
) : UserRepository {

    override suspend fun authenticate(serverUrl: String, username: String, password: String): Server {
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
        return server
    }

    override suspend fun getServers(): List<Server> =
        serverDao.getServers().map { it.toModel() }

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

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean) {
        val server = serverDao.getActiveServer() ?: return
        jellyfinDataSource.setFavorite(
            userId = UUID.fromString(server.userId),
            itemId = UUID.fromString(itemId),
            isFavorite = isFavorite,
        )
    }

    override suspend fun reportPlaybackStarted(itemId: String) {
        jellyfinDataSource.reportPlaybackStarted(UUID.fromString(itemId))
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long) {
        jellyfinDataSource.reportPlaybackProgress(UUID.fromString(itemId), positionMs * 10_000)
    }

    override suspend fun reportPlaybackStopped(itemId: String, positionMs: Long) {
        jellyfinDataSource.reportPlaybackStopped(UUID.fromString(itemId), positionMs * 10_000)
    }

    private fun ServerEntity.toModel() = Server(
        id = id,
        name = name,
        url = url,
        userId = userId,
        accessToken = accessToken,
    )
}
