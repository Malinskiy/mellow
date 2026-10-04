package dev.mellow.core.player

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * The in-app Previous button behaves like Previous on the notification, lock screen, Android Auto and headsets: those
 * call the session player's `seekToPrevious()`, which restarts the current track once it has played a few seconds
 * and goes to the previous track otherwise.
 */
class SkipToPreviousTest {

    @Test
    fun `previous asks the player to restart or go back, like the system controls`() {
        val player = mockk<Player>(relaxed = true)

        player.skipToPrevious()

        verify(exactly = 1) { player.seekToPrevious() }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
    }
}
