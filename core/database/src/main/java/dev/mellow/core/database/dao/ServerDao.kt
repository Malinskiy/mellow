package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    suspend fun getServers(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun observeActiveServer(): Flow<ServerEntity?>

    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun delete(serverId: String)
}
