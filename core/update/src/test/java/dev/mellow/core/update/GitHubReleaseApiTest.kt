package dev.mellow.core.update

import dev.mellow.core.common.MellowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric provides the real `org.json`; the plain unit-test android.jar only has stubs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubReleaseApiTest {

    private val server = MockWebServer()
    private val api = OkHttpGitHubReleaseApi(OkHttpClient(), testConfig(currentVersionName = "v1.0.0"), Dispatchers.IO)

    private val baseUrl: String get() = server.url("/").toString()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 is parsed with the asset digest and the GitHub headers are sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "W/\"etag-1\"").setBody(releaseJson()))

        val result = api.latestRelease(baseUrl, etag = null)

        val fresh = (result as MellowResult.Success).data as ReleaseResponse.Fresh
        assertEquals("W/\"etag-1\"", fresh.etag)
        with(fresh.release) {
            assertEquals("v1.2.0", tagName)
            assertEquals("Mellow 1.2.0", name)
            assertEquals("Faster sync.\r\n\r\n- Fixes", body)
            assertEquals("https://github.com/Malinskiy/mellow/releases/tag/v1.2.0", htmlUrl)
            assertEquals("2026-09-30T12:00:00Z", publishedAt)
            assertFalse(draft)
            assertFalse(prerelease)
            assertEquals(2, assets.size)
        }
        assertEquals(
            ReleaseAsset(
                name = "app-release.apk",
                downloadUrl = "https://github.com/Malinskiy/mellow/releases/download/v1.2.0/app-release.apk",
                sizeBytes = 23_456_789,
                sha256 = SHA256,
            ),
            fresh.release.assets[0],
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/repos/Malinskiy/mellow/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertEquals("Mellow/v1.0.0 (Android)", request.getHeader("User-Agent"))
        assertNull(request.getHeader("If-None-Match"))
    }

    @Test
    fun `200 without a digest leaves sha256 null`() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(digest = null)))

        val fresh = (api.latestRelease(baseUrl, etag = null) as MellowResult.Success).data as ReleaseResponse.Fresh

        assertNull(fresh.release.assets[0].sha256)
        assertNull(fresh.etag)
    }

    @Test
    fun `a digest with another algorithm is ignored`() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(digest = "\"sha512:${"ab".repeat(64)}\"")))

        val fresh = (api.latestRelease(baseUrl, etag = null) as MellowResult.Success).data as ReleaseResponse.Fresh

        assertNull(fresh.release.assets[0].sha256)
    }

    @Test
    fun `JSON nulls become absent fields, not the string null`() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(name = "null", body = "null", digest = "null")))

        val fresh = (api.latestRelease(baseUrl, etag = null) as MellowResult.Success).data as ReleaseResponse.Fresh

        assertEquals("v1.2.0", fresh.release.name)
        assertEquals("", fresh.release.body)
        assertNull(fresh.release.assets[0].sha256)
    }

    @Test
    fun `the cached ETag is sent and 304 means not modified`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        val result = api.latestRelease(baseUrl, etag = "\"etag-1\"")

        assertEquals(MellowResult.Success(ReleaseResponse.NotModified), result)
        assertEquals("\"etag-1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `403 is a rate limit with the reset time`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("x-ratelimit-remaining", "0")
                .setHeader("x-ratelimit-reset", "1790000000")
                .setBody("""{"message":"API rate limit exceeded"}"""),
        )

        val error = (api.latestRelease(baseUrl, etag = null) as MellowResult.Error).exception

        assertTrue(error is GitHubRateLimitedException)
        assertEquals(1_790_000_000L, (error as GitHubRateLimitedException).resetEpochSeconds)
    }

    @Test
    fun `429 is a rate limit even without a reset header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val error = (api.latestRelease(baseUrl, etag = null) as MellowResult.Error).exception

        assertNull((error as GitHubRateLimitedException).resetEpochSeconds)
    }

    @Test
    fun `other statuses are errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val error = (api.latestRelease(baseUrl, etag = null) as MellowResult.Error).exception

        assertEquals(404, (error as GitHubHttpException).code)
    }

    @Test
    fun `malformed JSON is an error, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name": "v1.2.0", "assets": ["""))

        val error = (api.latestRelease(baseUrl, etag = null) as MellowResult.Error).exception

        assertTrue(error is JSONException)
    }

    @Test
    fun `a release without a tag is an error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name": "Mellow", "assets": []}"""))

        val result = api.latestRelease(baseUrl, etag = null)

        assertTrue(result is MellowResult.Error)
    }

    @Test
    fun `an unreachable server is an error`() = runTest {
        val stopped = MockWebServer().apply { start() }
        val unreachable = stopped.url("/").toString()
        stopped.shutdown()

        val result = api.latestRelease(unreachable, etag = null)

        assertTrue(result is MellowResult.Error)
    }

    @Test
    fun `an invalid base URL is an error`() = runTest {
        val result = api.latestRelease("not a url", etag = null)

        assertTrue((result as MellowResult.Error).exception is IllegalArgumentException)
    }

    private fun releaseJson(
        name: String = "\"Mellow 1.2.0\"",
        body: String = "\"Faster sync.\\r\\n\\r\\n- Fixes\"",
        digest: String? = "\"sha256:${SHA256.uppercase()}\"",
    ): String {
        val digestField = if (digest == null) "" else """"digest": $digest,"""
        return """
            {
              "url": "https://api.github.com/repos/Malinskiy/mellow/releases/1",
              "html_url": "https://github.com/Malinskiy/mellow/releases/tag/v1.2.0",
              "id": 1,
              "tag_name": "v1.2.0",
              "name": $name,
              "draft": false,
              "prerelease": false,
              "created_at": "2026-09-30T11:00:00Z",
              "published_at": "2026-09-30T12:00:00Z",
              "assets": [
                {
                  "name": "app-release.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "state": "uploaded",
                  "size": 23456789,
                  $digestField
                  "browser_download_url": "https://github.com/Malinskiy/mellow/releases/download/v1.2.0/app-release.apk"
                },
                {
                  "name": "checksums.txt",
                  "size": 120,
                  "digest": null,
                  "browser_download_url": "https://github.com/Malinskiy/mellow/releases/download/v1.2.0/checksums.txt"
                }
              ],
              "body": $body
            }
        """.trimIndent()
    }

    private companion object {
        const val SHA256 = "0f4c1a3b5d7e9f11223344556677889900aabbccddeeff00112233445566778a"
    }
}
