package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDriveViewConditionTest {

    @Test
    fun topViewWhenBelow15Kmh() {
        assertFalse(isDriveViewActive(speedKmh = 0f, cruiseOn = false))
        assertFalse(isDriveViewActive(speedKmh = 5f, cruiseOn = false))
        assertFalse(isDriveViewActive(speedKmh = 14.9f, cruiseOn = false))
        assertFalse(isDriveViewActive(speedKmh = 0f, cruiseOn = true))
        assertFalse(isDriveViewActive(speedKmh = 5f, cruiseOn = true))
        assertFalse(isDriveViewActive(speedKmh = 14.9f, cruiseOn = true))
    }

    @Test
    fun driveViewWhenAtOrAbove15Kmh() {
        assertTrue(isDriveViewActive(speedKmh = 15.0f, cruiseOn = false))
        assertTrue(isDriveViewActive(speedKmh = 20.0f, cruiseOn = false))
        assertTrue(isDriveViewActive(speedKmh = 100.0f, cruiseOn = false))
        assertTrue(isDriveViewActive(speedKmh = 15.0f, cruiseOn = true))
        assertTrue(isDriveViewActive(speedKmh = 30.0f, cruiseOn = true))
    }

    @Test
    fun driveViewWhenRacingActive() {
        assertTrue(isDriveViewActive(speedKmh = 0f, cruiseOn = false, racing = true))
    }
}
