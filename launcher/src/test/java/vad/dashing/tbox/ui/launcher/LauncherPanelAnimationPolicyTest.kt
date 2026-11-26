package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPanelAnimationPolicyTest {

    private fun eval(
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
    ): Boolean = shouldAnimateRightPanel(
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

    @Test
    fun animatesOnCleanDesktop() {
        assertTrue(eval())
    }

    @Test
    fun animatesInAndroid9OffscreenMinimizeModeWhenStartedWithoutFocus() {
        assertTrue(eval(started = true, focused = false, pkg = null))
    }

    @Test
    fun pausesWhenCoveredByExternalAppOrOverlaysOrCameras() {
        assertFalse(eval(firstDraw = false))
        assertFalse(eval(started = false))
        assertFalse(eval(hidden = true))
        assertFalse(eval(pkg = "ru.yandex.yandexnavi"))
        assertFalse(eval(pkg = "ru.yandex.yandexnavi", fullscreen = true, focused = false))
        assertFalse(eval(shade = true))
        assertFalse(eval(vehSettings = true))
        assertFalse(eval(vehOverlay = true))
        assertFalse(eval(vehActivity = true))
        assertFalse(eval(about = true))
        assertFalse(eval(quick = true))
        assertFalse(eval(avm = true))
        assertFalse(eval(camd = true))
    }

    @Test
    fun resumesWhenRecoveredFromFullscreenWithFocus() {
        assertTrue(eval(pkg = "ru.yandex.yandexnavi", fullscreen = true, focused = true))
    }
}
