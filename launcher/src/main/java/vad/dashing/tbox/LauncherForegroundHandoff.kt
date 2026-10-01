package vad.dashing.tbox

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

/**
 * Weak link to the active [LauncherHomeActivity] so [BackgroundService] can hide HOME after
 * starting another app (some head units keep HOME visible even when startActivity succeeds).
 */
object LauncherHomeActivityHolder {
    @Volatile
    var instance: LauncherHomeActivity? = null
}

object LauncherWindowState {
    @Volatile
    var hiddenForExternalApp: Boolean = false
}

object LauncherForegroundHandoff {
    private const val TAG = "LauncherAppLaunch"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingHandoff: Runnable? = null

    fun requestLauncherHandoff(delayMs: Long = 150L) {
        cancelPendingHandoff()
    }

    fun cancelPendingHandoff() {
        pendingHandoff?.let { mainHandler.removeCallbacks(it) }
        pendingHandoff = null
    }

    fun restoreLauncherWindow() {
        cancelPendingHandoff()
        val home = LauncherHomeActivityHolder.instance ?: return
        if (home.isFinishing || home.isDestroyed) return
        runCatching {
            if (home.window.decorView.visibility != View.VISIBLE) {
                home.window.decorView.visibility = View.VISIBLE
            }
            LauncherWindowState.hiddenForExternalApp = false
            Log.w(TAG, "LauncherHomeActivity window restored")
        }.onFailure {
            Log.w(TAG, "restoreLauncherWindow failed", it)
        }
    }
}
