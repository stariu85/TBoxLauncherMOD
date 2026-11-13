package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.api.DclUiStateNotifier

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

    data class SavedAvmTask(
        val taskId: Int,
        val packageName: String,
        val bounds: Rect,
    )

    private val savedTasksBeforeAvm = mutableListOf<SavedAvmTask>()

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
                    val useDclwmMinimize = cameraAction == AVM_CAMERA_ACTION_MINIMIZE_APPS &&
                        LauncherAppConfigStore.experimentalHideAppsOnHome(appCtx)
                    vad.dashing.tbox.DclLogRecorder.log("AVM", "avm_state changed raw='$raw' active=$active cameraAction=$cameraAction useDclwmMinimize=$useDclwmMinimize")

                    when {
                        cameraAction == AVM_CAMERA_ACTION_DO_NOTHING -> {
                            Log.w(TAG, "AVM turned ON: cameraAction=DO_NOTHING -> leaving active apps untouched")
                            targetBeforeAvm = null
                        }
                        cameraAction == AVM_CAMERA_ACTION_CLOSE_APPS -> {
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
                        useDclwmMinimize -> {
                            Log.w(TAG, "AVM turned ON: cameraAction=MINIMIZE_APPS + dclwm -> saving tasks bounds, minimizing off-screen & going Home")
                            targetBeforeAvm = null
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    val client = vad.dashing.dclwm.Client()
                                    val tasks = client.list()
                                    val launcherPackage = vad.dashing.dclwm.Protocol.LAUNCHER_PACKAGE
                                    synchronized(savedTasksBeforeAvm) {
                                        savedTasksBeforeAvm.clear()
                                        for (task in tasks) {
                                            if (task.packageName == launcherPackage) continue
                                            if (task.packageName in SYSTEM_PROTECTED_PACKAGES) continue
                                            if (isCameraPackage(task.packageName)) continue
                                            if (task.left < 2000 && task.right > 0 && task.bottom > 0) {
                                                savedTasksBeforeAvm.add(
                                                    SavedAvmTask(
                                                        taskId = task.id,
                                                        packageName = task.packageName,
                                                        bounds = Rect(task.left, task.top, task.right, task.bottom),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    Log.w(TAG, "AVM ON (dclwm): saved ${savedTasksBeforeAvm.size} tasks to restore: $savedTasksBeforeAvm")
                                    minimizeForeignFreeformTasksViaDclwm(appCtx, source = "AVM:camera_on")
                                    withContext(Dispatchers.Main) {
                                        goLauncherHome(appCtx, source = "AVM:camera_on")
                                    }
                                }.onFailure { err ->
                                    Log.w(TAG, "AVM ON (dclwm) failed: ${err.message}, fallback to blank activity", err)
                                    withContext(Dispatchers.Main) {
                                        LauncherHomeSnapshotHolder.captureSnapshot(LauncherHomeActivityHolder.instance) {
                                            LauncherAvmBlankActivity.show(appCtx)
                                        }
                                    }
                                }
                            }
                        }
                        else -> { // cameraAction == AVM_CAMERA_ACTION_MINIMIZE_APPS && !useDclwmMinimize
                            Log.w(TAG, "AVM turned ON: cameraAction=MINIMIZE_APPS (legacy) -> capturing home snapshot & starting blank activity to pause foreign apps")
                            targetBeforeAvm = null
                            LauncherHomeSnapshotHolder.captureSnapshot(LauncherHomeActivityHolder.instance) {
                                LauncherAvmBlankActivity.show(appCtx)
                            }
                        }
                    }
                } else {
                    LauncherAvmBlankActivity.hide()
                    val cameraAction = LauncherAppConfigStore.avmCameraAction(appCtx)
                    val useDclwmMinimize = cameraAction == AVM_CAMERA_ACTION_MINIMIZE_APPS &&
                        LauncherAppConfigStore.experimentalHideAppsOnHome(appCtx)

                    if (useDclwmMinimize) {
                        val tasksToRestore = synchronized(savedTasksBeforeAvm) {
                            val list = ArrayList(savedTasksBeforeAvm)
                            savedTasksBeforeAvm.clear()
                            list
                        }
                        if (tasksToRestore.isNotEmpty()) {
                            Log.w(TAG, "AVM turned OFF: restoring ${tasksToRestore.size} tasks via dclwm: $tasksToRestore")
                            CoroutineScope(Dispatchers.IO).launch {
                                val client = vad.dashing.dclwm.Client()
                                val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                for (task in tasksToRestore) {
                                    runCatching {
                                        client.resize(
                                            task.taskId,
                                            task.bounds.left,
                                            task.bounds.top,
                                            task.bounds.right,
                                            task.bounds.bottom,
                                        )
                                        Log.w(TAG, "AVM restore: resized task=${task.taskId} pkg=${task.packageName} bounds=${task.bounds}")
                                        am.moveTaskToFront(task.taskId, 0)
                                    }.onFailure { err ->
                                        Log.w(TAG, "AVM restore: failed for task=${task.taskId} pkg=${task.packageName}: ${err.message}")
                                    }
                                }
                                tasksToRestore.lastOrNull()?.let { last ->
                                    LauncherActiveAppTracker.onAppLaunched(
                                        packageName = last.packageName,
                                        taskId = last.taskId,
                                        isFullscreen = last.bounds.left <= 0 && last.bounds.width() >= 1800,
                                    )
                                    withContext(Dispatchers.Main) {
                                        DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "AVM turned OFF: no tasks to restore, staying on Home")
                        }
                    } else {
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
