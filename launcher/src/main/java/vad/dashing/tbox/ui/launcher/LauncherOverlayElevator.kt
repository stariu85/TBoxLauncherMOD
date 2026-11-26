package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val holdSources = mutableSetOf<String>()

    @Volatile
    private var _overlayHoldActive: Boolean = false
    val overlayHoldActive: Boolean get() = _overlayHoldActive

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun setHoldSource(source: String, active: Boolean) {
        if (source.isBlank()) return
        val newHoldState: Boolean? = synchronized(holdSources) {
            if (active) holdSources.add(source) else holdSources.remove(source)
            val shouldHold = holdSources.isNotEmpty()
            if (shouldHold == _overlayHoldActive) {
                null
            } else {
                _overlayHoldActive = shouldHold
                shouldHold
            }
        }
        val shouldHold = newHoldState ?: return
        runOnMain {
            val home = LauncherHomeActivityHolder.instance ?: return@runOnMain
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
        runOnMain {
            val home = LauncherHomeActivityHolder.instance ?: return@runOnMain
            bringLauncherToFront(home)
        }
    }

    private fun bringLauncherToFront(home: vad.dashing.tbox.LauncherHomeActivity) {
        runOnMain {
            if (home.isFinishing || home.isDestroyed) return@runOnMain
            LauncherForegroundHandoff.restoreLauncherWindow()
            runCatching {
                home.window.decorView.visibility = View.VISIBLE
                val params = home.window.attributes
                params.alpha = 1f
                home.window.attributes = params
                home.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                home.window.decorView.isFocusable = true
                home.window.decorView.isFocusableInTouchMode = true
                home.window.decorView.requestFocus()
                val am = home.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.moveTaskToFront(home.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
                Log.d(TAG, "moveTaskToFront task=${home.taskId}")
            }.onFailure {
                Log.w(TAG, "bringLauncherToFront failed", it)
            }
        }
    }

    fun releaseOverlayElevation() {
        runOnMain {
            val home = LauncherHomeActivityHolder.instance ?: return@runOnMain
            if (home.isFinishing || home.isDestroyed) return@runOnMain
            runCatching {
                home.window.decorView.elevation = 0f
                home.window.decorView.translationZ = 0f
            }
        }
    }

    fun forceRecoverToFront(context: Context) {
        runOnMain {
            val home = LauncherHomeActivityHolder.instance ?: return@runOnMain
            bringLauncherToFront(home)
        }
    }
}
