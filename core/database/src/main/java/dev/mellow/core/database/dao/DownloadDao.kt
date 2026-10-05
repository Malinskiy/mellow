package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mellow.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun observeDownload(trackId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = 2")
    fun observeCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (0, 1)")
    fun observeActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND status = 2")
    fun getCompletedByServer(serverId: String): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE albumId = :albumId AND status = 2")
    fun getCompletedCountForAlbum(albumId: String): Flow<Int>

    /** The library's tracks of album [albumId], the count an album download is measured against. */
    @Query("SELECT COUNT(*) FROM tracks WHERE albumId = :albumId")
    fun observeAlbumTrackCount(albumId: String): Flow<Int>

    /**
     * The downloads of album [albumId]'s current tracks: a download whose track the library no longer lists under the
     * album (or lists for another server) isn't one of them.
     */
    @Query(
        "SELECT d.* FROM tracks t INNER JOIN downloads d ON d.trackId = t.id " +
            "WHERE t.albumId = :albumId AND d.serverId = t.serverId",
    )
    fun observeAlbumTrackDownloads(albumId: String): Flow<List<DownloadEntity>>

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads WHERE status = 2")
    fun getTotalDownloadedBytes(): Flow<Long>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Upsert
    suspend fun upsertAll(downloads: List<DownloadEntity>)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM downloads WHERE albumId = :albumId")
    suspend fun deleteByAlbum(albumId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("UPDATE downloads SET status = :status, progress = :progress, bytesDownloaded = :bytes WHERE trackId = :trackId")
    suspend fun updateProgress(trackId: String, status: Int, progress: Float, bytes: Long)

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun getDownload(trackId: String): DownloadEntity?

    /** The downloads of [trackIds]; keep a call under SQLite's 999 bound arguments (Android 8). */
    @Query("SELECT * FROM downloads WHERE trackId IN (:trackIds)")
    suspend fun getDownloads(trackIds: List<String>): List<DownloadEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE trackId = :trackId AND status = 2)")
    fun isDownloaded(trackId: String): Flow<Boolean>

    @Query("SELECT trackId FROM downloads WHERE status = 2")
    suspend fun getDownloadedTrackIds(): List<String>

    @Query("SELECT DISTINCT t.albumId FROM tracks t INNER JOIN downloads d ON t.id = d.trackId WHERE d.status = 2 AND t.albumId IS NOT NULL")
    suspend fun getDownloadedAlbumIds(): List<String>

    @Query("SELECT DISTINCT t.artistName FROM tracks t INNER JOIN downloads d ON t.id = d.trackId WHERE d.status = 2 AND t.artistName IS NOT NULL")
    suspend fun getDownloadedArtistNames(): List<String>

    @Query("SELECT * FROM downloads WHERE albumId = :albumId")
    suspend fun getDownloadsByAlbum(albumId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<DownloadEntity>
}
