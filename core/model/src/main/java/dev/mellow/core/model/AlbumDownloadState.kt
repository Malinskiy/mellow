package dev.mellow.core.model

data class AlbumDownloadState(
    val albumId: String,
    val totalTracks: Int,
    val downloadedTracks: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val overallStatus: Status,
) {
    enum class Status {
        NONE,
        PARTIAL,
        DOWNLOADING,
        COMPLETED,
    }
}
