package dev.mellow.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueWindowTest {

    @Test
    fun `a list that fits is queued whole, whichever track plays`() {
        assertEquals(0, queueWindowStart(index = 0, count = 500))
        assertEquals(0, queueWindowStart(index = 450, count = 500))
        assertEquals(0, queueWindowStart(index = 19, count = 20))
    }

    @Test
    fun `a long list is queued from a few tracks before the played one`() {
        assertEquals(900, queueWindowStart(index = 1_000, count = 50_000))
    }

    @Test
    fun `the window doesn't start before the list`() {
        assertEquals(0, queueWindowStart(index = 30, count = 50_000))
    }

    @Test
    fun `near the end the window stays full`() {
        assertEquals(49_500, queueWindowStart(index = 49_990, count = 50_000))
    }

    @Test
    fun `the played track is always inside the window`() {
        for (count in listOf(1, 499, 500, 501, 5_000)) {
            for (index in 0 until count step 7) {
                val start = queueWindowStart(index, count)
                assertTrue("index $index of $count", index in start until start + QUEUE_WINDOW_SIZE)
            }
        }
    }

    @Test
    fun `a track of a long list plays from a window of 500 around it`() = runTest {
        val list = ids(50_000)
        val requested = mutableListOf<Pair<Int, Int>>()

        val (queue, start) = queueWindow(1_000, list.size, "t1000", { it }, sliceOf(list, requested), { null })!!

        assertEquals(listOf(900 to QUEUE_WINDOW_SIZE), requested)
        assertEquals(list.subList(900, 1_400), queue)
        assertEquals("t1000", queue[start])
    }

    @Test
    fun `a track of a short list plays from the whole list`() = runTest {
        val list = ids(300)

        val (queue, start) = queueWindow(250, list.size, "t250", { it }, sliceOf(list), { null })!!

        assertEquals(list, queue)
        assertEquals(250, start)
    }

    @Test
    fun `without a track ID the window plays from the index, so Play starts at the top`() = runTest {
        val list = ids(2_000)

        val (queue, start) = queueWindow(0, list.size, null, { it }, sliceOf(list), { null })!!

        assertEquals(list.subList(0, QUEUE_WINDOW_SIZE), queue)
        assertEquals(0, start)
    }

    @Test
    fun `a track that moved since the list was shown plays on its own`() = runTest {
        val list = ids(10)

        assertEquals(listOf("gone") to 0, queueWindow(3, list.size, "gone", { it }, sliceOf(list), { "gone" }))
        assertNull(queueWindow(3, list.size, "missing", { it }, sliceOf(list), { null }))
    }

    @Test
    fun `nothing plays when the list can't be read or is empty`() = runTest {
        assertNull(queueWindow<String>(0, 5, "t0", { it }, { _, _ -> null }, { null }))
        assertNull(queueWindow(0, 0, null, { it }, sliceOf(emptyList()), { null }))
    }

    private fun ids(count: Int) = (0 until count).map { "t$it" }

    private fun sliceOf(
        list: List<String>,
        requested: MutableList<Pair<Int, Int>> = mutableListOf(),
    ): suspend (Int, Int) -> List<String> = { offset, limit ->
        requested += offset to limit
        list.drop(offset).take(limit)
    }
}
