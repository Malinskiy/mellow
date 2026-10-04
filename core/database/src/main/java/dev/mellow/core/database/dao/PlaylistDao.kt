package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** A playlist's tracks in playlist order; the ID makes the order total, so pages never overlap or skip a track. */
private const val PLAYLIST_TRACKS_QUERY = """
    SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId
    WHERE pt.playlistId = :playlistId
        AND (:downloadedOnly = 0 OR t.id IN ($DOWNLOADED_TRACK_IDS))
    ORDER BY pt.position ASC, t.id ASC
"""

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

    @Query(PLAYLIST_TRACKS_QUERY)
    fun getPlaylistTracksPaged(playlistId: String, downloadedOnly: Boolean): PagingSource<Int, TrackEntity>

    /** [limit] tracks of [getPlaylistTracksPaged] from position [offset]. */
    @Query("$PLAYLIST_TRACKS_QUERY LIMIT :limit OFFSET :offset")
    suspend fun getPlaylistTracksSlice(
        playlistId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getPlaylistsByServer(serverId: String): List<PlaylistEntity>

    @Query(
        """
        SELECT COUNT(*) FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId AND (:downloadedOnly = 0 OR t.id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countPlaylistTracks(playlistId: String, downloadedOnly: Boolean): Int

    /**
     * How many of the playlist's tracks (only downloaded ones if [downloadedOnly]) come before [trackId] in
     * [getPlaylistTracksPaged] order.
     */
    @Query(
        """
        SELECT COUNT(*) FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        INNER JOIN playlist_tracks target ON target.playlistId = pt.playlistId AND target.trackId = :trackId
        WHERE pt.playlistId = :playlistId
            AND (pt.position < target.position OR (pt.position = target.position AND t.id < :trackId))
            AND (:downloadedOnly = 0 OR t.id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countPlaylistTracksBefore(playlistId: String, trackId: String, downloadedOnly: Boolean): Int

    /**
     * [limit] of the playlist's tracks picked uniformly at random from all of them, in random order, for shuffling a
     * playlist without reading it whole.
     */
    @Query(
        """
        SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomPlaylistTracks(playlistId: String, limit: Int): List<TrackEntity>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT id, imageTag FROM playlists WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getImageTags(serverId: String): List<ImageTagRow>

    @Query("SELECT imageTag FROM playlists WHERE id = :id")
    suspend fun getImageTag(id: String): String?
}
