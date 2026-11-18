package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoreIdleFuelTest {

    @Test
    fun testIsEngineRunning() {
        CanDataRepository.updateEngineRPM(0f)
        assertFalse(CanDataRepository.isEngineRunning())

        CanDataRepository.updateEngineRPM(200f)
        assertFalse(CanDataRepository.isEngineRunning())

        CanDataRepository.updateEngineRPM(750f)
        assertTrue(CanDataRepository.isEngineRunning())

        CanDataRepository.updateEngineRPM(0f)
        assertFalse(CanDataRepository.isEngineRunning())
    }

    @Test
    fun testIsVehicleStationary() {
        CanDataRepository.updateCarSpeed(0f)
        CanDataRepository.updateCarSpeedAccurate(0f)
        assertTrue(CanDataRepository.isVehicleStationary())

        CanDataRepository.updateCarSpeed(0.5f)
        CanDataRepository.updateCarSpeedAccurate(0.5f)
        assertTrue(CanDataRepository.isVehicleStationary())

        CanDataRepository.updateCarSpeed(10f)
        CanDataRepository.updateCarSpeedAccurate(10f)
        assertFalse(CanDataRepository.isVehicleStationary())

        CanDataRepository.updateCarSpeed(0f)
        CanDataRepository.updateCarSpeedAccurate(0f)
        assertTrue(CanDataRepository.isVehicleStationary())
    }
}
