package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

    @Query("SELECT isFavorite FROM tracks WHERE id = :id")
    fun observeIsFavorite(id: String): Flow<Boolean?>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteTracks(serverId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(serverId: String, limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND (name LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%') ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 50): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("""
        UPDATE tracks SET artistId = (
            SELECT ar.id FROM artists ar
            WHERE ar.name = tracks.artistName AND ar.serverId = tracks.serverId
            LIMIT 1
        )
        WHERE serverId = :serverId
        AND artistName IS NOT NULL
        AND artistId != (
            SELECT ar2.id FROM artists ar2
            WHERE ar2.name = tracks.artistName AND ar2.serverId = tracks.serverId
            LIMIT 1
        )
    """)
    suspend fun resolveArtistIds(serverId: String)

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

    @Query("SELECT * FROM tracks WHERE (artistName = :artistName OR artistName = :altName) ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtistName(artistName: String, altName: String = artistName, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE artistName = :artistName OR artistName = :altName")
    suspend fun countTracksByArtistName(artistName: String, altName: String = artistName): Int

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

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getAllTracksByServer(serverId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY sortName ASC LIMIT :limit OFFSET :offset")
    suspend fun getTracksByServerPaged(serverId: String, limit: Int, offset: Int): List<TrackEntity>

    @RawQuery
    suspend fun getInstantMixRaw(query: SupportSQLiteQuery): List<TrackEntity>
}

suspend fun TrackDao.getInstantMix(
    serverId: String,
    seedTrackId: String,
    artistName: String?,
    genres: List<String>,
    downloadedOnly: Boolean = false,
    limit: Int = 50,
): List<TrackEntity> {
    val sb = if (downloadedOnly) {
        StringBuilder("SELECT t.* FROM tracks t INNER JOIN downloads d ON t.id = d.trackId AND d.status = 2 WHERE t.serverId = ? AND t.id != ?")
    } else {
        StringBuilder("SELECT * FROM tracks WHERE serverId = ? AND id != ?")
    }
    val args = mutableListOf<Any>(serverId, seedTrackId)
    val col = if (downloadedOnly) "t." else ""

    val conditions = mutableListOf<String>()
    if (artistName != null) {
        conditions.add("${col}artistName = ?")
        args.add(artistName)
    }
    for (genre in genres) {
        conditions.add("${col}genres LIKE '%' || ? || '%'")
        args.add(genre)
    }
    if (conditions.isEmpty()) return emptyList()

    sb.append(" AND (")
    sb.append(conditions.joinToString(" OR "))
    sb.append(") ORDER BY RANDOM() LIMIT ?")
    args.add(limit)

    return getInstantMixRaw(SimpleSQLiteQuery(sb.toString(), args.toTypedArray()))
}
