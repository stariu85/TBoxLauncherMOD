package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class SatelliteInfoResolutionTest {

    @Test
    fun tbmNormalGpsFixReturnsGreenAndSatelliteCount() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = true,
            tbmSatVisible = 12,
            tbmSatUsed = 8,
            tbmFixValid = true,
            tbmLocateStatus = true,
            tbmSpeedSource = "gps",
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertTrue(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(12, satInfo.visibleSatellites)
        assertEquals(8, satInfo.usingSatellites)
    }

    @Test
    fun tbmInertialOnlyWhenSpeedSourceInertialAndUsedSatellitesZero() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = true,
            tbmSatVisible = 10,
            tbmSatUsed = 0,
            tbmFixValid = true,
            tbmLocateStatus = true,
            tbmSpeedSource = "inertial",
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertFalse(satInfo.hasFix)
        assertTrue(satInfo.isInertial)
        assertEquals(10, satInfo.visibleSatellites)
        assertEquals(0, satInfo.usingSatellites)
    }

    @Test
    fun tbmInertialIgnoredWhenUsedSatellitesGreaterThanZero() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = true,
            tbmSatVisible = 10,
            tbmSatUsed = 8,
            tbmFixValid = true,
            tbmLocateStatus = true,
            tbmSpeedSource = "inertial",
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertTrue(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(10, satInfo.visibleSatellites)
        assertEquals(8, satInfo.usingSatellites)
    }

    @Test
    fun tbmFixWithZeroSatellitesUsedReturnsFixAndZeroSatellites() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = true,
            tbmSatVisible = 0,
            tbmSatUsed = 0,
            tbmFixValid = true,
            tbmLocateStatus = false,
            tbmSpeedSource = null,
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertTrue(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(0, satInfo.visibleSatellites)
        assertEquals(0, satInfo.usingSatellites)
    }

    @Test
    fun tbmNoFixAndNoInertialReturnsNoFixWithVisibleSatellites() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = true,
            tbmSatVisible = 4,
            tbmSatUsed = 0,
            tbmFixValid = false,
            tbmLocateStatus = false,
            tbmSpeedSource = null,
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertFalse(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(4, satInfo.visibleSatellites)
        assertEquals(0, satInfo.usingSatellites)
    }

    @Test
    fun tboxUdpDirectReturnsFixAndSatelliteCount() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = false,
            tbmSatVisible = null,
            tbmSatUsed = null,
            tbmFixValid = null,
            tbmLocateStatus = null,
            tbmSpeedSource = null,
            isTboxLocFresh = true,
            locValues = LocValues(
                locateStatus = true,
                visibleSatellites = 10,
                usingSatellites = 0,
            ),
        )

        assertTrue(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(10, satInfo.visibleSatellites)
        assertEquals(0, satInfo.usingSatellites)
    }

    @Test
    fun noTbmAndNoTboxReturnsNoFixAndZeroSatellites() {
        val satInfo = resolveSatelliteInfo(
            isTbmGeoAvailable = false,
            tbmSatVisible = null,
            tbmSatUsed = null,
            tbmFixValid = null,
            tbmLocateStatus = null,
            tbmSpeedSource = null,
            isTboxLocFresh = false,
            locValues = LocValues(),
        )

        assertFalse(satInfo.hasFix)
        assertFalse(satInfo.isInertial)
        assertEquals(0, satInfo.visibleSatellites)
        assertEquals(0, satInfo.usingSatellites)
    }
}
