package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY sortName ASC")
    fun getAlbumsByServer(serverId: String): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY sortName ASC")
    fun observeAlbumsByServer(serverId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbumById(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    fun getAlbumsByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteAlbums(serverId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 20): List<AlbumEntity>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("UPDATE albums SET isFavorite = :isFavorite WHERE id = :albumId")
    suspend fun setFavorite(albumId: String, isFavorite: Boolean)

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, MAX(lastPlayedAt) as maxPlayed 
            FROM tracks 
            WHERE serverId = :serverId AND lastPlayedAt > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.maxPlayed DESC 
        LIMIT :limit
    """)
    fun getRecentlyPlayedAlbums(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, SUM(playCount) as totalPlays 
            FROM tracks 
            WHERE serverId = :serverId AND playCount > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.totalPlays DESC 
        LIMIT :limit
    """)
    fun getMostPlayedAlbums(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("DELETE FROM albums WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)
}
