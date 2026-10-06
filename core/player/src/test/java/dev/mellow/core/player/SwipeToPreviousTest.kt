package dev.mellow.core.player

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Swiping the cover back always goes to the previous track. Unlike the Previous button, it never restarts the current
 * one, however long that has played.
 */
class SwipeToPreviousTest {

    @Test
    fun `swiping back goes to the previous track, never restarts this one`() {
        val player = mockk<Player>(relaxed = true)

        player.swipeToPrevious()

        verify(exactly = 1) { player.seekToPreviousMediaItem() }
        verify(exactly = 0) { player.seekToPrevious() }
    }
}
