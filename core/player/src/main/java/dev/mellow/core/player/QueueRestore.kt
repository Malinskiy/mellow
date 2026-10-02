package dev.mellow.core.player

import androidx.media3.common.C

/**
 * Which saved queue entries to restore, and where to start.
 *
 * @property keptIndices indices into the saved queue of the tracks that are still available, in order
 * @property startIndex index into [keptIndices] of the track to start from
 * @property startPositionMs position in that track
 */
internal data class RestorePlan(
    val keptIndices: List<Int>,
    val startIndex: Int,
    val startPositionMs: Long,
)

/**
 * Plans restoring a saved queue of [queueSize] tracks, where [isAvailable] tells whether the track at a saved index
 * can still be played (e.g. it hasn't been removed from the library).
 *
 * Starts at the saved track and position if it is still available. Otherwise starts at the beginning of the next
 * available track, or of the last one if none follow. Returns `null` if nothing is available.
 */
internal fun planQueueRestore(
    queueSize: Int,
    savedIndex: Int,
    savedPositionMs: Long,
    isAvailable: (Int) -> Boolean,
): RestorePlan? {
    val kept = (0 until queueSize).filter(isAvailable)
    if (kept.isEmpty()) return null
    val current = savedIndex.coerceIn(0, queueSize - 1)
    val next = kept.indexOfFirst { it >= current }
    return when {
        next == -1 -> RestorePlan(kept, kept.lastIndex, 0L)
        kept[next] == current -> RestorePlan(kept, next, savedPositionMs.coerceAtLeast(0L))
        else -> RestorePlan(kept, next, 0L)
    }
}

/**
 * Where to start once items are dropped from a queue: [kept] says which original items remain. Starts at the requested
 * item and position if it remains, otherwise at the beginning of the next remaining item (or the last one). An unset
 * start index stays unset. Returns the index into the remaining items and the start position.
 */
internal fun remapStart(kept: List<Boolean>, startIndex: Int, startPositionMs: Long): Pair<Int, Long> {
    if (startIndex == C.INDEX_UNSET) return C.INDEX_UNSET to startPositionMs
    val remaining = kept.count { it }
    require(remaining > 0) { "No items remain" }
    val requested = startIndex.coerceIn(0, kept.lastIndex)
    val index = kept.take(requested).count { it }.coerceAtMost(remaining - 1)
    return index to if (kept[requested]) startPositionMs else C.TIME_UNSET
}
