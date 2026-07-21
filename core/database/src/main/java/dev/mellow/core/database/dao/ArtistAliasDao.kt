package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.ArtistAliasEntity

@Dao
interface ArtistAliasDao {

    @Upsert
    suspend fun upsertAliases(aliases: List<ArtistAliasEntity>)

    @Query("DELETE FROM artist_aliases WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT canonicalArtistId FROM artist_aliases WHERE serverId = :serverId AND rawArtistId = :rawId")
    suspend fun getCanonicalId(serverId: String, rawId: String): String?
}
