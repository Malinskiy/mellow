package dev.mellow.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.preferences.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferences,
) {
    private val workManager = WorkManager.getInstance(context)

    fun syncNow(serverId: String): UUID {
        val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_SYNC)
            .build()
        workManager.beginUniqueWork(
            SYNC_CHAIN_NAME,
            ExistingWorkPolicy.KEEP,
            syncRequest,
        ).enqueue()
        return syncRequest.id
    }

    fun cleanupNow(serverId: String): UUID {
        val cleanupRequest = OneTimeWorkRequestBuilder<LibraryCleanupWorker>()
            .setInputData(workDataOf(LibraryCleanupWorker.KEY_SERVER_ID to serverId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_CLEANUP)
            .build()
        workManager.beginUniqueWork(
            SYNC_CHAIN_NAME,
            ExistingWorkPolicy.APPEND,
            cleanupRequest,
        ).enqueue()
        return cleanupRequest.id
    }

    suspend fun schedulePeriodicSync(serverId: String) {
        val intervalHours = syncPreferences.autoSyncIntervalHours.first()
        if (intervalHours == 0) {
            cancelPeriodicSync()
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
            .addTag(TAG_SYNC)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    fun observeSyncState(): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow(TAG_SYNC)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }

    fun observeSyncProgress(): Flow<SyncProgress?> {
        return workManager.getWorkInfosByTagFlow(TAG_SYNC)
            .map { workInfos ->
                val running = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                running?.progress?.let { data ->
                    val phase = data.getString(LibrarySyncWorker.KEY_PHASE) ?: return@let null
                    SyncProgress(
                        phase,
                        data.getInt(LibrarySyncWorker.KEY_CURRENT, 0),
                        data.getInt(LibrarySyncWorker.KEY_TOTAL, 0),
                    )
                }
            }
    }

    fun observeCleanupState(): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow(TAG_CLEANUP)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }

    companion object {
        private const val SYNC_CHAIN_NAME = "mellow_sync_chain"
        private const val PERIODIC_SYNC_WORK_NAME = "mellow_periodic_sync"
        private const val TAG_SYNC = "mellow_sync"
        private const val TAG_CLEANUP = "mellow_cleanup"
    }
}
