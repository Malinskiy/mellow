package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.LyricsEntity

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun getLyrics(trackId: String): LyricsEntity?

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)
}
