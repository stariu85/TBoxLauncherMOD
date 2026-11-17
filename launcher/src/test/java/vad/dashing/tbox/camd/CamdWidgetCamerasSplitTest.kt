package vad.dashing.tbox.camd

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CamdWidgetCamerasSplitTest {

    @Test
    fun leftRightSplit_dividesWidthExactlyInHalf() {
        val dispW = 1920f
        val dispH = 1080f

        val appRect = Rect(344, 51, 1920, 998)
        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge
        val halfW = appWidth / 2f

        // Left camera
        val leftX = leftEdge / dispW
        val leftY = topEdge / dispH
        val leftW = halfW / dispW
        val leftH = appHeight / dispH

        // Right camera
        val rightX = (leftEdge + halfW) / dispW
        val rightY = topEdge / dispH
        val rightW = halfW / dispW
        val rightH = appHeight / dispH

        assertEquals("Left and Right camera width must be identical", leftW, rightW, 0.0001f)
        assertEquals("Total width must match app width fraction", appWidth / dispW, leftW + rightW, 0.0001f)
        assertEquals("Right starts where left ends", leftX + leftW, rightX, 0.0001f)
        assertEquals("Both cameras share same height", leftH, rightH, 0.0001f)

        val leftCmd = CamdCameraRepository.formatCmd(CamdCameraRepository.CAMERA_ID_LEFT, leftX, leftY, leftW, leftH)
        val rightCmd = CamdCameraRepository.formatCmd(CamdCameraRepository.CAMERA_ID_RIGHT, rightX, rightY, rightW, rightH)

        assertTrue(leftCmd.startsWith("OPEN 4 "))
        assertTrue(rightCmd.startsWith("OPEN 5 "))
    }

    @Test
    fun upDownSplit_dividesHeightExactlyInHalf() {
        val dispW = 1920f
        val dispH = 1080f

        val appRect = Rect(344, 51, 1920, 998)
        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge
        val halfH = appHeight / 2f

        // Front (top) camera
        val frontX = leftEdge / dispW
        val frontY = topEdge / dispH
        val frontW = appWidth / dispW
        val frontH = halfH / dispH

        // Rear (bottom) camera
        val rearX = leftEdge / dispW
        val rearY = (topEdge + halfH) / dispH
        val rearW = appWidth / dispW
        val rearH = halfH / dispH

        assertEquals("Front and Rear camera height must be identical", frontH, rearH, 0.0001f)
        assertEquals("Total height must match app height fraction", appHeight / dispH, frontH + rearH, 0.0001f)
        assertEquals("Rear starts where front ends vertically", frontY + frontH, rearY, 0.0001f)
        assertEquals("Both cameras share same width", frontW, rearW, 0.0001f)

        val frontCmd = CamdCameraRepository.formatCmd(CamdCameraRepository.CAMERA_ID_FRONT, frontX, frontY, frontW, frontH)
        val rearCmd = CamdCameraRepository.formatCmd(CamdCameraRepository.CAMERA_ID_REAR, rearX, rearY, rearW, rearH)

        assertTrue(frontCmd.startsWith("OPEN 8 "))
        assertTrue(rearCmd.startsWith("OPEN 9 "))
    }

    @Test
    fun presetFormatting_matchesCamdProtocol() {
        val cmd = CamdCameraRepository.formatPresetCmd("my_wide_left", 0.18f, 0.05f, 0.41f, 0.88f)
        assertEquals("OPEN preset=my_wide_left 0.1800 0.0500 0.4100 0.8800", cmd)
    }
}
