package vad.dashing.tbox

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Weak link to the active [LauncherHomeActivity] so [BackgroundService] can hide HOME after
 * starting another app (some head units keep HOME visible even when startActivity succeeds).
 */
object LauncherHomeActivityHolder {
    @Volatile
    var instance: LauncherHomeActivity? = null

    val isResumedFlow = MutableStateFlow(false)

    var isResumed: Boolean
        get() = isResumedFlow.value
        set(value) {
            isResumedFlow.value = value
        }

    val isStartedFlow = MutableStateFlow(false)

    var isStarted: Boolean
        get() = isStartedFlow.value
        set(value) {
            isStartedFlow.value = value
        }

    val firstDrawFlow = MutableStateFlow(false)

    var isFirstDrawComplete: Boolean
        get() = firstDrawFlow.value
        set(value) {
            firstDrawFlow.value = value
        }

    val windowFocusedFlow = MutableStateFlow(false)

    var isWindowFocused: Boolean
        get() = windowFocusedFlow.value
        set(value) {
            windowFocusedFlow.value = value
        }
}

object LauncherWindowState {
    val hiddenForExternalAppFlow = MutableStateFlow(false)

    var hiddenForExternalApp: Boolean
        get() = hiddenForExternalAppFlow.value
        set(value) {
            hiddenForExternalAppFlow.value = value
        }
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
