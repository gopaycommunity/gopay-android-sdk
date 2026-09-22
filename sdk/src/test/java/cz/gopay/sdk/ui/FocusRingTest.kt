package cz.gopay.sdk.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusRingTest {

    private val field = Size(width = 300f, height = 48f)


    @Test
    fun pillRadius_isClampedToTheField() {
        val outline = focusRingOutline(field, strokeWidth = 4f, cornerRadius = 999f)

        assertEquals(field.height / 2f + 2f, outline.topLeftCornerRadius.x, 0f)
    }
}
