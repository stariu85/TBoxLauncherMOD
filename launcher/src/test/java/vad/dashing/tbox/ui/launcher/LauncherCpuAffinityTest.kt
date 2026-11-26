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
    fun resolveTargetCpuHexMask_returnsLittleClusterCpu05OrAllCores() {
        assertEquals("3f", LauncherCpuAffinity.resolveTargetCpuHexMask(pinToCpu45 = true, availableProcessors = 8))
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

        assertTrue(LauncherCpuAffinity.isRenderThreadName("RenderThread"))
        assertFalse(LauncherCpuAffinity.isRenderThreadName("main"))
        assertFalse(LauncherCpuAffinity.isRenderThreadName("FEngine::loop"))

        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("HeapTaskDaemon"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("Jit thread pool"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("Jit thread pool worker thread 0"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("FinalizerDaemon"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("FinalizerWatchd"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("FinalizerWatchdogDaemon"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("ReferenceQueueD"))
        assertTrue(LauncherCpuAffinity.isArtRuntimeThreadName("ReferenceQueueDaemon"))
        assertFalse(LauncherCpuAffinity.isArtRuntimeThreadName("RenderThread"))
        assertFalse(LauncherCpuAffinity.isArtRuntimeThreadName("g.tbox.launcher"))

        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-logger"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("tbox-diag-log"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("launcher-tbox-p"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("launcher-tbox-packets"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("vhal-state-appl"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("vhal-state-apply"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-shell-drain"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-affinity-ct"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-affinity-ctl"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-screencap"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("LiveWallpaperRe"))
        assertTrue(LauncherCpuAffinity.isServiceBackgroundThreadName("LiveWallpaperRelease"))
        assertFalse(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-can-poll-1"))
        assertFalse(LauncherCpuAffinity.isServiceBackgroundThreadName("dcl-poll-1"))

        assertTrue(LauncherCpuAffinity.isLightPollerThreadName("dcl-poll-1"))
        assertTrue(LauncherCpuAffinity.isLightPollerThreadName("dcl-poll-8"))
        assertFalse(LauncherCpuAffinity.isLightPollerThreadName("dcl-can-poll-1"))
        assertFalse(LauncherCpuAffinity.isLightPollerThreadName("dcl-logger"))
    }

    @Test
    fun renderThreadAffinityPolicy_handlesOffAlwaysAndSmartModes() {
        // Mutual exclusion resolution (always takes precedence if both somehow set)
        assertEquals(
            LauncherCpuAffinity.RenderThreadAffinityMode.OFF,
            LauncherCpuAffinity.resolveRenderThreadAffinityMode(alwaysEnabled = false, smartEnabled = false),
        )
        assertEquals(
            LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
            LauncherCpuAffinity.resolveRenderThreadAffinityMode(alwaysEnabled = true, smartEnabled = false),
        )
        assertEquals(
            LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
            LauncherCpuAffinity.resolveRenderThreadAffinityMode(alwaysEnabled = true, smartEnabled = true),
        )
        assertEquals(
            LauncherCpuAffinity.RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP,
            LauncherCpuAffinity.resolveRenderThreadAffinityMode(alwaysEnabled = false, smartEnabled = true),
        )

        // Pinning decision per mode (RenderThread)
        assertFalse(
            LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.OFF,
                isExternalAppOpen = true,
            ),
        )
        assertTrue(
            LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
                isExternalAppOpen = false,
            ),
        )
        assertTrue(
            LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
                isExternalAppOpen = true,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP,
                isExternalAppOpen = false,
            ),
        )
        assertTrue(
            LauncherCpuAffinity.shouldPinRenderThreadToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP,
                isExternalAppOpen = true,
            ),
        )

        // Pinning decision per mode (ART Runtime — active ONLY in smart mode when external app is open)
        assertFalse(
            LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.OFF,
                isExternalAppOpen = true,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
                isExternalAppOpen = false,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.ALWAYS_LITTLE,
                isExternalAppOpen = true,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP,
                isExternalAppOpen = false,
            ),
        )
        assertTrue(
            LauncherCpuAffinity.shouldPinArtRuntimeToLittleCores(
                LauncherCpuAffinity.RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP,
                isExternalAppOpen = true,
            ),
        )

        // Smart external-app detection restores big cores when launcher overlays/settings are open
        assertTrue(
            LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
                hiddenForExternalApp = false,
                activeAppPackage = "ru.yandex.yandexnavi",
                isActiveAppFullscreen = false,
                isWindowFocused = false,
                isLauncherOverlayOpen = false,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
                hiddenForExternalApp = false,
                activeAppPackage = "ru.yandex.yandexnavi",
                isActiveAppFullscreen = false,
                isWindowFocused = false,
                isLauncherOverlayOpen = true,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
                hiddenForExternalApp = false,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = true,
                isLauncherOverlayOpen = false,
            ),
        )
        assertFalse(
            LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
                hiddenForExternalApp = false,
                activeAppPackage = "ru.yandex.yandexnavi",
                isActiveAppFullscreen = true,
                isWindowFocused = true,
                isLauncherOverlayOpen = false,
            ),
        )
        assertTrue(
            LauncherCpuAffinity.isExternalAppForegroundForRenderThread(
                hiddenForExternalApp = true,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = false,
                isLauncherOverlayOpen = false,
            ),
        )
    }
}
