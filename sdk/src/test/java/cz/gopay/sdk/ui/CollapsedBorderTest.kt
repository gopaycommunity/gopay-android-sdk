package cz.gopay.sdk.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedBorderTest {

    private fun edges(
        position: CollapsedBorderPosition,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        rowsTouch: Boolean = true,
        bottomRowTouches: Boolean = true
    ) = collapsedBorderEdges(CollapsedBorderCell(position, rowsTouch, bottomRowTouches), layoutDirection)

    @Test
    fun sharedEdges_areDrawnExactlyOnce() {
        val top = edges(CollapsedBorderPosition.TOP)
        val start = edges(CollapsedBorderPosition.BOTTOM_START)
        val end = edges(CollapsedBorderPosition.BOTTOM_END)

        assertTrue("The top field owns the line it shares with the row below", top.drawBottom)
        assertFalse("The bottom row does not draw it again", start.drawTop)
        assertFalse("The bottom row does not draw it again", end.drawTop)

        assertTrue("The leading field owns the line between the two bottom fields", start.drawRight)
        assertFalse("The trailing field does not draw it again", end.drawLeft)
    }

    @Test
    fun radius_roundsOnlyTheOuterCornersOfTheBlock() {
        val top = edges(CollapsedBorderPosition.TOP)
        val start = edges(CollapsedBorderPosition.BOTTOM_START)
        val end = edges(CollapsedBorderPosition.BOTTOM_END)

        assertTrue(top.roundTopLeft && top.roundTopRight)
        assertFalse(top.roundBottomLeft || top.roundBottomRight)
        assertTrue(start.roundBottomLeft)
        assertFalse(start.roundBottomRight || start.roundTopLeft || start.roundTopRight)
        assertTrue(end.roundBottomRight)
        assertFalse(end.roundBottomLeft || end.roundTopLeft || end.roundTopRight)
    }

    @Test
    fun bottomRow_mirrorsInRightToLeftLayouts() {
        val start = edges(CollapsedBorderPosition.BOTTOM_START, LayoutDirection.Rtl)
        val end = edges(CollapsedBorderPosition.BOTTOM_END, LayoutDirection.Rtl)

        assertTrue("The leading field is on the right", start.roundBottomRight)
        assertFalse(start.roundBottomLeft)
        assertTrue("The trailing field is on the left", end.roundBottomLeft)
        assertTrue("It draws the outer left edge of the block", end.drawLeft)
        assertFalse("The shared line is left to the leading field", end.drawRight)
    }

    @Test
    fun rowsApart_giveTheBottomRowItsOwnTopLineAndRoundTheRowCorners() {
        val top = edges(CollapsedBorderPosition.TOP, rowsTouch = false)
        val start = edges(CollapsedBorderPosition.BOTTOM_START, rowsTouch = false)
        val end = edges(CollapsedBorderPosition.BOTTOM_END, rowsTouch = false)

        assertTrue("A field never floats without a top edge", start.drawTop && end.drawTop)
        assertTrue("The card number rounds its bottom corners", top.roundBottomLeft && top.roundBottomRight)
        assertTrue("The bottom row rounds its outer top corners", start.roundTopLeft && end.roundTopRight)
        assertFalse("The line between expiry and CVV is still shared", end.drawLeft)
        assertFalse("And the corners on it stay square", start.roundTopRight || end.roundTopLeft)
    }

    @Test
    fun bottomRowApart_givesBothFieldsAFullFrame() {
        val start = edges(CollapsedBorderPosition.BOTTOM_START, bottomRowTouches = false)
        val end = edges(CollapsedBorderPosition.BOTTOM_END, bottomRowTouches = false)

        assertTrue(start.drawLeft && start.drawRight && end.drawLeft && end.drawRight)
        assertTrue("The inner bottom corners are outer corners now", start.roundBottomRight && end.roundBottomLeft)
        assertFalse("The top corners still meet the card number", start.roundTopRight || end.roundTopLeft)
    }

    @Test
    fun rowsTouch_onlyWhenNothingIsRenderedBetweenTheRows() {
        val merged = PaymentCardFormTheme(groupSpacing = 0.dp, labelHidden = true, errorMinHeight = 0.dp)

        assertTrue(merged.collapsedRowsTouch(topRowHasFooter = false))
        assertTrue(merged.collapsedBottomRowTouches)
        assertFalse("An error or helper line under the card number separates the rows", merged.collapsedRowsTouch(topRowHasFooter = true))
        assertFalse("A visible label separates the rows", merged.copy(labelHidden = false).collapsedRowsTouch(false))
        assertFalse("A reserved error slot separates the rows", merged.copy(errorMinHeight = 14.dp).collapsedRowsTouch(false))
        assertFalse("A gap separates the rows", merged.copy(groupSpacing = 8.dp).collapsedRowsTouch(false))
        assertFalse("And the bottom row", merged.copy(groupSpacing = 8.dp).collapsedBottomRowTouches)
    }

    private val size = Size(width = 200f, height = 50f)
    private val stroke = 2f

    private fun lines(edges: CollapsedBorderEdges, fullOutline: Boolean, radius: Float = 0f) =
        collapsedBorderGeometry(size, stroke, radius, edges, fullOutline).lines

    @Test
    fun statefulCell_paintsASharedEdgeWhereItsOwnerDoes() {
        val start = edges(CollapsedBorderPosition.BOTTOM_START)
        val end = edges(CollapsedBorderPosition.BOTTOM_END)
        val top = edges(CollapsedBorderPosition.TOP)

        val startTop = lines(start, fullOutline = true).single { it.start.y == it.end.y && it.start.y < 0f }
        assertEquals("The top line sits half a stroke above the field, over the card number's line", -1f, startTop.start.y, 0f)
        assertEquals("It spans the whole width, closing the corners", 0f, startTop.start.x, 0f)
        assertEquals(size.width, startTop.end.x, 0f)

        val endLeft = lines(end, fullOutline = true).single { it.start.x == it.end.x && it.start.x < 0f }
        assertEquals("The left line sits over the expiration's line", -1f, endLeft.start.x, 0f)

        val topBottom = lines(top, fullOutline = true).single { it.start.y == it.end.y && it.start.y > size.height / 2 }
        assertEquals("The card number owns its bottom line and paints it in place", size.height - 1f, topBottom.start.y, 0f)

        assertEquals("A resting cell draws only its own edges", 3, lines(start, fullOutline = false).size)
        assertEquals("A stateful cell draws all four", 4, lines(start, fullOutline = true).size)
    }

    @Test
    fun squareCorners_overlapSoTheJointHasNoGap() {
        val end = edges(CollapsedBorderPosition.BOTTOM_END)
        val geometry = collapsedBorderGeometry(size, stroke, 0f, end, fullOutline = false)

        val bottom = geometry.lines.single { it.start.y == it.end.y }
        val right = geometry.lines.single { it.start.x == it.end.x }
        assertEquals("The bottom line reaches the very edge", Offset(0f, 49f), bottom.start)
        assertEquals(Offset(200f, 49f), bottom.end)
        assertEquals("The right line reaches the very top and bottom", Offset(199f, 0f), right.start)
        assertEquals(Offset(199f, 50f), right.end)
        assertTrue("No arcs on a square block", geometry.arcs.isEmpty())
    }

    @Test
    fun roundedCorners_stopTheLinesAtTheArc() {
        val top = edges(CollapsedBorderPosition.TOP)
        val geometry = collapsedBorderGeometry(size, stroke, 8f, top, fullOutline = false)

        val topLine = geometry.lines.single { it.start.y == it.end.y && it.start.y < size.height / 2 }
        assertEquals(Offset(9f, 1f), topLine.start)
        assertEquals(Offset(191f, 1f), topLine.end)
        val bottomLine = geometry.lines.single { it.start.y == it.end.y && it.start.y > size.height / 2 }
        assertEquals("Square bottom corners still overlap", Offset(0f, 49f), bottomLine.start)
        assertEquals(2, geometry.arcs.size)
        assertEquals(listOf(180f, 270f), geometry.arcs.map { it.startAngle })
    }

    @Test
    fun pillRadius_isClampedTheSameWayForBackgroundAndBorder() {
        assertEquals("Half the shorter side", 25f, collapsedCornerRadius(999f, size), 0f)
        assertEquals(8f, collapsedCornerRadius(8f, size), 0f)
        assertEquals(0f, collapsedCornerRadius(-4f, size), 0f)

        val top = edges(CollapsedBorderPosition.TOP)
        val geometry = collapsedBorderGeometry(size, stroke, 999f, top, fullOutline = false)
        assertEquals("The arcs use the clamped radius", 50f, geometry.arcs.first().diameter, 0f)
    }
}
