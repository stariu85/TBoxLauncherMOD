package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveModeDisplayLabelTest {

    @Test
    fun test6DctPrioritizesWetAndCanDriveModeOverStandard() {
        // On 6DCT, driveModeWetRaw = 1 is ECO, driveModeRaw = 0 is NOR.
        // When is6DCT is true, it must return "ECO" instead of "NOR".
        val label = resolveDriveModeDisplayLabel(
            driveModeRaw = 0,
            driveModeWetRaw = 1,
            gearBoxDriveMode = "ECO",
            is6DCT = true,
        )
        assertEquals("ECO", label)
    }

    @Test
    fun test7DctPrioritizesStandardDriveMode() {
        // On 7DCT (non-6DCT), driveModeRaw = 2 is ECO.
        // When is6DCT is false, it must return "ECO" based on driveModeRaw.
        val label = resolveDriveModeDisplayLabel(
            driveModeRaw = 2,
            driveModeWetRaw = 0,
            gearBoxDriveMode = "SPT",
            is6DCT = false,
        )
        assertEquals("ECO", label)
    }

    @Test
    fun test6DctFallsBackToCanGearBoxDriveModeText() {
        val label = resolveDriveModeDisplayLabel(
            driveModeRaw = 0,
            driveModeWetRaw = null,
            gearBoxDriveMode = "SPT",
            is6DCT = true,
        )
        assertEquals("SPT", label)
    }
}
