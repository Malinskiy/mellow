package dev.mellow.core.data.repository

import dev.mellow.core.model.Server

interface UserRepository {
    suspend fun authenticate(serverUrl: String, username: String, password: String): Server
    suspend fun getServers(): List<Server>
    suspend fun setFavorite(itemId: String, isFavorite: Boolean)
    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long)
    suspend fun reportPlaybackStarted(itemId: String)
    suspend fun reportPlaybackStopped(itemId: String, positionMs: Long)
}
