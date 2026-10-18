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

/**
 * Tracks 360 Camera (AVM) state via `Settings.Global.avm_state`.
 *
 * Values considered OFF: "0", "false", "off", "hide", "hidden", "avm_state_hide", null, empty.
 * Any other non-empty value means 360 Camera is ACTIVE.
 * When ACTIVE, 3D car model rendering is completely stopped to free 100% GPU for the camera,
 * and active applications are minimized. When camera turns OFF, previous app is restored.
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

        fun update() {
            val raw = runCatching {
                Settings.Global.getString(resolver, AVM_STATE_SETTING)
            }.getOrNull()
            val active = parseAvmActive(raw)
            val previous = _isAvmActive.value
            if (previous != active) {
                _isAvmActive.value = active
                DiagFileLog.i(TAG, "avm_state changed raw='$raw' active=$active")

                if (active) {
                    val isHomeActive = vad.dashing.tbox.LauncherHomeActivityHolder.isResumed
                    val lastFromHome = LauncherHistoryTracker.wasLastLaunchFromHome()
                    val foreign = if (!isHomeActive) findForeignAppTask(appCtx) else null

                    val activePkgIsCamera = isCameraPackage(foreign?.packageName) ||
                        isCameraPackage(LauncherHistoryTracker.lastActivePackage())

                    if (isHomeActive || (activePkgIsCamera && lastFromHome)) {
                        targetBeforeAvm = null
                    } else {
                        val history = LauncherHistoryTracker.getHistory(appCtx)
                        targetBeforeAvm = history.lastOrNull { !isCameraTarget(it) }
                    }
                    Log.w(TAG, "AVM turned ON: isHomeActive=$isHomeActive lastFromHome=$lastFromHome foreign=$foreign targetBeforeAvm=$targetBeforeAvm -> minimizing apps")
                    minimizeForeignFreeformTasks(appCtx)
                } else {
                    val restoreTarget = targetBeforeAvm
                    targetBeforeAvm = null
                    if (restoreTarget != null) {
                        Log.w(TAG, "AVM turned OFF: restoring targetBeforeAvm=$restoreTarget immediately")
                        launchTarget(appCtx, restoreTarget)
                    } else {
                        Log.w(TAG, "AVM turned OFF: no app was active before AVM, staying on Home")
                    }
                }
            }
        }

        update()

        if (observerRegistered) return
        observerRegistered = true

        runCatching {
            val uri = Settings.Global.getUriFor(AVM_STATE_SETTING)
            resolver.registerContentObserver(
                uri,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        update()
                    }
                },
            )
        }
    }
}
