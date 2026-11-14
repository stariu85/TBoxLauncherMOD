package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YandexNavWindowTrackerTest {

    @Before
    fun setUp() {
        YandexNavWindowTracker.setWindowOpenForTest(false)
    }

    @Test
    fun isYandexNavigationPackage_correctlyIdentifiesNavigationPackages() {
        assertTrue(isYandexNavigationPackage("ru.yandex.yandexnavi"))
        assertTrue(isYandexNavigationPackage("ru.yandex.yandexmaps"))
        assertTrue(isYandexNavigationPackage("ru.yandex.mobile.navigator"))
        assertTrue(isYandexNavigationPackage("com.yandex.navi"))
        assertTrue(isYandexNavigationPackage("ru.yandex.navi"))
        assertTrue(isYandexNavigationPackage("ru.yandex.autonavi"))
        assertTrue(isYandexNavigationPackage("ru.yandex.yandexnavi.sandbox"))
    }

    @Test
    fun isYandexNavigationPackage_rejectsNonNavigationPackages() {
        assertFalse(isYandexNavigationPackage("ru.yandex.music"))
        assertFalse(isYandexNavigationPackage("ru.yandex.radio"))
        assertFalse(isYandexNavigationPackage("ru.yandex.disk"))
        assertFalse(isYandexNavigationPackage("ru.yandex.weather"))
        assertFalse(isYandexNavigationPackage("vad.dashing.tbox"))
        assertFalse(isYandexNavigationPackage("com.android.settings"))
        assertFalse(isYandexNavigationPackage(""))
        assertFalse(isYandexNavigationPackage(null))
    }

    @Test
    fun resolveEffectiveNavSpeedLimit_showsLimitWhenYandexWindowOpen() {
        // Yandex limit preferred
        assertEquals("60", resolveEffectiveNavSpeedLimit(isYandexWindowOpen = true, yandexLimit = "60", cameraLimit = "90"))
        // Camera fallback if yandex limit null
        assertEquals("90", resolveEffectiveNavSpeedLimit(isYandexWindowOpen = true, yandexLimit = null, cameraLimit = "90"))
        // Null if no limits available
        assertNull(resolveEffectiveNavSpeedLimit(isYandexWindowOpen = true, yandexLimit = null, cameraLimit = null))
    }

    @Test
    fun resolveEffectiveNavSpeedLimit_removesLimitWhenYandexWindowClosed() {
        // When window is closed, speed limit is removed regardless of available data
        assertNull(resolveEffectiveNavSpeedLimit(isYandexWindowOpen = false, yandexLimit = "60", cameraLimit = "90"))
        assertNull(resolveEffectiveNavSpeedLimit(isYandexWindowOpen = false, yandexLimit = "80", cameraLimit = null))
        assertNull(resolveEffectiveNavSpeedLimit(isYandexWindowOpen = false, yandexLimit = null, cameraLimit = "90"))
        assertNull(resolveEffectiveNavSpeedLimit(isYandexWindowOpen = false, yandexLimit = null, cameraLimit = null))
    }

    @Test
    fun tracker_transitionsStateOnLifecycleEvents() {
        assertFalse(YandexNavWindowTracker.isWindowOpen.value)

        // Launch Yandex Navi -> opened
        YandexNavWindowTracker.onAppLaunched("ru.yandex.yandexnavi")
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        // Press Home -> window is moved off-screen beyond screen boundary -> STILL OPEN!
        YandexNavWindowTracker.onHomePressed()
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        // Minimized off-screen via dclwm -> STILL OPEN!
        YandexNavWindowTracker.onWindowMinimized("ru.yandex.yandexnavi")
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        // Freeform window removed/closed -> closed
        YandexNavWindowTracker.onFreeformRemoved("ru.yandex.yandexnavi")
        assertFalse(YandexNavWindowTracker.isWindowOpen.value)

        // Launch Yandex Maps -> opened
        YandexNavWindowTracker.onAppLaunched("ru.yandex.yandexmaps")
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        // Explicitly close app -> closed
        YandexNavWindowTracker.onAppClosed("ru.yandex.yandexmaps")
        assertFalse(YandexNavWindowTracker.isWindowOpen.value)
    }

    @Test
    fun tracker_offScreenParkedWindowIsNotConsideredClosed() {
        // Window parked at (2000, 2000) beyond screen edges is physically off-screen
        assertFalse(isWindowBoundsOnScreen(left = 2000, top = 2000, right = 2200, bottom = 2200, visible = true))

        // But in window tracker, parking off-screen or minimizing beyond screen boundary does NOT close the window
        YandexNavWindowTracker.onAppLaunched("ru.yandex.yandexnavi")
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        YandexNavWindowTracker.onWindowMinimized("ru.yandex.yandexnavi")
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)

        YandexNavWindowTracker.onHomePressed()
        assertTrue(YandexNavWindowTracker.isWindowOpen.value)
    }

    @Test
    fun isWindowBoundsOnScreen_detectsOffScreenParkedWindows() {
        // dclwm parked off-screen window (2000, 2000 - 2200, 2200)
        assertFalse(isWindowBoundsOnScreen(left = 2000, top = 2000, right = 2200, bottom = 2200, visible = true))

        // Off-screen right edge
        assertFalse(isWindowBoundsOnScreen(left = 1920, top = 50, right = 2500, bottom = 900, visible = true))

        // Off-screen bottom edge
        assertFalse(isWindowBoundsOnScreen(left = 344, top = 1080, right = 1920, bottom = 2000, visible = true))

        // Normal embedded window on screen
        assertTrue(isWindowBoundsOnScreen(left = 344, top = 51, right = 1920, bottom = 998, visible = true))

        // Normal fullscreen window on screen
        assertTrue(isWindowBoundsOnScreen(left = 0, top = 0, right = 1920, bottom = 1080, visible = true))

        // Invisible window
        assertFalse(isWindowBoundsOnScreen(left = 344, top = 51, right = 1920, bottom = 998, visible = false))
    }
}
