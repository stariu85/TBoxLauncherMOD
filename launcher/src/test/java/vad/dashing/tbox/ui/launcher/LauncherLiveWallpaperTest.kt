package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLiveWallpaperTest {

    @Test
    fun detectMediaType_videoExtensionsAndMime() {
        assertEquals(WallpaperMediaType.VIDEO, detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.mp4"))
        assertEquals(WallpaperMediaType.VIDEO, detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.WEBM"))
        assertEquals(WallpaperMediaType.VIDEO, detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.mkv"))
        assertEquals(WallpaperMediaType.VIDEO, detectWallpaperMediaType("/data/wallpapers/noext", mimeType = "video/mp4"))
    }

    @Test
    fun detectMediaType_gifAndAnimatedWebp() {
        assertEquals(WallpaperMediaType.ANIMATED_IMAGE, detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.gif"))
        assertEquals(WallpaperMediaType.ANIMATED_IMAGE, detectWallpaperMediaType("/data/wallpapers/noext", mimeType = "image/gif"))

        // RIFF....WEBPVP8X....<flags=0x02>
        val animatedVp8x = ByteArray(24)
        "RIFF".toByteArray().copyInto(animatedVp8x, 0)
        "WEBP".toByteArray().copyInto(animatedVp8x, 8)
        "VP8X".toByteArray().copyInto(animatedVp8x, 12)
        animatedVp8x[20] = 0x02
        assertTrue(isAnimatedWebpHeader(animatedVp8x))
        assertEquals(
            WallpaperMediaType.ANIMATED_IMAGE,
            detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.webp", headerBytes = animatedVp8x),
        )

        // Static WebP (VP8X without animation bit)
        val staticVp8x = animatedVp8x.copyOf()
        staticVp8x[20] = 0x10 // alpha bit only
        assertFalse(isAnimatedWebpHeader(staticVp8x))
        assertEquals(
            WallpaperMediaType.STATIC_IMAGE,
            detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.webp", headerBytes = staticVp8x),
        )
    }

    @Test
    fun detectMediaType_staticImagesAndEmpty() {
        assertEquals(WallpaperMediaType.STATIC_IMAGE, detectWallpaperMediaType(""))
        assertEquals(WallpaperMediaType.STATIC_IMAGE, detectWallpaperMediaType("/data/wallpapers/wallpaper_dark.jpg"))
        assertEquals(WallpaperMediaType.STATIC_IMAGE, detectWallpaperMediaType("/data/wallpapers/wallpaper_light.png"))
    }

    @Test
    fun smartPause_playsOnCleanDesktop() {
        val paused = shouldPauseLiveWallpaper(
            isFirstDrawComplete = true,
            isActivityStarted = true,
            isWindowFocused = true,
            hiddenForExternalApp = false,
            activeAppPackage = null,
            isActiveAppFullscreen = false,
            isTopShadeOpen = false,
            isVehicleSettingsOpen = false,
            isVehicleSettingsOverlayShowing = false,
            isVehicleSettingsActivityOpen = false,
            isAboutOverlayShowing = false,
            isQuickSettingsOpen = false,
            isAvmActive = false,
            isCamdCameraActive = false,
        )
        assertFalse(paused)
    }

    @Test
    fun smartPause_playsInHideOffScreenHomeModeWhenStartedWithoutFocus() {
        // In Android 9 off-screen minimize (2000,2000) or dclwm fullscreen/moveTaskToBack races,
        // HOME is visible (STARTED) with activeAppPackage == null even when paused/unfocused.
        val paused = shouldPauseLiveWallpaper(
            isFirstDrawComplete = true,
            isActivityStarted = true,
            isWindowFocused = false,
            hiddenForExternalApp = false,
            activeAppPackage = null,
            isActiveAppFullscreen = false,
            isTopShadeOpen = false,
            isVehicleSettingsOpen = false,
            isVehicleSettingsOverlayShowing = false,
            isVehicleSettingsActivityOpen = false,
            isAboutOverlayShowing = false,
            isQuickSettingsOpen = false,
            isAvmActive = false,
            isCamdCameraActive = false,
        )
        assertFalse(paused)
    }

    @Test
    fun smartPause_pausesOnEveryCoveringCondition() {
        fun call(
            firstDraw: Boolean = true,
            started: Boolean = true,
            focused: Boolean = true,
            hidden: Boolean = false,
            pkg: String? = null,
            fullscreen: Boolean = false,
            shade: Boolean = false,
            vehSettings: Boolean = false,
            vehOverlay: Boolean = false,
            vehActivity: Boolean = false,
            about: Boolean = false,
            quick: Boolean = false,
            avm: Boolean = false,
            camd: Boolean = false,
        ) = shouldPauseLiveWallpaper(
            isFirstDrawComplete = firstDraw,
            isActivityStarted = started,
            isWindowFocused = focused,
            hiddenForExternalApp = hidden,
            activeAppPackage = pkg,
            isActiveAppFullscreen = fullscreen,
            isTopShadeOpen = shade,
            isVehicleSettingsOpen = vehSettings,
            isVehicleSettingsOverlayShowing = vehOverlay,
            isVehicleSettingsActivityOpen = vehActivity,
            isAboutOverlayShowing = about,
            isQuickSettingsOpen = quick,
            isAvmActive = avm,
            isCamdCameraActive = camd,
        )

        assertTrue("before first draw", call(firstDraw = false))
        assertTrue("activity stopped", call(started = false))
        assertTrue("hidden for external app", call(hidden = true))
        assertTrue("embedded foreign app", call(pkg = "ru.yandex.yandexnavi", fullscreen = false, focused = true))
        assertTrue("fullscreen foreign app unfocused", call(pkg = "com.google.android.youtube", fullscreen = true, focused = false))
        assertTrue("top shade open", call(shade = true))
        assertTrue("vehicle settings open", call(vehSettings = true))
        assertTrue("vehicle settings overlay", call(vehOverlay = true))
        assertTrue("vehicle settings activity", call(vehActivity = true))
        assertTrue("about overlay", call(about = true))
        assertTrue("quick settings", call(quick = true))
        assertTrue("AVM 360 active", call(avm = true))
        assertTrue("Camd camera active", call(camd = true))
    }

    @Test
    fun smartPause_resumesIfFullscreenAppExitedBackToFocusedHome() {
        val paused = shouldPauseLiveWallpaper(
            isFirstDrawComplete = true,
            isActivityStarted = true,
            isWindowFocused = true,
            hiddenForExternalApp = false,
            activeAppPackage = "com.google.android.youtube",
            isActiveAppFullscreen = true,
            isTopShadeOpen = false,
            isVehicleSettingsOpen = false,
            isVehicleSettingsOverlayShowing = false,
            isVehicleSettingsActivityOpen = false,
            isAboutOverlayShowing = false,
            isQuickSettingsOpen = false,
            isAvmActive = false,
            isCamdCameraActive = false,
        )
        assertFalse(paused)
    }

    @Test
    fun videoTransformScale_matchesWallpaperScaleModes() {
        // View 1200x800 (1.5), Video 1920x1080 (16/9 = 1.7778)
        val fillHeight = computeVideoTransformScale(1200, 800, 1920, 1080, WallpaperScaleMode.FILL_HEIGHT)
        assertEquals((16f / 9f) / 1.5f, fillHeight.scaleX, 0.0001f)
        assertEquals(1f, fillHeight.scaleY, 0.0001f)

        val fillWidth = computeVideoTransformScale(1200, 800, 1920, 1080, WallpaperScaleMode.FILL_WIDTH)
        assertEquals(1f, fillWidth.scaleX, 0.0001f)
        assertEquals(1.5f / (16f / 9f), fillWidth.scaleY, 0.0001f)

        val fillBounds = computeVideoTransformScale(1200, 800, 1920, 1080, WallpaperScaleMode.FILL_BOUNDS)
        assertEquals(1f, fillBounds.scaleX, 0.0001f)
        assertEquals(1f, fillBounds.scaleY, 0.0001f)

        // Zero guard
        val zero = computeVideoTransformScale(0, 800, 1920, 1080, WallpaperScaleMode.FILL_HEIGHT)
        assertEquals(1f, zero.scaleX, 0.0001f)
        assertEquals(1f, zero.scaleY, 0.0001f)
    }

    @Test
    fun drawableMatrixParams_matchesWallpaperScaleModes() {
        // View 1200x800, Drawable 600x400 (same aspect 1.5)
        val fillHeight = computeDrawableMatrixParams(1200, 800, 600, 400, WallpaperScaleMode.FILL_HEIGHT)
        assertEquals(2f, fillHeight.scaleX, 0.0001f)
        assertEquals(2f, fillHeight.scaleY, 0.0001f)
        assertEquals(0f, fillHeight.translateX, 0.0001f)
        assertEquals(0f, fillHeight.translateY, 0.0001f)

        // View 1200x800, Drawable 1920x1080 -> FILL_WIDTH scales to width 1200, height 675, ty = (800-675)/2 = 62.5
        val fillWidth = computeDrawableMatrixParams(1200, 800, 1920, 1080, WallpaperScaleMode.FILL_WIDTH)
        assertEquals(1200f / 1920f, fillWidth.scaleX, 0.0001f)
        assertEquals(1200f / 1920f, fillWidth.scaleY, 0.0001f)
        assertEquals(0f, fillWidth.translateX, 0.0001f)
        assertEquals(62.5f, fillWidth.translateY, 0.0001f)

        val fillBounds = computeDrawableMatrixParams(1200, 800, 1920, 1080, WallpaperScaleMode.FILL_BOUNDS)
        assertEquals(1200f / 1920f, fillBounds.scaleX, 0.0001f)
        assertEquals(800f / 1080f, fillBounds.scaleY, 0.0001f)
        assertEquals(0f, fillBounds.translateX, 0.0001f)
        assertEquals(0f, fillBounds.translateY, 0.0001f)
    }
}
