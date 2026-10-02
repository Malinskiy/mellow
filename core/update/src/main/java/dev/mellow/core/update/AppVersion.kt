package dev.mellow.core.update

/**
 * A version parsed from a release tag or a `git describe` string.
 *
 * Accepted forms: `v1.2.3`, `1.2.3`, `1.2` (missing components are 0), `v1.2.3-4-gabcdef` (4 commits past the tag)
 * and the same with a `-dirty` suffix. Anything else — notably the bare commit hash `git describe --always` prints
 * when no tag is reachable — is unparseable.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val commitsAhead: Int = 0,
) : Comparable<AppVersion> {

    /** Orders by the numeric triple only; [commitsAhead] does not make a build newer than its tag's release. */
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)\.(\d+)(?:\.(\d+))?(?:-(\d+)-g[0-9a-fA-F]+)?(?:-dirty)?$""")

        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch, ahead) = match.destructured
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = if (patch.isEmpty()) 0 else patch.toIntOrNull() ?: return null,
                commitsAhead = if (ahead.isEmpty()) 0 else ahead.toIntOrNull() ?: return null,
            )
        }

        /**
         * True only when [remote] parses and its numeric triple is strictly greater than [current]'s. An equal triple
         * is not newer even when the local build is commits ahead of the tag, and an unparseable [current] (a local
         * build without a reachable tag) never reports an update.
         */
        fun isNewer(remote: String, current: String): Boolean {
            val remoteVersion = parse(remote) ?: return false
            val currentVersion = parse(current) ?: return false
            return remoteVersion > currentVersion
        }
    }
}
