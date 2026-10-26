package vad.dashing.tbox.mbcan

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.TboxRepository

/**
 * Session-only CAN diagnostics switch (mbCAN + VHAL).
 * Not persisted; should be explicitly reset when BackgroundService starts.
 */
object MbCanDiagnostics {
    private const val DEFAULT_TAG = "MBCAN_TMP"
    private const val LOG_COALESCE_MS = 1_000L

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val pendingLogs = mutableMapOf<String, Pair<Int, String>>()
    private var flushScheduled = false
    private val flushRunnable = Runnable { flushCoalescedLogs() }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun log(level: String, message: String) {
        log(level = level, tag = DEFAULT_TAG, message = message)
    }

    fun log(level: String, tag: String, message: String) {
        if (!_enabled.value) return
        
        synchronized(pendingLogs) {
            val key = "$level/$tag"
            val prev = pendingLogs[key]
            val count = (prev?.first ?: 0) + 1
            pendingLogs[key] = count to message

            if (!flushScheduled) {
                flushScheduled = true
                handler.postDelayed(flushRunnable, LOG_COALESCE_MS)
            }
        }
    }

    private fun flushCoalescedLogs() {
        val snapshot = synchronized(pendingLogs) {
            flushScheduled = false
            if (pendingLogs.isEmpty()) return
            pendingLogs.toMap().also { pendingLogs.clear() }
        }
        for ((key, pair) in snapshot) {
            val parts = key.split('/', limit = 2)
            val level = parts[0]
            val tag = parts.getOrElse(1) { DEFAULT_TAG }
            val (count, lastMsg) = pair
            val finalMsg = if (count > 1) "[$count x] $lastMsg" else lastMsg
            TboxRepository.addLog(level, tag, finalMsg)
        }
    }
}
