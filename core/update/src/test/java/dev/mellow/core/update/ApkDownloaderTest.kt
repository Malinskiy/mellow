package dev.mellow.core.update

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val verifier = mockk<ApkVerifier>()
    private val payload = Random(42).nextBytes(256 * 1024)
    private lateinit var directory: File
    private lateinit var preferences: UpdatePreferences
    private lateinit var store: DownloadedUpdateStore
    private lateinit var downloader: ApkDownloader

    @Before
    fun setUp() {
        server.start()
        directory = File(temporaryFolder.root, "updates")
        preferences = UpdatePreferences(InMemoryPreferencesDataStore(), testConfig())
        store = DownloadedUpdateStore(preferences, { directory }, Dispatchers.IO)
        downloader = ApkDownloader(OkHttpClient(), store, verifier, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun assetFor(
        bytes: ByteArray = payload,
        sizeBytes: Long = bytes.size.toLong(),
        sha256: String? = sha256(bytes),
    ) = asset(
        name = "app-release.apk",
        sizeBytes = sizeBytes,
        sha256 = sha256,
        downloadUrl = server.url("/download/v1.2.0/app-release.apk").toString(),
    )

    private fun serve(bytes: ByteArray = payload) {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
    }

    private suspend fun downloadFailure(asset: ReleaseAsset, tag: String = "v1.2.0"): DownloadException =
        try {
            downloader.download(asset, tag).collect()
            throw AssertionError("Expected a DownloadException")
        } catch (e: DownloadException) {
            e
        }

    private fun filesLeft(): List<String> = directory.list().orEmpty().sorted()

    @Test
    fun `downloads to mellow-tag apk reporting progress from zero to complete`() = runTest {
        serve()

        val progress = downloader.download(assetFor(), "v1.2.0").toList()

        val total = payload.size.toLong()
        assertEquals(DownloadProgress(0, total), progress.first())
        assertEquals(DownloadProgress(total, total), progress.last())
        assertTrue("expected intermediate progress, got $progress", progress.size > 2)
        assertTrue(progress.zipWithNext().all { (previous, next) -> next.bytesRead >= previous.bytesRead })
        assertTrue(progress.all { it.totalBytes == total })
        assertArrayEquals(payload, File(directory, "mellow-v1.2.0.apk").readBytes())
        assertEquals(listOf("mellow-v1.2.0.apk"), filesLeft())
        assertEquals("/download/v1.2.0/app-release.apk", server.takeRequest().path)
    }

    @Test
    fun `progress is throttled to roughly one emission per percent`() = runTest {
        val large = Random(7).nextBytes(4 * 1024 * 1024)
        serve(large)

        val progress = downloader.download(assetFor(large), "v1.2.0").toList()

        // 512 reads of 8 KiB; without throttling there would be one emission per read.
        assertTrue("emitted ${progress.size} times", progress.size in 3..150)
    }

    @Test
    fun `a body shorter than the published size fails and leaves nothing behind`() = runTest {
        serve()

        val failure = downloadFailure(assetFor(sizeBytes = payload.size + 10L))

        assertEquals(
            DownloadException.Reason.SizeMismatch(expected = payload.size + 10L, actual = payload.size.toLong()),
            failure.reason,
        )
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun `a body longer than the published size is cut off`() = runTest {
        serve()

        val failure = downloadFailure(assetFor(sizeBytes = payload.size - 10L))

        val reason = failure.reason as DownloadException.Reason.SizeMismatch
        assertEquals(payload.size - 10L, reason.expected)
        assertTrue(reason.actual > reason.expected)
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun `a SHA-256 mismatch fails and leaves nothing behind`() = runTest {
        serve()
        val published = sha256("something else".toByteArray())

        val failure = downloadFailure(assetFor(sha256 = published))

        assertEquals(DownloadException.Reason.ChecksumMismatch(published, sha256(payload)), failure.reason)
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun `the digest comparison ignores case`() = runTest {
        serve()

        downloader.download(assetFor(sha256 = sha256(payload).uppercase()), "v1.2.0").collect()

        assertEquals(listOf("mellow-v1.2.0.apk"), filesLeft())
    }

    @Test
    fun `without a published digest only the size is checked`() = runTest {
        serve()

        downloader.download(assetFor(sha256 = null), "v1.2.0").collect()

        assertArrayEquals(payload, File(directory, "mellow-v1.2.0.apk").readBytes())
    }

    @Test
    fun `an HTTP error fails with its status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val failure = downloadFailure(assetFor())

        assertEquals(DownloadException.Reason.Http(404), failure.reason)
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun `cancelling deletes the partial file`() = runTest {
        val large = Random(3).nextBytes(1024 * 1024)
        server.enqueue(
            MockResponse().setBody(Buffer().write(large)).throttleBody(16 * 1024L, 50, TimeUnit.MILLISECONDS),
        )

        val progress = downloader.download(assetFor(large), "v1.2.0").take(3).toList()

        assertEquals(3, progress.size)
        assertTrue(progress.last().bytesRead < large.size)
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun `files for other tags are purged before downloading`() = runTest {
        directory.mkdirs()
        val old = File(directory, "mellow-v1.1.0.apk").apply { writeText("old") }
        File(directory, "download-999.part").writeText("stale partial")
        store.record("v1.1.0", old)
        serve()

        downloader.download(assetFor(), "v1.2.0").collect()

        assertEquals(listOf("mellow-v1.2.0.apk"), filesLeft())
        assertNull(preferences.downloadedApk.first())
    }

    @Test
    fun `an apk already downloaded for the same tag survives a failed download`() = runTest {
        directory.mkdirs()
        val existing = File(directory, "mellow-v1.2.0.apk").apply { writeText("verified earlier") }
        File(directory, "mellow-v1.1.0.apk").writeText("old")
        server.enqueue(MockResponse().setResponseCode(500))

        downloadFailure(assetFor())

        assertEquals(listOf("mellow-v1.2.0.apk"), filesLeft())
        assertEquals("verified earlier", existing.readText())
    }

    @Test
    fun `a new download for the same tag replaces the file and drops its stale record`() = runTest {
        directory.mkdirs()
        val existing = File(directory, "mellow-v1.2.0.apk").apply { writeText("previous bytes") }
        store.record("v1.2.0", existing)
        serve()

        downloader.download(assetFor(), "v1.2.0").collect()

        assertArrayEquals(payload, existing.readBytes())
        assertNull(preferences.downloadedApk.first())
    }

    @Test
    fun `verifyAndRecord records an apk that passes verification`() = runTest {
        serve()
        downloader.download(assetFor(), "v1.2.0").collect()
        val target = File(directory, "mellow-v1.2.0.apk")
        coEvery { verifier.verify(target) } returns VerifyResult.Ok(42)

        assertEquals(VerifyResult.Ok(42), downloader.verifyAndRecord("v1.2.0"))

        assertEquals(DownloadedApk("v1.2.0", target.absolutePath, sha256(payload)), preferences.downloadedApk.first())
        coVerify(exactly = 1) { verifier.verify(target) }
    }

    @Test
    fun `verifyAndRecord deletes an apk that fails verification`() = runTest {
        serve()
        downloader.download(assetFor(), "v1.2.0").collect()
        coEvery { verifier.verify(any()) } returns VerifyResult.SignatureMismatch

        assertEquals(VerifyResult.SignatureMismatch, downloader.verifyAndRecord("v1.2.0"))

        assertEquals(emptyList<String>(), filesLeft())
        assertNull(preferences.downloadedApk.first())
    }
}
