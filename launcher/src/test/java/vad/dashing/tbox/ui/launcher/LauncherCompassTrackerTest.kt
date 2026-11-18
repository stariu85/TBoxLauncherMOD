package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun computeAverageVectorBearingReturnsNullForInsufficientPoints() {
        assertNull(computeAverageVectorBearing(emptyList()))
        assertNull(computeAverageVectorBearing(listOf(GpsPoint(55.0, 37.0))))
    }

    @Test
    fun computeAverageVectorBearingAveragesConsecutiveDirectionVectors() {
        // 5 points (4 vectors), all moving East: heading should be exactly 90°
        val pointsEast = listOf(
            GpsPoint(55.0, 37.000),
            GpsPoint(55.0, 37.001),
            GpsPoint(55.0, 37.002),
            GpsPoint(55.0, 37.003),
            GpsPoint(55.0, 37.004),
        )
        val bearingEast = computeAverageVectorBearing(pointsEast)
        assertNotNull(bearingEast)
        assertEquals(90f, bearingEast!!, 0.5f)
    }

    @Test
    fun computeAverageVectorBearingCancelsGpsJitter() {
        // Vehicle traveling East (90°), but individual segments have alternating lateral jitter:
        // segment 1: ~80°
        // segment 2: ~100°
        // segment 3: ~80°
        // segment 4: ~100°
        val points = listOf(
            GpsPoint(55.000000, 37.000000),
            GpsPoint(55.000100, 37.001000), // leaning slightly North-East (~83°)
            GpsPoint(54.999900, 37.002000), // leaning slightly South-East (~97°)
            GpsPoint(55.000100, 37.003000), // leaning slightly North-East (~83°)
            GpsPoint(55.000000, 37.004000), // leaning slightly South-East (~97°)
        )
        val smoothedBearing = computeAverageVectorBearing(points)
        assertNotNull(smoothedBearing)
        // Average vector should smooth out jitter back to pure East (90°)
        assertEquals(90f, smoothedBearing!!, 1.5f)
    }

    @Test
    fun computeAverageVectorBearingHandlesNorthWraparound() {
        // Segments oscillating across 0° / 360° (North):
        // 350° and 10°
        val p0 = GpsPoint(55.000, 37.000)
        // Step 1: bearing ~350°
        val p1 = GpsPoint(55.001, 36.9997)
        // Step 2: bearing ~10°
        val p2 = GpsPoint(55.002, 37.0000)

        val bearing = computeAverageVectorBearing(listOf(p0, p1, p2))
        assertNotNull(bearing)
        // Should average to ~0° (North), not 180° (South)
        assertTrue((bearing!! < 5f) || (bearing > 355f))
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
    fun trackerTracksHeadingOnCoordinateChangesWithSmoothing() {
        // First coordinate sets baseline (heading remains 0 initially)
        val firstLoc = LocValues(
            latitude = 55.750000,
            longitude = 37.610000,
            locateStatus = true,
        )
        val h1 = LauncherGpsCompassTracker.onLocationUpdate(firstLoc)
        assertEquals(0f, h1, 0.001f)

        // Moving East by ~60 meters (1 vector = East 90°)
        val secondLoc = LocValues(
            latitude = 55.750000,
            longitude = 37.611000,
            locateStatus = true,
        )
        val h2 = LauncherGpsCompassTracker.onLocationUpdate(secondLoc)
        assertEquals(90f, h2, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.headingFlow.value, 1.0f)

        // Now turn South by ~60 meters:
        // History now has: East (90°) and South (180°).
        // Average vector points South-East (135°), smoothing the turn.
        val thirdLoc = LocValues(
            latitude = 55.749460,
            longitude = 37.611000,
            locateStatus = true,
        )
        val h3 = LauncherGpsCompassTracker.onLocationUpdate(thirdLoc)
        assertEquals(135f, h3, 1.0f)
        assertEquals(135f, LauncherGpsCompassTracker.currentHeading, 1.0f)

        // Continuing South:
        val fourthLoc = LocValues(
            latitude = 55.748920,
            longitude = 37.611000,
            locateStatus = true,
        )
        LauncherGpsCompassTracker.onLocationUpdate(fourthLoc)

        val fifthLoc = LocValues(
            latitude = 55.748380,
            longitude = 37.611000,
            locateStatus = true,
        )
        LauncherGpsCompassTracker.onLocationUpdate(fifthLoc)

        // Sixth loc: oldest East vector drops out, all vectors are now South
        val sixthLoc = LocValues(
            latitude = 55.747840,
            longitude = 37.611000,
            locateStatus = true,
        )
        val h6 = LauncherGpsCompassTracker.onLocationUpdate(sixthLoc)
        assertEquals(180f, h6, 1.0f)
        assertEquals(180f, LauncherGpsCompassTracker.currentHeading, 1.0f)
    }

    @Test
    fun trackerMaintainsSlidingWindowOfFourPreviousPoints() {
        // Feed 10 consecutive coordinates moving North
        for (i in 0..10) {
            LauncherGpsCompassTracker.onCoordinatesUpdate(55.0 + i * 0.0005, 37.0)
        }
        val history = LauncherGpsCompassTracker.getHistoryPoints()
        // Window size must be at most MAX_HISTORY_POINTS (4 previous points + current point = 5 points)
        assertEquals(LauncherGpsCompassTracker.MAX_HISTORY_POINTS, history.size)
        assertEquals(0f, LauncherGpsCompassTracker.currentHeading, 0.5f)
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
        // Move North
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000, 37.610000)
        for (i in 1..4) {
            LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000 + i * 0.0005, 37.610000)
        }
        assertEquals(0f, LauncherGpsCompassTracker.currentHeading, 0.5f)

        // Turn East and establish new heading
        val currentLat = 55.750000 + 4 * 0.0005
        for (i in 1..4) {
            LauncherGpsCompassTracker.onCoordinatesUpdate(currentLat, 37.610000 + i * 0.0008)
        }
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)

        // Heading is retained in singleton object even when overlay unmounts
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)
        assertEquals(90f, LauncherGpsCompassTracker.headingFlow.value, 1.0f)
    }

    @Test
    fun trackerResetClearsHistoryAndHeading() {
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000, 37.610000)
        LauncherGpsCompassTracker.onCoordinatesUpdate(55.750000, 37.611000)
        assertEquals(90f, LauncherGpsCompassTracker.currentHeading, 1.0f)

        LauncherGpsCompassTracker.reset()
        assertEquals(0f, LauncherGpsCompassTracker.currentHeading, 0.001f)
        assertEquals(0f, LauncherGpsCompassTracker.headingFlow.value, 0.001f)
        assertTrue(LauncherGpsCompassTracker.getHistoryPoints().isEmpty())
    }
}
