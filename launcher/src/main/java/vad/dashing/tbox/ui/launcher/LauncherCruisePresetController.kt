package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.Android10VhalRepository
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
    private const val POST_CONVERGE_VERIFY_MS = 1000L
    private const val MAX_PULSES = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var adjustJob: Job? = null

    private val _adjustingTargetSpeed = MutableStateFlow<Int?>(null)
    val adjustingTargetSpeed: StateFlow<Int?> = _adjustingTargetSpeed.asStateFlow()

    private var rememberedSetpointKmh: Int? = null

    fun currentAccCruiseState(): AccCruiseState {
        val adas = LauncherAdasRepository.state.value
        val tboxCruise = CanDataRepository.cruiseSetSpeed.value
        val isTboxActive = tboxCruise != null && tboxCruise > 0u

        return when {
            adas.accActive || isTboxActive -> AccCruiseState.Active
            adas.accOverride -> AccCruiseState.Override
            adas.accStandby -> AccCruiseState.Standby
            else -> AccCruiseState.Off
        }
    }

    fun currentSetSpeedKmh(): Int? {
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
    ) {
        val target = targetKmh.coerceIn(30, 160)
        val ngpOn = UniversalCanRepository.tjaIcaState.value is MbCanBinaryState.On
        val ngpConfig = LauncherAppConfigStore.ngpEnabled(context)
        val ngp = ngpOn || ngpConfig
        updateRememberedSetpoint(context, target)

        if (LauncherDevVehicleState.simulateEnabled) {
            CanDataRepository.updateCruiseSetSpeed(target.toUInt())
            LauncherDevVehicleState.adasCruiseActive = true
            LauncherDevVehicleState.adasLanesActive = ngp
            LauncherDevVehicleState.adasLkaActive = ngp
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
                    DiagFileLog.i(TAG, "Already active at target $target km/h -> pulse MFS_CANCEL")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CANCEL)
                    return@launchExclusive
                }

                // Enable: Off -> Standby
                if (state == AccCruiseState.Off) {
                    DiagFileLog.i(TAG, "Cruise Off -> pulse MFS_CRUISE_CONTROL")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)

                    val waitStart = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - waitStart < 4000L) {
                        delay(100L)
                        state = currentAccCruiseState()
                        if (state == AccCruiseState.Standby || state == AccCruiseState.Active) break
                    }
                }

                // Activate: Standby -> Active
                if (state == AccCruiseState.Standby) {
                    DiagFileLog.i(TAG, "Cruise Standby -> pulse MFS_SET_MINUS")
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    delay(250L)
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
                var pulses = 0

                while (abs(current - target) > 0 && pulses < MAX_PULSES) {
                    val currentState = currentAccCruiseState()
                    if (currentState == AccCruiseState.Off || currentState == AccCruiseState.Fault) {
                        DiagFileLog.i(TAG, "Cruise turned off or faulted ($currentState), stopping loop")
                        break
                    }

                    if (current < target) {
                        pulse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS)
                        delay(increaseIntervalMs)
                    } else {
                        pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                        delay(decreaseIntervalMs)
                    }

                    val fresh = currentSetSpeedKmh()
                    if (fresh != null) {
                        current = fresh
                    } else {
                        current += if (current < target) 1 else -1
                    }
                    pulses++
                }

                DiagFileLog.i(TAG, "Stepping loop finished. Target=$target, pulses=$pulses, now=${currentSetSpeedKmh()}")

                // Post-converge verify & catch-up
                delay(POST_CONVERGE_VERIFY_MS)
                val postCheck = currentSetSpeedKmh()
                if (postCheck != null && postCheck != target && abs(postCheck - target) <= 5) {
                    DiagFileLog.i(TAG, "Post-converge catch-up: current=$postCheck, target=$target")
                    var catchUpPulses = 0
                    var catchCurrent: Int = postCheck
                    while (catchCurrent != target && catchUpPulses < 5) {
                        val cs = currentAccCruiseState()
                        if (cs == AccCruiseState.Off || cs == AccCruiseState.Fault) break
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
            } finally {
                _adjustingTargetSpeed.value = null
            }
        }
    }

    fun launchStatusSingleTap(context: Context) {
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
                    DiagFileLog.i(TAG, "Single tap in Active/Override -> pulse MFS_CANCEL (212)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CANCEL)
                }
                AccCruiseState.Off -> {
                    DiagFileLog.i(TAG, "Single tap in Off -> pulse MFS_CRUISE_CONTROL (210) then delay 500ms -> pulse MFS_SET_MINUS (214)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                    delay(500L)
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val speed = vehicleSpeedKmh().coerceAtLeast(30)
                    updateRememberedSetpoint(context, speed)
                }
                AccCruiseState.Standby -> {
                    DiagFileLog.i(TAG, "Single tap in Standby -> pulse MFS_SET_MINUS (214)")
                    pulse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS)
                    val speed = vehicleSpeedKmh().coerceAtLeast(30)
                    updateRememberedSetpoint(context, speed)
                }
            }
        }
    }

    fun launchNgpWithSetMinus(context: Context) {
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
        launchEngageToTarget(context, targetKmh)
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
        val validLevel = level.coerceIn(1, 3)
        LauncherAppConfigStore.setTimeGapLevel(context, validLevel)
        LauncherDevVehicleState.adasTimeGapLevel = validLevel
        LauncherDevVehicleState.triggerTimeGapFlash(3000L)
        LauncherAdasRepository.triggerTimeGapFlash(3000L)
        if (LauncherDevVehicleState.simulateEnabled) {
            return
        }
        scope.launch {
            pulse(MbCanKnownVehiclePropertyId.MFS_TIME_GAP)
        }
    }

    private suspend fun pulse(propertyId: Int) {
        if (UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal) {
            Android10VhalRepository.execute(MbCanCommand.SetProperty(propertyId, 1))
            delay(PULSE_HOLD_MS)
            Android10VhalRepository.execute(MbCanCommand.SetProperty(propertyId, 0))
        } else {
            MbCanEngineFacade.canSetVehicleParam(propertyId, 1)
            delay(PULSE_HOLD_MS)
            MbCanEngineFacade.canSetVehicleParam(propertyId, 0)
        }
    }
}
