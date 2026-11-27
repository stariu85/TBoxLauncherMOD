package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DclLogRecorderCanCounterTest {

    @Test
    fun formatCanTypeIntervalBlock_sortsDescendingAndFormatsRates() {
        val counts = IntArray(64)
        counts[1] = 200
        counts[4] = 500
        counts[22] = 100
        val names = arrayOfNulls<String>(64).apply {
            this[1] = "eMBCAN_VEHICLE_SPEED"
            this[4] = "eMBCAN_VEHICLE_WHEEL"
            this[22] = "eMBCAN_VEHICLE_ENGINE"
        }

        val line = DclLogRecorder.formatCanTypeIntervalBlock(
            timestamp = "2026-11-27 15:40:00.000",
            elapsedSec = 10.0f,
            intervalMs = 10_000L,
            counts = counts,
            unknownCount = 50,
            typeNames = names,
        )

        assertTrue(line.contains("total=850 (85.0/s)"))
        val wheelIdx = line.indexOf("eMBCAN_VEHICLE_WHEEL(4)=500 (50.0/s)")
        val speedIdx = line.indexOf("eMBCAN_VEHICLE_SPEED(1)=200 (20.0/s)")
        val engineIdx = line.indexOf("eMBCAN_VEHICLE_ENGINE(22)=100 (10.0/s)")
        val unknownIdx = line.indexOf("UNKNOWN=50 (5.0/s)")
        assertTrue(wheelIdx in 0 until speedIdx)
        assertTrue(speedIdx in 0 until engineIdx)
        assertTrue(engineIdx in 0 until unknownIdx)
    }

    @Test
    fun formatCanTypeIntervalBlock_handlesNoCallbacks() {
        val line = DclLogRecorder.formatCanTypeIntervalBlock(
            timestamp = "2026-11-27 15:40:10.000",
            elapsedSec = 20.0f,
            intervalMs = 10_000L,
            counts = IntArray(64),
            unknownCount = 0,
            typeNames = arrayOfNulls(64),
        )

        assertTrue(line.contains("total=0 (0.0/s) [no callbacks]"))
    }

    @Test
    fun formatCanTypeTotalSummary_includesSharePercentagesAndRates() {
        val counts = IntArray(64)
        counts[4] = 3000
        counts[1] = 1000
        val names = arrayOfNulls<String>(64).apply {
            this[1] = "eMBCAN_VEHICLE_SPEED"
            this[4] = "eMBCAN_VEHICLE_WHEEL"
        }

        val summary = DclLogRecorder.formatCanTypeTotalSummary(
            endTimestamp = "2026-11-27 15:41:00.000",
            totalDurationMs = 60_000L,
            counts = counts,
            unknownCount = 0,
            typeNames = names,
        )

        assertTrue(summary.contains("Total callbacks: 4000 (66.7/s)"))
        assertTrue(summary.contains("eMBCAN_VEHICLE_WHEEL(4)"))
        assertTrue(summary.contains("50.0/s"))
        assertTrue(summary.contains("75.0%"))
        assertTrue(summary.contains("eMBCAN_VEHICLE_SPEED(1)"))
        assertTrue(summary.contains("25.0%"))
        assertTrue(summary.indexOf("eMBCAN_VEHICLE_WHEEL(4)") < summary.indexOf("eMBCAN_VEHICLE_SPEED(1)"))
    }

    @Test
    fun recordMbCanCallbackType_isInactiveByDefault() {
        assertFalse(DclLogRecorder.isCanCounterRecordingActive())
        DclLogRecorder.recordMbCanCallbackType(1)
        DclLogRecorder.recordMbCanCallbackType(-5)
        DclLogRecorder.recordMbCanCallbackType(999)
        assertFalse(DclLogRecorder.isCanCounterRecordingActive())
    }
}
