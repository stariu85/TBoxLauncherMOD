package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDriveViewConditionTest {

    @Test
    fun topViewWhenBelow15KmhAndCruiseOff() {
        assertFalse(isDriveViewActive(speedKmh = 0f, cruiseOn = false))
        assertFalse(isDriveViewActive(speedKmh = 5f, cruiseOn = false))
        assertFalse(isDriveViewActive(speedKmh = 14.9f, cruiseOn = false))
    }

    @Test
    fun driveViewWhenAtOrAbove15KmhAndCruiseOff() {
        assertTrue(isDriveViewActive(speedKmh = 15.0f, cruiseOn = false))
        assertTrue(isDriveViewActive(speedKmh = 20.0f, cruiseOn = false))
        assertTrue(isDriveViewActive(speedKmh = 100.0f, cruiseOn = false))
    }

    @Test
    fun driveViewWhenCruiseOnRegardlessOfSpeed() {
        assertTrue(isDriveViewActive(speedKmh = 0f, cruiseOn = true))
        assertTrue(isDriveViewActive(speedKmh = 5f, cruiseOn = true))
        assertTrue(isDriveViewActive(speedKmh = 10f, cruiseOn = true))
        assertTrue(isDriveViewActive(speedKmh = 14.9f, cruiseOn = true))
        assertTrue(isDriveViewActive(speedKmh = 30f, cruiseOn = true))
    }

    @Test
    fun driveViewWhenRacingActive() {
        assertTrue(isDriveViewActive(speedKmh = 0f, cruiseOn = false, racing = true))
    }
}
