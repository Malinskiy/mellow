package dev.mellow.core.player

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
