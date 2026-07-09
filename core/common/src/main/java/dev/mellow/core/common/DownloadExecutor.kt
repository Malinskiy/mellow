package dev.mellow.core.common

interface DownloadExecutor {
    fun startDownload(trackId: String, serverUrl: String, apiKey: String)
    fun removeDownload(trackId: String)
}
