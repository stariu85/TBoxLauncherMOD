package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCpuAffinityTest {

    @Test
    fun sm6150Topology_allowsPinningToCpu45() {
        assertTrue(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 8,
                cpu4MaxFreqKhz = 1_593_600L,
                cpu5MaxFreqKhz = 1_593_600L,
                cpu6MaxFreqKhz = 1_900_800L,
                cpu7MaxFreqKhz = 1_900_800L,
            ),
        )
    }

    @Test
    fun nonOctaCoreOrHomogeneousOrMissingFreqs_disablesPinning() {
        // Non-8-core SoC
        assertFalse(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 4,
                cpu4MaxFreqKhz = 1_593_600L,
                cpu5MaxFreqKhz = 1_593_600L,
                cpu6MaxFreqKhz = 1_900_800L,
                cpu7MaxFreqKhz = 1_900_800L,
            ),
        )
        // Homogeneous 8-core (equal frequencies)
        assertFalse(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 8,
                cpu4MaxFreqKhz = 1_800_000L,
                cpu5MaxFreqKhz = 1_800_000L,
                cpu6MaxFreqKhz = 1_800_000L,
                cpu7MaxFreqKhz = 1_800_000L,
            ),
        )
        // 4+4 big.LITTLE where cpu4/5 are already big cores
        assertFalse(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 8,
                cpu4MaxFreqKhz = 2_000_000L,
                cpu5MaxFreqKhz = 2_000_000L,
                cpu6MaxFreqKhz = 2_000_000L,
                cpu7MaxFreqKhz = 2_000_000L,
            ),
        )
        // Missing sysfs value
        assertFalse(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 8,
                cpu4MaxFreqKhz = 1_593_600L,
                cpu5MaxFreqKhz = null,
                cpu6MaxFreqKhz = 1_900_800L,
                cpu7MaxFreqKhz = 1_900_800L,
            ),
        )
        // Zero / invalid frequency
        assertFalse(
            LauncherCpuAffinity.shouldPinBackgroundThreadsToCpu45(
                availableProcessors = 8,
                cpu4MaxFreqKhz = 0L,
                cpu5MaxFreqKhz = 1_593_600L,
                cpu6MaxFreqKhz = 1_900_800L,
                cpu7MaxFreqKhz = 1_900_800L,
            ),
        )
    }

    @Test
    fun parseCpuMaxFreqKhz_parsesValidAndRejectsInvalid() {
        assertEquals(1_593_600L, LauncherCpuAffinity.parseCpuMaxFreqKhz("1593600\n"))
        assertEquals(1_900_800L, LauncherCpuAffinity.parseCpuMaxFreqKhz("  1900800 "))
        assertNull(LauncherCpuAffinity.parseCpuMaxFreqKhz(""))
        assertNull(LauncherCpuAffinity.parseCpuMaxFreqKhz("0"))
        assertNull(LauncherCpuAffinity.parseCpuMaxFreqKhz("-100"))
        assertNull(LauncherCpuAffinity.parseCpuMaxFreqKhz("not_a_number"))
        assertNull(LauncherCpuAffinity.parseCpuMaxFreqKhz(null))
    }

    @Test
    fun resolveTargetCpuHexMask_returnsCpu45OrAllCores() {
        assertEquals("30", LauncherCpuAffinity.resolveTargetCpuHexMask(pinToCpu45 = true, availableProcessors = 8))
        assertEquals("ff", LauncherCpuAffinity.resolveTargetCpuHexMask(pinToCpu45 = false, availableProcessors = 8))
        assertEquals("f", LauncherCpuAffinity.resolveTargetCpuHexMask(pinToCpu45 = false, availableProcessors = 4))
    }

    @Test
    fun threadNameMatchers_selectOnlyExpected3dAndCanThreads() {
        assertTrue(LauncherCpuAffinity.is3dRenderThreadName("FEngine::loop"))
        assertTrue(LauncherCpuAffinity.is3dRenderThreadName("JobSystem::loop"))
        assertTrue(LauncherCpuAffinity.is3dRenderThreadName("FilamentRender"))
        assertTrue(LauncherCpuAffinity.is3dRenderThreadName("GLThread 123"))
        assertFalse(LauncherCpuAffinity.is3dRenderThreadName("RenderThread"))
        assertFalse(LauncherCpuAffinity.is3dRenderThreadName("main"))
        assertFalse(LauncherCpuAffinity.is3dRenderThreadName("dcl-can-poll-1"))

        assertTrue(LauncherCpuAffinity.isCanBusThreadName("dcl-can-poll-1"))
        assertTrue(LauncherCpuAffinity.isCanBusThreadName("dcl-can-poll-2"))
        assertTrue(LauncherCpuAffinity.isCanBusThreadName("mbcan-state-apply"))
        assertTrue(LauncherCpuAffinity.isCanBusThreadName("can-thread"))
        assertFalse(LauncherCpuAffinity.isCanBusThreadName("FEngine::loop"))
        assertFalse(LauncherCpuAffinity.isCanBusThreadName("RenderThread"))
        assertFalse(LauncherCpuAffinity.isCanBusThreadName("main"))
    }
}
