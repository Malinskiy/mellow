package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.SearchQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchQueryDao {

    @Query("SELECT * FROM search_queries WHERE serverId = :serverId ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(serverId: String, limit: Int = 20): Flow<List<SearchQueryEntity>>

    @Upsert
    suspend fun upsert(entity: SearchQueryEntity)

    @Query("DELETE FROM search_queries WHERE serverId = :serverId AND queryText = :queryText")
    suspend fun delete(serverId: String, queryText: String)

    @Query("DELETE FROM search_queries WHERE serverId = :serverId")
    suspend fun clearAll(serverId: String)
}
