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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        return try {
            setForeground(createForegroundInfo())
            ensureConnected()
            libraryRepository.syncLibrary(serverId)
            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    private fun createForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Library Sync",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Syncing library")
            .setContentText("Updating your music library…")
            .setOngoing(true)
            .setSilent(true)
            .build()

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
        private const val CHANNEL_ID = "mellow_sync"
        private const val NOTIFICATION_ID = 42
    }
}
