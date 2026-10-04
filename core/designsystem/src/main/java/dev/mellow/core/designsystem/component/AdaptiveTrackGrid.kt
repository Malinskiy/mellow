package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

private const val MAX_COLUMNS = 3
private val MIN_COLUMN_WIDTH = 350.dp

/**
 * Lays out track rows in 1–3 columns depending on the available width.
 *
 * With [nested] = false this is a scrollable [LazyVerticalGrid] that fills its parent.
 * With [nested] = true it is a plain [Column] of [Row]s sized by its children, suitable for
 * embedding in a scrolling parent (e.g. a section on the home screen). Nested lists are
 * expected to be short — every item is composed eagerly.
 *
 * When [columnFirst] is true items flow top-to-bottom within each column before moving to the
 * next; otherwise they flow left-to-right across each row.
 */
@Composable
fun <T> AdaptiveTrackGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    minColumnWidth: Dp = MIN_COLUMN_WIDTH,
    nested: Boolean = false,
    columnFirst: Boolean = true,
    itemContent: @Composable (index: Int, item: T, columns: Int) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = (maxWidth / minColumnWidth).toInt().coerceIn(1, MAX_COLUMNS)
        val rows = ceil(items.size.toFloat() / columns).toInt()

        // Grid position -> original item index. Identity when there is a single column or the
        // items flow row-first; otherwise transposed so each column reads top-to-bottom.
        val gridOrder = remember(items, columns, columnFirst) {
            if (!columnFirst || columns <= 1) {
                items.indices.toList()
            } else {
                (0 until rows * columns).mapNotNull { gridPos ->
                    val row = gridPos / columns
                    val col = gridPos % columns
                    val itemIndex = col * rows + row
                    if (itemIndex < items.size) itemIndex else null
                }
            }
        }

        if (nested) {
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                gridOrder.chunked(columns).forEach { rowIndices ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowIndices.forEach { index ->
                            key(key(items[index])) {
                                Box(modifier = Modifier.weight(1f)) {
                                    itemContent(index, items[index], columns)
                                }
                            }
                        }
                        repeat(columns - rowIndices.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(gridOrder.size, key = { key(items[gridOrder[it]]) }) { gridIndex ->
                    val index = gridOrder[gridIndex]
                    itemContent(index, items[index], columns)
                }
            }
        }
    }
}

/**
 * A scrollable [AdaptiveTrackGrid] of [itemCount] items addressed by position, for lists that load as they scroll,
 * such as Paging's `LazyPagingItems`. Items always flow left-to-right across each row: reading down a column would
 * need items from far apart in the list at once.
 */
@Composable
fun AdaptiveTrackGrid(
    itemCount: Int,
    key: (index: Int) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    minColumnWidth: Dp = MIN_COLUMN_WIDTH,
    itemContent: @Composable (index: Int, columns: Int) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = (maxWidth / minColumnWidth).toInt().coerceIn(1, MAX_COLUMNS)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(itemCount, key = key) { index -> itemContent(index, columns) }
        }
    }
}
