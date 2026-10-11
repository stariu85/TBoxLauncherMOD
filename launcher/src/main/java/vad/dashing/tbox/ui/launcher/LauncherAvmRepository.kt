package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.DiagFileLog

/**
 * Tracks 360 Camera (AVM) state via `Settings.Global.avm_state`.
 *
 * Values considered OFF: "0", "false", "off", "hide", "hidden", "avm_state_hide", null, empty.
 * Any other non-empty value means 360 Camera is ACTIVE.
 * When ACTIVE, 3D car model rendering is completely stopped to free 100% GPU for the camera.
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

    fun parseAvmActive(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val clean = raw.trim().lowercase()
        return clean !in OFF_VALUES
    }

    fun startObserving(context: Context) {
        val resolver = context.contentResolver
        fun update() {
            val raw = runCatching {
                Settings.Global.getString(resolver, AVM_STATE_SETTING)
            }.getOrNull()
            val active = parseAvmActive(raw)
            if (_isAvmActive.value != active) {
                _isAvmActive.value = active
                DiagFileLog.i(TAG, "avm_state changed raw='$raw' active=$active")
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
