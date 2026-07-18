package dev.mellow.core.common

import kotlinx.coroutines.flow.StateFlow

interface DownloadExecutor {
    val downloadProgress: StateFlow<Map<String, Float>>
    fun startDownload(trackId: String, serverUrl: String, apiKey: String, quality: String = "original")
    fun removeDownload(trackId: String)
}
