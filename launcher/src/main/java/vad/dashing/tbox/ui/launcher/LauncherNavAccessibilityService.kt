package vad.dashing.tbox.ui.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service for dock Back ([GLOBAL_ACTION_BACK]) and for noticing
 * when a GPU-heavy overlay (AVM) covers the home 3D surface.
 *
 * Freeform Back is handled in [goLauncherBack] (dock) — do not filter KEYCODE_BACK here:
 * consuming it broke overlay close and in-app navigation on single-activity apps.
 */
class LauncherNavAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: LauncherNavAccessibilityService? = null

        /**
         * Hardware-like Back via the accessibility global action. Works without INJECT_EVENTS,
         * which is why the dock Back button prefers it when the service is enabled.
         */
        fun dispatchGlobalBack(): Boolean {
            val service = instance ?: return false
            val home = vad.dashing.tbox.LauncherHomeActivityHolder.instance
            if (home != null && vad.dashing.tbox.LauncherHomeActivityHolder.isResumed) {
                if (findForeignAppTask(home) == null) {
                    val hasOverlays = LauncherVehicleSettingsOverlayWindow.isShowing() ||
                        vad.dashing.tbox.LauncherVehicleSettingsActivity.isOpen() ||
                        vad.dashing.tbox.MainActivity.isOpen() ||
                        LauncherAboutOverlayWindow.isShowing() ||
                        LauncherAppDrawerWindow.isShowing() ||
                        LauncherAppPickerOverlayWindow.isShowing()
                    if (!hasOverlays) {
                        android.util.Log.w("LauncherNav", "dispatchGlobalBack: ignored on root desktop")
                        return false
                    }
                }
            }
            return runCatching {
                service.performGlobalAction(GLOBAL_ACTION_BACK)
            }.getOrDefault(false)
        }

        /**
         * Home via the accessibility global action. Works without INJECT_EVENTS.
         */
        fun dispatchGlobalHome(): Boolean {
            val service = instance ?: return false
            return runCatching {
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            }.getOrDefault(false)
        }

        fun isConnected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty()) {
            LauncherCarSurfaceRecovery.onWindowPackage(pkg)
        }
    }

    override fun onInterrupt() = Unit
}
