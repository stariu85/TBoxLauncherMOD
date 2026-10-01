package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.View
import vad.dashing.tbox.LauncherForegroundHandoff
import vad.dashing.tbox.LauncherHomeActivityHolder

/**
 * While launcher overlays (vehicle settings / drawer) are open, keep HOME in front.
 * One-shot reorder only — no polling, no shell, no resize hacks.
 */
internal object LauncherOverlayElevator {
    private const val TAG = "LauncherOverlay"

    private val holdSources = mutableSetOf<String>()

    @Volatile
    private var _overlayHoldActive: Boolean = false
    val overlayHoldActive: Boolean get() = _overlayHoldActive

    fun setHoldSource(source: String, active: Boolean) {
        if (source.isBlank()) return
        synchronized(holdSources) {
            if (active) holdSources.add(source) else holdSources.remove(source)
            val shouldHold = holdSources.isNotEmpty()
            if (shouldHold == _overlayHoldActive) return
            _overlayHoldActive = shouldHold
            val home = LauncherHomeActivityHolder.instance ?: return
            if (shouldHold) {
                bringLauncherToFront(home)
            } else {
                releaseOverlayElevation()
            }
        }
    }

    @Deprecated("Use setHoldSource")
    fun applyOverlayHold(active: Boolean) {
        setHoldSource("legacy", active)
    }

    fun reset() {
        synchronized(holdSources) {
            holdSources.clear()
            _overlayHoldActive = false
        }
        releaseOverlayElevation()
    }

    fun clearHoldWithoutRestore() {
        reset()
    }

    fun bringLauncherToFront(context: Context) {
        val home = LauncherHomeActivityHolder.instance ?: return
        bringLauncherToFront(home)
    }

    private fun bringLauncherToFront(home: vad.dashing.tbox.LauncherHomeActivity) {
        if (home.isFinishing || home.isDestroyed) return
        LauncherForegroundHandoff.restoreLauncherWindow()
        runCatching {
            home.window.decorView.visibility = View.VISIBLE
            val params = home.window.attributes
            params.alpha = 1f
            home.window.attributes = params
            home.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            val am = home.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(home.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            Log.d(TAG, "moveTaskToFront task=${home.taskId}")
        }.onFailure {
            Log.w(TAG, "bringLauncherToFront failed", it)
        }
    }

    fun releaseOverlayElevation() {
        val home = LauncherHomeActivityHolder.instance ?: return
        if (home.isFinishing || home.isDestroyed) return
        runCatching {
            home.window.decorView.elevation = 0f
            home.window.decorView.translationZ = 0f
        }
    }

    fun forceRecoverToFront(context: Context) {
        val home = LauncherHomeActivityHolder.instance ?: return
        bringLauncherToFront(home)
    }
}
