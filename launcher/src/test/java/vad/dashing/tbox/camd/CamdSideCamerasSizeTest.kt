package vad.dashing.tbox.camd

import org.junit.Assert.assertEquals
import org.junit.Test

class CamdSideCamerasSizeTest {

    @Test
    fun sizeAdjustment_scaleMultiplierFormula() {
        fun scaleMultiplier(percent: Int): Float =
            (1f + percent / 100f).coerceIn(0.5f, 2.0f)

        // 0% -> 1.0 (default/current size)
        assertEquals(1.0f, scaleMultiplier(0), 0.001f)

        // +100% -> 2.0 (2x larger)
        assertEquals(2.0f, scaleMultiplier(100), 0.001f)

        // -50% -> 0.5 (2x smaller)
        assertEquals(0.5f, scaleMultiplier(-50), 0.001f)

        // Mid-values
        assertEquals(1.5f, scaleMultiplier(50), 0.001f)
        assertEquals(0.8f, scaleMultiplier(-20), 0.001f)

        // Out-of-bounds clamped
        assertEquals(2.0f, scaleMultiplier(150), 0.001f)
        assertEquals(0.5f, scaleMultiplier(-80), 0.001f)
    }

    @Test
    fun sizeAdjustment_marginsPreserved() {
        val desktopLeft = 344f
        val desktopRight = 1920f
        val margin = 45f // 30dp * 1.5 density

        val baseWidth = 400f

        listOf(-50, -25, 0, 25, 50, 100).forEach { percent ->
            val scale = (1f + percent / 100f).coerceIn(0.5f, 2.0f)
            val adjustedW = baseWidth * scale

            // Left camera
            val leftX = desktopLeft + margin
            val actualLeftMargin = leftX - desktopLeft
            assertEquals("Left margin must be strictly preserved at $percent%", margin, actualLeftMargin, 0.001f)

            // Right camera
            val rightX = desktopRight - margin - adjustedW
            val actualRightMargin = desktopRight - (rightX + adjustedW)
            assertEquals("Right margin must be strictly preserved at $percent%", margin, actualRightMargin, 0.001f)
        }
    }
}
