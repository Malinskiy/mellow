package dev.mellow.core.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "update",
)

/** The latest release seen on GitHub that passed the draft/prerelease filter, with the asset chosen for this device. */
data class CachedRelease(val release: GitHubRelease, val asset: ReleaseAsset)

@Singleton
class UpdatePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val config: UpdateConfig,
) {

    @Inject
    constructor(@ApplicationContext context: Context, config: UpdateConfig) : this(context.updateDataStore, config)

    /** Defaults to on for release builds and off for debug builds. */
    val autoCheckEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_CHECK_ENABLED] ?: !config.isDebugBuild
    }

    val lastCheckedAtMillis: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_CHECKED_AT] ?: 0L
    }

    val skippedTag: Flow<String?> = dataStore.data.map { preferences -> preferences[SKIPPED_TAG] }

    val etag: Flow<String?> = dataStore.data.map { preferences -> preferences[ETAG] }

    val cachedRelease: Flow<CachedRelease?> = dataStore.data.map { preferences -> preferences.cachedRelease() }

    val downloadedApk: Flow<DownloadedApk?> = dataStore.data.map { preferences ->
        val tag = preferences[DOWNLOADED_TAG]
        val path = preferences[DOWNLOADED_PATH]
        if (tag != null && path != null) DownloadedApk(tag, path, preferences[DOWNLOADED_SHA256]) else null
    }

    /**
     * Debug builds only (the repository ignores it otherwise): replaces [UpdateConfig.apiBaseUrl], i.e. the GitHub
     * REST API host the release check hits. Lets a local fake `releases/latest` stand in for api.github.com.
     */
    val devApiBaseUrlOverride: Flow<String?> = dataStore.data.map { preferences ->
        preferences[DEV_API_BASE_URL_OVERRIDE]
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_CHECK_ENABLED] = enabled
        }
    }

    suspend fun setLastCheckedAtMillis(millis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_CHECKED_AT] = millis
        }
    }

    suspend fun setSkippedTag(tag: String?) {
        dataStore.edit { preferences ->
            preferences.putOrRemove(SKIPPED_TAG, tag)
        }
    }

    /** Stores the release from a 200 response (null when it offered nothing installable) with that response's ETag. */
    suspend fun saveRelease(release: CachedRelease?, etag: String?) {
        dataStore.edit { preferences ->
            preferences.putOrRemove(ETAG, etag)
            preferences.putOrRemove(RELEASE_TAG, release?.release?.tagName)
            preferences.putOrRemove(RELEASE_NAME, release?.release?.name)
            preferences.putOrRemove(RELEASE_BODY, release?.release?.body)
            preferences.putOrRemove(RELEASE_HTML_URL, release?.release?.htmlUrl)
            preferences.putOrRemove(RELEASE_PUBLISHED_AT, release?.release?.publishedAt)
            preferences.putOrRemove(ASSET_NAME, release?.asset?.name)
            preferences.putOrRemove(ASSET_URL, release?.asset?.downloadUrl)
            preferences.putOrRemove(ASSET_SIZE, release?.asset?.sizeBytes)
            preferences.putOrRemove(ASSET_SHA256, release?.asset?.sha256)
        }
    }

    /** Forgets the cached release and the skipped tag; keeps the ETag, which still describes the server's answer. */
    suspend fun clearCachedRelease() {
        dataStore.edit { preferences ->
            CACHED_RELEASE_KEYS.forEach { key -> preferences.remove(key) }
            preferences.remove(SKIPPED_TAG)
        }
    }

    suspend fun setDownloadedApk(apk: DownloadedApk?) {
        dataStore.edit { preferences ->
            preferences.putOrRemove(DOWNLOADED_TAG, apk?.tag)
            preferences.putOrRemove(DOWNLOADED_PATH, apk?.path)
            preferences.putOrRemove(DOWNLOADED_SHA256, apk?.sha256)
        }
    }

    /** Also drops the ETag: it belongs to the previous server. */
    suspend fun setDevApiBaseUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            preferences.putOrRemove(DEV_API_BASE_URL_OVERRIDE, url)
            preferences.remove(ETAG)
        }
    }

    private fun Preferences.cachedRelease(): CachedRelease? {
        val tag = this[RELEASE_TAG] ?: return null
        val asset = ReleaseAsset(
            name = this[ASSET_NAME] ?: return null,
            downloadUrl = this[ASSET_URL] ?: return null,
            sizeBytes = this[ASSET_SIZE] ?: -1L,
            sha256 = this[ASSET_SHA256],
        )
        val release = GitHubRelease(
            tagName = tag,
            name = this[RELEASE_NAME] ?: tag,
            body = this[RELEASE_BODY].orEmpty(),
            htmlUrl = this[RELEASE_HTML_URL].orEmpty(),
            publishedAt = this[RELEASE_PUBLISHED_AT],
            draft = false,
            prerelease = false,
            assets = listOf(asset),
        )
        return CachedRelease(release, asset)
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        private val AUTO_CHECK_ENABLED = booleanPreferencesKey("auto_check_enabled")
        private val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        private val SKIPPED_TAG = stringPreferencesKey("skipped_tag")
        private val ETAG = stringPreferencesKey("etag")
        private val RELEASE_TAG = stringPreferencesKey("release_tag")
        private val RELEASE_NAME = stringPreferencesKey("release_name")
        private val RELEASE_BODY = stringPreferencesKey("release_body")
        private val RELEASE_HTML_URL = stringPreferencesKey("release_html_url")
        private val RELEASE_PUBLISHED_AT = stringPreferencesKey("release_published_at")
        private val ASSET_NAME = stringPreferencesKey("asset_name")
        private val ASSET_URL = stringPreferencesKey("asset_url")
        private val ASSET_SIZE = longPreferencesKey("asset_size")
        private val ASSET_SHA256 = stringPreferencesKey("asset_sha256")
        private val DOWNLOADED_TAG = stringPreferencesKey("downloaded_tag")
        private val DOWNLOADED_PATH = stringPreferencesKey("downloaded_path")
        private val DOWNLOADED_SHA256 = stringPreferencesKey("downloaded_sha256")
        private val DEV_API_BASE_URL_OVERRIDE = stringPreferencesKey("dev_api_base_url_override")

        private val CACHED_RELEASE_KEYS = listOf(
            RELEASE_TAG, RELEASE_NAME, RELEASE_BODY, RELEASE_HTML_URL, RELEASE_PUBLISHED_AT,
            ASSET_NAME, ASSET_URL, ASSET_SIZE, ASSET_SHA256,
        )
    }
}
