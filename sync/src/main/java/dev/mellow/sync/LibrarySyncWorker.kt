package dev.mellow.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.data.ArtworkPreCacher
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.network.JellyfinClientWrapper
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID

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
            setForeground(createForegroundInfo("Syncing library…", 0, 0))
            ensureConnected()

            val homeImageIds = libraryRepository.syncHomeScreenPriority(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
            }
            prefetchImages(homeImageIds)

            libraryRepository.syncLibrary(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
                setProgressAsync(
                    workDataOf(
                        KEY_PHASE to progress.phase,
                        KEY_CURRENT to progress.current,
                        KEY_TOTAL to progress.total,
                    ),
                )
            }
            artworkPreCacher.preCacheArtwork(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
                setProgressAsync(
                    workDataOf(
                        KEY_PHASE to progress.phase,
                        KEY_CURRENT to progress.current,
                        KEY_TOTAL to progress.total,
                    ),
                )
            }
            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Syncing library…", 0, 0)

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

    private suspend fun prefetchImages(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        val server = serverDao.getActiveServer() ?: return
        val imageLoader = SingletonImageLoader.get(applicationContext)
        itemIds.forEach { itemId ->
            val url = jellyfinImageUrl(server.url, itemId)
            imageLoader.enqueue(
                ImageRequest.Builder(applicationContext).data(url).build(),
            )
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
        private const val CHANNEL_ID = "mellow_sync"
        private const val NOTIFICATION_ID = 42
    }
}
