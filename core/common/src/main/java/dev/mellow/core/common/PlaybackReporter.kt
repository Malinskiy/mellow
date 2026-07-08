package dev.mellow.core.common

interface PlaybackReporter {
    suspend fun reportStarted(itemId: String)
    suspend fun reportProgress(itemId: String, positionMs: Long)
    suspend fun reportStopped(itemId: String, positionMs: Long)
}
