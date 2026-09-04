package cz.gopay.sdk.ui

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRingTest {

    private val field = Size(width = 300f, height = 48f)

    @Test
    fun ring_liesEntirelyOutsideTheField() {
        val stroke = 6f

        val outline = focusRingOutline(field, stroke, cornerRadius = 8f)

        // The stroke is centered on the outline: its inner edge is the field edge, so nothing is
        // painted over the field, and its outer edge is one ring width out on every side.
        assertEquals(0f, outline.left + stroke / 2f, 0f)
        assertEquals(0f, outline.top + stroke / 2f, 0f)
        assertEquals(field.width, outline.right - stroke / 2f, 0f)
        assertEquals(field.height, outline.bottom - stroke / 2f, 0f)
        assertEquals(-stroke, outline.left - stroke / 2f, 0f)
        assertEquals(field.width + stroke, outline.right + stroke / 2f, 0f)
        assertEquals(field.height + stroke, outline.bottom + stroke / 2f, 0f)
    }

    @Test
    fun ring_followsTheCornersOfTheField() {
        val outline = focusRingOutline(field, strokeWidth = 4f, cornerRadius = 8f)

        assertEquals("Concentric with the border radius", 10f, outline.topLeftCornerRadius.x, 0f)
        assertEquals(outline.topLeftCornerRadius, outline.bottomRightCornerRadius)
    }

    @Test
    fun squareField_keepsSquareOuterCorners() {
        val outline = focusRingOutline(field, strokeWidth = 4f, cornerRadius = 0f)

        assertEquals(2f, outline.topLeftCornerRadius.x, 0f)
    }

    @Test
    fun usableStrokeWidth_neverOutgrowsTheField() {
        val field = Size(200f, 50f)

        assertEquals("A normal stroke passes through", 2f, usableStrokeWidth(field, 2f), 0.01f)
        // Past half the smaller side the outline would turn itself inside out.
        assertEquals("A huge stroke is clamped to the field", 25f, usableStrokeWidth(field, 260f), 0.01f)
    }
}
