package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDriveViewConditionTest {

    @Test
    fun topViewByDefaultInDriveAndParkModeOnPark() {
        assertFalse(isDriveViewActive(speedKmh = 0f, pasOn = false, isParkGear = true, isDriveGear = false, accActive = false, cameraSwitchMode = CAMERA_SWITCH_MODE_DRIVE_AND_PARK))
    }

    @Test
    fun driveViewInDriveAndParkModeWhenInDriveGear() {
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = true, isParkGear = false, isDriveGear = true, accActive = false, cameraSwitchMode = CAMERA_SWITCH_MODE_DRIVE_AND_PARK))
        assertTrue(isDriveViewActive(speedKmh = 10f, pasOn = true, isParkGear = false, isDriveGear = true, accActive = false, cameraSwitchMode = CAMERA_SWITCH_MODE_DRIVE_AND_PARK))
    }

    @Test
    fun topViewInSmartModeWhenPasOn() {
        assertFalse(isDriveViewActive(speedKmh = 5f, pasOn = true, isParkGear = false, isDriveGear = true, accActive = false, cameraSwitchMode = CAMERA_SWITCH_MODE_PAS_AND_PARK))
    }

    @Test
    fun driveViewWhenMovingAtSpeed() {
        assertTrue(isDriveViewActive(speedKmh = 20f, pasOn = true, isParkGear = false, isDriveGear = true, accActive = false))
    }

    @Test
    fun driveViewWhenAccActiveEvenAtZeroSpeedNotInPark() {
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = true, isParkGear = false, isDriveGear = false, accActive = true))
    }

    @Test
    fun driveViewWhenRacingActiveNotInPark() {
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = true, isParkGear = false, isDriveGear = false, accActive = false, racing = true))
    }
}
