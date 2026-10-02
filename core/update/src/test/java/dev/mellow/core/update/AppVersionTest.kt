package dev.mellow.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses tags and git describe output`() {
        val cases = listOf(
            "v1.2.3" to AppVersion(1, 2, 3),
            "1.2.3" to AppVersion(1, 2, 3),
            "V10.20.30" to AppVersion(10, 20, 30),
            "1.2" to AppVersion(1, 2, 0),
            "v1.2" to AppVersion(1, 2, 0),
            " v1.2.3\n" to AppVersion(1, 2, 3),
            "v1.2.3-4-gabcdef" to AppVersion(1, 2, 3, commitsAhead = 4),
            "v1.2.3-4-gabcdef-dirty" to AppVersion(1, 2, 3, commitsAhead = 4),
            "v1.2.3-dirty" to AppVersion(1, 2, 3),
            "v1.2-12-g0123456789abcdef" to AppVersion(1, 2, 0, commitsAhead = 12),
        )
        for ((raw, expected) in cases) {
            assertEquals("parse('$raw')", expected, AppVersion.parse(raw))
        }
    }

    @Test
    fun `rejects what is not a release version`() {
        val cases = listOf(
            "abc1234",
            "abc1234-dirty",
            "",
            "v",
            "1",
            "1.2.3.4",
            "v1.2.3-beta.1",
            "v1.2.3-4-gxyz",
            "latest",
        )
        for (raw in cases) {
            assertEquals("parse('$raw')", null, AppVersion.parse(raw))
        }
    }

    @Test
    fun `isNewer compares the numeric triple only`() {
        data class Case(val remote: String, val current: String, val newer: Boolean)

        val cases = listOf(
            Case("v1.2.4", "v1.2.3", newer = true),
            Case("v1.3.0", "v1.2.9", newer = true),
            Case("v2.0.0", "v1.99.99", newer = true),
            Case("v1.10.0", "v1.9.0", newer = true),
            Case("1.3", "v1.2.9", newer = true),
            Case("v1.2.4", "v1.2.3-4-gabc", newer = true),
            Case("v1.2.3", "v1.2.3", newer = false),
            Case("v1.2", "1.2.0", newer = false),
            Case("v1.2.2", "v1.2.3", newer = false),
            // A dev build of the tag is ahead of the release, not behind it.
            Case("v1.2.3", "v1.2.3-4-gabc", newer = false),
            Case("v1.2.3", "v1.2.3-4-gabcdef-dirty", newer = false),
            Case("v1.2.3", "v1.2.3-dirty", newer = false),
            // Unparseable local builds never prompt; unparseable tags are never newer.
            Case("v1.2.4", "abc1234", newer = false),
            Case("v1.2.4", "abc1234-dirty", newer = false),
            Case("nightly", "v1.2.3", newer = false),
        )
        for (case in cases) {
            assertEquals(
                "isNewer(${case.remote}, ${case.current})",
                case.newer,
                AppVersion.isNewer(case.remote, case.current),
            )
        }
    }
}
