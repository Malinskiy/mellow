package dev.mellow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Stable
class CollapsibleToolbarState {
    private val _offsetY = mutableFloatStateOf(0f)
    val offsetY: Float get() = _offsetY.floatValue

    private val _heightPx = mutableFloatStateOf(0f)
    var heightPx: Float
        get() = _heightPx.floatValue
        internal set(value) { _heightPx.floatValue = value }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (heightPx <= 0f) return Offset.Zero
            _offsetY.floatValue = (_offsetY.floatValue + available.y).coerceIn(-heightPx, 0f)
            return Offset.Zero
        }
    }
}

@Composable
fun rememberCollapsibleToolbarState(): CollapsibleToolbarState {
    return remember { CollapsibleToolbarState() }
}

@Composable
fun CollapsibleToolbarLayout(
    state: CollapsibleToolbarState,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(state.nestedScrollConnection),
    ) {
        val topPadding = with(density) {
            if (state.heightPx > 0f) state.heightPx.toDp() else 56.dp
        }
        content(PaddingValues(top = topPadding))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { state.heightPx = it.height.toFloat() }
                .graphicsLayer { translationY = state.offsetY },
        ) {
            toolbar()
        }
    }
}
