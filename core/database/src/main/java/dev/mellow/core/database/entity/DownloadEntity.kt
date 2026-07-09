package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val albumId: String?,
    val serverId: String,
    val status: Int,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val quality: String,
    val filePath: String?,
    val requestedAt: Long,
    val completedAt: Long,
    val errorMessage: String?,
    val lastSynced: Long,
) {
    companion object {
        const val STATUS_QUEUED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
        const val STATUS_REMOVED = 4
    }
}
