package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.LocValues

class LauncherCompassTrackerTest {

    @Before
    fun setUp() {
        LauncherGpsCompassTracker.reset()
    }

    @Test
    fun computeGpsBearingCalculatesCardinalDirections() {
        // Moving North: lat increases
        val north = computeGpsBearing(55.0, 37.0, 55.01, 37.0)
        assertEquals(0f, north, 0.5f)

        // Moving East: lon increases
        val east = computeGpsBearing(55.0, 37.0, 55.0, 37.01)
        assertEquals(90f, east, 0.5f)

        // Moving South: lat decreases
        val south = computeGpsBearing(55.0, 37.0, 54.99, 37.0)
        assertEquals(180f, south, 0.5f)

        // Moving West: lon decreases
        val west = computeGpsBearing(55.0, 37.0, 55.0, 36.99)
        assertEquals(270f, west, 0.5f)
    }

    @Test
    fun trackerIgnoresZeroOrNoFixCoordinates() {
        val noFixLoc = LocValues(
            latitude = 55.75,
            longitude = 37.61,
            locateStatus = false,
            usingSatellites = 0,
        )
        val heading = LauncherGpsCompassTracker.onLocationUpdate(noFixLoc)
        assertEquals(0f, heading, 0.001f)
        assertEquals(0f, LauncherGpsCompassTracker.currentHeading, 0.001f)

        val zeroLoc = LocValues(
            latitude = 0.0,
            longitude = 0.0,
            locateStatus = true,
        )
        val headingZero = LauncherGpsCompassTracker.onLocationUpdate(zeroLoc)
        assertEquals(0f, headingZero, 0.001f)
    }

    @Test
    fun trackerTracksHeadingOnCoordinateChanges() {
        // First coordinate sets baseline (heading remains 0 initially)
        val firstLoc = LocValues(
            latitude = 55.750000,
            longitude = 37.610000,
            locateStatus = true,
        )
        val h1 = LauncherGpsCompassTracker.onLocationUpdate(firstLoc)
        assertEquals(0f, h1, 0.001f)

        // Moving East by ~60 meters
        val secondLoc = LocValues(
            latitude = 55.750000,
            longitude = 37.611000,
            locateStatus = true,
        )
        val h2 = LauncherGpsCompassTracker.onLocationUpdate(secondLoc)
        assertEquals(90f, h2, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.headingFlow.value, 1.0f)

        // Now move South by ~60 meters (heading should turn towards 180°)
        val thirdLoc = LocValues(
            latitude = 55.749460,
            longitude = 37.611000,
            locateStatus = true,
        )
        val h3 = LauncherGpsCompassTracker.onLocationUpdate(thirdLoc)
        assertEquals(180f, h3, 1.0f)
        assertEquals(180f, LauncherGpsCompassTracker.currentHeading, 1.0f)
    }

    @Test
    fun trackerIgnoresSubThresholdDisplacements() {
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000, 37.610000)

        // Very tiny displacement (~0.1 meters, well below 2.5m threshold)
        val h = LauncherGpsCompassTracker.onCoordinatesUpdate(55.750001, 37.610001)
        assertEquals(0f, h, 0.001f)
    }

    @Test
    fun trackerRetainsHeadingAcrossDriveViewTransitions() {
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000, 37.610000)
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.751000, 37.610000) // moving North
        assertEquals(0f, LauncherGpsCompassTracker.currentHeading, 0.5f)

        LauncherGpsCompassTracker.onCoordinatesUpdate(55.751000, 37.611000) // moving East
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)

        // Heading is retained in singleton object even when overlay unmounts
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.headingFlow.value, 1.0f)
    }
}
