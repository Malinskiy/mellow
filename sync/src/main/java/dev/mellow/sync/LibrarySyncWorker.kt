package dev.mellow.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.ArtworkPreCacher
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.network.JellyfinClientWrapper
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID

/**
 * Syncs the library, then pre-caches artwork. A sync that doesn't complete is retried with WorkManager's backoff, up
 * to [MAX_ATTEMPTS] runs; after that the next scheduled sync tries again. Only the library sync records completion.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val serverDao: ServerDao,
    private val jellyfinClient: JellyfinClientWrapper,
    private val syncPreferences: SyncPreferences,
    private val artworkPreCacher: ArtworkPreCacher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        return try {
            showNotification()
            if (syncLibrary(serverId)) {
                // The library sync is recorded by now, so a retry for artwork syncs only what changed meanwhile
                // (no full pass is pending any more) and then pre-caches again.
                artworkPreCacher.preCacheArtwork(serverId) { progress -> report(progress) }
                Result.success()
            } else {
                retryOrGiveUp()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync attempt ${runAttemptCount + 1} failed", e)
            retryOrGiveUp()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Syncing library…", 0, 0)

    /** Brings the library up to date. False if it couldn't, in which case nothing was recorded as synced. */
    private suspend fun syncLibrary(serverId: String): Boolean {
        val result = try {
            ensureConnected()
            prefetchHomeScreen(serverId)
            libraryRepository.syncLibrary(serverId) { progress -> report(progress) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
        if (result is MellowResult.Success) return true
        Log.w(TAG, "Library sync attempt ${runAttemptCount + 1} failed", (result as? MellowResult.Error)?.exception)
        syncPreferences.recordSyncFailed(System.currentTimeMillis())
        return false
    }

    /**
     * Saves what the home screen shows and fetches its artwork first, so a fresh install gets a home screen quickly.
     * Best effort: covers can fail to download while the API works, and that must never hold up the library sync.
     * The artwork step after the library sync fetches them again.
     */
    private suspend fun prefetchHomeScreen(serverId: String) {
        val home = libraryRepository.syncHomeScreenPriority(serverId) { progress ->
            setForegroundAsync(createForegroundInfo(progress))
        }
        val imageIds = (home as? MellowResult.Success)?.data.orEmpty()
        if (imageIds.isEmpty()) return
        try {
            artworkPreCacher.preCacheIds(imageIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Home screen artwork failed, syncing the library anyway", e)
        }
    }

    /** Shows the sync notification. Android may refuse a foreground service in the background; the sync runs anyway. */
    private suspend fun showNotification() {
        try {
            setForeground(createForegroundInfo("Syncing library…", 0, 0))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't show the sync notification", e)
        }
    }

    private fun report(progress: SyncProgress) {
        setForegroundAsync(createForegroundInfo(progress))
        setProgressAsync(
            workDataOf(
                KEY_PHASE to progress.phase,
                KEY_CURRENT to progress.current,
                KEY_TOTAL to progress.total,
            ),
        )
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private fun createForegroundInfo(progress: SyncProgress): ForegroundInfo {
        val text = if (progress.total > 0) {
            "Syncing ${progress.phase}… ${progress.current}/${progress.total}"
        } else {
            "Syncing ${progress.phase}…"
        }
        return createForegroundInfo(text, progress.current, progress.total)
    }

    private fun createForegroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Library Sync",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Syncing library")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)

        if (total > 0) {
            builder.setProgress(total, current, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun ensureConnected() {
        if (jellyfinClient.isConnected) return
        val server = serverDao.getActiveServer() ?: return
        jellyfinClient.connect(server.url, DeviceInfo(id = UUID.randomUUID().toString(), name = "Mellow"))
        jellyfinClient.authenticate(server.accessToken)
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"

        /** Runs of one sync request, the first included, before it gives up until the next scheduled sync. */
        const val MAX_ATTEMPTS = 5
        private const val TAG = "LibrarySyncWorker"
        private const val CHANNEL_ID = "mellow_sync"
        private const val NOTIFICATION_ID = 42
    }
}
