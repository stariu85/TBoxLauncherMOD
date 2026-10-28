package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION

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
    fun physicalOffOrPositionOverridesBcmLowBeam() {
        val offBeams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_OFF,
            headlightsSwitch = 0,
            bcmLowBeam = true, // BCM incorrectly reports low beam (e.g. DRL active)
            bcmHighBeam = false,
        )
        assertFalse(offBeams.lowBeam)

        val posBeams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_POSITION,
            headlightsSwitch = 0,
            bcmLowBeam = true,
            bcmHighBeam = false,
        )
        assertFalse(posBeams.lowBeam)
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
    fun autoModeDoesNotLightRoad() {
        val autoOnBeams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_AUTO,
            headlightsSwitch = 0,
            bcmLowBeam = true, // BCM incorrectly reports low beam when DRL is active
            bcmHighBeam = false,
        )
        assertFalse(autoOnBeams.lowBeam)

        val autoOffBeams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_AUTO,
            headlightsSwitch = 0,
            bcmLowBeam = false,
            bcmHighBeam = false,
        )
        assertFalse(autoOffBeams.lowBeam)
    }

    @Test
    fun highBeamForcesLowBeam() {
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

        val stalkBeams = resolveHeadlightBeams(
            simulateEnabled = false,
            simLowBeam = false,
            simHighBeam = false,
            lightControlRaw = LIGHT_CONTROL_OFF,
            headlightsSwitch = 3,
            bcmLowBeam = false,
            bcmHighBeam = false,
        )
        assertTrue(stalkBeams.lowBeam)
        assertTrue(stalkBeams.highBeam)
    }
}
