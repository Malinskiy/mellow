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

    @Query("SELECT * FROM downloads WHERE albumId = :albumId")
    fun observeAlbumDownloads(albumId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 2")
    fun observeCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (0, 1)")
    fun observeActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND status = 2")
    fun getCompletedByServer(serverId: String): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE albumId = :albumId AND status = 2")
    fun getCompletedCountForAlbum(albumId: String): Flow<Int>

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

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE trackId = :trackId AND status = 2)")
    fun isDownloaded(trackId: String): Flow<Boolean>

    @Query("SELECT trackId FROM downloads WHERE status = 2")
    suspend fun getDownloadedTrackIds(): List<String>

    @Query("SELECT * FROM downloads WHERE albumId = :albumId")
    suspend fun getDownloadsByAlbum(albumId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<DownloadEntity>
}
