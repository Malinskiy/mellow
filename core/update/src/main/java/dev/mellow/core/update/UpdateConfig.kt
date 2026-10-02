package dev.mellow.core.update

/** Logcat tag shared by every class in the updater. */
internal const val LOG_TAG = "MellowUpdate"

/**
 * Static facts about the running build that the updater needs. Provided by the app module (it owns `BuildConfig`).
 */
data class UpdateConfig(
    val githubOwner: String,
    val githubRepo: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val isDebugBuild: Boolean,
    /** GitHub REST API host used for `GET /repos/{owner}/{repo}/releases/latest`. */
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {
    companion object {
        const val DEFAULT_API_BASE_URL = "https://api.github.com"
    }
}
