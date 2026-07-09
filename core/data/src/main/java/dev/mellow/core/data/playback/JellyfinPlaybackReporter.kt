package dev.mellow.core.data.playback

import android.util.Log
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.common.PlaybackReporter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinPlaybackReporter @Inject constructor(
    private val jellyfinDataSource: JellyfinDataSource,
    private val trackDao: TrackDao,
) : PlaybackReporter {

    override suspend fun reportStarted(itemId: String) {
        try {
            trackDao.recordPlayback(itemId, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record playback locally", e)
        }
        val uuid = parseUuid(itemId) ?: return
        jellyfinDataSource.reportPlaybackStarted(uuid)
    }

    override suspend fun reportProgress(itemId: String, positionMs: Long) {
        val uuid = parseUuid(itemId) ?: return
        val positionTicks = positionMs * TICKS_PER_MS
        jellyfinDataSource.reportPlaybackProgress(uuid, positionTicks)
    }

    override suspend fun reportStopped(itemId: String, positionMs: Long) {
        val uuid = parseUuid(itemId) ?: return
        val positionTicks = positionMs * TICKS_PER_MS
        jellyfinDataSource.reportPlaybackStopped(uuid, positionTicks)
    }

    private fun parseUuid(itemId: String): UUID? {
        return try {
            UUID.fromString(itemId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val TAG = "PlaybackReporter"
        private const val TICKS_PER_MS = 10_000L
    }
}
