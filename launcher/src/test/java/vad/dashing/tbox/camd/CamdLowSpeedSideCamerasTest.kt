package vad.dashing.tbox.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalSide
import kotlin.math.roundToInt

class CamdLowSpeedSideCamerasTest {

    data class RectBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private fun resolveTargetSideCamera(
        speed: Float,
        lowSpeedThreshold: Float,
        lowSpeedEnabled: Boolean,
        turnSignalCamerasEnabled: Boolean,
        triggerMode: String,
        turnSignalSide: TurnSignalSide?,
        steerSide: TurnSignalSide?,
    ): Pair<TurnSignalSide?, Boolean> {
        val isLowSpeed = speed < lowSpeedThreshold

        return if (lowSpeedEnabled && isLowSpeed) {
            val side = when (triggerMode) {
                "turn_signals" -> turnSignalSide
                "steering_wheel" -> steerSide
                "both" -> turnSignalSide ?: steerSide
                else -> turnSignalSide ?: steerSide
            }
            if (side != null) side to true else null to false
        } else if (turnSignalCamerasEnabled) {
            if (turnSignalSide != null) turnSignalSide to false else null to false
        } else {
            null to false
        }
    }

    private fun updateSteerSide(
        currentSteerSide: TurnSignalSide?,
        steerAngle: Float?,
        onAngle: Float = 30f,
        offAngle: Float = 20f,
    ): TurnSignalSide? {
        if (steerAngle == null || !steerAngle.isFinite()) return null
        return when {
            steerAngle <= -onAngle -> TurnSignalSide.Left
            steerAngle >= onAngle -> TurnSignalSide.Right
            currentSteerSide == TurnSignalSide.Left && steerAngle > -offAngle -> null
            currentSteerSide == TurnSignalSide.Right && steerAngle < offAngle -> null
            else -> currentSteerSide
        }
    }

    private fun clampAndAdjustSteerAngles(
        rawOff: Float,
        rawOn: Float,
        currentOff: Int,
    ): Pair<Int, Int> {
        val offStep = (rawOff / 5f).roundToInt() * 5
        val onStep = (rawOn / 5f).roundToInt() * 5
        var newOff = offStep.coerceIn(5, 85)
        var newOn = onStep.coerceIn(10, 90)

        if (newOn - newOff < 5) {
            if (newOff != currentOff) {
                newOff = (newOn - 5).coerceAtLeast(5)
            } else {
                newOn = (newOff + 5).coerceAtMost(90)
            }
        }
        if (newOn <= newOff) {
            if (newOff <= 85) newOn = newOff + 5 else newOff = newOn - 5
        }
        return newOff to newOn
    }

    private fun computeLowSpeedWindowPx(
        desktopRect: RectBounds,
        screenBounds: RectBounds,
        widthPercent: Int,
        isRightCamera: Boolean,
        isFullscreen: Boolean = false,
        isFullWidth: Boolean = false,
    ): RectBounds {
        val validWidthPercent = widthPercent.coerceIn(10, 50)
        val dispW = screenBounds.width
        val dispH = screenBounds.height

        val rectW: Int
        val rectH: Int
        val xPx: Int
        val yPx: Int

        if (isFullscreen) {
            rectW = (dispW * (validWidthPercent / 100f)).roundToInt()
            rectH = dispH
            yPx = 0
            xPx = if (isRightCamera) dispW - rectW else 0
        } else if (isFullWidth) {
            rectW = (dispW * (validWidthPercent / 100f)).roundToInt()
            rectH = desktopRect.height
            yPx = desktopRect.top
            xPx = if (isRightCamera) dispW - rectW else 0
        } else {
            val desktopWidth = desktopRect.width.coerceAtLeast(100)
            rectW = (desktopWidth * (validWidthPercent / 100f)).roundToInt()
            rectH = desktopRect.height
            yPx = desktopRect.top
            xPx = if (isRightCamera) desktopRect.right - rectW else desktopRect.left
        }

        return RectBounds(xPx, yPx, xPx + rectW, yPx + rectH)
    }

    @Test
    fun lowSpeedTrigger_turnSignalOnly() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "turn_signals",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Right,
        )
        assertEquals(TurnSignalSide.Left, side)
        assertTrue(isLow)
    }

    @Test
    fun lowSpeedTrigger_steeringWheelOnly() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 12f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "steering_wheel",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Right,
        )
        assertEquals(TurnSignalSide.Right, side)
        assertTrue(isLow)
    }

    @Test
    fun lowSpeedTrigger_bothConditions_steeringWhenNoTurnSignal() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Left,
        )
        assertEquals(TurnSignalSide.Left, side)
        assertTrue(isLow)
    }

    @Test
    fun highSpeed_switchesToRegularBlindSpotCamera() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 25f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Left,
        )
        assertEquals(TurnSignalSide.Left, side)
        assertFalse(isLow)
    }

    @Test
    fun highSpeed_steeringWheelNeverTriggers() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 30f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Right,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun steeringAngle_defaultHysteresis() {
        var current: TurnSignalSide? = null

        // Neutral
        current = updateSteerSide(current, 10f)
        assertNull(current)

        // Turn right >= 30 deg
        current = updateSteerSide(current, 30f)
        assertEquals(TurnSignalSide.Right, current)

        // Stays right while >= 20 deg
        current = updateSteerSide(current, 21f)
        assertEquals(TurnSignalSide.Right, current)

        // Drops back to neutral when returning below 20 deg
        current = updateSteerSide(current, 19f)
        assertNull(current)

        // Turn left <= -30 deg
        current = updateSteerSide(current, -30f)
        assertEquals(TurnSignalSide.Left, current)

        // Stays left while <= -20 deg
        current = updateSteerSide(current, -21f)
        assertEquals(TurnSignalSide.Left, current)

        // Drops back to neutral when returning above -20 deg
        current = updateSteerSide(current, -18f)
        assertNull(current)
    }

    @Test
    fun steeringAngle_customAnglesHysteresis() {
        var current: TurnSignalSide? = null
        val onAngle = 45f
        val offAngle = 25f

        // At 30 deg: does not activate yet because onAngle is 45 deg
        current = updateSteerSide(current, 30f, onAngle = onAngle, offAngle = offAngle)
        assertNull(current)

        // At 45 deg: activates Right
        current = updateSteerSide(current, 45f, onAngle = onAngle, offAngle = offAngle)
        assertEquals(TurnSignalSide.Right, current)

        // In between 25 and 45 deg: stays active
        current = updateSteerSide(current, 30f, onAngle = onAngle, offAngle = offAngle)
        assertEquals(TurnSignalSide.Right, current)

        // Below 25 deg: deactivates
        current = updateSteerSide(current, 24f, onAngle = onAngle, offAngle = offAngle)
        assertNull(current)
    }

    @Test
    fun steerAnglesSlider_minimumDifference5Degrees() {
        // Dragging left thumb onto right thumb (right at 30, drag left to 30)
        val (off1, on1) = clampAndAdjustSteerAngles(rawOff = 30f, rawOn = 30f, currentOff = 20)
        assertEquals(25, off1)
        assertEquals(30, on1)
        assertEquals(5, on1 - off1)

        // Dragging right thumb onto left thumb (left at 25, drag right to 25)
        val (off2, on2) = clampAndAdjustSteerAngles(rawOff = 25f, rawOn = 25f, currentOff = 25)
        assertEquals(25, off2)
        assertEquals(30, on2)
        assertEquals(5, on2 - off2)
    }

    @Test
    fun steerAnglesSlider_boundsClampedWithin5And90() {
        val (offLow, onLow) = clampAndAdjustSteerAngles(rawOff = 0f, rawOn = 0f, currentOff = 20)
        assertEquals(5, offLow)
        assertEquals(10, onLow)

        val (offHigh, onHigh) = clampAndAdjustSteerAngles(rawOff = 100f, rawOn = 100f, currentOff = 20)
        assertEquals(85, offHigh)
        assertEquals(90, onHigh)
    }

    @Test
    fun windowGeometry_embeddedMode() {
        val desktop = RectBounds(344, 57, 1920, 1006)
        val screen = RectBounds(0, 0, 1920, 1080)
        val desktopWidth = desktop.width

        val leftWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = false)
        assertEquals(desktop.left, leftWindow.left)
        assertEquals(desktop.top, leftWindow.top)
        assertEquals(desktop.bottom, leftWindow.bottom)
        val expectedWidth = (desktopWidth * 0.30f).roundToInt()
        assertEquals(expectedWidth, leftWindow.width)

        val rightWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = true)
        assertEquals(desktop.right, rightWindow.right)
        assertEquals(desktop.top, rightWindow.top)
        assertEquals(desktop.bottom, rightWindow.bottom)
        assertEquals(expectedWidth, rightWindow.width)
        assertEquals(desktop.right - expectedWidth, rightWindow.left)
    }

    @Test
    fun windowGeometry_fullWidthMode() {
        val desktop = RectBounds(0, 57, 1920, 1006)
        val screen = RectBounds(0, 0, 1920, 1080)
        val expectedWidth = (screen.width * 0.30f).roundToInt()

        val leftWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = false, isFullWidth = true)
        assertEquals(0, leftWindow.left)
        assertEquals(desktop.top, leftWindow.top)
        assertEquals(desktop.bottom, leftWindow.bottom)
        assertEquals(expectedWidth, leftWindow.width)

        val rightWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = true, isFullWidth = true)
        assertEquals(screen.right, rightWindow.right)
        assertEquals(desktop.top, rightWindow.top)
        assertEquals(desktop.bottom, rightWindow.bottom)
        assertEquals(expectedWidth, rightWindow.width)
        assertEquals(screen.right - expectedWidth, rightWindow.left)
    }

    @Test
    fun windowGeometry_fullscreenMode() {
        val desktop = RectBounds(0, 57, 1920, 1006)
        val screen = RectBounds(0, 0, 1920, 1080)
        val expectedWidth = (screen.width * 0.30f).roundToInt()

        val leftWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = false, isFullscreen = true)
        assertEquals(0, leftWindow.left)
        assertEquals(0, leftWindow.top)
        assertEquals(screen.bottom, leftWindow.bottom)
        assertEquals(expectedWidth, leftWindow.width)

        val rightWindow = computeLowSpeedWindowPx(desktop, screen, widthPercent = 30, isRightCamera = true, isFullscreen = true)
        assertEquals(screen.right, rightWindow.right)
        assertEquals(0, rightWindow.top)
        assertEquals(screen.bottom, rightWindow.bottom)
        assertEquals(expectedWidth, rightWindow.width)
        assertEquals(screen.right - expectedWidth, rightWindow.left)
    }

    @Test
    fun windowWidth_clampedBetween10And50() {
        val desktop = RectBounds(0, 0, 1000, 1000)
        val screen = RectBounds(0, 0, 1000, 1000)

        val windowMin = computeLowSpeedWindowPx(desktop, screen, widthPercent = 5, isRightCamera = false)
        assertEquals(100, windowMin.width)

        val windowMax = computeLowSpeedWindowPx(desktop, screen, widthPercent = 80, isRightCamera = false)
        assertEquals(500, windowMax.width)
    }
}
