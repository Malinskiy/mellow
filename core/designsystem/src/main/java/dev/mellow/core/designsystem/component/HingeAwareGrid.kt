package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.FoldableState
import dev.mellow.core.designsystem.theme.LocalFoldableState
import dev.mellow.core.designsystem.theme.MellowSpacing
import kotlin.math.ceil

@Composable
fun <T> AdaptiveSectionGrid(
    items: List<T>,
    key: (T) -> Any,
    minCellSize: Dp,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    maxRows: Int = Int.MAX_VALUE,
    spacing: Dp = MellowSpacing.Sp3,
    itemContent: @Composable (T) -> Unit,
) {
    val foldableState = LocalFoldableState.current

    if (foldableState.hasVerticalFold) {
        HingeSplitGrid(
            items = items,
            key = key,
            minCellSize = minCellSize,
            rowHeight = rowHeight,
            foldableState = foldableState,
            maxRows = maxRows,
            spacing = spacing,
            modifier = modifier,
            itemContent = itemContent,
        )
    } else {
        BoxWithConstraints(modifier = modifier) {
            val columns = GridCells.Adaptive(minSize = minCellSize)
            val cols = maxOf(1, (maxWidth / (minCellSize + spacing)).toInt())
            val displayItems = if (maxRows < Int.MAX_VALUE) items.take(cols * maxRows) else items
            val rows = ceil(displayItems.size.toFloat() / cols).toInt()
            val gridModifier = Modifier.height((rowHeight * rows) + (spacing * (rows - 1).coerceAtLeast(0)))
            LazyVerticalGrid(
                columns = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                userScrollEnabled = false,
                modifier = gridModifier,
            ) {
                items(items, key = key) { item ->
                    itemContent(item)
                }
            }
        }
    }
}

@Composable
fun <T> AdaptiveMediaGrid(
    items: List<T>,
    key: (T) -> Any,
    minCellSize: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = MellowSpacing.Sp3,
    contentPadding: PaddingValues = PaddingValues(),
    itemContent: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val foldableState = LocalFoldableState.current
        val columns = if (foldableState.hasVerticalFold) {
            val totalCols = maxOf(2, (maxWidth / (minCellSize + spacing)).toInt())
            val evenCols = if (totalCols % 2 != 0) totalCols + 1 else totalCols
            GridCells.Fixed(evenCols)
        } else {
            GridCells.Adaptive(minSize = minCellSize)
        }
        LazyVerticalGrid(
            columns = columns,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = true,
            contentPadding = contentPadding,
        ) {
            items(items, key = key) { item ->
                itemContent(item)
            }
        }
    }
}

@Composable
private fun <T> HingeSplitGrid(
    items: List<T>,
    key: (T) -> Any,
    minCellSize: Dp,
    rowHeight: Dp,
    foldableState: FoldableState,
    maxRows: Int,
    spacing: Dp,
    modifier: Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val density = LocalDensity.current
    val paneWidth = with(density) { foldableState.hingeBounds.left.toDp() }
    val hingeWidth = with(density) {
        (foldableState.hingeBounds.right - foldableState.hingeBounds.left).toDp()
    }.coerceAtLeast(16.dp)
    val colsPerPane = maxOf(1, (paneWidth / (minCellSize + spacing)).toInt())
    val maxItemsPerPane = if (maxRows < Int.MAX_VALUE) colsPerPane * maxRows else items.size
    val leftItems = items.take(maxItemsPerPane.coerceAtMost(items.size / 2 + items.size % 2))
    val rightItems = items.drop(leftItems.size).take(maxItemsPerPane)

    val leftRows = ceil(leftItems.size.toFloat() / colsPerPane).toInt()
    val rightRows = ceil(rightItems.size.toFloat() / colsPerPane).toInt()
    val totalRows = maxOf(leftRows, rightRows)
    val gridHeight = (rowHeight * totalRows) + (spacing * (totalRows - 1).coerceAtLeast(0))

    Row(modifier = modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(colsPerPane),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = false,
            modifier = Modifier.weight(1f).height(gridHeight),
        ) {
            items(leftItems, key = key) { item -> itemContent(item) }
        }
        Spacer(modifier = Modifier.width(hingeWidth))
        if (rightItems.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(colsPerPane),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                userScrollEnabled = false,
                modifier = Modifier.weight(1f).height(gridHeight),
            ) {
                items(rightItems, key = key) { item -> itemContent(item) }
            }
        }
    }
}
