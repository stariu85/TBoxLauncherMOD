package vad.dashing.tbox.ui.launcher

import android.content.Context

/**
 * Legacy stub for Launcher Right Panel Overlay.
 * The primary desktop grid [LauncherRightPanel] is now rendered directly on Layer 2
 * inside [TeslaLauncherScreen], eliminating secondary overlay window layer conflicts.
 */
internal object LauncherRightPanelOverlayWindow {
    fun isShowing(): Boolean = false
    fun ensureShowing(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
    fun show(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false
    fun updateBounds() = Unit
    fun hide() = Unit
    fun destroy() = Unit
}
