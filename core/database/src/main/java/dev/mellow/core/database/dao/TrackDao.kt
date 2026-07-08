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

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtist(artistId: String, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentTracks(serverId: String, limit: Int = 500): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)
}
