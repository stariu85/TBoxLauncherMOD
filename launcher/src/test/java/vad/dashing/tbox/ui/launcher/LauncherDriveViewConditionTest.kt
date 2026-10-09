package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDriveViewConditionTest {

    @Test
    fun topViewWhenInParkInAnyCase() {
        // Park gear ALWAYS forces Top View, even if cruise control is reported on
        assertFalse(isDriveViewActive(pasOn = false, isParkGear = true, cruiseOn = false))
        assertFalse(isDriveViewActive(pasOn = true, isParkGear = true, cruiseOn = false))
        assertFalse(isDriveViewActive(pasOn = false, isParkGear = true, cruiseOn = true))
        assertFalse(isDriveViewActive(pasOn = true, isParkGear = true, cruiseOn = true))
    }

    @Test
    fun topViewWhenPasOnAndCruiseOff() {
        assertFalse(isDriveViewActive(pasOn = true, isParkGear = false, cruiseOn = false))
    }

    @Test
    fun driveViewWhenPasOffAndNotInPark() {
        assertTrue(isDriveViewActive(pasOn = false, isParkGear = false, cruiseOn = false))
    }

    @Test
    fun driveViewWhenCruiseOnNotInPark() {
        // Cruise control forces Drive View when not in Park, even if PAS turns on
        assertTrue(isDriveViewActive(pasOn = true, isParkGear = false, cruiseOn = true))
        assertTrue(isDriveViewActive(pasOn = false, isParkGear = false, cruiseOn = true))
    }

    @Test
    fun driveViewWhenRacingActiveNotInPark() {
        assertTrue(isDriveViewActive(pasOn = true, isParkGear = false, cruiseOn = false, racing = true))
    }
}
