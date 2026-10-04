package dev.mellow.sync

import android.content.Context
import androidx.work.BackoffPolicy
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferences,
) {
    private val workManager = WorkManager.getInstance(context)

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Syncs now. A sync that is running carries on; one that is waiting, e.g. to retry after a failure, starts over
     * right away.
     */
    suspend fun syncNow(serverId: String) {
        val running = workManager.getWorkInfosForUniqueWorkFlow(SYNC_CHAIN_NAME).first()
            .any { it.state == WorkInfo.State.RUNNING }
        enqueueSync(serverId, if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
    }

    /**
     * Rebuilds the library: a full pass that re-fetches everything and removes what the server deleted. It stays
     * pending until a pass completes, so an interrupted rebuild is retried as one. Restarts a sync that is running.
     */
    suspend fun rebuildNow(serverId: String) {
        syncPreferences.requestFullPass()
        enqueueSync(serverId, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueSync(serverId: String, policy: ExistingWorkPolicy) {
        val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_SYNC)
            .build()
        workManager.enqueueUniqueWork(SYNC_CHAIN_NAME, policy, syncRequest)
    }

    suspend fun schedulePeriodicSync(serverId: String) {
        val intervalHours = syncPreferences.autoSyncIntervalHours.first()
        if (intervalHours == 0) {
            cancelPeriodicSync()
            return
        }
        val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS,
        )
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
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

    /**
     * Cancels requests that older versions queued for the removed Clean Up Library worker, which WorkManager could no
     * longer load. Does nothing when there are none.
     */
    fun cancelRemovedWork() {
        workManager.cancelAllWorkByTag(REMOVED_CLEANUP_TAG)
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

    companion object {
        private const val SYNC_CHAIN_NAME = "mellow_sync_chain"
        private const val PERIODIC_SYNC_WORK_NAME = "mellow_periodic_sync"
        private const val TAG_SYNC = "mellow_sync"

        /** The tag of Clean Up Library's requests, which shared the sync chain. */
        private const val REMOVED_CLEANUP_TAG = "mellow_cleanup"

        /** First retry after a failed sync; WorkManager doubles it for each further retry. */
        private const val RETRY_BACKOFF_MINUTES = 1L
    }
}
