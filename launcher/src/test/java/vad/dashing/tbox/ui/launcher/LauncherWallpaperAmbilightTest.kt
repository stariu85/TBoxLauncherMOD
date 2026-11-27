package vad.dashing.tbox.ui.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWallpaperAmbilightTest {

    @Test
    fun modeFromCodeMapsValidAndFallbackValues() {
        assertEquals(WallpaperAmbilightMode.OFF, WallpaperAmbilightMode.fromCode(0))
        assertEquals(WallpaperAmbilightMode.EDGE_GRADIENT, WallpaperAmbilightMode.fromCode(1))
        // Legacy blurred mode code (2) gracefully migrates to EDGE_GRADIENT
        assertEquals(WallpaperAmbilightMode.EDGE_GRADIENT, WallpaperAmbilightMode.fromCode(2))
        assertEquals(WallpaperAmbilightMode.OFF, WallpaperAmbilightMode.fromCode(-1))
        assertEquals(WallpaperAmbilightMode.OFF, WallpaperAmbilightMode.fromCode(99))
    }

    @Test
    fun shouldShowWallpaperAmbilightAlwaysTurnsOffWhenAnyAppIsOpen() {
        // Off mode stays off
        assertFalse(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.OFF,
                isActivityStarted = true,
                hiddenForExternalApp = false,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = true,
            ),
        )

        // Embedded / desktop / full-width app turns Ambilight off
        assertFalse(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.EDGE_GRADIENT,
                isActivityStarted = true,
                hiddenForExternalApp = false,
                activeAppPackage = "ru.yandex.yandexnavi",
                isActiveAppFullscreen = false,
                isWindowFocused = false,
            ),
        )

        // Fullscreen app (hiddenForExternalApp) turns Ambilight off
        assertFalse(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.BLURRED_WALLPAPER,
                isActivityStarted = true,
                hiddenForExternalApp = true,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = false,
            ),
        )

        // Activity stopped by an external fullscreen app turns Ambilight off
        assertFalse(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.EDGE_GRADIENT,
                isActivityStarted = false,
                hiddenForExternalApp = false,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = false,
            ),
        )

        // Returning to Home from fullscreen app recovers Ambilight when focused and started
        assertTrue(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.BLURRED_WALLPAPER,
                isActivityStarted = true,
                hiddenForExternalApp = false,
                activeAppPackage = "com.example.video",
                isActiveAppFullscreen = true,
                isWindowFocused = true,
            ),
        )

        // Clean Home desktop turns Ambilight on
        assertTrue(
            shouldShowWallpaperAmbilight(
                mode = WallpaperAmbilightMode.BLURRED_WALLPAPER,
                isActivityStarted = true,
                hiddenForExternalApp = false,
                activeAppPackage = null,
                isActiveAppFullscreen = false,
                isWindowFocused = true,
            ),
        )
    }

    @Test
    fun visibleCropRectRespectsScaleModes() {
        val fillBounds = computeVisibleWallpaperCropRect(200, 100, 1.0f, WallpaperScaleMode.FILL_BOUNDS)
        assertEquals(WallpaperCropRect(0, 0, 200, 100), fillBounds)

        val fillHeight = computeVisibleWallpaperCropRect(200, 100, 1.0f, WallpaperScaleMode.FILL_HEIGHT)
        assertEquals(WallpaperCropRect(50, 0, 100, 100), fillHeight)

        val fillWidth = computeVisibleWallpaperCropRect(100, 200, 2.0f, WallpaperScaleMode.FILL_WIDTH)
        assertEquals(WallpaperCropRect(0, 75, 100, 50), fillWidth)
    }

    @Test
    fun tuneAmbilightColorPreservesDarkShadowsAndKeepsBrightColorsVivid() {
        val pureWhite = 0xFFFFFFFF.toInt()
        val pureBlack = 0xFF000000.toInt()
        val darkNavyShadow = 0xFF08101C.toInt()
        val deepBlue = 0xFF0A2463.toInt()
        val brightSkyBlue = 0xFF4696DC.toInt()
        val brightCyan = 0xFF00FFFF.toInt()

        val darkFromWhite = tuneAmbilightColorForTheme(pureWhite, isDarkTheme = true)
        assertTrue("Dark theme tint should keep white icons readable", darkFromWhite.red < 0.45f)

        // Near-black tinted shadow (#08101C) must STAY dark in dark theme, never inflated to bright blue
        val darkShadow = tuneAmbilightColorForTheme(darkNavyShadow, isDarkTheme = true)
        assertTrue("Dark shadow must stay dark in dark theme (got $darkShadow)", darkShadow.blue < 0.16f && darkShadow.red < 0.10f)

        // Deep saturated blue in dark theme should still be visibly blue
        val darkBlue = tuneAmbilightColorForTheme(deepBlue, isDarkTheme = true)
        assertTrue("Dark theme saturated blue should be visibly illuminated", darkBlue.blue > 0.35f)

        // Bright sky blue should glow vividly and be much brighter than dark shadow
        val darkSky = tuneAmbilightColorForTheme(brightSkyBlue, isDarkTheme = true)
        assertTrue("Bright sky should glow vividly in dark theme", darkSky.blue > 0.60f && darkSky.blue > darkShadow.blue * 3f)

        // Pure black in light theme must NEVER darken light panels (#E5E7EB ~ 0.90)
        val lightFromBlack = tuneAmbilightColorForTheme(pureBlack, isDarkTheme = false)
        assertTrue("Light theme black edge must stay bright neutral", lightFromBlack.red >= 0.88f)
        assertTrue("Light theme black edge must stay bright neutral", lightFromBlack.green >= 0.88f)
        assertTrue("Light theme black edge must stay bright neutral", lightFromBlack.blue >= 0.88f)

        // Colorful accents in light theme should be clearly vivid (strong chroma separation, not washed-out white)
        val lightCyan = tuneAmbilightColorForTheme(brightCyan, isDarkTheme = false)
        assertTrue("Light theme cyan should have vivid saturation", (lightCyan.blue - lightCyan.red) > 0.40f)
        assertTrue("Light theme cyan should stay bright enough for dark icons", lightCyan.green > 0.75f)

        val darkCyan = tuneAmbilightColorForTheme(brightCyan, isDarkTheme = true)
        assertTrue("Hue should be preserved", darkCyan.blue > darkCyan.red * 2f)
        assertTrue("Hue should be preserved", darkCyan.green > darkCyan.red * 2f)
    }

    @Test
    fun toneMapBlurredStripPreventsDarkeningInLightThemeAndLiftsColorInDarkTheme() {
        val lightPixels = intArrayOf(0xFF000000.toInt(), 0xFF102030.toInt())
        toneMapBlurredStripForThemeInPlace(lightPixels, isDarkTheme = false)
        val blackMappedR = (lightPixels[0] ushr 16) and 0xFF
        val blackMappedG = (lightPixels[0] ushr 8) and 0xFF
        val blackMappedB = lightPixels[0] and 0xFF
        assertTrue("Black pixel in light blurred strip must not darken panels", blackMappedR >= 220)
        assertTrue("Black pixel in light blurred strip must not darken panels", blackMappedG >= 220)
        assertTrue("Black pixel in light blurred strip must not darken panels", blackMappedB >= 220)

        val darkPixels = intArrayOf(0xFF08101C.toInt(), 0xFF0A2463.toInt())
        toneMapBlurredStripForThemeInPlace(darkPixels, isDarkTheme = true)
        val shadowBlue = darkPixels[0] and 0xFF
        val liftedBlue = darkPixels[1] and 0xFF
        assertTrue("Dark shadow pixel in dark blurred strip should remain dark", shadowBlue < 45)
        assertTrue("Saturated deep blue in dark blurred strip should glow visibly", liftedBlue >= 80)
    }

    @Test
    fun sampleEdgeSegmentColorsStaysLocalizedAndIgnoresCenterColor() {
        val w = 48
        val h = 32
        // Bright yellow center should NEVER bleed into red top, green bottom, or blue left edges
        val pixels = IntArray(w * h) { 0xFFFFFF00.toInt() }
        for (y in 0 until 5) {
            for (x in 0 until w) pixels[y * w + x] = if (x < w / 2) 0xFFE53935.toInt() else 0xFF1E88E5.toInt()
        }
        for (y in (h - 5) until h) {
            for (x in 0 until w) pixels[y * w + x] = 0xFF43A047.toInt()
        }
        for (y in 5 until (h - 5)) {
            for (x in 0 until 8) pixels[y * w + x] = 0xFF8E24AA.toInt()
        }

        val top = sampleEdgeSegmentColors(pixels, w, h, AmbilightEdge.TOP, segmentCount = 6, isDarkTheme = true)
        val bottom = sampleEdgeSegmentColors(pixels, w, h, AmbilightEdge.BOTTOM, segmentCount = 6, isDarkTheme = true)
        val left = sampleEdgeSegmentColors(pixels, w, h, AmbilightEdge.LEFT, segmentCount = 6, isDarkTheme = true)

        assertEquals(6, top.size)
        // Top bar must show red on the left half and blue on the right half (localized gradient)
        assertTrue("Top-left segment should be red-dominant", top[0].red > top[0].blue * 1.8f && top[0].red > top[0].green * 1.5f)
        assertTrue("Top-right segment should be blue-dominant", top[5].blue > top[5].red * 1.8f && top[5].blue > top[5].green * 1.5f)
        assertTrue("Bottom center should stay green, not yellow center", bottom[3].green > bottom[3].red * 1.5f)
        assertTrue("Left middle should stay purple, not yellow center", left[3].blue > left[3].green * 1.5f && left[3].red > left[3].green * 1.3f)
    }

    @Test
    fun sampleEdgeSegmentColorsRespectsBlackBorderRegionsWithoutPullingInnerColors() {
        val w = 48
        val h = 32
        // Outer 15% border is pure black while inner area is bright cyan/orange
        val pixels = IntArray(w * h) { 0xFF000000.toInt() }
        for (y in 5 until 10) {
            for (x in 0 until w) pixels[y * w + x] = 0xFF00D8FF.toInt()
        }
        for (y in (h - 10) until (h - 5)) {
            for (x in 0 until w) pixels[y * w + x] = 0xFFFF6D00.toInt()
        }

        val topDark = sampleEdgeSegmentColors(pixels, w, h, AmbilightEdge.TOP, segmentCount = 3, isDarkTheme = true)
        val bottomDark = sampleEdgeSegmentColors(pixels, w, h, AmbilightEdge.BOTTOM, segmentCount = 3, isDarkTheme = true)

        assertTrue("Black top edge must stay dark neutral, not pull inner cyan", topDark[1].blue < 0.20f && topDark[1].red < 0.20f)
        assertTrue("Black bottom edge must stay dark neutral, not pull inner orange", bottomDark[1].red < 0.20f && bottomDark[1].green < 0.20f)
    }

    @Test
    fun boxBlurSmoothsSharpBoundaryAndPreservesOpaqueAlpha() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        boxBlurArgbInPlace(pixels, w, h, radius = 2, passes = 2)

        val midPixel = pixels[(h / 2) * w + (w / 2)]
        val alpha = (midPixel ushr 24) and 0xFF
        val red = (midPixel ushr 16) and 0xFF
        val blue = midPixel and 0xFF
        assertEquals(0xFF, alpha)
        assertTrue("Boundary pixel should contain blurred red", red > 40)
        assertTrue("Boundary pixel should contain blurred blue", blue > 40)
    }

    @Test
    fun mirroredEdgeStripProducesValidDimensionsAndSeamContinuity() {
        val w = 24
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (y < 4) 0xFFFF0000.toInt() else if (x < 6) 0xFF0000FF.toInt() else 0xFF00FF00.toInt()
        }

        val (topPx, topW, topH) = createMirroredEdgeStripArgb(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            edge = AmbilightEdge.TOP,
            stripDepth = 8,
            leftPanelFraction = 0.25f,
        )
        assertTrue(topW > w)
        assertEquals(8, topH)
        assertEquals(topW * topH, topPx.size)
        val seamPixel = topPx[(topH - 1) * topW + (topW - 2)]
        val red = (seamPixel ushr 16) and 0xFF
        val green = (seamPixel ushr 8) and 0xFF
        assertTrue("Top strip seam should reflect red top edge", red > green)

        val (leftPx, leftW, leftH) = createMirroredEdgeStripArgb(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            edge = AmbilightEdge.LEFT,
            stripDepth = 8,
            leftPanelFraction = 0.25f,
        )
        assertEquals(8, leftW)
        assertEquals(h, leftH)
        assertEquals(leftW * leftH, leftPx.size)
    }

    @Test
    fun liveFrameHelpersFilterBlackBuffersAndBlendSmoothly() {
        val allBlack = IntArray(64) { 0xFF000000.toInt() }
        val allTransparent = IntArray(64) { 0x00000000 }
        val visibleBlue = IntArray(64) { 0xFF0088FF.toInt() }
        val visibleRed = IntArray(64) { 0xFFFF0000.toInt() }

        assertFalse(hasVisibleFrameContent(allBlack))
        assertFalse(hasVisibleFrameContent(allTransparent))
        assertTrue(hasVisibleFrameContent(visibleBlue))

        val blended = blendPixelBuffers(visibleRed, visibleBlue, blendFactor = 0.5f)
        val r = (blended[0] ushr 16) and 0xFF
        val b = blended[0] and 0xFF
        assertTrue("Blended red channel should be near half", r in 115..140)
        assertTrue("Blended blue channel should be near half", b in 115..140)
    }

    @Test
    fun liveBridgePublishesAndClearsBySourceKey() {
        LiveWallpaperAmbilightBridge.clear()
        val keyA = LiveWallpaperAmbilightBridge.buildSourceKey("/a.mp4", 1, true, WallpaperAmbilightMode.EDGE_GRADIENT)
        val keyB = LiveWallpaperAmbilightBridge.buildSourceKey("/b.mp4", 1, true, WallpaperAmbilightMode.EDGE_GRADIENT)
        val sample = WallpaperAmbilightData(
            topEdgeColors = listOf(Color.Cyan),
            bottomEdgeColors = listOf(Color.Blue),
            leftEdgeColors = listOf(Color.Magenta),
            leftAverageColor = Color.Magenta,
            leftHorizonColor = Color.Magenta,
        )

        assertTrue(LiveWallpaperAmbilightBridge.publish(keyA, sample))
        assertEquals(keyA, LiveWallpaperAmbilightBridge.liveEntryFlow.value?.sourceKey)

        // Tiny sub-threshold jitter (ΔRGB < 4) for the same source must be skipped
        val tinyChange = sample.copy(
            topEdgeColors = listOf(Color(red = 2f / 255f, green = 1f, blue = 1f, alpha = 1f)),
        )
        assertFalse(
            "Sub-threshold ΔRGB < 4 must be skipped",
            LiveWallpaperAmbilightBridge.publish(keyA, tinyChange),
        )
        assertEquals(sample, LiveWallpaperAmbilightBridge.liveEntryFlow.value?.data)

        // Meaningful change (ΔRGB >= 4) must be published
        val meaningfulChange = sample.copy(
            topEdgeColors = listOf(Color(red = 10f / 255f, green = 1f, blue = 1f, alpha = 1f)),
        )
        assertTrue(
            "Meaningful ΔRGB >= 4 must be published",
            LiveWallpaperAmbilightBridge.publish(keyA, meaningfulChange),
        )
        assertEquals(meaningfulChange, LiveWallpaperAmbilightBridge.liveEntryFlow.value?.data)

        // Clearing a different key must not clear keyA
        LiveWallpaperAmbilightBridge.clear(keyB)
        assertEquals(keyA, LiveWallpaperAmbilightBridge.liveEntryFlow.value?.sourceKey)

        // Clearing matching key clears entry
        LiveWallpaperAmbilightBridge.clear(keyA)
        assertNull(LiveWallpaperAmbilightBridge.liveEntryFlow.value)
    }

    @Test
    fun syncedAnimatorInterpolatesOn30FpsClockAndUnregistersWhenSettled() {
        val clock = LauncherSynced30FpsClock()
        val uiState = LauncherAmbilightUiState()
        uiState.modeState.value = WallpaperAmbilightMode.EDGE_GRADIENT
        val animator = AmbilightSyncedAnimator(uiState)
        animator.attach(clock)
        animator.setShouldShow(true, durationMillis = 200)

        val redFrame = WallpaperAmbilightData(
            topEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Red },
            bottomEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Red },
            leftEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Red },
            leftAverageColor = Color.Red,
            leftHorizonColor = Color.Red,
        )
        // First frame snaps colors immediately and starts alpha fade-in on the shared clock
        animator.applyTargetData(redFrame, fallbackColor = Color.Black, durationMillis = 300)
        assertEquals(1f, uiState.topEdgeColorStates[0].value.red, 0.01f)
        assertTrue("Clock should be active while alpha fades in", clock.hasActiveListeners)

        // Advance alpha to completion using realistic ~30 FPS steps (34 ms x 11 = 374 ms > 360 ms)
        var stillActive = true
        repeat(11) { stillActive = animator.step(34_000_000L) }
        assertFalse("Alpha should finish after ~374 ms", stillActive)
        assertEquals(1f, uiState.alphaState.floatValue, 0.01f)

        // Retarget to blue -> smoothly interpolates at midpoint and settles at end
        val blueFrame = WallpaperAmbilightData(
            topEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Blue },
            bottomEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Blue },
            leftEdgeColors = List(AMBILIGHT_EDGE_SEGMENTS) { Color.Blue },
            leftAverageColor = Color.Blue,
            leftHorizonColor = Color.Blue,
        )
        animator.applyTargetData(blueFrame, fallbackColor = Color.Black, durationMillis = 300)
        assertTrue("Animator should be active after retargeting colors", animator.isAnimating)
        assertTrue("Shared clock should have listener attached", clock.hasActiveListeners)

        repeat(4) { stillActive = animator.step(34_000_000L) }
        assertTrue("Midpoint (~136 ms) should still be animating", stillActive)
        val midColor = uiState.topEdgeColorStates[0].value
        assertTrue("Midpoint should blend red and blue", midColor.red in 0.05f..0.95f && midColor.blue in 0.05f..0.95f)

        repeat(6) { stillActive = animator.step(34_000_000L) }
        assertFalse("Tween should settle after total ~340 ms", stillActive)
        assertEquals(1f, uiState.topEdgeColorStates[0].value.blue, 0.01f)
        assertEquals(0f, uiState.topEdgeColorStates[0].value.red, 0.01f)

        // Resetting for OFF clears state and unregisters clock listener
        animator.resetForOff()
        assertFalse("Clock should have zero listeners after OFF", clock.hasActiveListeners)
        assertEquals(0f, uiState.alphaState.floatValue, 0.001f)
        assertFalse(uiState.isVisuallyActive)
    }
}
