package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.LauncherWindowState
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdConnectionStatus
import vad.dashing.tbox.camd.CamdWidgetCameraMode

internal data class RightPanelAnimationSnapshot(
    val isFirstDrawComplete: Boolean,
    val isActivityStarted: Boolean,
    val isWindowFocused: Boolean,
    val hiddenForExternalApp: Boolean,
    val activeAppPackage: String?,
    val isActiveAppFullscreen: Boolean,
    val isTopShadeOpen: Boolean,
    val isVehicleSettingsOpen: Boolean,
    val isVehicleSettingsOverlayShowing: Boolean,
    val isVehicleSettingsActivityOpen: Boolean,
    val isAboutOverlayShowing: Boolean,
    val isQuickSettingsOpen: Boolean,
    val isAvmActive: Boolean,
    val isCamdCameraActive: Boolean,
) {
    val shouldAnimate: Boolean
        get() = shouldAnimateRightPanel(
            isFirstDrawComplete = isFirstDrawComplete,
            isActivityStarted = isActivityStarted,
            isWindowFocused = isWindowFocused,
            hiddenForExternalApp = hiddenForExternalApp,
            activeAppPackage = activeAppPackage,
            isActiveAppFullscreen = isActiveAppFullscreen,
            isTopShadeOpen = isTopShadeOpen,
            isVehicleSettingsOpen = isVehicleSettingsOpen,
            isVehicleSettingsOverlayShowing = isVehicleSettingsOverlayShowing,
            isVehicleSettingsActivityOpen = isVehicleSettingsActivityOpen,
            isAboutOverlayShowing = isAboutOverlayShowing,
            isQuickSettingsOpen = isQuickSettingsOpen,
            isAvmActive = isAvmActive,
            isCamdCameraActive = isCamdCameraActive,
        )

    val shouldAnimateLeftPanel: Boolean
        get() = shouldAnimateLeftPanel(
            isFirstDrawComplete = isFirstDrawComplete,
            isActivityStarted = isActivityStarted,
            isWindowFocused = isWindowFocused,
            hiddenForExternalApp = hiddenForExternalApp,
            activeAppPackage = activeAppPackage,
            isActiveAppFullscreen = isActiveAppFullscreen,
            isTopShadeOpen = isTopShadeOpen,
            isVehicleSettingsOpen = isVehicleSettingsOpen,
            isVehicleSettingsOverlayShowing = isVehicleSettingsOverlayShowing,
            isVehicleSettingsActivityOpen = isVehicleSettingsActivityOpen,
            isAboutOverlayShowing = isAboutOverlayShowing,
            isQuickSettingsOpen = isQuickSettingsOpen,
            isAvmActive = isAvmActive,
            isCamdCameraActive = isCamdCameraActive,
        )
}

/**
 * Pure predicate deciding whether right-panel / desktop animations (live wallpapers,
 * track title marquee) should run.
 *
 * Uses [isActivityStarted] (onStart/onStop) rather than onResume so animations still run reliably
 * on Android 9 in all Home button modes — including off-screen freeform hiding (`dclwm minimize`
 * to 2000,2000), fullscreen windowing-mode switching, and moveTaskToBack, where a background
 * freeform stack may keep HOME in STARTED instead of RESUMED.
 *
 * Returns `false` whenever an external app is open in the working area, the home window is hidden
 * or stopped, or a shade/settings/overlay/camera (AVM or Camd) is covering the screen.
 */
internal fun shouldAnimateRightPanel(
    isFirstDrawComplete: Boolean,
    isActivityStarted: Boolean,
    isWindowFocused: Boolean,
    hiddenForExternalApp: Boolean,
    activeAppPackage: String?,
    isActiveAppFullscreen: Boolean,
    isTopShadeOpen: Boolean,
    isVehicleSettingsOpen: Boolean,
    isVehicleSettingsOverlayShowing: Boolean,
    isVehicleSettingsActivityOpen: Boolean = false,
    isAboutOverlayShowing: Boolean,
    isQuickSettingsOpen: Boolean,
    isAvmActive: Boolean,
    isCamdCameraActive: Boolean,
): Boolean {
    if (!isFirstDrawComplete) return false
    if (!isActivityStarted) return false
    if (hiddenForExternalApp) return false
    if (isTopShadeOpen) return false
    if (isVehicleSettingsOpen || isVehicleSettingsOverlayShowing || isVehicleSettingsActivityOpen) return false
    if (isAboutOverlayShowing || isQuickSettingsOpen) return false
    if (isAvmActive || isCamdCameraActive) return false
    if (!activeAppPackage.isNullOrBlank()) {
        val recoveredFromFullscreen = isActiveAppFullscreen && isWindowFocused
        if (!recoveredFromFullscreen) return false
    }
    return true
}

/**
 * Pure predicate deciding whether left-panel widget animations (such as the 15 FPS rotating
 * player name ring) should run.
 *
 * Unlike [shouldAnimateRightPanel], keeps running when an external app is open in the right
 * working area (`isActiveAppFullscreen == false`), because the left panel remains visible on
 * screen. Still pauses when a fullscreen external app covers the launcher, when the home window
 * is stopped/hidden, or when a shade/settings/overlay/camera is covering the screen.
 */
internal fun shouldAnimateLeftPanel(
    isFirstDrawComplete: Boolean,
    isActivityStarted: Boolean,
    isWindowFocused: Boolean,
    hiddenForExternalApp: Boolean,
    activeAppPackage: String?,
    isActiveAppFullscreen: Boolean,
    isTopShadeOpen: Boolean,
    isVehicleSettingsOpen: Boolean,
    isVehicleSettingsOverlayShowing: Boolean,
    isVehicleSettingsActivityOpen: Boolean = false,
    isAboutOverlayShowing: Boolean,
    isQuickSettingsOpen: Boolean,
    isAvmActive: Boolean,
    isCamdCameraActive: Boolean,
): Boolean {
    if (!isFirstDrawComplete) return false
    if (!isActivityStarted) return false
    if (hiddenForExternalApp) return false
    if (isTopShadeOpen) return false
    if (isVehicleSettingsOpen || isVehicleSettingsOverlayShowing || isVehicleSettingsActivityOpen) return false
    if (isAboutOverlayShowing || isQuickSettingsOpen) return false
    if (isAvmActive || isCamdCameraActive) return false
    return activeAppPackage.isNullOrBlank() || !isActiveAppFullscreen || isWindowFocused
}

/**
 * Collects all workspace/overlay/camera visibility signals using [Lifecycle.State.CREATED]
 * so `onStop()` (`isStarted = false`) and pause signals are always delivered even after the
 * activity transitions to `STOPPED`.
 */
@Composable
internal fun rememberRightPanelAnimationSnapshot(): RightPanelAnimationSnapshot {
    val isFirstDrawComplete by LauncherHomeActivityHolder.firstDrawFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isActivityStarted by LauncherHomeActivityHolder.isStartedFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isWindowFocused by LauncherHomeActivityHolder.windowFocusedFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val hiddenForExternalApp by LauncherWindowState.hiddenForExternalAppFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeAppPackage by LauncherActiveAppTracker.activeAppPackageFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isActiveAppFullscreen by LauncherActiveAppTracker.isFullscreenFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isTopShadeOpen by LauncherCar3DModelTrigger.topShadeOpen
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isVehicleSettingsOpen = LauncherVehicleSettingsUiState.open
    val isVehicleSettingsOverlayShowing by LauncherVehicleSettingsOverlayWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isVehicleSettingsActivityOpen by LauncherVehicleSettingsActivity.isOpenFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isAboutOverlayShowing by LauncherAboutOverlayWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isQuickSettingsOpen by LauncherAdasQuickSettingsWindow.isShowingFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isAvmActive by LauncherAvmRepository.isAvmActive
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

    val isDesktopCamerasOpen by CamdCameraRepository.isDesktopCamerasOpen
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isReverseCamerasOpen by CamdCameraRepository.isReverseCamerasOpen
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeWidgetCameraMode by CamdCameraRepository.activeWidgetCameraMode
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeTurnSignalCamera by CamdCameraRepository.activeTurnSignalCamera
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeTurnSignalSide by CamdCameraRepository.activeTurnSignalSideFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val camdStatus by CamdCameraRepository.status
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

    val isCamdCameraActive = isDesktopCamerasOpen ||
        isReverseCamerasOpen ||
        activeWidgetCameraMode != CamdWidgetCameraMode.NONE ||
        activeTurnSignalCamera != null ||
        activeTurnSignalSide != null ||
        ((camdStatus as? CamdConnectionStatus.Connected)?.openWindows?.isNotEmpty() == true)

    return RightPanelAnimationSnapshot(
        isFirstDrawComplete = isFirstDrawComplete,
        isActivityStarted = isActivityStarted,
        isWindowFocused = isWindowFocused,
        hiddenForExternalApp = hiddenForExternalApp,
        activeAppPackage = activeAppPackage,
        isActiveAppFullscreen = isActiveAppFullscreen,
        isTopShadeOpen = isTopShadeOpen,
        isVehicleSettingsOpen = isVehicleSettingsOpen,
        isVehicleSettingsOverlayShowing = isVehicleSettingsOverlayShowing,
        isVehicleSettingsActivityOpen = isVehicleSettingsActivityOpen,
        isAboutOverlayShowing = isAboutOverlayShowing,
        isQuickSettingsOpen = isQuickSettingsOpen,
        isAvmActive = isAvmActive,
        isCamdCameraActive = isCamdCameraActive,
    )
}
