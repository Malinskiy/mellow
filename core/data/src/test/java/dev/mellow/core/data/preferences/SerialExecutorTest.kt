package dev.mellow.core.data.preferences

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SerialExecutorTest {

    @Test
    fun `a suspended block is not overtaken by later ones`() = runTest {
        val log = mutableListOf<String>()
        val executor = SerialExecutor(backgroundScope)
        executor.submit {
            log += "save queue: start"
            delay(1_000)
            log += "save queue: end"
        }
        executor.submit { log += "clear" }
        executor.submit { log += "save position" }
        executor.call { }
        assertEquals(listOf("save queue: start", "save queue: end", "clear", "save position"), log)
    }

    @Test
    fun `call waits for earlier blocks and returns its result`() = runTest {
        var saved = 0
        val executor = SerialExecutor(backgroundScope)
        executor.submit {
            delay(1_000)
            saved = 42
        }
        assertEquals(42, executor.call { saved })
    }

    @Test
    fun `a failing block is reported and does not stop later ones`() = runTest {
        val errors = mutableListOf<Throwable>()
        val log = mutableListOf<String>()
        val executor = SerialExecutor(backgroundScope) { errors += it }
        executor.submit { error("disk full") }
        executor.submit { log += "next" }
        executor.call { }
        assertEquals(listOf("disk full"), errors.map { it.message })
        assertEquals(listOf("next"), log)
    }

    @Test(expected = IllegalStateException::class)
    fun `call rethrows the failure of its own block`() = runTest {
        SerialExecutor(backgroundScope).call { error("disk full") }
    }

    @Test
    fun `blocks submitted from another scope keep their order`() {
        val scope = TestScope(StandardTestDispatcher())
        val log = mutableListOf<Int>()
        val executor = SerialExecutor(scope)
        repeat(100) { i -> executor.submit { if (i % 3 == 0) delay(10); log += i } }
        scope.advanceUntilIdle()
        assertEquals((0 until 100).toList(), log)
    }
}
