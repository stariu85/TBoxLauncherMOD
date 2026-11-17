package vad.dashing.tbox.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.ui.launcher.LauncherPdcZones
import vad.dashing.tbox.ui.launcher.hasPhysicalPdcObstacle
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
        activeSide: TurnSignalSide? = null,
        activeIsLowSpeed: Boolean = false,
        isAvmActive: Boolean = false,
        isReverseActive: Boolean = false,
        isReverseGear: Boolean = false,
        invertSteerOnReverse: Boolean = false,
        isWidgetActive: Boolean = false,
    ): Pair<TurnSignalSide?, Boolean> {
        return CamdCameraRepository.resolveTargetSideCamera(
            speed = speed,
            lowSpeedThreshold = lowSpeedThreshold,
            lowSpeedEnabled = lowSpeedEnabled,
            turnSignalCamerasEnabled = turnSignalCamerasEnabled,
            triggerMode = triggerMode,
            turnSignalSide = turnSignalSide,
            steerSide = steerSide,
            activeSide = activeSide,
            activeIsLowSpeed = activeIsLowSpeed,
            isAvmActive = isAvmActive,
            isReverseActive = isReverseActive,
            isReverseGear = isReverseGear,
            invertSteerOnReverse = invertSteerOnReverse,
            isWidgetActive = isWidgetActive,
        )
    }

    private fun updateSteerSide(
        currentSteerSide: TurnSignalSide?,
        steerAngle: Float?,
        onAngle: Float = 30f,
        offAngle: Float = 20f,
    ): TurnSignalSide? {
        return updateSteerSideWithHold(currentSteerSide, steerAngle, onAngle, offAngle).first
    }

    private fun updateSteerSideWithHold(
        currentSteerSide: TurnSignalSide?,
        steerAngle: Float?,
        onAngle: Float = 30f,
        offAngle: Float = 20f,
    ): Pair<TurnSignalSide?, Boolean> {
        if (steerAngle == null || !steerAngle.isFinite()) return null to false
        return when {
            steerAngle >= onAngle -> TurnSignalSide.Left to false
            steerAngle <= -onAngle -> TurnSignalSide.Right to false
            currentSteerSide == TurnSignalSide.Left -> {
                if (steerAngle < offAngle) {
                    TurnSignalSide.Left to true
                } else {
                    TurnSignalSide.Left to false
                }
            }
            currentSteerSide == TurnSignalSide.Right -> {
                if (steerAngle > -offAngle) {
                    TurnSignalSide.Right to true
                } else {
                    TurnSignalSide.Right to false
                }
            }
            else -> null to false
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
    fun speedDropsToLow_whileBlindSpotCameraActive_preservesBlindSpotCamera() {
        // Blind spot camera is currently active on the left (!activeIsLowSpeed)
        // Speed drops from high to low (10 km/h < 20 km/h) while turn signal is still active
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Left,
            activeSide = TurnSignalSide.Left,
            activeIsLowSpeed = false,
        )
        assertEquals(TurnSignalSide.Left, side)
        // Must stay in blind-spot mode (false) and NOT switch to low-speed mode (true)
        assertFalse(isLow)
    }

    @Test
    fun turnSignalEndsAtLowSpeed_withSteeringWheelTurned_switchesToLowSpeedCamera() {
        // Previously blind spot was active, but now turn signal has turned off (null)
        // Driver is still steering at low speed (10 km/h)
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Left,
            activeSide = TurnSignalSide.Left,
            activeIsLowSpeed = false,
        )
        assertEquals(TurnSignalSide.Left, side)
        // Now it properly triggers low-speed camera by steering wheel
        assertTrue(isLow)
    }

    @Test
    fun turnSignalEndsAtLowSpeed_withoutSteering_closesCamera() {
        // Turn signal turned off and steering wheel is neutral
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = null,
            activeSide = TurnSignalSide.Left,
            activeIsLowSpeed = false,
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

        // Turn left >= 30 deg
        current = updateSteerSide(current, 30f)
        assertEquals(TurnSignalSide.Left, current)

        // Stays left while >= 20 deg
        current = updateSteerSide(current, 21f)
        assertEquals(TurnSignalSide.Left, current)

        // Turn right <= -30 deg
        current = updateSteerSide(current, -30f)
        assertEquals(TurnSignalSide.Right, current)

        // Stays right while <= -20 deg
        current = updateSteerSide(current, -21f)
        assertEquals(TurnSignalSide.Right, current)
    }

    @Test
    fun steeringAngle_holdDelayTriggersOnExitAngle() {
        var current: TurnSignalSide? = null

        // Turn left >= 30 deg
        val (side1, hold1) = updateSteerSideWithHold(current, 35f)
        assertEquals(TurnSignalSide.Left, side1)
        assertFalse(hold1)
        current = side1

        // Cross exit angle (< 20 deg) towards neutral: camera stays active with hold=true
        val (side2, hold2) = updateSteerSideWithHold(current, 15f)
        assertEquals(TurnSignalSide.Left, side2)
        assertTrue(hold2)

        // Return back beyond exit angle (>= 20 deg): hold becomes false, camera stays active
        val (side3, hold3) = updateSteerSideWithHold(current, 25f)
        assertEquals(TurnSignalSide.Left, side3)
        assertFalse(hold3)
    }

    @Test
    fun steeringAngle_customAnglesHysteresis() {
        var current: TurnSignalSide? = null
        val onAngle = 45f
        val offAngle = 25f

        // At 30 deg: does not activate yet because onAngle is 45 deg
        val (side, _) = updateSteerSideWithHold(current, 30f, onAngle = onAngle, offAngle = offAngle)
        assertNull(side)

        // At 45 deg: activates Left
        val res1 = updateSteerSideWithHold(current, 45f, onAngle = onAngle, offAngle = offAngle)
        assertEquals(TurnSignalSide.Left, res1.first)
        assertFalse(res1.second)
        current = res1.first

        // In between 25 and 45 deg: stays active
        val res2 = updateSteerSideWithHold(current, 30f, onAngle = onAngle, offAngle = offAngle)
        assertEquals(TurnSignalSide.Left, res2.first)
        assertFalse(res2.second)

        // Below 25 deg: enters 3s hold delay before deactivating
        val res3 = updateSteerSideWithHold(current, 24f, onAngle = onAngle, offAngle = offAngle)
        assertEquals(TurnSignalSide.Left, res3.first)
        assertTrue(res3.second)
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

    @Test
    fun avmActive_turnSignalDoesNotTriggerCamera() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 25f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = null,
            isAvmActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun avmActive_steeringWheelDoesNotTriggerCameraAtLowSpeed() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Right,
            isAvmActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun avmBecomesActive_activeBlindSpotCameraCloses() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 30f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = null,
            activeSide = TurnSignalSide.Left,
            activeIsLowSpeed = false,
            isAvmActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun avmBecomesActive_activeLowSpeedCameraCloses() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 10f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Right,
            steerSide = TurnSignalSide.Right,
            activeSide = TurnSignalSide.Right,
            activeIsLowSpeed = true,
            isAvmActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun reverseCamerasTarget_whenEnabledAndRGear_activates() {
        val target = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = false,
            activeGear = 'R',
            speed = 0f,
            enabled = true,
        )
        assertTrue(target)
    }

    @Test
    fun reverseCamerasTarget_whenPGear_closesImmediately() {
        val target = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = true,
            activeGear = 'P',
            speed = 0f,
            enabled = true,
        )
        assertFalse(target)
    }

    @Test
    fun reverseCamerasTarget_whenDGear_staysActiveBelow15Kmh() {
        val targetLow = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = true,
            activeGear = 'D',
            speed = 10f,
            enabled = true,
        )
        assertTrue(targetLow)

        val targetBorder = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = true,
            activeGear = 'D',
            speed = 14.9f,
            enabled = true,
        )
        assertTrue(targetBorder)
    }

    @Test
    fun reverseCamerasTarget_whenDGear_closesAtOrAbove15Kmh() {
        val target15 = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = true,
            activeGear = 'D',
            speed = 15.0f,
            enabled = true,
        )
        assertFalse(target15)

        val targetFast = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = true,
            activeGear = 'D',
            speed = 25f,
            enabled = true,
        )
        assertFalse(targetFast)
    }

    @Test
    fun reverseCamerasTarget_whenDisabled_neverActivates() {
        val target = CamdCameraRepository.resolveReverseCamerasTarget(
            currentTriggered = false,
            activeGear = 'R',
            speed = 0f,
            enabled = false,
        )
        assertFalse(target)
    }

    @Test
    fun reverseActive_suppressesSideCameras() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Left,
            isReverseActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun reverseGear_whenSteeringLeft_showsRightCamera() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Left,
            isReverseGear = true,
            invertSteerOnReverse = true,
        )
        // Steering left on R gear with inversion -> Right camera
        assertEquals(TurnSignalSide.Right, side)
        assertTrue(isLow)
    }

    @Test
    fun reverseGear_whenSteeringRight_showsLeftCamera() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Right,
            isReverseGear = true,
            invertSteerOnReverse = true,
        )
        // Steering right on R gear with inversion -> Left camera
        assertEquals(TurnSignalSide.Left, side)
        assertTrue(isLow)
    }

    @Test
    fun forwardGear_whenSteeringLeft_showsLeftCamera() {
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Left,
            isReverseGear = false,
            invertSteerOnReverse = true,
        )
        // In forward gear (not R), inversion does not apply -> Left camera
        assertEquals(TurnSignalSide.Left, side)
        assertTrue(isLow)
    }

    @Test
    fun hasPhysicalPdcObstacle_detectsFrontAndRearObstacles() {
        // Front sensor detection
        val frontObstacle = LauncherPdcZones(frontLeftCm = 45)
        assertTrue(hasPhysicalPdcObstacle(frontObstacle))

        // Rear sensor detection
        val rearObstacle = LauncherPdcZones(rearMidRightCm = 30)
        assertTrue(hasPhysicalPdcObstacle(rearObstacle))

        // Boundary distances: 10 cm and 150 cm are within valid range
        val minObstacle = LauncherPdcZones(frontRightCm = 10)
        assertTrue(hasPhysicalPdcObstacle(minObstacle))

        val maxObstacle = LauncherPdcZones(rearLeftCm = 150)
        assertTrue(hasPhysicalPdcObstacle(maxObstacle))

        // Distance < 10 cm is transient/invalid error code, distance > 150 cm is free space
        val invalidLow = LauncherPdcZones(frontLeftCm = 5)
        assertFalse(hasPhysicalPdcObstacle(invalidLow))

        val freeSpace = LauncherPdcZones(rearMidLeftCm = 151)
        assertFalse(hasPhysicalPdcObstacle(freeSpace))

        // No obstacles (all 0)
        val clearZones = LauncherPdcZones()
        assertFalse(hasPhysicalPdcObstacle(clearZones))

        // Side-only sensors (FrontSideLeft, RearSideRight) are not in physical front/rear channels
        val sideOnlyObstacle = LauncherPdcZones(frontSideLeftCm = 40, rearSideRightCm = 40)
        assertFalse(hasPhysicalPdcObstacle(sideOnlyObstacle))
    }

    @Test
    fun resolvePdcCamerasState_whenDisabled_neverActivates() {
        val state = CamdCameraRepository.resolvePdcCamerasState(
            currentPdcTriggered = false,
            hasObstacle = true,
            activeGear = 'D',
            enabled = false,
        )
        assertFalse(state.pdcTriggered)
        assertFalse(state.shouldScheduleTurnOff)
        assertTrue(state.cancelTurnOff)
    }

    @Test
    fun resolvePdcCamerasState_whenObstacleDetected_activatesImmediatelyAndCancelsTurnOff() {
        val state = CamdCameraRepository.resolvePdcCamerasState(
            currentPdcTriggered = false,
            hasObstacle = true,
            activeGear = 'D',
            enabled = true,
        )
        assertTrue(state.pdcTriggered)
        assertFalse(state.shouldScheduleTurnOff)
        assertTrue(state.cancelTurnOff)
    }

    @Test
    fun resolvePdcCamerasState_whenObstacleCeasesInForwardGear_schedulesTurnOff() {
        val state = CamdCameraRepository.resolvePdcCamerasState(
            currentPdcTriggered = true,
            hasObstacle = false,
            activeGear = 'D',
            enabled = true,
        )
        assertTrue(state.pdcTriggered)
        assertTrue(state.shouldScheduleTurnOff)
        assertFalse(state.cancelTurnOff)
    }

    @Test
    fun resolvePdcCamerasState_whenObstacleCeasesInReverseGear_holdsOpenAndCancelsTurnOff() {
        // If gear is R when detection ceases (or shifted to R), cameras do not turn off after 3s
        val state = CamdCameraRepository.resolvePdcCamerasState(
            currentPdcTriggered = true,
            hasObstacle = false,
            activeGear = 'R',
            enabled = true,
        )
        assertTrue(state.pdcTriggered)
        assertFalse(state.shouldScheduleTurnOff)
        assertTrue(state.cancelTurnOff)
    }

    @Test
    fun resolvePdcCamerasState_whenClearedAndNoObstacle_remainsOff() {
        val state = CamdCameraRepository.resolvePdcCamerasState(
            currentPdcTriggered = false,
            hasObstacle = false,
            activeGear = 'D',
            enabled = true,
        )
        assertFalse(state.pdcTriggered)
        assertFalse(state.shouldScheduleTurnOff)
        assertFalse(state.cancelTurnOff)
    }

    @Test
    fun widgetActive_suppressesTurnSignalCameras() {
        // Turn signal is active at low speed, but widget cameras are active
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = null,
            isWidgetActive = true,
        )
        assertNull(side)
        assertFalse(isLow)

        // Turn signal is active at high speed (blind spot mode), but widget cameras are active
        val (sideHigh, isLowHigh) = resolveTargetSideCamera(
            speed = 60f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Right,
            steerSide = null,
            isWidgetActive = true,
        )
        assertNull(sideHigh)
        assertFalse(isLowHigh)
    }

    @Test
    fun widgetActive_suppressesLowSpeedSteeringCameras() {
        // Steering wheel turned past threshold at low speed, but widget cameras are active
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Left,
            isWidgetActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun widgetActive_suppressesBothTurnSignalAndSteeringCameras() {
        // Both steering and turn signal active, but widget cameras are active
        val (side, isLow) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = TurnSignalSide.Right,
            isWidgetActive = true,
        )
        assertNull(side)
        assertFalse(isLow)
    }

    @Test
    fun widgetInactive_allowsTurnSignalAndSteerCameras() {
        // When widget is inactive (false), normal camera activation works
        val (sideTurn, isLowTurn) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = true,
            triggerMode = "both",
            turnSignalSide = TurnSignalSide.Left,
            steerSide = null,
            isWidgetActive = false,
        )
        assertEquals(TurnSignalSide.Left, sideTurn)
        assertTrue(isLowTurn)

        val (sideSteer, isLowSteer) = resolveTargetSideCamera(
            speed = 5f,
            lowSpeedThreshold = 20f,
            lowSpeedEnabled = true,
            turnSignalCamerasEnabled = false,
            triggerMode = "both",
            turnSignalSide = null,
            steerSide = TurnSignalSide.Right,
            isWidgetActive = false,
        )
        assertEquals(TurnSignalSide.Right, sideSteer)
        assertTrue(isLowSteer)
    }

    @Test
    fun shouldBlinkCameraWindowBorder_conditions() {
        // Disabled when Camd is not installed
        assertFalse(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = false,
                turnSignalCameras = true,
                shapeCircle = true,
                rectCorners = false,
            )
        )

        // Disabled when turn signal cameras are disabled in settings
        assertFalse(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = true,
                turnSignalCameras = false,
                shapeCircle = true,
                rectCorners = false,
            )
        )

        // Disabled when neither circle nor rectangle in corners is selected (e.g. full ADAS panel)
        assertFalse(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = true,
                turnSignalCameras = true,
                shapeCircle = false,
                rectCorners = false,
            )
        )

        // Enabled for circular camera windows
        assertTrue(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = true,
                turnSignalCameras = true,
                shapeCircle = true,
                rectCorners = false,
            )
        )

        // Enabled for rectangular camera windows in corners
        assertTrue(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = true,
                turnSignalCameras = true,
                shapeCircle = false,
                rectCorners = true,
            )
        )

        // Enabled when both shape options are selected (rectCorners takes precedence in geometry, but condition is true)
        assertTrue(
            CamdCameraRepository.shouldBlinkCameraWindowBorder(
                isCamdInstalled = true,
                turnSignalCameras = true,
                shapeCircle = true,
                rectCorners = true,
            )
        )
    }

    @Test
    fun computeSideCameraWindowBounds_circleGeometry() {
        val desktopLeft = 344f
        val desktopTop = 51f
        val desktopRight = 1920f
        val desktopBottom = 998f
        val density = 1.0f

        val leftCircle = CamdCameraRepository.computeSideCameraWindowBounds(
            desktopLeft = desktopLeft,
            desktopTop = desktopTop,
            desktopRight = desktopRight,
            desktopBottom = desktopBottom,
            density = density,
            isRightSide = false,
            shapeCircle = true,
            rectCorners = false,
            sizeAdjustment = 0,
        )
        assertTrue(leftCircle != null)
        assertTrue(leftCircle!!.isCircle)
        assertEquals(desktopLeft + 30f, leftCircle.xPx, 0.01f)
        assertEquals(desktopTop + 30f, leftCircle.yPx, 0.01f)
        assertEquals(leftCircle.widthPx, leftCircle.heightPx, 0.01f)

        val rightCircle = CamdCameraRepository.computeSideCameraWindowBounds(
            desktopLeft = desktopLeft,
            desktopTop = desktopTop,
            desktopRight = desktopRight,
            desktopBottom = desktopBottom,
            density = density,
            isRightSide = true,
            shapeCircle = true,
            rectCorners = false,
            sizeAdjustment = 0,
        )
        assertTrue(rightCircle != null)
        assertTrue(rightCircle!!.isCircle)
        assertEquals(desktopRight - 30f - rightCircle.widthPx, rightCircle.xPx, 0.01f)
        assertEquals(desktopTop + 30f, rightCircle.yPx, 0.01f)
        assertEquals(leftCircle.widthPx, rightCircle.widthPx, 0.01f)
    }

    @Test
    fun computeSideCameraWindowBounds_rectCornersGeometry() {
        val desktopLeft = 344f
        val desktopTop = 51f
        val desktopRight = 1920f
        val desktopBottom = 998f
        val density = 1.0f

        val leftRect = CamdCameraRepository.computeSideCameraWindowBounds(
            desktopLeft = desktopLeft,
            desktopTop = desktopTop,
            desktopRight = desktopRight,
            desktopBottom = desktopBottom,
            density = density,
            isRightSide = false,
            shapeCircle = true,
            rectCorners = true, // takes precedence over circle
            sizeAdjustment = 0,
        )
        assertTrue(leftRect != null)
        assertFalse(leftRect!!.isCircle)
        assertEquals(desktopLeft + 30f, leftRect.xPx, 0.01f)
        assertEquals(desktopTop + 30f, leftRect.yPx, 0.01f)
        // 4:3 aspect ratio (height = width * 0.75)
        assertEquals(leftRect.widthPx * 0.75f, leftRect.heightPx, 0.01f)

        val rightRect = CamdCameraRepository.computeSideCameraWindowBounds(
            desktopLeft = desktopLeft,
            desktopTop = desktopTop,
            desktopRight = desktopRight,
            desktopBottom = desktopBottom,
            density = density,
            isRightSide = true,
            shapeCircle = false,
            rectCorners = true,
            sizeAdjustment = 0,
        )
        assertTrue(rightRect != null)
        assertFalse(rightRect!!.isCircle)
        assertEquals(desktopRight - 30f - rightRect.widthPx, rightRect.xPx, 0.01f)
        assertEquals(desktopTop + 30f, rightRect.yPx, 0.01f)
        assertEquals(leftRect.widthPx, rightRect.widthPx, 0.01f)
        assertEquals(leftRect.heightPx, rightRect.heightPx, 0.01f)
    }

    @Test
    fun computeSideCameraWindowBounds_returnsNullWhenDisabled() {
        val bounds = CamdCameraRepository.computeSideCameraWindowBounds(
            desktopLeft = 344f,
            desktopTop = 51f,
            desktopRight = 1920f,
            desktopBottom = 998f,
            density = 1.0f,
            isRightSide = false,
            shapeCircle = false,
            rectCorners = false,
        )
        assertNull(bounds)
    }
}
