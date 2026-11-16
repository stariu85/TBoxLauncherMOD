package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF

class LauncherHeadlightBeamsTest {

    @Test
    fun simulationModeUsesSimulationState() {
        val beamsOn = resolveHeadlightBeams(
            simulateEnabled = true,
            simLowBeam = true,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_OFF,
            headlightsSwitch = 0,
            bcmLowBeam = false,
            bcmHighBeam = false,
        )
        assertTrue(beamsOn.lowBeam)

        val beamsOff = resolveHeadlightBeams(
            simulateEnabled = true,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_LOW_BEAM,
            headlightsSwitch = 3,
            bcmLowBeam = true,
            bcmHighBeam = false,
        )
        assertFalse(beamsOff.lowBeam)
    }

    @Test
    fun bcmLowBeamEnablesLowBeam() {
        val bcmLowOn = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_AUTO,
            headlightsSwitch = 0,
            bcmLowBeam = true,
            bcmHighBeam = false,
        )
        assertTrue(bcmLowOn.lowBeam)

        val bcmLowOff = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_AUTO,
            headlightsSwitch = 0,
            bcmLowBeam = false,
            bcmHighBeam = false,
        )
        assertFalse(bcmLowOff.lowBeam)
    }

    @Test
    fun lowBeamModeForcesLowBeam() {
        val beams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_LOW_BEAM,
            headlightsSwitch = 0,
            bcmLowBeam = false,
            bcmHighBeam = false,
        )
        assertTrue(beams.lowBeam)
    }

    @Test
    fun bcmHighBeamForcesHighBeamAndLowBeam() {
        val beams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_OFF,
            headlightsSwitch = 0,
            bcmLowBeam = false,
            bcmHighBeam = true,
        )
        assertTrue(beams.lowBeam)
        assertTrue(beams.highBeam)
    }

    @Test
    fun isHighBeamIndicatedInAdasManualMode() {
        assertFalse(isHighBeamIndicatedInAdas(isHmaOn = false, isHighBeamBurning = false))
        assertTrue(isHighBeamIndicatedInAdas(isHmaOn = false, isHighBeamBurning = true))
    }

    @Test
    fun isHighBeamIndicatedInAdasAutoHmaMode() {
        // In Auto mode, HMA on standby (green icon, low beam) does NOT indicate high beam
        assertFalse(isHighBeamIndicatedInAdas(isHmaOn = true, isHighBeamBurning = false))
        // When Auto HMA physically triggers high beam (icon turns blue), high beam is indicated
        assertTrue(isHighBeamIndicatedInAdas(isHmaOn = true, isHighBeamBurning = true))
    }

    @Test
    fun isHighBeamIndicatedInAdasSimulationShowAll() {
        assertTrue(isHighBeamIndicatedInAdas(isHmaOn = false, isHighBeamBurning = false, showAll = true))
    }
}
