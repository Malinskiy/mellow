package dev.mellow.core.model

sealed interface DownloadState {
    val trackId: String

    data class Queued(override val trackId: String) : DownloadState

    data class Downloading(
        override val trackId: String,
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Completed(
        override val trackId: String,
        val bytesDownloaded: Long,
        val filePath: String?,
        val completedAt: Long,
    ) : DownloadState

    data class Failed(
        override val trackId: String,
        val error: String?,
    ) : DownloadState

    data class Removed(override val trackId: String) : DownloadState
}
