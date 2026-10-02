package dev.mellow.core.update

/** The fields of a GitHub release the updater uses. */
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

/** A downloadable file attached to a release; [sha256] is the lowercase hex digest GitHub reports, when it does. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)
