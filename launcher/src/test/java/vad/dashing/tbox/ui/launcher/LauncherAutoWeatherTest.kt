package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LauncherAutoWeatherTest {

    @Before
    fun setup() {
        LauncherAutoWeatherManager.resetForTesting()
    }

    @Test
    fun `auto weather does not turn on with less than 3 detections in 30 seconds`() {
        // Pulse 1 at t=1000ms
        LauncherAutoWeatherManager.updateAtTime(
            now = 1000L,
            rainDetected = true,
            outsideTemp = 15f,
            autoEnabled = true,
            manualActive = false,
        )
        LauncherAutoWeatherManager.updateAtTime(
            now = 2000L,
            rainDetected = false,
            outsideTemp = 15f,
            autoEnabled = true,
            manualActive = false,
        )
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // Pulse 2 at t=10000ms
        LauncherAutoWeatherManager.updateAtTime(
            now = 10_000L,
            rainDetected = true,
            outsideTemp = 15f,
            autoEnabled = true,
            manualActive = false,
        )
        LauncherAutoWeatherManager.updateAtTime(
            now = 11_000L,
            rainDetected = false,
            outsideTemp = 15f,
            autoEnabled = true,
            manualActive = false,
        )
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `auto weather turns on rain when 3 rain detections occur within 30 seconds and temp greater than 3`() {
        // Pulse 1 at t=1000ms
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 5f, true, false)
        LauncherAutoWeatherManager.updateAtTime(2000L, false, 5f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // Pulse 2 at t=10000ms
        LauncherAutoWeatherManager.updateAtTime(10_000L, true, 5f, true, false)
        LauncherAutoWeatherManager.updateAtTime(11_000L, false, 5f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // Pulse 3 at t=20000ms (within 30s)
        LauncherAutoWeatherManager.updateAtTime(20_000L, true, 5f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `auto weather turns on snow when 3 rain detections occur within 30 seconds and temp less than 3`() {
        // Pulse 1 at t=1000ms
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 1f, true, false)
        LauncherAutoWeatherManager.updateAtTime(2000L, false, 1f, true, false)

        // Pulse 2 at t=5000ms
        LauncherAutoWeatherManager.updateAtTime(5000L, true, 1f, true, false)
        LauncherAutoWeatherManager.updateAtTime(6000L, false, 1f, true, false)

        // Pulse 3 at t=12000ms
        LauncherAutoWeatherManager.updateAtTime(12_000L, true, 1f, true, false)
        assertEquals(AutoWeatherType.Snow, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `continuous rain registers repeated events and turns on`() {
        // Continuous rain starts at t=1000ms (Event 1)
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // At t=6000ms (Event 2 after 5s continuous)
        LauncherAutoWeatherManager.updateAtTime(6000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // At t=11000ms (Event 3 after another 5s continuous)
        LauncherAutoWeatherManager.updateAtTime(11_000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `detections older than 30 seconds expire from window`() {
        // Pulse 1 at t=1000ms
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(2000L, false, 10f, true, false)

        // Pulse 2 at t=5000ms
        LauncherAutoWeatherManager.updateAtTime(5000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(6000L, false, 10f, true, false)

        // Pulse 3 arrives at t=33000ms (pulse 1 was 32s ago -> expired!)
        LauncherAutoWeatherManager.updateAtTime(33_000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)

        // Pulse 4 arrives at t=34000ms (now pulse 2 and 3 and 4 are within 30s)
        LauncherAutoWeatherManager.updateAtTime(34_000L, false, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(34_500L, true, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `rain turning off stays active until 120 seconds of no rain`() {
        // Trigger 3 pulses
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(2000L, false, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(5000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(6000L, false, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(10_000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)

        // Rain stops at 12s
        LauncherAutoWeatherManager.updateAtTime(12_000L, false, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)

        // At 100s without rain (12s -> 112s = 100s < 120s), still active
        LauncherAutoWeatherManager.updateAtTime(112_000L, false, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)

        // At 133s without rain (12s -> 133s = 121s >= 120s), turns off
        LauncherAutoWeatherManager.updateAtTime(133_000L, false, 10f, true, false)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)
    }

    @Test
    fun `manual active immediately disables auto weather`() {
        // Trigger 3 pulses
        LauncherAutoWeatherManager.updateAtTime(1000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(2000L, false, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(3000L, true, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(4000L, false, 10f, true, false)
        LauncherAutoWeatherManager.updateAtTime(5000L, true, 10f, true, false)
        assertEquals(AutoWeatherType.Rain, LauncherAutoWeatherManager.autoWeatherType.value)

        // Manual mode enabled
        LauncherAutoWeatherManager.updateAtTime(6000L, true, 10f, true, manualActive = true)
        assertEquals(AutoWeatherType.None, LauncherAutoWeatherManager.autoWeatherType.value)
    }
}
