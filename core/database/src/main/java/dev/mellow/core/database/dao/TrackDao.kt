package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY sortName ASC")
    fun getTracksByServer(serverId: String): PagingSource<Int, TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getTracksByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteTracks(serverId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(serverId: String, limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 50): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun recordPlayback(trackId: String, timestamp: Long)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun updateLastPlayedAt(trackId: String, timestamp: Long)

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtist(artistId: String, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistName = :artistName ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtistName(artistName: String, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE artistName = :artistName")
    suspend fun countTracksByArtistName(artistName: String): Int

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentTracks(serverId: String, limit: Int = 500): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayedTracks(serverId: String, limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteTracksSync(serverId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    suspend fun getTracksByAlbumSync(albumId: String): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteTrackIds(serverId: String): List<String>

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("DELETE FROM tracks WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
