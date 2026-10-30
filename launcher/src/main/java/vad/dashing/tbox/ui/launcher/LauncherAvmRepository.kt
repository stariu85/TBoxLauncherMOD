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

    private var observerRegistered = false

    @Volatile
    private var targetBeforeAvm: LaunchedTarget? = null

    fun parseAvmActive(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val clean = raw.trim().lowercase()
        return clean !in OFF_VALUES
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
                    // On initial cold startup check, record baseline state without triggering overlay/blank activity actions
                    return
                }

                if (active) {
                    val cameraAction = LauncherAppConfigStore.avmCameraAction(appCtx)
                    vad.dashing.tbox.DclLogRecorder.log("AVM", "avm_state changed raw='$raw' active=$active cameraAction=$cameraAction")

                    when (cameraAction) {
                        AVM_CAMERA_ACTION_DO_NOTHING -> {
                            Log.w(TAG, "AVM turned ON: cameraAction=DO_NOTHING -> leaving active apps untouched")
                            targetBeforeAvm = null
                        }
                        AVM_CAMERA_ACTION_CLOSE_APPS -> {
                            val isHomeActive = LauncherHomeActivityHolder.isResumed
                            val foreign = if (!isHomeActive) findForeignAppTask(appCtx) else null

                            val activePkgIsCamera = isCameraPackage(foreign?.packageName) ||
                                isCameraPackage(LauncherHistoryTracker.lastActivePackage())

                            if (isHomeActive || foreign == null || activePkgIsCamera) {
                                targetBeforeAvm = null
                            } else {
                                val history = LauncherHistoryTracker.getHistory(appCtx)
                                targetBeforeAvm = history.lastOrNull { !isCameraTarget(it) }
                            }
                            Log.w(TAG, "AVM turned ON: cameraAction=CLOSE_APPS -> targetBeforeAvm=$targetBeforeAvm, closing foreign tasks & going Home")
                            dismissForeignFreeformTasks(appCtx)
                            goLauncherHome(appCtx)
                        }
                        AVM_CAMERA_ACTION_MINIMIZE_APPS -> {
                            Log.w(TAG, "AVM turned ON: cameraAction=MINIMIZE_APPS -> starting blank activity to pause foreign apps")
                            targetBeforeAvm = null
                            LauncherAvmBlankActivity.show(appCtx)
                        }
                    }
                } else {
                    LauncherAvmBlankActivity.hide()
                    val cameraAction = LauncherAppConfigStore.avmCameraAction(appCtx)
                    val restoreTarget = targetBeforeAvm
                    targetBeforeAvm = null
                    if (cameraAction == AVM_CAMERA_ACTION_CLOSE_APPS && restoreTarget != null) {
                        Log.w(TAG, "AVM turned OFF: cameraAction=CLOSE_APPS -> restoring targetBeforeAvm=$restoreTarget")
                        launchTarget(appCtx, restoreTarget)
                    } else {
                        Log.w(TAG, "AVM turned OFF: cameraAction=$cameraAction -> staying as is")
                    }
                }
            }
        }

        update(isInitial = true)

        if (observerRegistered) return
        observerRegistered = true

        runCatching {
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
        }
    }
}
