package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.mellow.core.database.entity.PendingPlaybackEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPlaybackEventDao {

    @Query("SELECT * FROM pending_playback_events ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<PendingPlaybackEventEntity>

    @Insert
    suspend fun insert(event: PendingPlaybackEventEntity)

    @Query("DELETE FROM pending_playback_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_playback_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_playback_events")
    fun observeCount(): Flow<Int>
}
