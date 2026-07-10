package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName ASC")
    fun getArtistsByServer(serverId: String): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName ASC")
    fun observeArtistsByServer(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtistById(id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteArtists(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 10): List<ArtistEntity>

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id = :artistId")
    suspend fun setFavorite(artistId: String, isFavorite: Boolean)

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getAllArtistsByServer(serverId: String): List<ArtistEntity>

    @Query("SELECT id FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteArtistIds(serverId: String): List<String>

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("SELECT id FROM artists WHERE serverId = :serverId")
    suspend fun getAllArtistIdsByServer(serverId: String): List<String>

    @Query("DELETE FROM artists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM artists WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id FROM artists WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getIdsWithImage(serverId: String): List<String>
}
