package dev.mellow.feature.settings.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `strips headings, emphasis, bullets and links`() {
        val body = "## What's new\r\n- **Offline** queue\r\n* Fixes [#12](https://x/12)\r\n\r\n\r\n\r\n`adb` tip"
        val expected = "What's new\n• Offline queue\n• Fixes #12\n\nadb tip"
        assertEquals(expected, body.asPlainReleaseNotes())
    }

    @Test
    fun `plain text passes through`() {
        assertEquals("Just text", "  Just text \n".asPlainReleaseNotes())
    }
}
