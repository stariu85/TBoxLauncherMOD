package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.LauncherHomeActivityHolder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks 360 Camera (AVM) state via `Settings.Global.avm_state`.
 *
 * Values considered OFF: "0", "false", "off", "hide", "hidden", "avm_state_hide", null, empty.
 * Any other non-empty value means 360 Camera is ACTIVE.
 * When ACTIVE, 3D car model rendering is completely stopped to free 100% GPU for the camera,
 * and active applications are minimized/hidden with Home screen brought to front.
 * When camera turns OFF, previous app or split preset is restored.
 */
object LauncherAvmRepository {
    private const val TAG = "LauncherAvm"
    private const val AVM_STATE_SETTING = "avm_state"

    private val OFF_VALUES = setOf(
        "0",
        "false",
        "off",
        "hide",
        "hidden",
        "avm_state_hide",
    )

    private val _isAvmActive = MutableStateFlow(false)
    val isAvmActive: StateFlow<Boolean> = _isAvmActive.asStateFlow()

    private val observerRegistered = AtomicBoolean(false)
    private val observerLock = Any()

    @Volatile
    private var targetBeforeAvm: LaunchedTarget? = null

    fun parseAvmActive(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val clean = raw.trim().lowercase()
        return clean !in OFF_VALUES
    }

    private fun captureTargetBeforeAvm(context: Context): LaunchedTarget? {
        val foreign = findForeignAppTask(context)
        val trackedPackages = FreeformLaunchRegistry.snapshot()
        val hasActiveApp = LauncherActiveAppTracker.hasActiveApp()
        val isHomeActive = LauncherHomeActivityHolder.isResumed && foreign == null && trackedPackages.isEmpty()

        val activePkgIsCamera = isCameraPackage(foreign?.packageName) ||
            isCameraPackage(LauncherHistoryTracker.lastActivePackage()) ||
            trackedPackages.any { isCameraPackage(it) }

        if (!hasActiveApp || isHomeActive || (foreign == null && trackedPackages.isEmpty()) || activePkgIsCamera) {
            return null
        }

        val history = LauncherHistoryTracker.getHistory(context)
        val lastTarget = history.lastOrNull { !isCameraTarget(it) }

        return when {
            trackedPackages.size >= 2 -> {
                if (lastTarget is LaunchedTarget.SplitPreset) {
                    lastTarget
                } else {
                    val sorted = trackedPackages.toList().sortedBy {
                        FreeformLaunchRegistry.lastBounds(it)?.left ?: 0
                    }
                    LaunchedTarget.SplitPreset(
                        leftPackage = sorted[0],
                        rightPackage = sorted[1],
                    )
                }
            }
            trackedPackages.size == 1 -> {
                val singlePkg = trackedPackages.first()
                if (lastTarget is LaunchedTarget.SingleApp && lastTarget.packageName == singlePkg) {
                    lastTarget
                } else {
                    LaunchedTarget.SingleApp(singlePkg)
                }
            }
            foreign != null -> LaunchedTarget.SingleApp(foreign.packageName, foreign.topActivityClass)
            lastTarget is LaunchedTarget.SingleApp -> lastTarget
            else -> null
        }
    }

    fun startObserving(context: Context) {
        val appCtx = context.applicationContext
        val resolver = appCtx.contentResolver

        fun update(isInitial: Boolean = false) {
            val raw = runCatching {
                Settings.Global.getString(resolver, AVM_STATE_SETTING)
            }.getOrNull()
            val active = parseAvmActive(raw)
            val previous = _isAvmActive.value
            if (previous != active) {
                _isAvmActive.value = active
                DiagFileLog.i(TAG, "avm_state changed raw='$raw' active=$active isInitial=$isInitial")

                if (isInitial) {
                    // On initial cold startup check, record baseline state without triggering actions
                    return
                }

                if (active) {
                    cancelPendingSplitRightLaunch()
                    val cameraAction = LauncherAppConfigStore.avmCameraAction(appCtx)
                    val isOverlayFullscreenHome = LauncherAppConfigStore.experimentalHomeWindowingMode(appCtx)
                    vad.dashing.tbox.DclLogRecorder.log("AVM", "avm_state changed raw='$raw' active=$active cameraAction=$cameraAction overlayFullscreen=$isOverlayFullscreenHome")

                    when (cameraAction) {
                        AVM_CAMERA_ACTION_DO_NOTHING -> {
                            Log.w(TAG, "AVM turned ON: cameraAction=DO_NOTHING -> leaving active apps untouched")
                            targetBeforeAvm = null
                        }
                        AVM_CAMERA_ACTION_CLOSE_APPS -> {
                            targetBeforeAvm = captureTargetBeforeAvm(appCtx)
                            Log.w(TAG, "AVM turned ON: cameraAction=CLOSE_APPS -> targetBeforeAvm=$targetBeforeAvm, capturing snapshot & blank activity between app and cameras")
                            LauncherHomeSnapshotHolder.captureSnapshot(LauncherHomeActivityHolder.instance) {
                                if (!_isAvmActive.value) return@captureSnapshot
                                LauncherAvmBlankActivity.show(appCtx)
                                dismissForeignFreeformTasks(appCtx)
                                goLauncherHome(appCtx, source = "AVM:camera_on")
                            }
                        }
                        else -> { // AVM_CAMERA_ACTION_MINIMIZE_APPS
                            targetBeforeAvm = null
                            Log.w(TAG, "AVM turned ON: cameraAction=MINIMIZE_APPS -> showing blank activity with desktop snapshot over active apps")
                            LauncherHomeSnapshotHolder.captureSnapshot(LauncherHomeActivityHolder.instance) {
                                if (!_isAvmActive.value) return@captureSnapshot
                                LauncherAvmBlankActivity.show(appCtx)
                            }
                        }
                    }
                } else {
                    if (LauncherAvmBlankActivity.isShowing()) {
                        LauncherAvmBlankActivity.hide()
                    }
                    LauncherHomeSnapshotHolder.onAvmClosed()
                    val cameraAction = LauncherAppConfigStore.avmCameraAction(appCtx)
                    val restoreTarget = targetBeforeAvm
                    targetBeforeAvm = null
                    if (cameraAction == AVM_CAMERA_ACTION_CLOSE_APPS && restoreTarget != null) {
                        Log.w(TAG, "AVM turned OFF: cameraAction=CLOSE_APPS -> restoring targetBeforeAvm=$restoreTarget")
                        vad.dashing.tbox.DclLogRecorder.log("AVM", "AVM turned OFF: restoring targetBeforeAvm=$restoreTarget")
                        launchTarget(appCtx, restoreTarget)
                    } else {
                        Log.w(TAG, "AVM turned OFF: cameraAction=$cameraAction -> staying as is (blank activity hidden)")
                    }
                }
            }
        }

        update(isInitial = true)

        if (observerRegistered.get()) return
        synchronized(observerLock) {
            if (observerRegistered.get()) return
            val registered = runCatching {
                val uri = Settings.Global.getUriFor(AVM_STATE_SETTING)
                resolver.registerContentObserver(
                    uri,
                    false,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            update(isInitial = false)
                        }
                    },
                )
            }.isSuccess
            if (registered) {
                observerRegistered.set(true)
            }
        }
    }
}
