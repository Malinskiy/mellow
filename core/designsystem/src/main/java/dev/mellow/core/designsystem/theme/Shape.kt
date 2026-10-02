package dev.mellow.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

object MellowShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Full = RoundedCornerShape(50)

    /**
     * Proportional corners for album art — scales with image size during shared element transitions.
     *
     * The radius is 7% of the shorter side but never below [Small]'s 8.dp, so small thumbnails
     * (e.g. the 60.dp art in Quick Picks) match the 8.dp card they sit in instead of showing
     * sharper corners that snap when a shared element transition starts or ends. Because the
     * radius is derived from the current bounds, the same shape works as both the resting clip
     * and the overlay clip, and the radius animates continuously between the two.
     */
    val AlbumArt: Shape = MinRadiusPercentShape(percent = 7, minRadius = 8.dp)
}

private class MinRadiusPercentShape(
    private val percent: Int,
    private val minRadius: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val proportional = min(size.width, size.height) * percent / 100f
        val radius = max(proportional, with(density) { minRadius.toPx() })
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}
