package dev.mellow.core.designsystem.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

enum class DevicePosture {
    Flat,
    Tabletop,
    Book,
}

data class FoldableState(
    val posture: DevicePosture = DevicePosture.Flat,
    val hingeBounds: Rect = Rect.Zero,
    val isSeparating: Boolean = false,
    val isOccluded: Boolean = false,
) {
    val hasFold: Boolean get() = hingeBounds != Rect.Zero
    val hasVerticalFold: Boolean get() = hasFold && hingeBounds.left > 0f && hingeBounds.top == 0f
    val hasHorizontalFold: Boolean get() = hasFold && hingeBounds.top > 0f && hingeBounds.left == 0f
}

val LocalFoldableState = compositionLocalOf { FoldableState() }

@Composable
fun rememberFoldableState(): State<FoldableState> {
    val context = LocalContext.current
    val activity = context as? Activity ?: return mutableStateOf(FoldableState())

    return produceState(initialValue = FoldableState()) {
        WindowInfoTracker.getOrCreate(context)
            .windowLayoutInfo(activity)
            .collect { layoutInfo ->
                val foldingFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                value = if (foldingFeature != null) {
                    val posture = when {
                        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                            DevicePosture.Tabletop

                        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL ->
                            DevicePosture.Book

                        else -> DevicePosture.Flat
                    }
                    val bounds = foldingFeature.bounds
                    FoldableState(
                        posture = posture,
                        hingeBounds = Rect(
                            left = bounds.left.toFloat(),
                            top = bounds.top.toFloat(),
                            right = bounds.right.toFloat(),
                            bottom = bounds.bottom.toFloat(),
                        ),
                        isSeparating = foldingFeature.isSeparating,
                        isOccluded = foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL,
                    )
                } else {
                    FoldableState()
                }
            }
    }
}
