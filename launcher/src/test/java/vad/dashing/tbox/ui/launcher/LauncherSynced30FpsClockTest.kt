package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LauncherSynced30FpsClockTest {

    @Test
    fun stepFrame_caps60HzVsyncTo30FpsAndNotifiesListenersSynchronously() {
        val clock = LauncherSynced30FpsClock(frameIntervalNs = VIRTUAL_ROAD_FRAME_INTERVAL_NS)
        val roadTicks = mutableListOf<Pair<Long, Long>>()
        val otherTicks = mutableListOf<Pair<Long, Long>>()

        assertFalse(clock.hasActiveListeners)
        val unregRoad = clock.registerListener(tickDivisor = 1) { frameNs, deltaNs ->
            roadTicks.add(frameNs to deltaNs)
        }
        val unregOther = clock.registerListener(tickDivisor = 1) { frameNs, deltaNs ->
            otherTicks.add(frameNs to deltaNs)
        }
        assertTrue(clock.hasActiveListeners)
        assertEquals(VIRTUAL_ROAD_FRAME_INTERVAL_NS, clock.effectiveIntervalNs)

        // Simulate 60 Hz VSYNC timestamps (16.67 ms apart):
        val vsyncStepNs = 16_666_667L
        val t0 = 1_000_000_000L
        assertFalse(clock.stepFrame(t0)) // Priming frame
        assertFalse(clock.stepFrame(t0 + vsyncStepNs)) // 16.67 ms < 32 ms -> skipped
        assertTrue(clock.stepFrame(t0 + 2 * vsyncStepNs)) // 33.33 ms >= 32 ms -> tick!
        assertFalse(clock.stepFrame(t0 + 3 * vsyncStepNs)) // 16.67 ms < 32 ms -> skipped
        assertTrue(clock.stepFrame(t0 + 4 * vsyncStepNs)) // 33.33 ms >= 32 ms -> tick!

        // Both listeners received the exact same frame timestamps and deltas on the same ticks.
        assertEquals(2, roadTicks.size)
        assertEquals(roadTicks, otherTicks)
        assertEquals(t0 + 2 * vsyncStepNs, roadTicks[0].first)
        assertEquals(2 * vsyncStepNs, roadTicks[0].second)
        assertEquals(t0 + 4 * vsyncStepNs, roadTicks[1].first)

        // Unregistering all listeners stops active state and resets timing.
        unregRoad()
        assertTrue(clock.hasActiveListeners)
        unregOther()
        assertFalse(clock.hasActiveListeners)
    }

    @Test
    fun stepFrame_syncs15FpsPdcToEverySecond30FpsRoadTickOnSameVsync() {
        val clock = LauncherSynced30FpsClock(frameIntervalNs = VIRTUAL_ROAD_FRAME_INTERVAL_NS)
        val roadTicks = mutableListOf<Pair<Long, Long>>()
        val pdcTicks = mutableListOf<Pair<Long, Long>>()

        val unregRoad = clock.registerListener(tickDivisor = 1) { frameNs, deltaNs ->
            roadTicks.add(frameNs to deltaNs)
        }
        val unregPdc = clock.registerListener(tickDivisor = PDC_CLOCK_TICK_DIVISOR) { frameNs, deltaNs ->
            pdcTicks.add(frameNs to deltaNs)
        }
        assertEquals(VIRTUAL_ROAD_FRAME_INTERVAL_NS, clock.effectiveIntervalNs)

        val vsyncStepNs = 16_666_667L
        val t0 = 1_000_000_000L
        assertFalse(clock.stepFrame(t0)) // Prime
        assertTrue(clock.stepFrame(t0 + 2 * vsyncStepNs)) // Tick #1 -> road only
        assertTrue(clock.stepFrame(t0 + 4 * vsyncStepNs)) // Tick #2 -> road + PDC synced!
        assertTrue(clock.stepFrame(t0 + 6 * vsyncStepNs)) // Tick #3 -> road only
        assertTrue(clock.stepFrame(t0 + 8 * vsyncStepNs)) // Tick #4 -> road + PDC synced!

        assertEquals(4, roadTicks.size)
        assertEquals(2, pdcTicks.size)
        // PDC fired on the exact same VSYNC timestamps as road ticks #2 and #4, with full ~66.7ms delta.
        assertEquals(roadTicks[1].first, pdcTicks[0].first)
        assertEquals(4 * vsyncStepNs, pdcTicks[0].second)
        assertEquals(roadTicks[3].first, pdcTicks[1].first)
        assertEquals(4 * vsyncStepNs, pdcTicks[1].second)

        unregRoad()
        // With only PDC left, effective interval doubles to 64 ms (~15 FPS).
        assertEquals(VIRTUAL_ROAD_FRAME_INTERVAL_NS * 2, clock.effectiveIntervalNs)
        unregPdc()
        assertFalse(clock.hasActiveListeners)
    }

    @Test
    fun pdcOnly_runsAt15FpsAndSleepsBetweenTicksInsteadOfPolling60Hz() {
        val clock = LauncherSynced30FpsClock(frameIntervalNs = VIRTUAL_ROAD_FRAME_INTERVAL_NS)
        val pdcTicks = mutableListOf<Pair<Long, Long>>()

        val unregPdc = clock.registerListener(tickDivisor = PDC_CLOCK_TICK_DIVISOR) { frameNs, deltaNs ->
            pdcTicks.add(frameNs to deltaNs)
        }
        assertEquals(64_000_000L, clock.effectiveIntervalNs)

        val vsyncStepNs = 16_666_667L
        val t0 = 2_000_000_000L
        assertEquals(0L, clock.computeSleepBeforeNextFrameNs(t0))
        assertFalse(clock.stepFrame(t0, t0)) // Prime

        // Immediately after prime/tick, sleep should be ~60 ms (64 ms - 4 ms lead), skipping 3 intermediate VSYNCs.
        val expectedSleepNs = 64_000_000L - LauncherSynced30FpsClock.WAKE_LEAD_NS
        assertEquals(expectedSleepNs, clock.computeSleepBeforeNextFrameNs(t0))

        // Intermediate VSYNCs (16.7ms, 33.3ms, 50.0ms) must not tick.
        assertFalse(clock.stepFrame(t0 + vsyncStepNs, t0 + vsyncStepNs))
        assertFalse(clock.stepFrame(t0 + 2 * vsyncStepNs, t0 + 2 * vsyncStepNs))
        assertFalse(clock.stepFrame(t0 + 3 * vsyncStepNs, t0 + 3 * vsyncStepNs))
        // 4th VSYNC (66.7ms >= 64ms) fires PDC tick cleanly.
        assertTrue(clock.stepFrame(t0 + 4 * vsyncStepNs, t0 + 4 * vsyncStepNs))
        assertEquals(1, pdcTicks.size)
        assertEquals(t0 + 4 * vsyncStepNs, pdcTicks[0].first)
        assertEquals(4 * vsyncStepNs, pdcTicks[0].second)

        unregPdc()
    }

    @Test
    fun stepFrame_wallClockGuardPreventsBackToBackTicksAfterSlowDrawFrame() {
        val clock = LauncherSynced30FpsClock(frameIntervalNs = VIRTUAL_ROAD_FRAME_INTERVAL_NS)
        val ticks = mutableListOf<Long>()
        val unreg = clock.registerListener(tickDivisor = 1) { frameNs, _ -> ticks.add(frameNs) }

        val vsyncStepNs = 16_666_667L
        val f0 = 5_000_000_000L
        val w0 = 5_000_000_000L
        assertFalse(clock.stepFrame(f0, w0)) // Prime

        // Normal first tick after 33.33 ms on both VSYNC and wall clock.
        val f1 = f0 + 2 * vsyncStepNs
        val w1 = w0 + 2 * vsyncStepNs
        assertTrue(clock.stepFrame(f1, w1))
        assertEquals(1, ticks.size)

        // Simulate a slow little-core DrawFrame: VSYNC frameNs jumped by 33.33 ms,
        // but only 21.5 ms of actual wall time elapsed since w1 (< 28 ms wall guard).
        val f2Premature = f1 + 2 * vsyncStepNs
        val w2Premature = w1 + 21_500_000L
        assertFalse(clock.stepFrame(f2Premature, w2Premature))
        assertEquals(1, ticks.size)

        // Once wall time catches up (>= 28 ms after w1), the next VSYNC ticks normally.
        val f3Valid = f1 + 3 * vsyncStepNs
        val w3Valid = w1 + 33_333_334L
        assertTrue(clock.stepFrame(f3Valid, w3Valid))
        assertEquals(2, ticks.size)
        assertEquals(f3Valid, ticks[1])

        unreg()
    }

    @Test
    fun computePdcWaveforms_produceExpectedBoundsAndSeamlessWrap() {
        assertEquals(0.6f, computePdcPulseValue(0f), 1e-4f)
        assertEquals(1.0f, computePdcPulseValue(PDC_PULSE_HALF_PERIOD_MS), 1e-4f)
        assertEquals(0.6f, computePdcPulseValue(PDC_PULSE_HALF_PERIOD_MS * 2f), 1e-4f)
        assertEquals(0.6f, computePdcPulseValue(PDC_ANIM_WRAP_PERIOD_MS), 1e-4f)

        assertEquals(0f, computePdcStandbyJitterPhase(0f), 1e-4f)
        assertEquals(PI.toFloat(), computePdcStandbyJitterPhase(PDC_STANDBY_JITTER_PERIOD_MS / 2f), 1e-4f)
        assertEquals(0f, computePdcStandbyJitterPhase(PDC_STANDBY_JITTER_PERIOD_MS), 1e-4f)
        assertEquals(0f, computePdcStandbyJitterPhase(PDC_ANIM_WRAP_PERIOD_MS), 1e-4f)
    }
}
