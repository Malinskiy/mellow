package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

private const val MAX_COLUMNS = 3
private val MIN_COLUMN_WIDTH = 350.dp
private val ROW_HEIGHT = 64.dp

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

        if (columnFirst) {
            val reordered = remember(items, columns) {
                if (columns <= 1) items
                else {
                    (0 until rows * columns).mapNotNull { gridPos ->
                        val row = gridPos / columns
                        val col = gridPos % columns
                        val itemIndex = col * rows + row
                        items.getOrNull(itemIndex)?.let { itemIndex to it }
                    }.map { it.second }
                }
            }
            val reorderedIndices = remember(items, columns) {
                if (columns <= 1) items.indices.toList()
                else {
                    (0 until rows * columns).mapNotNull { gridPos ->
                        val row = gridPos / columns
                        val col = gridPos % columns
                        val itemIndex = col * rows + row
                        if (itemIndex < items.size) itemIndex else null
                    }
                }
            }

            val gridModifier = if (nested) {
                Modifier.height(ROW_HEIGHT * rows)
            } else {
                Modifier.fillMaxSize()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = contentPadding,
                userScrollEnabled = !nested,
                modifier = gridModifier,
            ) {
                items(reordered.size, key = { reorderedIndices.getOrNull(it)?.let { idx -> key(items[idx]) } ?: it }) { gridIndex ->
                    val originalIndex = reorderedIndices[gridIndex]
                    itemContent(originalIndex, reordered[gridIndex], columns)
                }
            }
        } else {
            val gridModifier = if (nested) {
                Modifier.height(ROW_HEIGHT * rows)
            } else {
                Modifier.fillMaxSize()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = contentPadding,
                userScrollEnabled = !nested,
                modifier = gridModifier,
            ) {
                items(items.size, key = { key(items[it]) }) { index ->
                    itemContent(index, items[index], columns)
                }
            }
        }
    }
}
