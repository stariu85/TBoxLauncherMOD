package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test

class MbCanSignalStateEngineTest {

    @Test
    fun resolveHvacFrontDefrostMbCanToggleTarget_alwaysEnablesDefrostOnlyWhenOff() {
        // When defrost is off, pressing the button must always target HVAC_FAN_DIRECTION_DEFROST (4)
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
            MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
            )
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
            MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE
            )
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
            MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT
            )
        )
    }

    @Test
    fun resolveHvacFrontDefrostMbCanToggleTarget_disablesDefrostWhenOn() {
        // When defrost is on, pressing the button turns it off
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
            MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST
            )
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
            MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT
            )
        )
    }
}
