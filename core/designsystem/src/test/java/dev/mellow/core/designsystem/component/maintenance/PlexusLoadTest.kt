package dev.mellow.core.designsystem.component.maintenance

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How many lines the plexus draws per frame over a loop. Linking every settled point to every other within the drift's
 * reach once drew ~26,000 lines a frame as the logo formed: 40 fps on a 120 Hz phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native graphics: the default fake Region contains nothing, so no link would be inside the logo.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlexusLoadTest {

    @Test
    fun `no frame of the loop draws more than twice the drift's lines`() {
        val state = PlexusState()
        state.prepare(state.geometryFor(Size(1000f, 1000f)))

        state.update(time = 0f, still = false, pulseAlpha = 1f)
        val drift = state.lineCount + state.glowLineCount
        var peak = 0
        var t = 0f
        while (t < PLEXUS_LOOP_SECONDS) {
            state.update(t, still = false, pulseAlpha = 1f)
            peak = maxOf(peak, state.lineCount + state.glowLineCount)
            t += 0.1f
        }

        assertTrue("drift draws lines: $drift", drift > 1000)
        assertTrue("peak $peak lines a frame, drift $drift", peak <= drift * 2)
    }
}
