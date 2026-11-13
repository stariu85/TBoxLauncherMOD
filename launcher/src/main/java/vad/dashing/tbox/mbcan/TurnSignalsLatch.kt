package vad.dashing.tbox.mbcan

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Retriggerable off-delay latch for turn signals on Android 9 mbCAN.
 *
 * Rules:
 * 1. Each flash (raw 2) restarts the 2.5s timer (HOLD_MS = 2500L).
 * 2. In pauses between flashes (raw 0 or 1), the state remains active (true).
 * 3. If no new flash occurs within 2.5s, the signal drops back to false (null).
 * 4. Activating the opposite turn signal or hazard immediately cancels the active timer and clears the previous side.
 */
class TurnSignalsLatch(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeProvider: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    },
    holdMs: Long = HOLD_MS,
    private val onSideChanged: ((TurnSignalSide?) -> Unit)? = null,
) {
    @Volatile
    var holdMs: Long = holdMs
        set(value) {
            field = value.coerceIn(100L, 10_000L)
        }

    private var lastLeftElapsedMs: Long = NEVER
    private var lastRightElapsedMs: Long = NEVER

    private var holdJob: Job? = null

    private val _latchedSide = MutableStateFlow<TurnSignalSide?>(null)
    val latchedSide: StateFlow<TurnSignalSide?> = _latchedSide.asStateFlow()

    @Synchronized
    fun onRawTurnLights(leftRaw: Int, rightRaw: Int): TurnSignalSide? {
        val state = TurnSignalsDomain.fromMbCanTurnLightRaw(leftRaw, rightRaw)
        return onState(state)
    }

    @Synchronized
    fun onState(state: TurnSignalsState): TurnSignalSide? {
        val now = timeProvider()

        if (isHazard(state)) {
            logDebug("Hazard detected: cancelling turn signal latch")
            cancelHold()
            lastLeftElapsedMs = NEVER
            lastRightElapsedMs = NEVER
            emitSide(null)
            return null
        }

        val left = state.leftActive == true
        val right = state.rightActive == true

        when {
            left && !right -> {
                // Left flash (2) -> restart 2.5s timer, cancel opposite
                lastLeftElapsedMs = now
                lastRightElapsedMs = NEVER
                emitSide(TurnSignalSide.Left)
                scheduleHold(TurnSignalSide.Left)
            }
            right && !left -> {
                // Right flash (2) -> restart 2.5s timer, cancel opposite
                lastRightElapsedMs = now
                lastLeftElapsedMs = NEVER
                emitSide(TurnSignalSide.Right)
                scheduleHold(TurnSignalSide.Right)
            }
            else -> {
                // Pause between flashes (raw 0 or 1 on both sides).
                // Do not update timestamps. State remains active while hold timer runs.
            }
        }

        return currentSide(now)
    }

    fun currentSide(now: Long = timeProvider()): TurnSignalSide? {
        val left = held(lastLeftElapsedMs, now)
        val right = held(lastRightElapsedMs, now)
        return when {
            left && !right -> TurnSignalSide.Left
            right && !left -> TurnSignalSide.Right
            else -> null
        }
    }

    @Synchronized
    fun reset() {
        cancelHold()
        lastLeftElapsedMs = NEVER
        lastRightElapsedMs = NEVER
        emitSide(null)
    }

    private fun held(lastElapsedMs: Long, now: Long): Boolean {
        if (lastElapsedMs == NEVER) return false
        return (now - lastElapsedMs) <= holdMs
    }

    private fun scheduleHold(side: TurnSignalSide) {
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(holdMs)
            synchronized(this@TurnSignalsLatch) {
                val now = timeProvider()
                val lastMs = if (side == TurnSignalSide.Left) lastLeftElapsedMs else lastRightElapsedMs
                if (lastMs == NEVER || (now - lastMs) >= holdMs) {
                    logDebug("Turn latch hold expired for side $side after ${holdMs}ms")
                    if (side == TurnSignalSide.Left) lastLeftElapsedMs = NEVER
                    if (side == TurnSignalSide.Right) lastRightElapsedMs = NEVER
                    emitSide(null)
                }
            }
        }
    }

    private fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
    }

    private fun emitSide(side: TurnSignalSide?) {
        if (_latchedSide.value != side) {
            logDebug("Latched turn signal: ${_latchedSide.value} -> $side")
            _latchedSide.value = side
            onSideChanged?.invoke(side)
        }
    }

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // Ignored in JVM unit tests
        }
    }

    companion object {
        private const val TAG = "TurnSignalsLatch"
        const val HOLD_MS = 2_500L
        private const val NEVER = Long.MIN_VALUE

        fun isHazard(state: TurnSignalsState): Boolean =
            state.hazardActive == true ||
                ((state.leftActive == true) && (state.rightActive == true))
    }
}
