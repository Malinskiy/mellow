package dev.mellow.core.player

/**
 * The part of a browsable list a browser asked for: [limit] items from position [offset].
 *
 * @property offset position of the first item
 * @property limit most items to return
 */
internal data class BrowseWindow(val offset: Int, val limit: Int) {
    companion object {
        /**
         * The window for [page] of [pageSize] items, at most [maxItems] long: legacy browsers such as Android Auto
         * don't page, and ask for page 0 of `Int.MAX_VALUE` items. `null` if the page starts beyond any list.
         */
        fun of(page: Int, pageSize: Int, maxItems: Int): BrowseWindow? {
            val size = pageSize.coerceAtLeast(1)
            val offset = page.coerceAtLeast(0).toLong() * size
            if (offset > Int.MAX_VALUE) return null
            return BrowseWindow(offset.toInt(), size.coerceAtMost(maxItems))
        }
    }
}

/** The items of an in-memory list that fall in [window]. */
internal fun <T> List<T>.page(window: BrowseWindow): List<T> = drop(window.offset).take(window.limit)
