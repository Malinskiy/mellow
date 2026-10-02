package dev.mellow.core.update

import android.util.Log
import dev.mellow.core.common.MellowResult
import dev.mellow.core.update.di.GitHubClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface ReleaseResponse {
    /** A 200 with the parsed release and the `ETag` to send as `If-None-Match` next time. */
    data class Fresh(val release: GitHubRelease, val etag: String?) : ReleaseResponse

    /** A 304: the release is unchanged since the response the cached ETag belongs to. */
    data object NotModified : ReleaseResponse
}

/** GitHub answered 403/429: the unauthenticated rate limit is used up until [resetEpochSeconds] (if it said). */
class GitHubRateLimitedException(val resetEpochSeconds: Long?) : IOException("GitHub API rate limit exceeded")

/** GitHub answered with a status other than 200, 304, 403 or 429. */
class GitHubHttpException(val code: Int) : IOException("GitHub API answered HTTP $code")

interface GitHubReleaseApi {
    /**
     * `GET {baseUrl}/repos/{owner}/{repo}/releases/latest`, conditional on [etag] when non-null.
     * Never throws: every failure is a [MellowResult.Error] ([GitHubRateLimitedException], [GitHubHttpException],
     * [JSONException] for a malformed body, or the underlying IO exception).
     */
    suspend fun latestRelease(baseUrl: String, etag: String?): MellowResult<ReleaseResponse>
}

@Singleton
class OkHttpGitHubReleaseApi internal constructor(
    private val client: OkHttpClient,
    private val config: UpdateConfig,
    private val ioDispatcher: CoroutineDispatcher,
) : GitHubReleaseApi {

    @Inject
    constructor(@GitHubClient client: OkHttpClient, config: UpdateConfig) : this(client, config, Dispatchers.IO)

    override suspend fun latestRelease(baseUrl: String, etag: String?): MellowResult<ReleaseResponse> {
        val result = try {
            withContext(ioDispatcher) { fetch(baseUrl, etag) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
        if (result is MellowResult.Error) Log.w(LOG_TAG, "Release check against $baseUrl failed", result.exception)
        return result
    }

    private fun fetch(baseUrl: String, etag: String?): MellowResult<ReleaseResponse> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("repos/${config.githubOwner}/${config.githubRepo}/releases/latest")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", "Mellow/${config.currentVersionName} (Android)")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                HTTP_OK -> {
                    val body = response.body?.string() ?: throw IOException("Empty release response")
                    MellowResult.Success(ReleaseResponse.Fresh(parseRelease(body), response.header("ETag")))
                }
                HTTP_NOT_MODIFIED -> MellowResult.Success(ReleaseResponse.NotModified)
                HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> MellowResult.Error(
                    GitHubRateLimitedException(response.header("x-ratelimit-reset")?.trim()?.toLongOrNull()),
                )
                else -> MellowResult.Error(GitHubHttpException(response.code))
            }
        }
    }

    private fun parseRelease(json: String): GitHubRelease {
        val root = JSONObject(json)
        val tagName = root.stringOrNull("tag_name")?.takeIf { it.isNotBlank() }
            ?: throw JSONException("Release has no tag_name")
        val assetsJson = root.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until assetsJson.length()).mapNotNull { index ->
            val asset = assetsJson.optJSONObject(index) ?: return@mapNotNull null
            ReleaseAsset(
                name = asset.stringOrNull("name") ?: return@mapNotNull null,
                downloadUrl = asset.stringOrNull("browser_download_url") ?: return@mapNotNull null,
                sizeBytes = asset.optLong("size", -1L),
                sha256 = parseSha256Digest(asset.stringOrNull("digest")),
            )
        }
        return GitHubRelease(
            tagName = tagName,
            name = root.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: tagName,
            body = root.stringOrNull("body").orEmpty(),
            htmlUrl = root.stringOrNull("html_url")
                ?: "https://github.com/${config.githubOwner}/${config.githubRepo}/releases/tag/$tagName",
            publishedAt = root.stringOrNull("published_at"),
            draft = root.optBoolean("draft", false),
            prerelease = root.optBoolean("prerelease", false),
            assets = assets,
        )
    }

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val HTTP_OK = 200
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
private const val SHA256_PREFIX = "sha256:"

/** `"sha256:<hex>"` → lowercase hex; null when absent, another algorithm, or not a SHA-256 hex string. */
internal fun parseSha256Digest(digest: String?): String? {
    if (digest == null || !digest.startsWith(SHA256_PREFIX, ignoreCase = true)) return null
    return digest.substring(SHA256_PREFIX.length).lowercase().takeIf { SHA256_HEX.matches(it) }
}

/** org.json turns JSON `null` into the string "null" in `optString`; treat it (and a missing key) as absent. */
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
