package dev.mellow.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.ArtworkPreCacher
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.network.JellyfinClientWrapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncWorkerTest {

    private val repository = mockk<LibraryRepository>()
    private val serverDao = mockk<ServerDao>()
    private val client = mockk<JellyfinClientWrapper>()
    private val preferences = mockk<SyncPreferences>(relaxUnitFun = true)
    private val artwork = mockk<ArtworkPreCacher>()

    @Before
    fun setUp() {
        every { client.isConnected } returns true
        coEvery { repository.syncHomeScreenPriority(SERVER, any()) } returns MellowResult.Success(emptySet())
        coEvery { repository.syncLibrary(SERVER, any()) } returns MellowResult.Success(Unit)
        coEvery { artwork.preCacheArtwork(SERVER, any()) } returns Unit
    }

    @Test
    fun `a library sync that completes succeeds`() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        coVerify { artwork.preCacheArtwork(SERVER, any()) }
        coVerify(exactly = 0) { preferences.recordSyncFailed(any()) }
    }

    @Test
    fun `a failed library sync is retried and not recorded as synced`() = runTest {
        coEvery { repository.syncLibrary(SERVER, any()) } returns MellowResult.Error(IOException("Server unreachable"))

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        coVerify { preferences.recordSyncFailed(any()) }
        coVerify(exactly = 0) { preferences.recordSyncSucceeded(any(), any(), any(), any()) }
        coVerify(exactly = 0) { artwork.preCacheArtwork(any(), any()) }
    }

    @Test
    fun `home screen artwork failing doesn't hold up the library sync`() = runTest {
        coEvery { repository.syncHomeScreenPriority(SERVER, any()) } returns MellowResult.Success(setOf("album"))
        coEvery { artwork.preCacheIds(setOf("album")) } throws IOException("Server unreachable")

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        coVerify { repository.syncLibrary(SERVER, any()) }
        coVerify(exactly = 0) { preferences.recordSyncFailed(any()) }
    }

    @Test
    fun `failing artwork is retried without marking the library sync failed`() = runTest {
        coEvery { artwork.preCacheArtwork(SERVER, any()) } throws IOException("Server unreachable")

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        coVerify { repository.syncLibrary(SERVER, any()) }
        coVerify(exactly = 0) { preferences.recordSyncFailed(any()) }
    }

    @Test
    fun `a stopped sync stays stopped instead of turning into a retry`() {
        coEvery { artwork.preCacheArtwork(SERVER, any()) } throws CancellationException("stopped")
        assertThrows(CancellationException::class.java) { runBlocking { worker().doWork() } }

        coEvery { repository.syncHomeScreenPriority(SERVER, any()) } returns MellowResult.Success(setOf("album"))
        coEvery { artwork.preCacheIds(setOf("album")) } throws CancellationException("stopped")
        assertThrows(CancellationException::class.java) { runBlocking { worker().doWork() } }

        coVerify(exactly = 1) { repository.syncLibrary(SERVER, any()) }
        coVerify(exactly = 0) { preferences.recordSyncFailed(any()) }
    }

    @Test
    fun `the last attempt gives up until the next scheduled sync`() = runTest {
        coEvery { repository.syncLibrary(SERVER, any()) } returns MellowResult.Error(IOException("Server unreachable"))

        val result = worker(runAttemptCount = LibrarySyncWorker.MAX_ATTEMPTS - 1).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private fun worker(runAttemptCount: Int = 0): LibrarySyncWorker =
        TestListenableWorkerBuilder<LibrarySyncWorker>(RuntimeEnvironment.getApplication())
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to SERVER))
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = LibrarySyncWorker(
                        appContext, workerParameters, repository, serverDao, client, preferences, artwork,
                    )
                },
            )
            .build()

    private companion object {
        const val SERVER = "server"
    }
}
