package dev.mellow.core.player

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whether a button skip moved to another track, read off the player right after the call: a MediaController masks a
 * seek, so its queue position is the new one as soon as the seek is asked for.
 */
class SkippedToTest {

    private val player = mockk<Player>(relaxed = true)

    @Test
    fun `previous that goes back reports the new position`() {
        every { player.currentMediaItemIndex } returnsMany listOf(4, 3)

        assertEquals(3, player.queueIndexAfter { player.skipToPrevious() })
        verify(exactly = 1) { player.seekToPrevious() }
    }

    @Test
    fun `previous that restarts the track reports nothing`() {
        // Past 3 s Media3 seeks to 0 in the same item: the position in the queue doesn't change.
        every { player.currentMediaItemIndex } returnsMany listOf(4, 4)

        assertNull(player.queueIndexAfter { player.skipToPrevious() })
    }

    @Test
    fun `next at the end of the queue reports nothing`() {
        every { player.currentMediaItemIndex } returnsMany listOf(9, 9)

        assertNull(player.queueIndexAfter { player.seekToNextMediaItem() })
    }

    @Test
    fun `next reports the position it went to`() {
        every { player.currentMediaItemIndex } returnsMany listOf(2, 7)

        assertEquals(7, player.queueIndexAfter { player.seekToNextMediaItem() })
    }
}
