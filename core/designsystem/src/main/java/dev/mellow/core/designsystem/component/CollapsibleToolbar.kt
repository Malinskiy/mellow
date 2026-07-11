package dev.mellow.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class CollapsibleToolbarState(
    private val coroutineScope: CoroutineScope,
) {
    internal val offsetY = Animatable(0f)
    internal var heightPx: Float = 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (heightPx <= 0f) return Offset.Zero
            val newOffset = (offsetY.value + available.y).coerceIn(-heightPx, 0f)
            coroutineScope.launch { offsetY.snapTo(newOffset) }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (heightPx <= 0f) return Velocity.Zero
            val target = if (offsetY.value < -heightPx / 2) -heightPx else 0f
            offsetY.animateTo(target, tween(150))
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsibleToolbarState(): CollapsibleToolbarState {
    val scope = rememberCoroutineScope()
    return remember { CollapsibleToolbarState(scope) }
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
                .graphicsLayer { translationY = state.offsetY.value },
        ) {
            toolbar()
        }
    }
}
