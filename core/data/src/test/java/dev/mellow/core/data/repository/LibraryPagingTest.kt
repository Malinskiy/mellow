package dev.mellow.core.data.repository

import dev.mellow.core.database.dao.LibraryOrder
import dev.mellow.core.model.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPagingTest {

    @Test
    fun `each sort maps to its query order`() {
        assertEquals(LibraryOrder.RECENTLY_ADDED, LibrarySort.RecentlyAdded.toOrder())
        assertEquals(LibraryOrder.NAME_ASC, LibrarySort.NameAscending.toOrder())
        assertEquals(LibraryOrder.NAME_DESC, LibrarySort.NameDescending.toOrder())
        assertEquals(LibraryOrder.YEAR, LibrarySort.Year.toOrder())
    }

    @Test
    fun `top genres add up the albums of every genre list a genre is in`() {
        val counts = listOf(
            listOf("Rock", "Pop") to 3,
            listOf("Jazz") to 4,
            listOf("Pop") to 2,
        )

        assertEquals(listOf("Pop", "Jazz", "Rock"), topGenres(counts, limit = 15))
    }

    @Test
    fun `top genres keep first-seen order among ties and stop at the limit`() {
        val counts = listOf(
            listOf("Ambient") to 1,
            listOf("Blues", "Ambient") to 1,
            listOf("Blues") to 1,
            listOf("Celtic") to 2,
        )

        assertEquals(listOf("Ambient", "Blues"), topGenres(counts, limit = 2))
    }
}
