package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.Android10VhalRepository
import vad.dashing.tbox.mbcan.CcsRememberedSetpoint
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.sendToggleTjaIca
import kotlin.math.abs

enum class AccCruiseState {
    Off,
    Standby,
    Active,
    Override,
    Fault,
}

/**
 * Steering wheel MFS preset controller for Cruise / ACC speed stepping.
 */
internal object LauncherCruisePresetController {
    private const val TAG = "CruisePreset"
    private const val PULSE_HOLD_MS = 80L
    private const val CONVENTIONAL_PULSE_HOLD_MS = 150L
    private const val POST_CONVERGE_VERIFY_MS = 1000L
    private const val MAX_PULSES = 50
    private const val CONVENTIONAL_BATCH_SIZE = 10
    private const val MAX_CONVENTIONAL_TOTAL_PULSES = 60
    private const val TARGET_FINE_STEP_BAND_KMH = 5
    private const val SMART_ACC_DEACTIVATE_DELAY_MS = 3000L
    private const val CCS_ENGAGE_TIMEOUT_MS = 4000L
    private const val CCS_STATE_POLL_MS = 50L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var adjustJob: Job? = null
    private var smartAccExitJob: Job? = null

    private val _adjustingTargetSpeed = MutableStateFlow<Int?>(null)
    val adjustingTargetSpeed: StateFlow<Int?> = _adjustingTargetSpeed.asStateFlow()

    private val _isSmartSpeedLimitActive = MutableStateFlow(false)
    val isSmartSpeedLimitActive: StateFlow<Boolean> = _isSmartSpeedLimitActive.asStateFlow()

    private val _isSmartAccDeactivating = MutableStateFlow(false)
    val isSmartAccDeactivating: StateFlow<Boolean> = _isSmartAccDeactivating.asStateFlow()

    private var wasSmartActiveBeforeOverride = false
    private var lastKnownSpeedLimit: Int? = null

    fun setSmartSpeedLimitActive(active: Boolean) {
        smartAccExitJob?.cancel()
        smartAccExitJob = null
        _isSmartAccDeactivating.value = false
        val changed = _isSmartSpeedLimitActive.value != active
        _isSmartSpeedLimitActive.value = active
        if (!active) {
            wasSmartActiveBeforeOverride = false
        }
        DiagFileLog.i(TAG, "Smart speed limit active set to $active")
        if (changed) {
            val ctx = vad.dashing.tbox.AppContextHolder.appContextOrNull
            if (ctx != null) {
                vad.dashing.tbox.api.DclUiStateNotifier.notifyUiStateChanged(ctx, immediate = false)
            }
        }
    }

    private var rememberedSetpointKmh: Int? = null
    private var conventionalCruiseStandby = false
    private var lastSeenTboxSpeed: UInt? = null

    private fun isConventionalCruise(): Boolean {
        val ctx = vad.dashing.tbox.AppContextHolder.appContextOrNull ?: return false
        return LauncherAppConfigStore.isConventionalCruise(ctx)
    }

    private suspend fun waitPredicate(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            delay(CCS_STATE_POLL_MS)
        }
        return predicate()
    }

    private fun isBrakeActive(): Boolean {
        val breakingForce = CanDataRepository.breakingForce.value ?: 0u
        if (breakingForce > 0u) return true
        if (MbCanEngineFacade.readBrakePedalPressed()) return true
        return false
    }

    private fun computeAccCruiseState(): AccCruiseState {
        val adas = LauncherAdasRepository.state.value
        val tboxCruise = CanDataRepository.cruiseSetSpeed.value
        val isTboxActive = tboxCruise != null && tboxCruise > 0u

        if (isConventionalCruise()) {
            val ccsStatus = CanDataRepository.ccsCruiseStatus.value
            if (ccsStatus != null) {
                return when (ccsStatus) {
                    1 -> {
                        conventionalCruiseStandby = false
                        AccCruiseState.Active
                    }
                    2 -> {
                        conventionalCruiseStandby = true
                        AccCruiseState.Standby
                    }
                    3 -> {
                        conventionalCruiseStandby = false
                        AccCruiseState.Override
                    }
                    else -> {
                        conventionalCruiseStandby = false
                        AccCruiseState.Off
                    }
                }
            }

            // Fallback for packet 0x305 if GaspedStatus is not yet received
            val statusByte = CanDataRepository.cruiseStatusByte.value
            if (statusByte != null && statusByte > 0) {
                val status = statusByte and 0x0F
                when (status) {
                    2 -> {
                        conventionalCruiseStandby = false
                        return AccCruiseState.Active
                    }
                    3 -> {
                        conventionalCruiseStandby = false
                        return AccCruiseState.Override
                    }
                    1 -> {
                        conventionalCruiseStandby = true
                        return AccCruiseState.Standby
                    }
                    0 -> {
                        conventionalCruiseStandby = false
                        return AccCruiseState.Off
                    }
                }
            }

            if (!isTboxActive) {
                conventionalCruiseStandby = false
                lastSeenTboxSpeed = null
                return AccCruiseState.Off
            }

            if (isBrakeActive()) {
                conventionalCruiseStandby = true
            }

            if (tboxCruise != lastSeenTboxSpeed && lastSeenTboxSpeed != null && !isBrakeActive()) {
                conventionalCruiseStandby = false
            }
            lastSeenTboxSpeed = tboxCruise

            return if (conventionalCruiseStandby) AccCruiseState.Standby else AccCruiseState.Active
        }

        return when {
            adas.accMode == LauncherAdasAccMode.Fault -> AccCruiseState.Fault
            adas.accActive -> AccCruiseState.Active
            adas.accOverride -> AccCruiseState.Override
            adas.accStandby -> AccCruiseState.Standby
            adas.accMode == LauncherAdasAccMode.Unknown && isTboxActive -> AccCruiseState.Active
            else -> AccCruiseState.Off
        }
    }

    private val _accCruiseState = MutableStateFlow(computeAccCruiseState())
    val accCruiseState: StateFlow<AccCruiseState> = _accCruiseState.asStateFlow()

    fun currentAccCruiseState(): AccCruiseState = computeAccCruiseState()

    init {
        scope.launch {
            combine(
                LauncherAdasRepository.state,
                CanDataRepository.cruiseSetSpeed,
                CanDataRepository.breakingForce,
                CanDataRepository.cruiseStatusByte,
                CanDataRepository.ccsCruiseStatus,
                LauncherAppConfigStore.cruiseTypeRevisionFlow,
            ) { _ ->
                computeAccCruiseState()
            }.collect { newState ->
                val previousState = _accCruiseState.value
                _accCruiseState.value = newState
                if (previousState != newState) {
                    val ctx = vad.dashing.tbox.AppContextHolder.appContextOrNull
                    if (ctx != null) {
                        vad.dashing.tbox.api.DclUiStateNotifier.notifyUiStateChanged(ctx, immediate = false)
                    }
                }

                if (previousState == AccCruiseState.Active && newState == AccCruiseState.Override) {
                    wasSmartActiveBeforeOverride = _isSmartSpeedLimitActive.value
                    DiagFileLog.i(TAG, "ACC transition Active -> Override (wasSmartActiveBeforeOverride=$wasSmartActiveBeforeOverride)")
                } else if (previousState == AccCruiseState.Override && newState == AccCruiseState.Active) {
                    DiagFileLog.i(TAG, "ACC transition Override -> Active (wasSmartActiveBeforeOverride=$wasSmartActiveBeforeOverride)")
                    if (wasSmartActiveBeforeOverride) {
                        wasSmartActiveBeforeOverride = false
                        val ctx = vad.dashing.tbox.AppContextHolder.appContextOrNull
                        if (ctx != null) {
                            triggerSmartAccOnOverrideExit(ctx)
                        }
                    }
                } else if (newState == AccCruiseState.Off || newState == AccCruiseState.Fault) {
                    wasSmartActiveBeforeOverride = false
                }

                if (newState == AccCruiseState.Active || newState == AccCruiseState.Override) {
                    if (smartAccExitJob?.isActive == true) {
                        DiagFileLog.i(TAG, "ACC returned to $newState before ${SMART_ACC_DEACTIVATE_DELAY_MS}ms timeout -> cancelling smart speed limit deactivation")
                        smartAccExitJob?.cancel()
                        smartAccExitJob = null
                        _isSmartAccDeactivating.value = false
                    }
                } else if ((previousState == AccCruiseState.Active || previousState == AccCruiseState.Override) &&
                    _isSmartSpeedLimitActive.value
                ) {
                    DiagFileLog.i(TAG, "ACC transitioned from $previousState to $newState -> scheduling smart speed limit deactivation in ${SMART_ACC_DEACTIVATE_DELAY_MS}ms")
                    smartAccExitJob?.cancel()
                    _isSmartAccDeactivating.value = true
                    smartAccExitJob = scope.launch {
                        delay(SMART_ACC_DEACTIVATE_DELAY_MS)
                        val currentState = _accCruiseState.value
                        if (currentState != AccCruiseState.Active && currentState != AccCruiseState.Override) {
                            DiagFileLog.i(TAG, "ACC remained in $currentState for ${SMART_ACC_DEACTIVATE_DELAY_MS}ms -> turning off smart speed limit mode")
                            _isSmartSpeedLimitActive.value = false
                        }
                        _isSmartAccDeactivating.value = false
                    }
                }
            }
        }
    }

    fun currentSetSpeedKmh(): Int? {
        if (isConventionalCruise()) {
            val ccsSetpoint = CcsRememberedSetpoint.kmh.value?.takeIf { it > 0 }
            if (ccsSetpoint != null) return ccsSetpoint
            val tboxSpeed = CanDataRepository.cruiseSetSpeed.value?.toInt()?.takeIf { it > 0 }
            if (tboxSpeed != null) return tboxSpeed
        }
        val adasSpeed = LauncherAdasRepository.state.value.accSetSpeedKmh?.takeIf { it > 0 }
        if (adasSpeed != null) return adasSpeed
        val tboxSpeed = CanDataRepository.cruiseSetSpeed.value?.toInt()?.takeIf { it > 0 }
        if (tboxSpeed != null) return tboxSpeed
        return null
    }

    fun vehicleSpeedKmh(): Int {
        val raw = CanDataRepository.carSpeedAccurate.value ?: CanDataRepository.carSpeed.value ?: 0f
        return if (raw.isFinite()) kotlin.math.round(raw).toInt().coerceIn(0, 260) else 0
    }

    private fun getRememberedSetpoint(context: Context): Int? {
        val canSpeed = currentSetSpeedKmh()
        if (canSpeed != null && canSpeed >= 30) {
            rememberedSetpointKmh = canSpeed
            return canSpeed
        }
        if (rememberedSetpointKmh != null && rememberedSetpointKmh!! >= 30) {
            return rememberedSetpointKmh
        }
        val saved = LauncherAppConfigStore.lastCruiseSpeedKmh(context)
        if (saved >= 30) return saved
        return null
    }

    private fun updateRememberedSetpoint(context: Context, speedKmh: Int) {
        val valid = speedKmh.coerceIn(30, 160)
        rememberedSetpointKmh = valid
        LauncherAppConfigStore.setLastCruiseSpeedKmh(context, valid)
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        adjustJob?.cancel()
        _adjustingTargetSpeed.value = null
        adjustJob = scope.launch {
            mutex.withLock {
                block()
            }
        }
    }

    fun launchEngageToTarget(
        context: Context,
        targetKmh: Int,
        increaseIntervalMs: Long = LauncherAppConfigStore.cruiseIncreaseIntervalMs(context),
        decreaseIntervalMs: Long = LauncherAppConfigStore.cruiseDecreaseIntervalMs(context),
        isFromSpeedLimit: Boolean = false,
    ) {
        setSmartSpeedLimitActive(isFromSpeedLimit)
        if (isConventionalCruise()) {
            conventionalCruiseStandby = false
        }
        val target = targetKmh.coerceIn(30, 160)
        val ngpOn = UniversalCanRepository.tjaIcaState.value is MbCanBinaryState.On
        val ngpConfig = LauncherAppConfigStore.ngpEnabled(context)
        val ngp = ngpOn || ngpConfig
        updateRememberedSetpoint(context, target)

        if (LauncherDevVehicleState.simulateEnabled) {
            CanDataRepository.updateCruiseSetSpeed(target.toUInt())
            LauncherDevVehicleState.adasCruiseActive = true
            if (ngp) {
                LauncherDevVehicleState.adasLanesActive = true
                LauncherDevVehicleState.adasLkaActive = true
            }
            LauncherAdasRepository.triggerTimeGapFlash(3000L)
            return
        }

        launchExclusive {
            _adjustingTargetSpeed.value = target
            try {
                var state = currentAccCruiseState()
                if (state == AccCruiseState.Fault) {
                    DiagFileLog.w(TAG, "Cruise state is Fault (ACCMode 9), ignoring click")
                    return@launchExclusive
                }

                val currentSpeed = currentSetSpeedKmh()

                // If already Active and at target speed, single click pauses (MFS_CANCEL)
                if (state == AccCruiseState.Active && currentSpeed == target) {
                    if (isFromSpeedLimit) {
                        DiagFileLog.i(TAG, "Already active at target limit $target km/h (isFromSpeedLimit), ignoring MFS_CANCEL")
                        return@launchExclusive
                    }
                    DiagFileLog.i(TAG, "Already active at target $target km/h -> pulse MFS_CANCEL")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CANCEL)
                    return@launchExclusive
                }

                // Enable: Off -> Standby
                if (state == AccCruiseState.Off) {
                    DiagFileLog.i(TAG, "Cruise Off -> pulse MFS_CRUISE_CONTROL")
                    if (isConventionalCruise()) {
                        CcsRememberedSetpoint.markOurPulse()
                    }
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)

                    if (isConventionalCruise()) {
                        val waitStart = SystemClock.uptimeMillis()
                        while (SystemClock.uptimeMillis() - waitStart < CCS_ENGAGE_TIMEOUT_MS) {
                            delay(CCS_STATE_POLL_MS)
                            state = currentAccCruiseState()
                            if (state == AccCruiseState.Standby || state == AccCruiseState.Active) break
                        }
                    } else {
                        val waitStart = SystemClock.uptimeMillis()
                        while (SystemClock.uptimeMillis() - waitStart < 4000L) {
                            delay(100L)
                            state = currentAccCruiseState()
                            if (state == AccCruiseState.Standby || state == AccCruiseState.Active) break
                        }
                    }
                }

                // Activate: Standby -> Active
                if (state == AccCruiseState.Standby) {
                    DiagFileLog.i(TAG, "Cruise Standby -> pulse MFS_SET_MINUS")
                    if (isConventionalCruise()) {
                        CcsRememberedSetpoint.markOurPulse()
                    }
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    if (isConventionalCruise()) {
                        waitPredicate(CCS_ENGAGE_TIMEOUT_MS) {
                            val s = currentAccCruiseState()
                            s == AccCruiseState.Active || s == AccCruiseState.Override
                        }
                    } else {
                        delay(250L)
                    }
                    state = currentAccCruiseState()
                }

                // Enable NGP if configured
                if (ngpConfig && !ngpOn) {
                    UniversalCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.TJA_ICA, 2)
                    )
                }

                // Stepping loop
                var current = currentSetSpeedKmh() ?: vehicleSpeedKmh().coerceAtLeast(30)
                var totalPulses = 0
                val maxAllowedPulses = if (isConventionalCruise()) MAX_CONVENTIONAL_TOTAL_PULSES else MAX_PULSES

                steppingLoop@ while (current != target && totalPulses < maxAllowedPulses) {
                    val currentState = currentAccCruiseState()
                    if (currentState != AccCruiseState.Active && currentState != AccCruiseState.Override) {
                        DiagFileLog.i(TAG, "Cruise no longer active ($currentState), stopping loop")
                        break@steppingLoop
                    }

                    val distance = abs(current - target)
                    val batchSize = if (isConventionalCruise() && distance > TARGET_FINE_STEP_BAND_KMH) {
                        minOf(CONVENTIONAL_BATCH_SIZE, distance - TARGET_FINE_STEP_BAND_KMH)
                    } else {
                        1
                    }

                    var step = 0
                    batchLoop@ while (step < batchSize) {
                        step++
                        val cs = currentAccCruiseState()
                        if (cs != AccCruiseState.Active && cs != AccCruiseState.Override) {
                            DiagFileLog.i(TAG, "Cruise no longer active ($cs), stopping loop")
                            break@steppingLoop
                        }

                        if (current < target) {
                            pulse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS)
                            if (isConventionalCruise()) {
                                CcsRememberedSetpoint.markOurPulse()
                                CcsRememberedSetpoint.nudgeBy(1, "preset_stepping")
                            }
                            delay(increaseIntervalMs)
                        } else {
                            pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                            if (isConventionalCruise()) {
                                CcsRememberedSetpoint.markOurPulse()
                                CcsRememberedSetpoint.nudgeBy(-1, "preset_stepping")
                            }
                            delay(decreaseIntervalMs)
                        }

                        val fresh = currentSetSpeedKmh()
                        current = fresh ?: (current + if (current < target) 1 else -1)
                        totalPulses++

                        if (current == target) {
                            break@steppingLoop
                        }
                    }

                    if (batchSize > 1) {
                        delay(100L)
                    }
                }

                DiagFileLog.i(TAG, "Stepping loop finished. Target=$target, pulses=$totalPulses, now=${currentSetSpeedKmh()}")

                if (isConventionalCruise()) {
                    val speedDelta = abs((currentSetSpeedKmh() ?: target) - vehicleSpeedKmh())
                    // Allow 1.2s per km/h of speed delta + 5s buffer (min 8s, max 45s) for vehicle to reach target
                    val settleMs = (speedDelta * 1200L + 5000L).coerceIn(8_000L, 45_000L)
                    CcsRememberedSetpoint.markOurPulse(settleMs)
                }

                // Post-converge verify & catch-up (ACC only, since conventional handles fine steps in main loop)
                if (!isConventionalCruise()) {
                    delay(POST_CONVERGE_VERIFY_MS)
                    val postCheck = currentSetSpeedKmh()
                    if (postCheck != null && postCheck != target && abs(postCheck - target) <= 5) {
                        DiagFileLog.i(TAG, "Post-converge catch-up: current=$postCheck, target=$target")
                        var catchUpPulses = 0
                        var catchCurrent: Int = postCheck
                        while (catchCurrent != target && catchUpPulses < 5) {
                            val cs = currentAccCruiseState()
                            if (cs != AccCruiseState.Active && cs != AccCruiseState.Override) break
                            if (catchCurrent < target) {
                                pulse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS)
                                delay(increaseIntervalMs)
                            } else {
                                pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                                delay(decreaseIntervalMs)
                            }
                            val fresh = currentSetSpeedKmh()
                            catchCurrent = fresh ?: (catchCurrent + if (catchCurrent < target) 1 else -1)
                            catchUpPulses++
                        }
                    }
                }
            } finally {
                _adjustingTargetSpeed.value = null
            }
        }
    }

    fun launchStatusSingleTap(context: Context) {
        setSmartSpeedLimitActive(false)
        if (LauncherDevVehicleState.simulateEnabled) {
            val active = LauncherDevVehicleState.adasCruiseActive
            if (active) {
                LauncherDevVehicleState.adasCruiseActive = false
            } else {
                LauncherDevVehicleState.adasCruiseActive = true
                val speed = getRememberedSetpoint(context) ?: 90
                CanDataRepository.updateCruiseSetSpeed(speed.toUInt())
            }
            return
        }

        launchExclusive {
            val state = currentAccCruiseState()
            DiagFileLog.i(TAG, "launchStatusSingleTap state=$state")

            when (state) {
                AccCruiseState.Fault -> {
                    DiagFileLog.w(TAG, "Single tap in Fault state, ignored")
                }
                AccCruiseState.Active, AccCruiseState.Override -> {
                    DiagFileLog.i(TAG, "Single tap in Active/Override -> turn off cruise via pulse MFS_CRUISE_CONTROL (210)")
                    currentSetSpeedKmh()?.let { LauncherAppConfigStore.setLastCruiseSpeedKmh(context, it) }
                    _adjustingTargetSpeed.value = null
                    LauncherAdasRepository.clearTimeGapFlash()
                    if (isConventionalCruise()) {
                        conventionalCruiseStandby = false
                        CcsRememberedSetpoint.markOurPulse()
                    }
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                    if (isConventionalCruise()) {
                        waitPredicate(CCS_ENGAGE_TIMEOUT_MS) {
                            currentAccCruiseState() == AccCruiseState.Off
                        }
                    }
                }
                AccCruiseState.Off -> {
                    if (isConventionalCruise()) {
                        DiagFileLog.i(TAG, "Single tap in Off (CCS) -> pulse MFS_CRUISE_CONTROL (210), wait Standby/Active, then pulse MFS_SET_MINUS (214)")
                        CcsRememberedSetpoint.markOurPulse()
                        pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                        val enteredReady = waitPredicate(CCS_ENGAGE_TIMEOUT_MS) {
                            val s = currentAccCruiseState()
                            s == AccCruiseState.Standby || s == AccCruiseState.Active
                        }
                        if (!enteredReady) {
                            DiagFileLog.w(TAG, "CCS enable timeout waiting for Standby/Active")
                            return@launchExclusive
                        }
                        if (currentAccCruiseState() == AccCruiseState.Active) {
                            DiagFileLog.i(TAG, "CCS became Active immediately")
                            val speed = vehicleSpeedKmh().coerceAtLeast(30)
                            updateRememberedSetpoint(context, speed)
                            conventionalCruiseStandby = false
                            return@launchExclusive
                        }
                        if (currentAccCruiseState() == AccCruiseState.Standby) {
                            delay(50L)
                            CcsRememberedSetpoint.markOurPulse()
                            pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                            val becameActive = waitPredicate(CCS_ENGAGE_TIMEOUT_MS) {
                                currentAccCruiseState() == AccCruiseState.Active
                            }
                            DiagFileLog.i(TAG, "CCS SET- finished becameActive=$becameActive")
                            val speed = vehicleSpeedKmh().coerceAtLeast(30)
                            updateRememberedSetpoint(context, speed)
                            conventionalCruiseStandby = false
                        }
                    } else {
                        DiagFileLog.i(TAG, "Single tap in Off -> pulse MFS_CRUISE_CONTROL (210) then delay 500ms -> pulse MFS_SET_MINUS (214)")
                        pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                        delay(500L)
                        pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                        val speed = vehicleSpeedKmh().coerceAtLeast(30)
                        updateRememberedSetpoint(context, speed)
                    }
                }
                AccCruiseState.Standby -> {
                    if (isConventionalCruise()) {
                        DiagFileLog.i(TAG, "Single tap in Standby (CCS) -> pulse MFS_SET_MINUS (214)")
                        CcsRememberedSetpoint.markOurPulse()
                        pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                        waitPredicate(CCS_ENGAGE_TIMEOUT_MS) {
                            currentAccCruiseState() == AccCruiseState.Active
                        }
                        val speed = vehicleSpeedKmh().coerceAtLeast(30)
                        updateRememberedSetpoint(context, speed)
                        conventionalCruiseStandby = false
                    } else {
                        DiagFileLog.i(TAG, "Single tap in Standby -> pulse MFS_SET_MINUS (214)")
                        pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                        val speed = vehicleSpeedKmh().coerceAtLeast(30)
                        updateRememberedSetpoint(context, speed)
                    }
                }
            }
        }
    }

    fun launchNgpWithSetMinus(context: Context) {
        setSmartSpeedLimitActive(false)
        if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.toggleAdasLanes()
            LauncherDevVehicleState.adasCruiseActive = LauncherDevVehicleState.adasLanesActive
            return
        }

        sendToggleTjaIca(context)

        launchExclusive {
            val state = currentAccCruiseState()
            DiagFileLog.i(TAG, "launchNgpWithSetMinus state=$state")

            when (state) {
                AccCruiseState.Off -> {
                    DiagFileLog.i(TAG, "NGP tap in Off -> pulse MFS_CRUISE_CONTROL (210) then delay 500ms -> pulse MFS_SET_MINUS (214)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                    delay(500L)
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val speed = vehicleSpeedKmh().coerceAtLeast(30)
                    updateRememberedSetpoint(context, speed)
                }
                AccCruiseState.Standby -> {
                    DiagFileLog.i(TAG, "NGP tap in Standby -> delay 500ms -> pulse MFS_SET_MINUS (214)")
                    delay(500L)
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val speed = vehicleSpeedKmh().coerceAtLeast(30)
                    updateRememberedSetpoint(context, speed)
                }
                else -> {
                    // Cruise is already active
                }
            }
        }
    }

    fun launchStatusSwipeUp(context: Context) {
        setSmartSpeedLimitActive(false)
        if (LauncherDevVehicleState.simulateEnabled) {
            val current = currentSetSpeedKmh() ?: vehicleSpeedKmh().coerceAtLeast(30)
            val next = (current + 1).coerceIn(30, 160)
            CanDataRepository.updateCruiseSetSpeed(next.toUInt())
            LauncherDevVehicleState.adasCruiseActive = true
            return
        }

        launchExclusive {
            val state = currentAccCruiseState()
            DiagFileLog.i(TAG, "launchStatusSwipeUp state=$state")

            when (state) {
                AccCruiseState.Standby -> {
                    DiagFileLog.i(TAG, "SwipeUp in Standby -> pulse MFS_RES_PLUS (213)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS)
                    if (isConventionalCruise()) {
                        conventionalCruiseStandby = false
                    }
                }
                AccCruiseState.Active, AccCruiseState.Override -> {
                    DiagFileLog.i(TAG, "SwipeUp in Active/Override -> pulse MFS_RES_PLUS (213) (+1 km/h)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS)
                    val cur = currentSetSpeedKmh() ?: getRememberedSetpoint(context) ?: vehicleSpeedKmh()
                    updateRememberedSetpoint(context, (cur + 1).coerceIn(30, 160))
                }
                AccCruiseState.Off, AccCruiseState.Fault -> {
                    DiagFileLog.i(TAG, "SwipeUp in $state -> ignored")
                }
            }
        }
    }

    fun launchStatusSwipeDown(context: Context) {
        setSmartSpeedLimitActive(false)
        if (LauncherDevVehicleState.simulateEnabled) {
            val current = currentSetSpeedKmh() ?: vehicleSpeedKmh().coerceAtLeast(30)
            val next = (current - 1).coerceIn(30, 160)
            CanDataRepository.updateCruiseSetSpeed(next.toUInt())
            LauncherDevVehicleState.adasCruiseActive = true
            return
        }

        launchExclusive {
            val state = currentAccCruiseState()
            DiagFileLog.i(TAG, "launchStatusSwipeDown state=$state")

            when (state) {
                AccCruiseState.Standby -> {
                    DiagFileLog.i(TAG, "SwipeDown in Standby -> pulse MFS_SET_MINUS (214)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val speed = vehicleSpeedKmh().coerceAtLeast(30)
                    updateRememberedSetpoint(context, speed)
                    if (isConventionalCruise()) {
                        conventionalCruiseStandby = false
                    }
                }
                AccCruiseState.Active, AccCruiseState.Override -> {
                    DiagFileLog.i(TAG, "SwipeDown in Active/Override -> pulse MFS_SET_MINUS (214) (-1 km/h)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val cur = currentSetSpeedKmh() ?: getRememberedSetpoint(context) ?: vehicleSpeedKmh()
                    updateRememberedSetpoint(context, (cur - 1).coerceIn(30, 160))
                }
                AccCruiseState.Off, AccCruiseState.Fault -> {
                    DiagFileLog.i(TAG, "SwipeDown in $state -> ignored")
                }
            }
        }
    }

    fun launchFullOff(context: Context) {
        setSmartSpeedLimitActive(false)
        if (isConventionalCruise()) {
            conventionalCruiseStandby = false
        }
        adjustJob?.cancel()
        _adjustingTargetSpeed.value = null
        LauncherAdasRepository.clearTimeGapFlash()
        if (LauncherDevVehicleState.simulateEnabled) {
            CanDataRepository.updateCruiseSetSpeed(0u)
            LauncherDevVehicleState.adasCruiseActive = false
            LauncherDevVehicleState.adasLanesActive = false
            LauncherDevVehicleState.adasLkaActive = false
            LauncherDevVehicleState.clearTimeGapFlash()
            return
        }
        scope.launch {
            mutex.withLock {
                pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
            }
        }
    }

    fun applyPreset(context: Context, targetKmh: Int) {
        setSmartSpeedLimitActive(false)
        launchEngageToTarget(context, targetKmh, isFromSpeedLimit = false)
    }

    fun currentEffectiveSpeedLimit(context: Context): Int? {
        val navState = YandexNavRepository.state.value
        val navSpeedVisible = LauncherAppConfigStore.navSpeedVisible(context)
        val rawYandexLimit = navState.speedLimit?.filter { it.isDigit() }?.toIntOrNull()
        val yandexLimit = if (navSpeedVisible) rawYandexLimit else null
        val tsrSystemEnabled = LauncherAppConfigStore.tsrSystemEnabled(context)
        val classicSpeedLimitVisible = LauncherAppConfigStore.classicSpeedLimitVisible(context)
        val adas = LauncherDevVehicleState.adasStateOrNull() ?: LauncherAdasRepository.state.value
        val rawCameraLimit = adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
        val cameraLimit = if (tsrSystemEnabled && (classicSpeedLimitVisible || navSpeedVisible)) {
            if (yandexLimit != null) null else rawCameraLimit
        } else null
        val effective = yandexLimit ?: cameraLimit
        if (effective != null) {
            lastKnownSpeedLimit = effective
        }
        return effective ?: lastKnownSpeedLimit
    }

    private fun triggerSmartAccOnOverrideExit(context: Context) {
        val adasAccBySpeedLimitEnabled = LauncherAppConfigStore.adasAccBySpeedLimitEnabled(context)
        if (!adasAccBySpeedLimitEnabled) {
            DiagFileLog.i(TAG, "Override -> Active: adasAccBySpeedLimitEnabled is false, keeping active state only")
            setSmartSpeedLimitActive(true)
            return
        }
        val limit = currentEffectiveSpeedLimit(context) ?: lastKnownSpeedLimit
        val offset = LauncherAppConfigStore.adasAccSpeedOffset(context)
        if (limit != null) {
            val targetSpeed = limit + offset
            DiagFileLog.i(TAG, "Override -> Active: trigger Smart ACC target=$targetSpeed km/h (limit=$limit, offset=$offset)")
            applySpeedLimitPreset(context, targetSpeed)
        } else {
            DiagFileLog.i(TAG, "Override -> Active: no speed limit known, keeping smart speed limit active")
            setSmartSpeedLimitActive(true)
        }
    }

    fun applySpeedLimitPreset(context: Context, targetKmh: Int) {
        val offset = LauncherAppConfigStore.adasAccSpeedOffset(context)
        lastKnownSpeedLimit = targetKmh - offset
        setSmartSpeedLimitActive(true)
        launchEngageToTarget(context, targetKmh, isFromSpeedLimit = true)
    }

    fun setNgpEnabled(context: Context, enabled: Boolean) {
        LauncherAppConfigStore.setNgpEnabled(context, enabled)
        val cruiseActive = currentAccCruiseState() == AccCruiseState.Active
        if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.adasLanesActive = cruiseActive && enabled
            LauncherDevVehicleState.adasLkaActive = cruiseActive && enabled
            return
        }
        scope.launch {
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(
                    MbCanKnownVehiclePropertyId.TJA_ICA,
                    if (enabled) 2 else 1,
                )
            )
        }
    }

    fun toggleCruise(context: Context, currentSpeedKmh: Int? = null) {
        val state = currentAccCruiseState()
        if (state == AccCruiseState.Active || state == AccCruiseState.Standby) {
            currentSetSpeedKmh()?.let { LauncherAppConfigStore.setLastCruiseSpeedKmh(context, it) }
            launchFullOff(context)
        } else {
            val targetKmh = (currentSpeedKmh?.takeIf { it >= 30 })
                ?: LauncherAppConfigStore.lastCruiseSpeedKmh(context)
            launchEngageToTarget(context, targetKmh)
        }
    }

    fun setTimeGap(context: Context, level: Int) {
        val validLevel = level.coerceIn(0, 3)
        val storedLevel = if (validLevel in 0..2) validLevel + 1 else validLevel
        LauncherAppConfigStore.setTimeGapLevel(context, storedLevel)
        LauncherAdasRepository.triggerTimeGapFlash(3000L)
        if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.adasTimeGapLevel = validLevel
            LauncherDevVehicleState.triggerTimeGapFlash(3000L)
            return
        }
        scope.launch {
            mutex.withLock {
                pulse(MbCanKnownVehiclePropertyId.MFS_TIME_GAP)
            }
        }
    }

    private suspend fun pulse(propertyId: Int) = withContext(Dispatchers.IO) {
        val holdMs = if (isConventionalCruise()) CONVENTIONAL_PULSE_HOLD_MS else PULSE_HOLD_MS
        val isAndroid10Vhal = UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal
        try {
            if (isAndroid10Vhal) {
                Android10VhalRepository.execute(MbCanCommand.SetProperty(propertyId, 1))
            } else {
                MbCanEngineFacade.canSetVehicleParam(propertyId, 1)
            }
            delay(holdMs)
        } finally {
            withContext(NonCancellable) {
                if (isAndroid10Vhal) {
                    Android10VhalRepository.execute(MbCanCommand.SetProperty(propertyId, 0))
                } else {
                    MbCanEngineFacade.canSetVehicleParam(propertyId, 0)
                }
            }
        }
    }
}
