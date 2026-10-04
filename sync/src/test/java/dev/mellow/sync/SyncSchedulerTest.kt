package dev.mellow.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `requests of the removed Clean Up Library worker are cancelled, syncs are not`() {
        // As an older version queued them: Clean Up Library's work appended to the sync chain, tagged mellow_cleanup.
        val sync = waiting("mellow_sync")
        val cleanup = waiting("mellow_cleanup")
        workManager.enqueueUniqueWork("mellow_sync_chain", ExistingWorkPolicy.APPEND, sync).result.get()
        workManager.enqueueUniqueWork("mellow_sync_chain", ExistingWorkPolicy.APPEND, cleanup).result.get()

        SyncScheduler(context, mockk()).cancelRemovedWork()

        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(cleanup.id).get()?.state)
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(sync.id).get()?.state)
    }

    private fun waiting(tag: String) = OneTimeWorkRequestBuilder<IdleWorker>()
        .addTag(tag)
        .setInitialDelay(1, TimeUnit.HOURS)
        .build()

    class IdleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }
}
