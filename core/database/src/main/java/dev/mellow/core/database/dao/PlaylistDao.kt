package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY sortName ASC")
    fun observePlaylists(serverId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoritePlaylists(serverId: String): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlists WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC",
    )
    fun getPlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
}
