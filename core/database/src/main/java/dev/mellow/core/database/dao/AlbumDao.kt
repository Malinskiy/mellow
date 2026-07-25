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

    @Query("SELECT * FROM albums WHERE artistName = :artistName OR artistName = :altName ORDER BY year DESC")
    fun getAlbumsByArtistName(artistName: String, altName: String = artistName): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums WHERE artistName = :artistName OR artistName = :altName")
    suspend fun countAlbumsByArtistName(artistName: String, altName: String = artistName): Int

    @Query("SELECT * FROM albums WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteAlbums(serverId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND (name LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%') ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 20): List<AlbumEntity>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("""
        UPDATE albums SET artistId = (
            SELECT ar.id FROM artists ar
            WHERE ar.name = albums.artistName AND ar.serverId = albums.serverId
            LIMIT 1
        )
        WHERE serverId = :serverId
        AND artistName IS NOT NULL
        AND artistId != (
            SELECT ar2.id FROM artists ar2
            WHERE ar2.name = albums.artistName AND ar2.serverId = albums.serverId
            LIMIT 1
        )
    """)
    suspend fun resolveArtistIds(serverId: String)

    @Query("""
        UPDATE albums SET resolvedArtistId = (
            SELECT aa.canonicalArtistId FROM artist_aliases aa
            WHERE aa.serverId = albums.serverId AND aa.rawArtistId = albums.artistId
        )
        WHERE serverId = :serverId AND artistId IS NOT NULL
    """)
    suspend fun resolveArtistAliases(serverId: String)

    @Query("SELECT * FROM albums WHERE resolvedArtistId = :artistId ORDER BY year DESC")
    fun getAlbumsByResolvedArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE resolvedArtistId = :artistId ORDER BY year DESC")
    suspend fun getAllAlbumsByResolvedArtist(artistId: String): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE resolvedArtistId = :artistId")
    suspend fun countAlbumsByResolvedArtist(artistId: String): Int

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

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getAllAlbumsByServer(serverId: String): List<AlbumEntity>

    @Query("SELECT DISTINCT genres FROM albums WHERE serverId = :serverId AND genres != ''")
    suspend fun getRawGenreStrings(serverId: String): List<String>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND genres LIKE '%' || :genre || '%' ORDER BY sortName ASC")
    suspend fun getAlbumsByGenre(genre: String, serverId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    suspend fun getAllAlbumsByArtist(artistId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistName = :artistName OR artistName = :altName ORDER BY year DESC")
    suspend fun getAllAlbumsByArtistName(artistName: String, altName: String = artistName): List<AlbumEntity>

    @Query("SELECT id FROM albums WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteAlbumIds(serverId: String): List<String>

    @Query("UPDATE albums SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("SELECT id FROM albums WHERE serverId = :serverId")
    suspend fun getAllAlbumIdsByServer(serverId: String): List<String>

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM albums WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id FROM albums WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getIdsWithImage(serverId: String): List<String>

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
    suspend fun getRecentlyPlayedAlbumsSync(serverId: String, limit: Int = 12): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY dateAdded DESC LIMIT :limit")
    suspend fun getRecentlyAddedAlbums(serverId: String, limit: Int = 20): List<AlbumEntity>

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
    suspend fun getMostPlayedAlbumsSync(serverId: String, limit: Int = 20): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteAlbumsSync(serverId: String): List<AlbumEntity>
}
