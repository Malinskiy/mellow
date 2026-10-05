package dev.mellow.core.designsystem.component.maintenance

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.mellow.core.designsystem.theme.DevicePosture
import dev.mellow.core.designsystem.theme.FoldableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceLayoutTest {

    private val spacing = MaintenanceSpacing(minMargin = 60f, gap = 40f)
    private val caption = Size(500f, 50f)

    @Test
    fun `portrait and near-square windows stack, wide ones go side by side`() {
        assertEquals(MaintenanceShape.Stacked, maintenanceShape(Size(1236f, 2745f), hinge = null))
        assertEquals(MaintenanceShape.Stacked, maintenanceShape(Size(2300f, 2685f), hinge = null))
        assertEquals(MaintenanceShape.Stacked, maintenanceShape(Size(2685f, 2300f), hinge = null))
        assertEquals(MaintenanceShape.SideBySide, maintenanceShape(Size(2745f, 1236f), hinge = null))
        assertEquals(MaintenanceShape.SideBySide, maintenanceShape(Size(2560f, 1600f), hinge = null))
    }

    @Test
    fun `a hinge across splits top and bottom, an upright one left and right`() {
        assertEquals(MaintenanceShape.Tabletop, maintenanceShape(Size(2685f, 2300f), Rect(0f, 1150f, 2685f, 1150f)))
        assertEquals(MaintenanceShape.Book, maintenanceShape(Size(2300f, 2685f), Rect(1150f, 0f, 1150f, 2685f)))
        // Too close to an edge to lay out around.
        assertEquals(MaintenanceShape.Stacked, maintenanceShape(Size(2300f, 2685f), Rect(100f, 0f, 100f, 2685f)))
    }

    @Test
    fun `only a half-folded or separating fold is laid out around`() {
        val hinge = Rect(1150f, 0f, 1150f, 2685f)

        assertNull(FoldableState(DevicePosture.Flat, hinge, isSeparating = false).layoutHinge)
        assertNull(FoldableState().layoutHinge)
        assertEquals(hinge, FoldableState(DevicePosture.Book, hinge, isSeparating = true).layoutHinge)
        assertEquals(hinge, FoldableState(DevicePosture.Flat, hinge, isSeparating = true).layoutHinge)
    }

    @Test
    fun `stacked, the graphic and the caption under it are centred together`() {
        val area = Size(1236f, 2745f)
        val plan = maintenancePlan(MaintenanceShape.Stacked, area, null, caption, spacing)

        assertEquals(area.width, plan.graphic.width, 0.5f)
        assertEquals(plan.graphic.bottom + spacing.gap, plan.captionTopLeft.y, 0.5f)
        val bottom = plan.captionTopLeft.y + caption.height
        assertEquals(area.height - bottom, plan.graphic.top, 0.5f)
        assertEquals(area.width / 2f, plan.captionTopLeft.x + caption.width / 2f, 0.5f)
        assertInside(area, plan)
    }

    @Test
    fun `near square, the group keeps well clear of the top and bottom`() {
        val area = Size(2685f, 2300f)
        val plan = maintenancePlan(MaintenanceShape.Stacked, area, null, caption, spacing)

        val bottom = plan.captionTopLeft.y + caption.height
        assertEquals(area.height * 0.82f, bottom - plan.graphic.top, 0.5f)
        assertEquals(area.height - bottom, plan.graphic.top, 0.5f)
        assertTrue(plan.graphic.top >= spacing.margin(area))
        assertEquals(area.width / 2f, plan.graphic.center.x, 0.5f)
        assertInside(area, plan)
    }

    @Test
    fun `side by side, the graphic and the caption beside it are centred together`() {
        val area = Size(2745f, 1236f)
        val plan = maintenancePlan(MaintenanceShape.SideBySide, area, null, caption, spacing)

        assertEquals(area.height * 0.82f, plan.graphic.height, 0.5f)
        assertEquals(plan.graphic.right + spacing.gap, plan.captionTopLeft.x, 0.5f)
        val right = plan.captionTopLeft.x + caption.width
        assertEquals(area.width - right, plan.graphic.left, 0.5f)
        assertEquals(area.height / 2f, plan.graphic.center.y, 0.5f)
        assertEquals(area.height / 2f, plan.captionTopLeft.y + caption.height / 2f, 0.5f)
        assertInside(area, plan)
    }

    @Test
    fun `tabletop, the graphic stays above the hinge and the caption below it`() {
        val area = Size(2685f, 2300f)
        val hinge = Rect(0f, 1150f, 2685f, 1150f)
        val plan = maintenancePlan(MaintenanceShape.Tabletop, area, hinge, caption, spacing)

        assertTrue(plan.graphic.bottom <= hinge.top - spacing.minMargin + 0.5f)
        assertTrue(plan.captionTopLeft.y > hinge.bottom)
        assertEquals(area.width / 2f, plan.graphic.center.x, 0.5f)
        assertInside(area, plan)
    }

    @Test
    fun `book, the graphic stays left of the hinge and the caption right of it`() {
        val area = Size(2300f, 2685f)
        val hinge = Rect(1150f, 0f, 1150f, 2685f)
        val plan = maintenancePlan(MaintenanceShape.Book, area, hinge, caption, spacing)

        assertTrue(plan.graphic.right <= hinge.left - spacing.minMargin + 0.5f)
        assertTrue(plan.captionTopLeft.x > hinge.right)
        assertEquals((hinge.right + area.width) / 2f, plan.captionTopLeft.x + caption.width / 2f, 0.5f)
        assertInside(area, plan)
    }

    @Test
    fun `the caption scales with the graphic, from titleMedium to headlineMedium`() {
        assertEquals(15f, captionFontSize(slotDp = 300f, minSp = 15f, maxSp = 18f), 0.01f)
        assertEquals(16f, captionFontSize(slotDp = 457.1f, minSp = 15f, maxSp = 18f), 0.01f)
        assertEquals(18f, captionFontSize(slotDp = 800f, minSp = 15f, maxSp = 18f), 0.01f)
    }

    @Test
    fun `the caption may use the width its place has`() {
        val hinge = Rect(1150f, 0f, 1150f, 2685f)

        assertEquals(2745f * 0.4f, captionMaxWidth(MaintenanceShape.SideBySide, Size(2745f, 1236f), null, spacing), 1f)
        assertEquals(1030f, captionMaxWidth(MaintenanceShape.Book, Size(2300f, 2685f), hinge, spacing), 1f)
        assertEquals(1112.4f, captionMaxWidth(MaintenanceShape.Stacked, Size(1236f, 2745f), null, spacing), 1f)
    }

    @Test
    fun `a collapsed window lays out without failing`() {
        for (shape in MaintenanceShape.entries) {
            val empty = maintenancePlan(shape, Size.Zero, null, Size.Zero, spacing)
            assertTrue(empty.graphic.width >= 0f)
            maintenancePlan(shape, Size(10f, 10f), Rect(0f, 5f, 10f, 5f), caption, spacing)
        }
    }

    /** The square graphic and the caption lie inside [area]. */
    private fun assertInside(area: Size, plan: MaintenancePlan) {
        val graphic = plan.graphic
        assertEquals("graphic not square", graphic.width, graphic.height, 0.5f)
        assertTrue("graphic $graphic outside $area", graphic.left >= -0.5f && graphic.top >= -0.5f)
        assertTrue("graphic $graphic outside $area", graphic.right <= area.width + 0.5f)
        assertTrue("graphic $graphic outside $area", graphic.bottom <= area.height + 0.5f)
        val captionRight = plan.captionTopLeft.x + caption.width
        val captionBottom = plan.captionTopLeft.y + caption.height
        assertTrue("caption outside $area", plan.captionTopLeft.x >= 0f && captionRight <= area.width)
        assertTrue("caption outside $area", plan.captionTopLeft.y >= 0f && captionBottom <= area.height)
    }
}
