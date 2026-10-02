package dev.mellow.core.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class RemapStartTest {

    @Test
    fun `keeps the requested item and position when nothing is dropped`() {
        assertEquals(2 to 5_000L, remapStart(listOf(true, true, true), 2, 5_000L))
    }

    @Test
    fun `shifts the index when earlier items are dropped`() {
        assertEquals(1 to 5_000L, remapStart(listOf(false, true, true), 2, 5_000L))
    }

    @Test
    fun `starts the next item from its beginning when the requested one is dropped`() {
        assertEquals(1 to C.TIME_UNSET, remapStart(listOf(true, false, true), 1, 5_000L))
    }

    @Test
    fun `falls back to the last item when nothing after the requested one remains`() {
        assertEquals(0 to C.TIME_UNSET, remapStart(listOf(true, false, false), 2, 5_000L))
    }

    @Test
    fun `leaves an unset start index unset`() {
        assertEquals(C.INDEX_UNSET to C.TIME_UNSET, remapStart(listOf(false, true), C.INDEX_UNSET, C.TIME_UNSET))
    }
}
