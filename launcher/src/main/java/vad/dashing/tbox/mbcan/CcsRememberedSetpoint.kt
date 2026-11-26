package vad.dashing.tbox.mbcan

import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataRepository

/**
 * Session-only remembered CCS cruise setpoint (km/h) for conventional cruise on Android 9 mbCAN.
 *
 * Conventional cruise has no HU VSetDis — we track the last SET/RES target from:
 * 1. Widget / MFS pulses ([markOurPulse] / [nudgeBy] / capture),
 * 2. A9 hardkey left joystick (RES+ = 29, SET− = 30),
 * 3. Fallback status+speed heuristics when hardkey is missing,
 * 4. Stable-speed reconcile when live speed holds far from remembered.
 *
 * Cleared on full Off (status 0). Does not survive process/app restart.
 * TBox `cruiseSetSpeed` is intentionally ignored. ACC path is unaffected.
 */
object CcsRememberedSetpoint {
    private const val LOG_TAG = "CcsSetpoint"

    /** A9 mbCAN: left-wheel joystick up → RES+. */
    const val HARDKEY_RES_PLUS = 29

    /** A9 mbCAN: left-wheel joystick down → SET−. */
    const val HARDKEY_SET_MINUS = 30

    /** A9 mbCAN hardkey: 0 = pressed, 1 = released. */
    const val HARDKEY_STATUS_PRESSED = 0

    /** Ignore duplicate hardkey presses within this window. */
    const val HARDKEY_DEBOUNCE_MS = 120L

    /** Stalk enter-Active fallback (no hardkey): keep if speed within this band of remembered. */
    const val STALK_MATCH_THRESHOLD_KMH = 2

    /** Ignore external enter-Active / speed reconcile while our MFS pulse may still settle. */
    const val OUR_PULSE_WINDOW_MS = 2_000L

    /** Active ±1 without hardkey: adopt rounded speed after it holds this long. */
    const val NUDGE_SETTLE_MS = 3_000L

    /**
     * When vehicle speed stays within [STABLE_SPEED_BAND_KMH] of an anchor for this long
     * and differs from remembered by ≥ [RECONCILE_MIN_DELTA_KMH], adopt speed as setpoint.
     */
    const val STABLE_RECONCILE_MS = 10_000L

    /** Max |speed − stabilityAnchor| while building a stable window. */
    const val STABLE_SPEED_BAND_KMH = 1f

    /** Minimum |speed − remembered| to trigger stable reconcile. */
    const val RECONCILE_MIN_DELTA_KMH = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private var started = false

    @Volatile
    internal var clockMs: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    private fun nowElapsedMs(): Long = clockMs()

    private val _kmh = MutableStateFlow<Int?>(null)
    val kmh: StateFlow<Int?> = _kmh.asStateFlow()

    private var ourPulseUntilElapsed: Long = 0L

    private var lastStatus: Int? = null
    private var pendingNudgeKmh: Int? = null
    private var pendingNudgeSinceElapsed: Long = 0L
    private var nudgeTimerJob: Job? = null

    /** Hardkey already explained the next Standby→Active transition. */
    private var hardkeyOwnedEnterActive = false

    private var lastHardkeyCode: Int? = null
    private var lastHardkeyElapsed: Long = 0L

    private var stabilityAnchorKmh: Float? = null
    private var stabilitySinceElapsed: Long = 0L
    private var stabilityTimerJob: Job? = null

    fun hasSetpoint(): Boolean = _kmh.value != null

    fun ensureStarted() {
        synchronized(stateLock) {
            if (started) return
            started = true
        }
        scope.launch {
            CanDataRepository.ccsCruiseStatus.collectLatest { status ->
                onStatus(status)
            }
        }
        scope.launch {
            CanDataRepository.carSpeed.collectLatest { speed ->
                val effective = CanDataRepository.carSpeedAccurate.value ?: speed
                onSpeedWhileActive(effective)
            }
        }
        scope.launch {
            CanDataRepository.carSpeedAccurate.collectLatest { speed ->
                if (speed != null) {
                    onSpeedWhileActive(speed)
                }
            }
        }
    }

    fun normalizeTargetKmh(value: Int): Int = value.coerceIn(30, 150)

    /** Mark that we just sent an MFS pulse so stalk inference yields to controller updates. */
    fun markOurPulse(windowMs: Long = OUR_PULSE_WINDOW_MS) {
        ensureStarted()
        synchronized(stateLock) {
            ourPulseUntilElapsed = nowElapsedMs() + windowMs
            cancelNudgeLocked()
            resetStabilityLocked("our_pulse")
        }
    }

    fun isWithinOurPulseWindow(nowElapsed: Long = nowElapsedMs()): Boolean = synchronized(stateLock) {
        nowElapsed < ourPulseUntilElapsed
    }

    fun clear(reason: String = "manual") {
        synchronized(stateLock) {
            if (_kmh.value == null) return
            debug("clear reason=$reason was=${_kmh.value}")
            _kmh.value = null
            cancelNudgeLocked()
            hardkeyOwnedEnterActive = false
            resetStabilityLocked(reason)
        }
    }

    fun remember(kmh: Int, reason: String) {
        ensureStarted()
        val normalized = normalizeTargetKmh(kmh)
        synchronized(stateLock) {
            if (_kmh.value == normalized) return
            debug("set reason=$reason kmh=$normalized was=${_kmh.value}")
            _kmh.value = normalized
            cancelNudgeLocked()
            resetStabilityLocked(reason)
        }
    }

    /** Capture rounded vehicle speed as remembered setpoint. */
    fun captureFromVehicleSpeed(reason: String): Boolean {
        val speed = CanDataRepository.carSpeedAccurate.value ?: CanDataRepository.carSpeed.value
        val rounded = roundSpeedKmh(speed) ?: return false
        remember(rounded, reason)
        return true
    }

    fun nudgeBy(delta: Int, reason: String) {
        val current = _kmh.value
        if (current == null) {
            captureFromVehicleSpeed(reason)
            return
        }
        remember(current + delta, reason)
    }

    /**
     * A9 hardkey callback (left joystick RES+/SET−). Only PRESSED events; debounced.
     * Safe to call from OEM callback threads.
     */
    fun onHardKey(keyCode: Int, keyStatus: Int, keyType: Int) {
        if (keyStatus != HARDKEY_STATUS_PRESSED) return
        val key = parseHardkeyCruiseKey(keyCode) ?: return
        val decision = synchronized(stateLock) {
            val now = nowElapsedMs()
            if (lastHardkeyCode == keyCode && now - lastHardkeyElapsed < HARDKEY_DEBOUNCE_MS) {
                return
            }
            lastHardkeyCode = keyCode
            lastHardkeyElapsed = now

            val status = lastStatus ?: CanDataRepository.ccsCruiseStatus.value
            val d = decideHardkey(
                key = key,
                ccsStatus = status,
            )
            debug(
                "hardkey key=$key status=$status decision=$d " +
                    "keyType=$keyType remembered=${_kmh.value}",
            )
            d
        }
        applyHardkeyDecision(decision)
    }

    fun applyHardkeyDecision(decision: HardkeySetpointDecision) {
        when (decision) {
            HardkeySetpointDecision.Ignore -> Unit
            HardkeySetpointDecision.KeepRemembered -> {
                synchronized(stateLock) {
                    hardkeyOwnedEnterActive = true
                }
            }
            is HardkeySetpointDecision.CaptureSpeed -> {
                synchronized(stateLock) {
                    hardkeyOwnedEnterActive = true
                }
                if (!captureFromVehicleSpeed(decision.reason) && _kmh.value == null) {
                    debug("hardkey capture_pending_no_speed reason=${decision.reason}")
                }
            }
            is HardkeySetpointDecision.Nudge -> {
                nudgeBy(decision.delta, decision.reason)
            }
        }
    }

    /**
     * Pure hardkey → setpoint action.
     * Standby RES+ keeps remembered (resume); Standby SET− captures current speed;
     * Active RES+/SET− nudge ±1. Off / unknown → ignore.
     */
    fun decideHardkey(
        key: HardkeyCruiseKey,
        ccsStatus: Int?,
    ): HardkeySetpointDecision {
        val active = ccsStatus == 1
        val standby = ccsStatus == 2
        return when (key) {
            HardkeyCruiseKey.ResPlus -> when {
                active -> HardkeySetpointDecision.Nudge(1, "hardkey_res")
                standby -> HardkeySetpointDecision.KeepRemembered
                else -> HardkeySetpointDecision.Ignore
            }
            HardkeyCruiseKey.SetMinus -> when {
                active -> HardkeySetpointDecision.Nudge(-1, "hardkey_set")
                standby -> HardkeySetpointDecision.CaptureSpeed("hardkey_set")
                else -> HardkeySetpointDecision.Ignore
            }
        }
    }

    fun parseHardkeyCruiseKey(keyCode: Int): HardkeyCruiseKey? = when (keyCode) {
        HARDKEY_RES_PLUS -> HardkeyCruiseKey.ResPlus
        HARDKEY_SET_MINUS -> HardkeyCruiseKey.SetMinus
        else -> null
    }

    /**
     * Pure decision for stalk-driven enter Active when hardkey was not seen.
     * Null remembered → capture speed; within threshold of remembered → keep; else capture (new SET).
     */
    fun decideStalkEnterActive(
        rememberedKmh: Int?,
        vehicleSpeedKmh: Float?,
    ): StalkEnterActiveDecision {
        val rounded = roundSpeedKmh(vehicleSpeedKmh)
        if (rememberedKmh == null) {
            return if (rounded != null) {
                StalkEnterActiveDecision.Capture(rounded)
            } else {
                StalkEnterActiveDecision.Keep
            }
        }
        if (rounded == null) return StalkEnterActiveDecision.Keep
        return if (abs(rounded - rememberedKmh) <= STALK_MATCH_THRESHOLD_KMH) {
            StalkEnterActiveDecision.Keep
        } else {
            StalkEnterActiveDecision.Capture(rounded)
        }
    }

    /**
     * Pure: adopt live speed as remembered when it has been stable long enough and
     * differs from remembered by at least [RECONCILE_MIN_DELTA_KMH].
     */
    fun shouldReconcileStableSpeed(
        rememberedKmh: Int?,
        roundedSpeedKmh: Int?,
        stableForMs: Long,
        withinOurPulse: Boolean,
    ): Boolean {
        if (withinOurPulse) return false
        val remembered = rememberedKmh ?: return false
        val rounded = roundedSpeedKmh ?: return false
        if (stableForMs < STABLE_RECONCILE_MS) return false
        return abs(rounded - remembered) >= RECONCILE_MIN_DELTA_KMH
    }

    private fun onStatus(status: Int?) {
        val enteredActive = synchronized(stateLock) {
            val previous = lastStatus
            lastStatus = status
            when {
                status == null -> {
                    clearLocked("status_null")
                    return
                }
                status == 0 -> {
                    clearLocked("status_off")
                    return
                }
            }
            if (status != 1) { // Not Active (i.e. Standby = 2)
                cancelNudgeLocked()
                resetStabilityLocked("not_active")
                return
            }
            previous != 1
        }
        if (enteredActive) {
            onEnteredActive()
        }
    }

    private fun clearLocked(reason: String) {
        if (_kmh.value != null) {
            debug("clear reason=$reason was=${_kmh.value}")
            _kmh.value = null
        }
        cancelNudgeLocked()
        hardkeyOwnedEnterActive = false
        resetStabilityLocked(reason)
    }

    private fun onEnteredActive() {
        val currentSpeed = CanDataRepository.carSpeedAccurate.value ?: CanDataRepository.carSpeed.value
        synchronized(stateLock) {
            val now = nowElapsedMs()
            if (now < ourPulseUntilElapsed) {
                if (_kmh.value == null) {
                    roundSpeedKmh(currentSpeed)?.let { rememberLocked(it, "our_pulse_enter_active") }
                }
                hardkeyOwnedEnterActive = false
                return
            }
            if (hardkeyOwnedEnterActive) {
                hardkeyOwnedEnterActive = false
                if (_kmh.value == null) {
                    roundSpeedKmh(currentSpeed)?.let { rememberLocked(it, "hardkey_enter_active_fallback") }
                } else {
                    debug("hardkey_enter_active keep remembered=${_kmh.value}")
                }
                return
            }
            when (val decision = decideStalkEnterActive(_kmh.value, currentSpeed)) {
                StalkEnterActiveDecision.Keep ->
                    debug("stalk_enter_active keep remembered=${_kmh.value}")
                is StalkEnterActiveDecision.Capture ->
                    rememberLocked(decision.kmh, "stalk_enter_active")
            }
        }
    }

    private fun rememberLocked(kmh: Int, reason: String) {
        val normalized = normalizeTargetKmh(kmh)
        if (_kmh.value == normalized) return
        debug("set reason=$reason kmh=$normalized was=${_kmh.value}")
        _kmh.value = normalized
        cancelNudgeLocked()
        resetStabilityLocked(reason)
    }

    private fun onSpeedWhileActive(speed: Float?) {
        synchronized(stateLock) {
            val now = nowElapsedMs()
            if (lastStatus != 1) {
                cancelNudgeLocked()
                resetStabilityLocked("not_active")
                return
            }
            if (now < ourPulseUntilElapsed) {
                cancelNudgeLocked()
                resetStabilityLocked("our_pulse")
                return
            }
            val remembered = _kmh.value
            val rounded = roundSpeedKmh(speed)
            if (remembered == null || rounded == null || speed == null || !speed.isFinite()) {
                cancelNudgeLocked()
                resetStabilityLocked("no_speed")
                return
            }

            val delta = abs(rounded - remembered)
            when {
                delta == 0 -> {
                    cancelNudgeLocked()
                    updateStabilityLocked(speed, now)
                }
                delta == 1 -> {
                    if (pendingNudgeKmh != rounded) {
                        pendingNudgeKmh = rounded
                        pendingNudgeSinceElapsed = now
                        scheduleNudgeSettleLocked(rounded)
                    } else if (now - pendingNudgeSinceElapsed >= NUDGE_SETTLE_MS) {
                        rememberLocked(rounded, "stalk_nudge")
                        return
                    }
                    updateStabilityLocked(speed, now)
                }
                else -> {
                    cancelNudgeLocked()
                    updateStabilityLocked(speed, now)
                    val stableFor = currentStableDurationMsLocked(now)
                    if (shouldReconcileStableSpeed(remembered, rounded, stableFor, false)) {
                        rememberLocked(rounded, "stable_reconcile")
                    }
                }
            }
        }
    }

    private fun cancelNudgeLocked() {
        pendingNudgeKmh = null
        pendingNudgeSinceElapsed = 0L
        nudgeTimerJob?.cancel()
        nudgeTimerJob = null
    }

    private fun scheduleNudgeSettleLocked(targetRounded: Int) {
        nudgeTimerJob?.cancel()
        nudgeTimerJob = scope.launch {
            delay(NUDGE_SETTLE_MS)
            synchronized(stateLock) {
                val now = nowElapsedMs()
                if (lastStatus != 1 || now < ourPulseUntilElapsed) return@synchronized
                if (pendingNudgeKmh != targetRounded) return@synchronized
                if (now - pendingNudgeSinceElapsed < NUDGE_SETTLE_MS) return@synchronized
                val liveSpeed = CanDataRepository.carSpeedAccurate.value ?: CanDataRepository.carSpeed.value
                val liveRounded = roundSpeedKmh(liveSpeed) ?: return@synchronized
                val remembered = _kmh.value ?: return@synchronized
                if (liveRounded == targetRounded && abs(liveRounded - remembered) == 1) {
                    rememberLocked(targetRounded, "stalk_nudge")
                }
            }
        }
    }

    private fun updateStabilityLocked(speed: Float, now: Long) {
        val anchor = stabilityAnchorKmh
        if (anchor == null || abs(speed - anchor) > STABLE_SPEED_BAND_KMH) {
            stabilityAnchorKmh = speed
            stabilitySinceElapsed = now
            scheduleStabilityReconcileLocked(speed)
            return
        }
    }

    private fun scheduleStabilityReconcileLocked(anchorSpeed: Float) {
        stabilityTimerJob?.cancel()
        stabilityTimerJob = scope.launch {
            delay(STABLE_RECONCILE_MS)
            synchronized(stateLock) {
                val now = nowElapsedMs()
                if (lastStatus != 1 || now < ourPulseUntilElapsed) return@synchronized
                val anchor = stabilityAnchorKmh ?: return@synchronized
                if (anchor != anchorSpeed) return@synchronized
                val liveSpeed = CanDataRepository.carSpeedAccurate.value ?: CanDataRepository.carSpeed.value
                if (liveSpeed == null || !liveSpeed.isFinite()) return@synchronized
                if (abs(liveSpeed - anchor) > STABLE_SPEED_BAND_KMH) return@synchronized
                val rounded = roundSpeedKmh(liveSpeed) ?: return@synchronized
                val stableFor = currentStableDurationMsLocked(now)
                if (shouldReconcileStableSpeed(_kmh.value, rounded, stableFor, false)) {
                    rememberLocked(rounded, "stable_reconcile")
                }
            }
        }
    }

    private fun currentStableDurationMsLocked(now: Long): Long {
        if (stabilityAnchorKmh == null) return 0L
        return (now - stabilitySinceElapsed).coerceAtLeast(0L)
    }

    private fun resetStabilityLocked(reason: String) {
        if (stabilityAnchorKmh != null) {
            debug("stability_reset reason=$reason")
        }
        stabilityAnchorKmh = null
        stabilitySinceElapsed = 0L
        stabilityTimerJob?.cancel()
        stabilityTimerJob = null
    }

    private fun roundSpeedKmh(speed: Float?): Int? {
        if (speed == null || !speed.isFinite()) return null
        return speed.roundToInt().coerceIn(0, 300)
    }

    private fun debug(message: String) {
        MbCanDiagnostics.log("DEBUG", LOG_TAG, message)
    }

    enum class HardkeyCruiseKey {
        ResPlus,
        SetMinus,
    }

    sealed class HardkeySetpointDecision {
        data object Ignore : HardkeySetpointDecision()
        data object KeepRemembered : HardkeySetpointDecision()
        data class CaptureSpeed(val reason: String) : HardkeySetpointDecision()
        data class Nudge(val delta: Int, val reason: String) : HardkeySetpointDecision()
    }

    sealed class StalkEnterActiveDecision {
        data object Keep : StalkEnterActiveDecision()
        data class Capture(val kmh: Int) : StalkEnterActiveDecision()
    }
}
