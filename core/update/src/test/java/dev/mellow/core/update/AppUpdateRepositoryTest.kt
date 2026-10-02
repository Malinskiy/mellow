package dev.mellow.core.update

import dev.mellow.core.common.MellowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.io.IOException

/** Robolectric only so that `android.util.Log` works; time, the API and the preferences are fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val api = FakeGitHubReleaseApi()
    private var now = 1_790_000_000_000L
    private var abis = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var updatesDirectory: File

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        updatesDirectory = File(temporaryFolder.root, "updates")
    }

    private fun preferences(config: UpdateConfig = testConfig()) = UpdatePreferences(dataStore, config)

    private fun store(config: UpdateConfig = testConfig()) =
        DownloadedUpdateStore(preferences(config), { updatesDirectory }, Dispatchers.IO)

    private fun repository(config: UpdateConfig = testConfig()) = AppUpdateRepository(
        config = config,
        api = api,
        preferences = preferences(config),
        downloadedStore = store(config),
        currentTimeMillis = { now },
        supportedAbis = { abis },
    )

    private fun respondWith(release: GitHubRelease, etag: String? = "\"etag-1\"") {
        api.response = MellowResult.Success(ReleaseResponse.Fresh(release, etag))
    }

    private fun MellowResult<UpdateCheck>.available(): UpdateCheck.Available =
        (this as MellowResult.Success).data as UpdateCheck.Available

    private fun MellowResult<UpdateCheck>.isUpToDate(): Boolean =
        this == MellowResult.Success(UpdateCheck.UpToDate)

    @Test
    fun `automatic checks switched off never touch the network`() = runTest {
        val repository = repository()
        repository.setAutoCheckEnabled(false)
        respondWith(release())

        assertTrue(repository.check(force = false).isUpToDate())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `debug builds default to no automatic check but can still check manually`() = runTest {
        val repository = repository(testConfig(isDebugBuild = true))
        respondWith(release())

        assertFalse(repository.autoCheckEnabled.first())
        assertTrue(repository.check(force = false).isUpToDate())
        assertTrue(api.calls.isEmpty())

        assertEquals("v1.2.0", repository.check(force = true).available().release.tagName)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `a newer release is available and is cached with its ETag and the check time`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))

        val available = repository.check(force = false).available()

        assertEquals("v1.2.0", available.release.tagName)
        assertEquals("app-release.apk", available.asset.name)
        assertFalse(available.skipped)
        assertNull(available.downloaded)
        assertEquals(listOf(FakeGitHubReleaseApi.Call(UpdateConfig.DEFAULT_API_BASE_URL, null)), api.calls)
        val preferences = preferences()
        assertEquals("\"etag-1\"", preferences.etag.first())
        assertEquals(now, preferences.lastCheckedAtMillis.first())
        assertEquals("v1.2.0", preferences.cachedRelease.first()?.release?.tagName)
    }

    @Test
    fun `within 24 hours the cached answer is returned without a request`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))
        val first = repository.check(force = false).available()

        now += CHECK_INTERVAL - 1
        val second = repository.check(force = false).available()

        assertEquals(first, second)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `after 24 hours the ETag is sent and 304 reuses the cached release`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))
        repository.check(force = false)

        now += CHECK_INTERVAL
        api.response = MellowResult.Success(ReleaseResponse.NotModified)
        val available = repository.check(force = false).available()

        assertEquals("v1.2.0", available.release.tagName)
        assertEquals("\"etag-1\"", api.calls.last().etag)
        assertEquals(now, preferences().lastCheckedAtMillis.first())
    }

    @Test
    fun `a clock set backwards does not suppress checks forever`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))
        repository.check(force = false)

        now -= CHECK_INTERVAL * 10
        repository.check(force = false)

        assertEquals(2, api.calls.size)
    }

    @Test
    fun `force bypasses the throttle`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))
        repository.check(force = false)

        now += 60_000
        respondWith(release("v1.3.0"), etag = "\"etag-2\"")
        val available = repository.check(force = true).available()

        assertEquals("v1.3.0", available.release.tagName)
        assertEquals(2, api.calls.size)
        assertEquals("\"etag-2\"", preferences().etag.first())
    }

    @Test
    fun `errors are returned and do not count as a check`() = runTest {
        val repository = repository()
        val failure = IOException("offline")
        api.response = MellowResult.Error(failure)

        assertEquals(MellowResult.Error(failure), repository.check(force = false))
        assertEquals(0L, preferences().lastCheckedAtMillis.first())

        respondWith(release("v1.2.0"))
        repository.check(force = false).available()
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `a skipped version is still reported, marked as skipped`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0"))
        repository.skipVersion("v1.2.0")

        assertTrue(repository.check(force = true).available().skipped)

        respondWith(release("v1.3.0"))
        assertFalse(repository.check(force = true).available().skipped)
    }

    @Test
    fun `prereleases and drafts are ignored, also when answered from the cache`() = runTest {
        val repository = repository()
        respondWith(release("v1.2.0", prerelease = true))
        assertTrue(repository.check(force = false).isUpToDate())

        now += 60_000
        assertTrue(repository.check(force = false).isUpToDate())

        respondWith(release("v1.3.0", draft = true))
        assertTrue(repository.check(force = true).isUpToDate())

        api.response = MellowResult.Success(ReleaseResponse.NotModified)
        assertTrue(repository.check(force = true).isUpToDate())
    }

    @Test
    fun `the same version, an older one or a dev build of the tag is up to date`() = runTest {
        respondWith(release("v1.2.0"))

        assertTrue(repository(testConfig(currentVersionName = "v1.2.0")).check(force = true).isUpToDate())
        assertTrue(repository(testConfig(currentVersionName = "v1.3.0")).check(force = true).isUpToDate())
        assertTrue(repository(testConfig(currentVersionName = "v1.2.0-3-gabc1234")).check(force = true).isUpToDate())
        assertTrue(repository(testConfig(currentVersionName = "abc1234-dirty")).check(force = true).isUpToDate())
    }

    @Test
    fun `asset selection prefers app-release apk`() = runTest {
        respondWith(release(assets = listOf(asset("mellow-arm64-v8a.apk"), asset("app-release.apk"))))

        assertEquals("app-release.apk", repository().check(force = true).available().asset.name)
    }

    @Test
    fun `asset selection falls back to the device ABIs in order`() = runTest {
        respondWith(
            release(
                assets = listOf(
                    asset("mellow-x86_64.apk"),
                    asset("mellow-armeabi-v7a.apk"),
                    asset("mellow-arm64-v8a.apk"),
                ),
            ),
        )

        assertEquals("mellow-arm64-v8a.apk", repository().check(force = true).available().asset.name)
    }

    @Test
    fun `an ABI must match a whole name part, so x86 does not pick x86_64`() = runTest {
        abis = listOf("x86")
        respondWith(release(assets = listOf(asset("mellow-x86_64.apk"), asset("mellow-x86.apk"))))

        assertEquals("mellow-x86.apk", repository().check(force = true).available().asset.name)
    }

    @Test
    fun `asset selection falls back to the first apk`() = runTest {
        respondWith(
            release(assets = listOf(asset("notes.txt"), asset("mellow-universal.apk"), asset("mellow-x86.apk"))),
        )

        assertEquals("mellow-universal.apk", repository().check(force = true).available().asset.name)
    }

    @Test
    fun `a release without an apk is up to date`() = runTest {
        respondWith(release(assets = listOf(asset("notes.txt"), asset("source.zip"))))

        assertTrue(repository().check(force = true).isUpToDate())
    }

    @Test
    fun `a downloaded apk for the release tag is surfaced while its file exists`() = runTest {
        val repository = repository()
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        val recorded = store().record("v1.2.0", apk)
        respondWith(release("v1.2.0"))

        assertEquals(recorded, repository.check(force = true).available().downloaded)

        apk.delete()
        assertNull(repository.check(force = true).available().downloaded)
    }

    @Test
    fun `a downloaded apk for another tag is not surfaced`() = runTest {
        val repository = repository()
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        store().record("v1.2.0", apk)
        respondWith(release("v1.3.0"))

        assertNull(repository.check(force = true).available().downloaded)
    }

    @Test
    fun `availableUpdate follows the cache, the skip and the download`() = runTest {
        val repository = repository()
        assertNull(repository.availableUpdate.first())

        respondWith(release("v1.2.0"))
        repository.check(force = true)
        assertEquals("v1.2.0", repository.availableUpdate.first()?.release?.tagName)

        repository.skipVersion("v1.2.0")
        assertEquals(true, repository.availableUpdate.first()?.skipped)

        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        val recorded = store().record("v1.2.0", apk)
        assertEquals(recorded, repository.availableUpdate.first()?.downloaded)
    }

    @Test
    fun `the API base URL override is only used by debug builds`() = runTest {
        respondWith(release("v1.2.0"))
        val override = "http://10.0.2.2:8000"

        val releaseRepository = repository(testConfig(isDebugBuild = false))
        releaseRepository.setDevApiBaseUrlOverride(override)
        releaseRepository.check(force = true)
        assertEquals(UpdateConfig.DEFAULT_API_BASE_URL, api.calls.last().baseUrl)

        val debugRepository = repository(testConfig(isDebugBuild = true))
        debugRepository.check(force = true)
        assertEquals(override, api.calls.last().baseUrl)

        debugRepository.setDevApiBaseUrlOverride("  ")
        debugRepository.check(force = true)
        assertEquals(UpdateConfig.DEFAULT_API_BASE_URL, api.calls.last().baseUrl)
    }

    @Test
    fun `cleanup after updating to the cached release clears everything`() = runTest {
        respondWith(release("v1.2.0"))
        repository(testConfig(currentVersionName = "v1.0.0")).apply {
            check(force = true)
            skipVersion("v1.2.0")
        }
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        store().record("v1.2.0", apk)

        repository(testConfig(currentVersionName = "v1.2.0")).cleanupAfterUpdate()

        val preferences = preferences()
        assertNull(preferences.cachedRelease.first())
        assertNull(preferences.skippedTag.first())
        assertNull(preferences.downloadedApk.first())
        assertFalse(updatesDirectory.exists())
    }

    @Test
    fun `cleanup keeps a verified apk for a still newer release`() = runTest {
        val repository = repository(testConfig(currentVersionName = "v1.0.0"))
        respondWith(release("v1.2.0"))
        repository.check(force = true)
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        val recorded = store().record("v1.2.0", apk)

        repository.cleanupAfterUpdate()

        assertTrue(apk.isFile)
        assertEquals(recorded, preferences().downloadedApk.first())
        assertEquals("v1.2.0", preferences().cachedRelease.first()?.release?.tagName)
    }

    @Test
    fun `cleanup forgets a downloaded apk whose file is gone or changed, and only the entry`() = runTest {
        val repository = repository(testConfig(currentVersionName = "v1.0.0"))
        respondWith(release("v1.2.0"))
        repository.check(force = true)
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        store().record("v1.2.0", apk)

        apk.writeText("tampered")
        repository.cleanupAfterUpdate()

        assertNull(preferences().downloadedApk.first())
        assertTrue(apk.isFile)
        assertEquals("v1.2.0", preferences().cachedRelease.first()?.release?.tagName)
    }

    @Test
    fun `cleanup deletes a downloaded apk the installed version has caught up with`() = runTest {
        val apk = File(updatesDirectory.apply { mkdirs() }, "mellow-v1.2.0.apk").apply { writeText("apk") }
        store().record("v1.2.0", apk)

        repository(testConfig(currentVersionName = "v1.2.0")).cleanupAfterUpdate()

        assertNull(preferences().downloadedApk.first())
        assertFalse(updatesDirectory.exists())
    }

    private companion object {
        const val CHECK_INTERVAL = AppUpdateRepository.CHECK_INTERVAL_MILLIS
    }
}
