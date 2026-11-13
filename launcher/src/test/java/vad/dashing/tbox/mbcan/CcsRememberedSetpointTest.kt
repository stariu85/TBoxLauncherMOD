package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcsRememberedSetpointTest {

    @Test
    fun decideHardkey_standbyResKeepsRemembered() {
        val decision = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
            ccsStatus = 2, // Standby
        )
        assertTrue(decision is CcsRememberedSetpoint.HardkeySetpointDecision.KeepRemembered)
    }

    @Test
    fun decideHardkey_standbySetCapturesSpeed() {
        val decision = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
            ccsStatus = 2, // Standby
        )
        assertTrue(decision is CcsRememberedSetpoint.HardkeySetpointDecision.CaptureSpeed)
    }

    @Test
    fun decideHardkey_activeNudgesSetpoint() {
        val res = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
            ccsStatus = 1, // Active
        )
        assertTrue(res is CcsRememberedSetpoint.HardkeySetpointDecision.Nudge)
        assertEquals(1, (res as CcsRememberedSetpoint.HardkeySetpointDecision.Nudge).delta)

        val set = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
            ccsStatus = 1, // Active
        )
        assertTrue(set is CcsRememberedSetpoint.HardkeySetpointDecision.Nudge)
        assertEquals(-1, (set as CcsRememberedSetpoint.HardkeySetpointDecision.Nudge).delta)
    }

    @Test
    fun decideHardkey_offIgnoresKeys() {
        val res = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
            ccsStatus = 0, // Off
        )
        assertTrue(res is CcsRememberedSetpoint.HardkeySetpointDecision.Ignore)

        val set = CcsRememberedSetpoint.decideHardkey(
            key = CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
            ccsStatus = 0, // Off
        )
        assertTrue(set is CcsRememberedSetpoint.HardkeySetpointDecision.Ignore)
    }

    @Test
    fun parseHardkeyCruiseKey() {
        assertEquals(
            CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
            CcsRememberedSetpoint.parseHardkeyCruiseKey(29),
        )
        assertEquals(
            CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
            CcsRememberedSetpoint.parseHardkeyCruiseKey(30),
        )
        assertNull(CcsRememberedSetpoint.parseHardkeyCruiseKey(28))
        assertNull(CcsRememberedSetpoint.parseHardkeyCruiseKey(103))
    }

    @Test
    fun decideStalkEnterActive_matchesWithinThreshold() {
        // Empty remembered -> capture
        val decisionNull = CcsRememberedSetpoint.decideStalkEnterActive(
            rememberedKmh = null,
            vehicleSpeedKmh = 90.2f,
        )
        assertTrue(decisionNull is CcsRememberedSetpoint.StalkEnterActiveDecision.Capture)
        assertEquals(90, (decisionNull as CcsRememberedSetpoint.StalkEnterActiveDecision.Capture).kmh)

        // Close to remembered -> keep
        val decisionClose = CcsRememberedSetpoint.decideStalkEnterActive(
            rememberedKmh = 90,
            vehicleSpeedKmh = 91.4f,
        )
        assertTrue(decisionClose is CcsRememberedSetpoint.StalkEnterActiveDecision.Keep)

        // Far from remembered -> new SET capture
        val decisionFar = CcsRememberedSetpoint.decideStalkEnterActive(
            rememberedKmh = 90,
            vehicleSpeedKmh = 110f,
        )
        assertTrue(decisionFar is CcsRememberedSetpoint.StalkEnterActiveDecision.Capture)
        assertEquals(110, (decisionFar as CcsRememberedSetpoint.StalkEnterActiveDecision.Capture).kmh)
    }

    @Test
    fun shouldReconcileStableSpeed_requiresDurationAndDifference() {
        // Less than 2 seconds -> false
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 100,
                stableForMs = 1500L,
                withinOurPulse = false,
            ),
        )

        // Within our pulse -> false
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 100,
                stableForMs = 2500L,
                withinOurPulse = true,
            ),
        )

        // Difference < 2 -> false
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 91,
                stableForMs = 2500L,
                withinOurPulse = false,
            ),
        )

        // >= 2000 ms, diff >= 2, not within pulse -> true
        assertTrue(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 100,
                stableForMs = 2500L,
                withinOurPulse = false,
            ),
        )
    }
}
