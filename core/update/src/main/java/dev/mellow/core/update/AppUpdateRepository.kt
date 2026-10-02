package dev.mellow.core.update

import android.os.Build
import android.util.Log
import dev.mellow.core.common.MellowResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(
        val release: GitHubRelease,
        val asset: ReleaseAsset,
        val skipped: Boolean,
        /** Non-null when a verified APK for this tag is already on disk. */
        val downloaded: DownloadedApk?,
    ) : UpdateCheck
}

/** Decides whether a newer release exists on GitHub, at most once a day unless forced. */
@Singleton
class AppUpdateRepository internal constructor(
    private val config: UpdateConfig,
    private val api: GitHubReleaseApi,
    private val preferences: UpdatePreferences,
    private val downloadedStore: DownloadedUpdateStore,
    private val currentTimeMillis: () -> Long,
    private val supportedAbis: () -> List<String>,
) {

    @Inject
    constructor(
        config: UpdateConfig,
        api: GitHubReleaseApi,
        preferences: UpdatePreferences,
        downloadedStore: DownloadedUpdateStore,
    ) : this(
        config = config,
        api = api,
        preferences = preferences,
        downloadedStore = downloadedStore,
        currentTimeMillis = System::currentTimeMillis,
        supportedAbis = { Build.SUPPORTED_ABIS.toList() },
    )

    val autoCheckEnabled: Flow<Boolean> = preferences.autoCheckEnabled

    /** Only honoured in debug builds. */
    val devApiBaseUrlOverride: Flow<String?> = preferences.devApiBaseUrlOverride

    /** The cached release when it is newer than this build (for the Settings badge). Never touches the network. */
    val availableUpdate: Flow<UpdateCheck.Available?> =
        combine(preferences.cachedRelease, preferences.skippedTag, downloadedStore.entry) { cached, skippedTag, apk ->
            Triple(cached, skippedTag, apk)
        }
            .distinctUntilChanged()
            .map { (cached, skippedTag, _) -> cached?.let { availableOrNull(it, skippedTag) } }

    /**
     * Without [force] this is the throttled start-up check: nothing when automatic checks are off, the cached answer
     * when the last successful check was less than a day ago. Errors are returned, never thrown.
     */
    suspend fun check(force: Boolean): MellowResult<UpdateCheck> {
        if (!force && !preferences.autoCheckEnabled.first()) return MellowResult.Success(UpdateCheck.UpToDate)
        val now = currentTimeMillis()
        if (!force) {
            val elapsed = now - preferences.lastCheckedAtMillis.first()
            if (elapsed in 0L until CHECK_INTERVAL_MILLIS) {
                return MellowResult.Success(resultFor(preferences.cachedRelease.first()))
            }
        }
        val release = when (val response = api.latestRelease(apiBaseUrl(), preferences.etag.first())) {
            is MellowResult.Error -> return response
            MellowResult.Loading -> return MellowResult.Error(IllegalStateException("Release API is still loading"))
            is MellowResult.Success -> when (val body = response.data) {
                ReleaseResponse.NotModified -> preferences.cachedRelease.first()
                is ReleaseResponse.Fresh -> installableRelease(body.release).also { installable ->
                    preferences.saveRelease(installable, body.etag)
                }
            }
        }
        preferences.setLastCheckedAtMillis(now)
        return MellowResult.Success(resultFor(release))
    }

    suspend fun skipVersion(tag: String) {
        preferences.setSkippedTag(tag)
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        preferences.setAutoCheckEnabled(enabled)
    }

    /** Debug builds only: point the GitHub release check at another host (e.g. a local fake). Blank clears it. */
    suspend fun setDevApiBaseUrlOverride(url: String?) {
        preferences.setDevApiBaseUrlOverride(url?.trim()?.takeIf { it.isNotEmpty() })
    }

    /**
     * Run once per process start. Once this build is at or past the cached (or downloaded) release, forgets it, the
     * skipped tag and the downloaded APK, and deletes the updates directory. A downloaded APK for a still-newer tag is
     * kept — the user may be waiting out Android's 24-hour unverified-developer delay — unless its file is gone or
     * changed, in which case only the entry is dropped.
     */
    suspend fun cleanupAfterUpdate() {
        val current = config.currentVersionName
        val cached = preferences.cachedRelease.first()
        if (cached != null && !AppVersion.isNewer(cached.release.tagName, current)) {
            Log.i(LOG_TAG, "Running $current, at or past ${cached.release.tagName}: clearing update state")
            preferences.clearCachedRelease()
            downloadedStore.deleteAll()
            return
        }
        val downloaded = downloadedStore.entry.first() ?: return
        if (!AppVersion.isNewer(downloaded.tag, current)) {
            Log.i(LOG_TAG, "Running $current, at or past downloaded ${downloaded.tag}: deleting it")
            downloadedStore.deleteAll()
            return
        }
        downloadedStore.validate()
    }

    private suspend fun resultFor(release: CachedRelease?): UpdateCheck {
        if (release == null) return UpdateCheck.UpToDate
        if (AppVersion.parse(config.currentVersionName) == null) {
            Log.w(LOG_TAG, "Can't compare versions: '${config.currentVersionName}' is not a release version")
            return UpdateCheck.UpToDate
        }
        return availableOrNull(release, preferences.skippedTag.first()) ?: UpdateCheck.UpToDate
    }

    private suspend fun availableOrNull(cached: CachedRelease, skippedTag: String?): UpdateCheck.Available? {
        val tag = cached.release.tagName
        if (!AppVersion.isNewer(tag, config.currentVersionName)) return null
        return UpdateCheck.Available(
            release = cached.release,
            asset = cached.asset,
            skipped = tag == skippedTag,
            downloaded = downloadedStore.forTag(tag),
        )
    }

    private fun installableRelease(release: GitHubRelease): CachedRelease? {
        if (release.draft || release.prerelease) return null
        val asset = selectApkAsset(release.assets, supportedAbis())
        if (asset == null) {
            Log.w(LOG_TAG, "Release ${release.tagName} has no .apk asset")
            return null
        }
        // Keep only the chosen asset, exactly as the cache stores it, so fresh and cached answers are identical.
        return CachedRelease(release.copy(assets = listOf(asset)), asset)
    }

    private suspend fun apiBaseUrl(): String {
        if (!config.isDebugBuild) return config.apiBaseUrl
        return preferences.devApiBaseUrlOverride.first()?.takeIf { it.isNotBlank() } ?: config.apiBaseUrl
    }

    companion object {
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val PREFERRED_ASSET_NAME = "app-release.apk"

        /**
         * `app-release.apk` if present; else the first `.apk` naming one of [abis] (in the device's preference order)
         * as a whole token, so `x86` does not pick `x86_64`; else the first `.apk`; else null.
         */
        internal fun selectApkAsset(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? {
            val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            apks.firstOrNull { it.name == PREFERRED_ASSET_NAME }?.let { return it }
            for (abi in abis) {
                val token = Regex("(?<![A-Za-z0-9_])${Regex.escape(abi)}(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE)
                apks.firstOrNull { token.containsMatchIn(it.name) }?.let { return it }
            }
            return apks.firstOrNull()
        }
    }
}
