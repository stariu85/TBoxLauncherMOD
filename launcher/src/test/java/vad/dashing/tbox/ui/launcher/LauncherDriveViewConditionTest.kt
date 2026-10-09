package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDriveViewConditionTest {

    @Test
    fun topViewByDefaultOnStartup() {
        assertFalse(isDriveViewActive(speedKmh = 0f, pasOn = false, isParkGear = false, accActive = false))
    }

    @Test
    fun topViewWhenInPark() {
        assertFalse(isDriveViewActive(speedKmh = 0f, pasOn = false, isParkGear = true, accActive = false))
        assertFalse(isDriveViewActive(speedKmh = 0f, pasOn = false, isParkGear = true, accActive = true))
    }

    @Test
    fun topViewWhenPasOn() {
        assertFalse(isDriveViewActive(speedKmh = 5f, pasOn = true, isParkGear = false, accActive = false))
    }

    @Test
    fun driveViewWhenMovingAboveThresholdWithoutPas() {
        assertTrue(isDriveViewActive(speedKmh = 5f, pasOn = false, isParkGear = false, accActive = false))
        assertTrue(isDriveViewActive(speedKmh = 20f, pasOn = false, isParkGear = false, accActive = false))
    }

    @Test
    fun driveViewWhenAccActiveEvenAtZeroSpeedNotInPark() {
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = false, isParkGear = false, accActive = true))
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = true, isParkGear = false, accActive = true))
    }

    @Test
    fun driveViewWhenRacingActiveNotInPark() {
        assertTrue(isDriveViewActive(speedKmh = 0f, pasOn = true, isParkGear = false, accActive = false, racing = true))
    }
}
