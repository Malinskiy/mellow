package dev.mellow.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseWindowTest {

    @Test
    fun `a browser that doesn't page gets the first page, capped`() {
        // Android Auto asks for page 0 of Int.MAX_VALUE items.
        assertEquals(BrowseWindow(offset = 0, limit = 500), BrowseWindow.of(0, Int.MAX_VALUE, maxItems = 500))
    }

    @Test
    fun `a paging browser gets the page it asked for`() {
        assertEquals(BrowseWindow(offset = 100, limit = 50), BrowseWindow.of(2, 50, maxItems = 500))
    }

    @Test
    fun `a page that starts past any list is nothing`() {
        assertNull(BrowseWindow.of(2, Int.MAX_VALUE, maxItems = 500))
    }

    @Test
    fun `an in-memory list is cut to the window`() {
        val list = (0 until 10).toList()

        assertEquals(listOf(8, 9), list.page(BrowseWindow(offset = 8, limit = 5)))
        assertEquals(listOf(0, 1, 2), list.page(BrowseWindow(offset = 0, limit = 3)))
        assertEquals(emptyList<Int>(), list.page(BrowseWindow(offset = 10, limit = 3)))
    }
}
