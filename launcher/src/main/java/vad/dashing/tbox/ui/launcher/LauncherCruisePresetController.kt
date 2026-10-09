package vad.dashing.tbox.ui.launcher

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.Android10VhalRepository
import kotlin.math.abs

/**
 * One-tap cruise / ACC speed presets.
 *
 * OEM has no "set 110 km/h" API. Confirmed path is the steering-wheel MFS keys
 * ([HardKeyService] pulses [MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL] = 1).
 * We SET if needed, then tap RES+ / SET− until the displayed set-speed matches.
 * On Android 10 also try VirtualCar `ID_CRUISE_SPEED_SET` (826278914).
 */
internal object LauncherCruisePresetController {
    private const val TAG = "CruisePreset"
    private const val VHAL_CRUISE_SPEED_SET = 826278914
    private const val PULSE_HOLD_MS = 80L
    private const val PULSE_GAP_MS = 160L
    private const val SETTLE_MS = 350L
    private const val MAX_PULSES = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var job: Job? = null

    fun applyPreset(context: Context, targetKmh: Int) {
        val kmh = targetKmh.coerceIn(30, 160)
        val ngp = LauncherAppConfigStore.ngpEnabled(context)
        LauncherAppConfigStore.setLastCruiseSpeedKmh(context, kmh)
        CanDataRepository.updateCruiseSetSpeed(kmh.toUInt())
        if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.adasCruiseActive = true
            LauncherDevVehicleState.adasLanesActive = ngp
            LauncherDevVehicleState.adasLkaActive = ngp
            LauncherAdasRepository.triggerTimeGapFlash(3000L)
            return
        }
        job?.cancel()
        job = scope.launch {
            mutex.withLock {
                MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.TJA_ICA, if (ngp) 2 else 1)
                runPreset(kmh)
            }
        }
    }

    fun setNgpEnabled(context: Context, enabled: Boolean) {
        LauncherAppConfigStore.setNgpEnabled(context, enabled)
        val cruiseActive = isCruiseEngaged()
        if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.adasLanesActive = cruiseActive && enabled
            LauncherDevVehicleState.adasLkaActive = cruiseActive && enabled
            return
        }
        if (cruiseActive) {
            scope.launch {
                MbCanEngineFacade.canSetVehicleParam(
                    MbCanKnownVehiclePropertyId.TJA_ICA,
                    if (enabled) 2 else 1,
                )
            }
        }
    }

    fun toggleCruise(context: Context) {
        val currentlyEngaged = isCruiseEngaged()
        if (currentlyEngaged) {
            currentSetSpeed()?.let { LauncherAppConfigStore.setLastCruiseSpeedKmh(context, it) }
            CanDataRepository.updateCruiseSetSpeed(0u)
            LauncherAdasRepository.clearTimeGapFlash()
            if (LauncherDevVehicleState.simulateEnabled) {
                LauncherDevVehicleState.adasCruiseActive = false
                LauncherDevVehicleState.adasLanesActive = false
                LauncherDevVehicleState.adasLkaActive = false
                LauncherDevVehicleState.clearTimeGapFlash()
            } else {
                scope.launch {
                    pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
                }
            }
        } else {
            val targetKmh = LauncherAppConfigStore.lastCruiseSpeedKmh(context)
            applyPreset(context, targetKmh)
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

    private suspend fun runPreset(targetKmh: Int) {
        DiagFileLog.i(TAG, "apply preset=$targetKmh")
        if (tryAbsoluteSet(targetKmh)) {
            delay(SETTLE_MS)
            if (currentSetSpeed() == targetKmh) {
                DiagFileLog.i(TAG, "absolute set ok target=$targetKmh")
                return
            }
        }
        if (!isCruiseEngaged()) {
            pulse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL)
            delay(SETTLE_MS)
        }
        var current = adasSetSpeed() ?: vehicleSpeedKmh() ?: targetKmh
        if (current == targetKmh) return
        var step = 1
        var unchanged = 0
        var pulses = 0
        while (abs(current - targetKmh) > 0 && pulses < MAX_PULSES) {
            val key = if (current < targetKmh) {
                MbCanKnownVehiclePropertyId.MFS_RES_PLUS
            } else {
                MbCanKnownVehiclePropertyId.MFS_SET_MINUS
            }
            pulse(key)
            delay(PULSE_GAP_MS)
            val next = adasSetSpeed()
            if (next != null) {
                val delta = abs(next - current)
                if (delta >= 2) step = delta
                if (next == current) {
                    unchanged++
                    if (unchanged >= 3) {
                        DiagFileLog.i(TAG, "set-speed not moving, stop at $current")
                        break
                    }
                } else {
                    unchanged = 0
                }
                current = next
            } else {
                current += if (key == MbCanKnownVehiclePropertyId.MFS_RES_PLUS) step else -step
            }
            pulses++
        }
        DiagFileLog.i(TAG, "done target=$targetKmh now=${currentSetSpeed()} pulses=$pulses")
    }

    private fun isCruiseEngaged(): Boolean {
        val adas = LauncherAdasRepository.state.value
        if (adas.accActive || adas.accStandby) return true
        val tbox = CanDataRepository.cruiseSetSpeed.value
        return tbox != null && tbox > 0u
    }

    private fun adasSetSpeed(): Int? {
        return LauncherAdasRepository.state.value.accSetSpeedKmh?.takeIf { it > 0 }
    }

    private fun currentSetSpeed(): Int? {
        LauncherAdasRepository.state.value.accSetSpeedKmh?.takeIf { it > 0 }?.let { return it }
        return CanDataRepository.cruiseSetSpeed.value?.toInt()?.takeIf { it > 0 }
    }

    private fun vehicleSpeedKmh(): Int? {
        val speed = CanDataRepository.carSpeedAccurate.value
            ?: CanDataRepository.carSpeed.value
            ?: return null
        return speed.roundToIntOrNull()?.takeIf { it > 0 }
    }

    private fun Float.roundToIntOrNull(): Int? =
        if (this.isFinite()) kotlin.math.round(this).toInt() else null

    private suspend fun pulse(propertyId: Int) {
        MbCanEngineFacade.canSetVehicleParam(propertyId, 1)
        delay(PULSE_HOLD_MS)
        MbCanEngineFacade.canSetVehicleParam(propertyId, 0)
    }

    private fun tryAbsoluteSet(targetKmh: Int): Boolean {
        if (UniversalCanRepository.mode.value != HeadUnitCanMode.Android10Vhal) return false
        return Android10VhalRepository.trySetIntProperty(VHAL_CRUISE_SPEED_SET, targetKmh)
    }
}
