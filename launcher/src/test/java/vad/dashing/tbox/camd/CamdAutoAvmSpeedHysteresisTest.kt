package vad.dashing.tbox.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.roundToInt

class CamdAutoAvmSpeedHysteresisTest {

    private fun resolveTargetHoldOff(
        speed: Float?,
        activeGear: Char? = null,
        onSpeed: Int = 2,
        offSpeed: Int = 20,
    ): Boolean? {
        return CamdCameraRepository.resolveAutoAvmHoldOff(
            speed = speed,
            activeGear = activeGear,
            onSpeed = onSpeed,
            offSpeed = offSpeed,
        )
    }

    private fun clampAndAdjustSliderRange(
        rawStart: Float,
        rawEnd: Float,
        currentOn: Int,
        currentOff: Int,
    ): Pair<Int, Int> {
        var newOn = rawStart.roundToInt().coerceIn(0, 29)
        var newOff = rawEnd.roundToInt().coerceIn(1, 30)
        if (newOff - newOn < 1) {
            if (newOn != currentOn) {
                newOn = (newOff - 1).coerceAtLeast(0)
            } else {
                newOff = (newOn + 1).coerceAtMost(30)
            }
        }
        if (newOff <= newOn) {
            if (newOn < 30) newOff = newOn + 1 else newOn = newOff - 1
        }
        return newOn to newOff
    }

    @Test
    fun targetHoldOff_defaultThresholds() {
        assertEquals(true, resolveTargetHoldOff(20.1f))
        assertEquals(false, resolveTargetHoldOff(1.9f))
        assertNull(resolveTargetHoldOff(10f))
    }

    @Test
    fun targetHoldOff_customThresholds() {
        // Custom: activate below 5 km/h, deactivate above 15 km/h
        assertEquals(true, resolveTargetHoldOff(15.1f, onSpeed = 5, offSpeed = 15))
        assertEquals(false, resolveTargetHoldOff(4.9f, onSpeed = 5, offSpeed = 15))
        assertNull(resolveTargetHoldOff(10f, onSpeed = 5, offSpeed = 15))
        assertNull(resolveTargetHoldOff(5.0f, onSpeed = 5, offSpeed = 15))
        assertNull(resolveTargetHoldOff(15.0f, onSpeed = 5, offSpeed = 15))
    }

    @Test
    fun targetHoldOff_reverseOnlyMode() {
        // onSpeed = 0 means "R" mode: AVM is enabled only in R, otherwise disabled
        // In R gear: holdOff = false (enable OEM AVM)
        assertEquals(false, resolveTargetHoldOff(speed = 0f, activeGear = 'R', onSpeed = 0, offSpeed = 20))
        assertEquals(false, resolveTargetHoldOff(speed = 15f, activeGear = 'R', onSpeed = 0, offSpeed = 20))

        // In other gears (P, D, N, etc.): holdOff = true (disable OEM AVM)
        assertEquals(true, resolveTargetHoldOff(speed = 0f, activeGear = 'P', onSpeed = 0, offSpeed = 20))
        assertEquals(true, resolveTargetHoldOff(speed = 0f, activeGear = 'D', onSpeed = 0, offSpeed = 20))
        assertEquals(true, resolveTargetHoldOff(speed = 5f, activeGear = 'D', onSpeed = 0, offSpeed = 20))
        assertEquals(true, resolveTargetHoldOff(speed = 0f, activeGear = 'N', onSpeed = 0, offSpeed = 20))
        assertEquals(true, resolveTargetHoldOff(speed = 0f, activeGear = null, onSpeed = 0, offSpeed = 20))
    }

    @Test
    fun sliderRange_minimumDifferenceEnforced() {
        // Attempting to move left thumb onto right thumb (right at 20, drag left to 20)
        val (on1, off1) = clampAndAdjustSliderRange(20f, 20f, currentOn = 2, currentOff = 20)
        assertEquals(19, on1)
        assertEquals(20, off1)
        assertEquals(1, off1 - on1)

        // Attempting to move right thumb onto left thumb (left at 5, drag right to 5)
        val (on2, off2) = clampAndAdjustSliderRange(5f, 5f, currentOn = 5, currentOff = 20)
        assertEquals(5, on2)
        assertEquals(6, off2)
        assertEquals(1, off2 - on2)
    }

    @Test
    fun sliderRange_boundsClampedWithin0And30() {
        // Extreme left / right
        val (onLow, offLow) = clampAndAdjustSliderRange(-5f, 0f, currentOn = 2, currentOff = 20)
        assertEquals(0, onLow)
        assertEquals(1, offLow)

        val (onHigh, offHigh) = clampAndAdjustSliderRange(35f, 40f, currentOn = 2, currentOff = 20)
        assertEquals(29, onHigh)
        assertEquals(30, offHigh)
    }

    @Test
    fun sliderRange_leftCanBeZeroForReverse() {
        val (onR, off) = clampAndAdjustSliderRange(0f, 15f, currentOn = 2, currentOff = 15)
        assertEquals(0, onR)
        assertEquals(15, off)
    }

    @Test
    fun sliderRange_independentMovement() {
        // Moving left thumb from 2 to 7 (right thumb at 20)
        val (on1, off1) = clampAndAdjustSliderRange(7f, 20f, currentOn = 2, currentOff = 20)
        assertEquals(7, on1)
        assertEquals(20, off1)

        // Moving right thumb from 20 to 12 (left thumb at 7)
        val (on2, off2) = clampAndAdjustSliderRange(7f, 12f, currentOn = 7, currentOff = 20)
        assertEquals(7, on2)
        assertEquals(12, off2)
    }

    @Test
    fun targetHoldOff_invalidValues_returnsNull() {
        assertNull(resolveTargetHoldOff(null))
        assertNull(resolveTargetHoldOff(-1f))
        assertNull(resolveTargetHoldOff(Float.NaN))
        assertNull(resolveTargetHoldOff(Float.POSITIVE_INFINITY))
    }

    @Test
    fun hysteresisSimulation_withCustomThresholds() {
        var currentHoldOff: Boolean? = false // Initially at rest: AVM is active
        val onSpeed = 10
        val offSpeed = 25

        val speedSequence = listOf(
            0f to false,     // stationary -> holdOff false
            9.9f to false,   // < 10 km/h -> stays false
            15.0f to false,  // accelerating -> stays false
            25.0f to false,  // boundary -> stays false
            25.1f to true,   // > 25 km/h -> turns off AVM (holdOff true)
            40.0f to true,   // fast driving -> stays true
            20.0f to true,   // decelerating -> stays true
            10.1f to true,   // decelerating in hysteresis -> stays true!
            10.0f to true,   // boundary -> stays true!
            9.5f to false,   // < 10 km/h -> turns on AVM (holdOff false)
            0.0f to false,   // stopped -> stays false
        )

        for ((speed, expectedHoldOff) in speedSequence) {
            val target = resolveTargetHoldOff(speed, onSpeed = onSpeed, offSpeed = offSpeed)
            if (target != null) {
                currentHoldOff = target
            }
            assertEquals("At speed $speed km/h", expectedHoldOff, currentHoldOff)
        }
    }
}
