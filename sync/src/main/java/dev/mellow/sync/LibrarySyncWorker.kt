package dev.mellow.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
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
            ensureConnected()
            libraryRepository.syncLibrary(serverId)
            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
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
    }
}
