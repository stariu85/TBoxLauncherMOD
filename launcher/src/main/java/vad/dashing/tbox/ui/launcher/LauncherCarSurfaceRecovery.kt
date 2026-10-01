package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.LauncherWindowState

/**
 * Remounts the home Filament [io.github.sceneview.SceneView] after a GPU-heavy
 * fullscreen app (360 AVM) steals the EGL surface. Without this the car either
 * stays invisible (`ModelNode` rebuilt with `isVisible=false` while Compose
 * still thinks it is ready) or the camera snaps back to the top-down default.
 */
internal object LauncherCarSurfaceRecovery {
    private const val TAG = "LauncherCar3D"
    private const val MIN_RECOVER_INTERVAL_MS = 2_500L
    private const val COLD_START_GRACE_MS = 12_000L

    private val coveringPackages = setOf(
        "com.mengbo.avm",
        "com.mengbo.avmconfig",
    )

    private val processStartedAtMs = SystemClock.elapsedRealtime()

    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    @Volatile
    private var coveredBy: String? = null
    @Volatile
    private var awaitFirstFrame = false
    @Volatile
    private var lastRecoverAtMs = 0L

    fun isCoveringPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in coveringPackages) return true
        return pkg.contains(".avm") || pkg.endsWith("avm") ||
            pkg.contains("park") && pkg.contains("cam")
    }

    fun isCovered(): Boolean =
        coveredBy != null || LauncherWindowState.hiddenForExternalApp

    fun onWindowPackage(packageName: String) {
        if (packageName.isBlank()) return
        if (isCoveringPackage(packageName)) {
            if (coveredBy != packageName) {
                coveredBy = packageName
                Log.w(TAG, "3D surface covered by $packageName")
            }
            return
        }
        if (coveredBy != null && packageName == BuildConfig.APPLICATION_ID) {
            val previous = coveredBy
            coveredBy = null
            recover("launcher window after $previous")
        }
    }

    fun onHomePaused() {
        awaitFirstFrame = true
    }

    fun onHomeResumed() {
        awaitFirstFrame = true
        if (LauncherWindowState.hiddenForExternalApp) return
        val cover = coveredBy ?: return
        coveredBy = null
        recover("resume after $cover")
    }

    fun onFrameObserved() {
        awaitFirstFrame = false
    }

    fun requestRecovery(reason: String = "manual request") {
        if (isCovered()) return
        awaitFirstFrame = false
        coveredBy = null
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoverAtMs < MIN_RECOVER_INTERVAL_MS) return
        lastRecoverAtMs = now
        val next = _epoch.value + 1
        _epoch.value = next
        Log.w(TAG, "requestRecovery SceneView epoch=$next reason=$reason")
    }

    fun onFramesStalled(neverStarted: Boolean = false) {
        if (isCovered()) return
        awaitFirstFrame = false
        recover(if (neverStarted) "frames never started" else "frames stalled")
    }

    private fun recover(reason: String) {
        if (isCovered()) {
            Log.w(TAG, "recover skipped (covered) reason=$reason")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoverAtMs < MIN_RECOVER_INTERVAL_MS) {
            Log.w(TAG, "recover skipped (throttle) reason=$reason")
            return
        }
        lastRecoverAtMs = now
        val next = _epoch.value + 1
        _epoch.value = next
        Log.w(TAG, "recover SceneView epoch=$next reason=$reason")
    }
}
