package dev.mellow.core.data.preferences

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs submitted suspending blocks one at a time, in submission order, each to completion before the next starts,
 * even when a block suspends. Unlike `limitedParallelism(1)`, a later block can't overtake an earlier one that is
 * suspended.
 */
class SerialExecutor(scope: CoroutineScope, private val onError: (Throwable) -> Unit = {}) {
    private val blocks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (block in blocks) {
                try {
                    block()
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    /** Queues [block] without waiting for it. Safe to call from any thread. */
    fun submit(block: suspend () -> Unit) {
        blocks.trySend(block)
    }

    /** Queues [block] and waits for its result, so it sees every block submitted before it. */
    suspend fun <T> call(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        submit {
            try {
                result.complete(block())
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }
        return result.await()
    }
}
