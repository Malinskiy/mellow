package dev.mellow.core.common

/** The most tracks a queue holds when it's built from a list: a longer list is queued in part. */
const val QUEUE_WINDOW_SIZE = 500

/** How many of the list's tracks before the played one such a queue keeps, so Previous has somewhere to go. */
const val QUEUE_WINDOW_BEFORE = 100

/**
 * Where in a list of [count] tracks to start a queue of at most [size] of them, to play the track at [index]: a few
 * tracks before it and as many as fit after it. A list of [size] tracks or fewer is queued whole.
 */
fun queueWindowStart(
    index: Int,
    count: Int,
    size: Int = QUEUE_WINDOW_SIZE,
    before: Int = QUEUE_WINDOW_BEFORE,
): Int = (index - before).coerceIn(0, (count - size).coerceAtLeast(0))

/**
 * What to queue to play the track at [index] of a list of [count] tracks, and where that track is in it: the part of
 * the list [queueWindowStart] picks, read by [loadSlice] as (offset, limit). With a [trackId], the track is looked
 * for by ID; if the list changed since it was shown and the track isn't there any more, [loadTrack] fetches it to
 * play on its own. `null` if there's nothing to play.
 */
suspend fun <T> queueWindow(
    index: Int,
    count: Int,
    trackId: String?,
    idOf: (T) -> String,
    loadSlice: suspend (offset: Int, limit: Int) -> List<T>?,
    loadTrack: suspend (trackId: String) -> T?,
): Pair<List<T>, Int>? {
    val start = queueWindowStart(index, count)
    val window = loadSlice(start, QUEUE_WINDOW_SIZE) ?: return null
    val position = if (trackId == null) index - start else window.indexOfFirst { idOf(it) == trackId }
    if (position in window.indices) return window to position
    if (trackId == null) return null
    val track = loadTrack(trackId) ?: return null
    return listOf(track) to 0
}
