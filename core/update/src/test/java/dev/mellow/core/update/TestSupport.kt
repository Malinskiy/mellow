package dev.mellow.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.mellow.core.common.MellowResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** A DataStore that never touches disk. */
internal class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

/** Records every call and answers with [response]. */
internal class FakeGitHubReleaseApi : GitHubReleaseApi {
    var response: MellowResult<ReleaseResponse> = MellowResult.Error(IOException("no response configured"))
    val calls = mutableListOf<Call>()

    data class Call(val baseUrl: String, val etag: String?)

    override suspend fun latestRelease(baseUrl: String, etag: String?): MellowResult<ReleaseResponse> {
        calls += Call(baseUrl, etag)
        return response
    }
}

internal fun testConfig(
    currentVersionName: String = "v1.0.0",
    currentVersionCode: Long = 10,
    isDebugBuild: Boolean = false,
) = UpdateConfig(
    githubOwner = "Malinskiy",
    githubRepo = "mellow",
    currentVersionName = currentVersionName,
    currentVersionCode = currentVersionCode,
    isDebugBuild = isDebugBuild,
)

internal fun asset(
    name: String = "app-release.apk",
    sizeBytes: Long = 1_024,
    sha256: String? = null,
    downloadUrl: String = "https://github.com/Malinskiy/mellow/releases/download/v1.2.0/$name",
) = ReleaseAsset(name = name, downloadUrl = downloadUrl, sizeBytes = sizeBytes, sha256 = sha256)

internal fun release(
    tag: String = "v1.2.0",
    assets: List<ReleaseAsset> = listOf(asset()),
    draft: Boolean = false,
    prerelease: Boolean = false,
) = GitHubRelease(
    tagName = tag,
    name = "Mellow $tag",
    body = "What's new in $tag",
    htmlUrl = "https://github.com/Malinskiy/mellow/releases/tag/$tag",
    publishedAt = "2026-09-30T12:00:00Z",
    draft = draft,
    prerelease = prerelease,
    assets = assets,
)
