package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.LastAppTracker
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import kotlin.math.roundToInt

private val steeringHeatToggleLock = Any()
private var steeringHeatToggleBlockedUntilMs = 0L
private const val STEERING_HEAT_TOGGLE_LOCKOUT_MS = 500L

private val windscreenHeatToggleLock = Any()
private var windscreenHeatToggleBlockedUntilMs = 0L

private val wiperMaintenanceToggleLock = Any()
private var wiperMaintenanceToggleBlockedUntilMs = 0L

private val parkingRadarToggleLock = Any()
private var parkingRadarToggleBlockedUntilMs = 0L

private val hvacDefrosterToggleLock = Any()
private var hvacDefrosterToggleBlockedUntilMs = 0L

private val hvacAirRecirculationToggleLock = Any()
private var hvacAirRecirculationToggleBlockedUntilMs = 0L

private val hvacAcToggleLock = Any()
private var hvacAcToggleBlockedUntilMs = 0L

private val hvacAutoToggleLock = Any()
private var hvacAutoToggleBlockedUntilMs = 0L

private val hvacDefrosterFrontToggleLock = Any()
private var hvacDefrosterFrontToggleBlockedUntilMs = 0L

private val hvacTempAdjustLock = Any()
private var hvacTempAdjustBlockedUntilMs = 0L

internal fun launchAppFromWidget(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    try {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
        LastAppTracker.recordLaunch(context, packageName)
        context.startActivity(launchIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun openMainActivityFromWidget(context: Context) {
    try {
        val intent = MainActivityIntentHelper.createBringToFrontIntent(context)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun sendToggleHideOtherFloatingPanels(
    context: Context,
    originPanelId: String,
    excludeOriginPanel: Boolean = true
) {
    if (excludeOriginPanel && originPanelId.isBlank()) return
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS
                putExtra(BackgroundService.EXTRA_FLOATING_PANEL_ORIGIN_ID, originPanelId)
                putExtra(BackgroundService.EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN, excludeOriginPanel)
            }
        )
    } catch (_: Exception) {
    }
}

/**
 * Double-tap on «toggle floating panels enabled» tile: flip [FloatingDashboardConfig.enabled]
 * for every panel except [originPanelId], or for all panels when [toggleAllPanels] is true.
 */
internal fun sendToggleFloatingPanelsEnabled(
    context: Context,
    originPanelId: String,
    toggleAllPanels: Boolean
) {
    if (!toggleAllPanels && originPanelId.isBlank()) return
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_TOGGLE_FLOATING_PANELS_ENABLED
                putExtra(BackgroundService.EXTRA_FLOATING_PANEL_ORIGIN_ID, originPanelId)
                putExtra(BackgroundService.EXTRA_TOGGLE_FLOATING_ENABLED_ALL, toggleAllPanels)
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleMbCanProperty(context: Context, propertyId: Int) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val got = MbCanEngineFacade.canGetVehicleParam(propertyId)
            val nextRaw = if (got == 2) 1 else 2
            val written = MbCanEngineFacade.canSetVehicleParam(propertyId, nextRaw)
            if (written == null) {
                sendSetMbCanProperty(context, propertyId, nextRaw)
            }
            UniversalCanRepository.execute(
                MbCanCommand.ToggleProperty(propertyId)
            )
            Log.w("DashboardAppNav", "sendToggleMbCanProperty propertyId=$propertyId got=$got next=$nextRaw written=$written")
        } catch (e: Exception) {
            Log.e("DashboardAppNav", "sendToggleMbCanProperty failed propertyId=$propertyId", e)
        }
    }
}

internal fun sendToggleSteeringWheelHeat(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(steeringHeatToggleLock) {
        if (now < steeringHeatToggleBlockedUntilMs) return
        steeringHeatToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
}

internal fun sendToggleFrontWindscreenHeat(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(windscreenHeatToggleLock) {
        if (now < windscreenHeatToggleBlockedUntilMs) return
        windscreenHeatToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH)
}

internal fun sendToggleWiperMaintenance(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(wiperMaintenanceToggleLock) {
        if (now < wiperMaintenanceToggleBlockedUntilMs) return
        wiperMaintenanceToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH)
}

internal fun sendToggleParkingRadar(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(parkingRadarToggleLock) {
        if (now < parkingRadarToggleBlockedUntilMs) return
        parkingRadarToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH)
}

internal fun sendToggleRearWindowMirrorsDefrost(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacDefrosterToggleLock) {
        if (now < hvacDefrosterToggleBlockedUntilMs) return
        hvacDefrosterToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH)
}

internal fun sendToggleHvacAirRecirculation(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAirRecirculationToggleLock) {
        if (now < hvacAirRecirculationToggleBlockedUntilMs) return
        hvacAirRecirculationToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION)
}

private val hvacPowerToggleLock = Any()
private var hvacPowerToggleBlockedUntilMs = 0L

internal fun sendToggleHvacPower(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacPowerToggleLock) {
        if (now < hvacPowerToggleBlockedUntilMs) return
        hvacPowerToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }

    val currentAcState = UniversalCanRepository.hvacAcPowerState.value
    val currentFanSpeed = UniversalCanRepository.hvacFanSpeedRawState.value

    val isCurrentlyOn = currentAcState is MbCanBinaryState.On || currentFanSpeed > 0
    val turnOn = !isCurrentlyOn

    val targetFanSpeed = if (turnOn) 3 else 0
    val targetAcPower = if (turnOn) 2 else 1
    val targetWorkingReq = if (turnOn) 2 else 1

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val lTemp = ((CanDataRepository.climateSetTemperature1.value ?: 22f)).roundToInt().coerceIn(16, 30).toByte()
            val rTemp = ((CanDataRepository.climateSetTemperature2.value ?: 22f)).roundToInt().coerceIn(16, 30).toByte()

            val airCond = com.mengbo.mbCan.entity.MBAirCondition(
                targetFanSpeed.toByte(),
                0.toByte(),
                targetAcPower.toByte(),
                targetWorkingReq.toByte(),
                lTemp,
                rTemp,
            )
            MbCanEngineFacade.canSetAirCondition(airCond)

            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_POWER, targetAcPower)
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_POWER, targetAcPower)

            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED, targetFanSpeed)
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED, targetFanSpeed)

            UniversalCanRepository.updateHvacFanSpeedRaw(targetFanSpeed)
            UniversalCanRepository.execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_POWER, targetAcPower))

            Log.i("DashboardAppNav", "sendToggleHvacPower turnOn=$turnOn (fan=$targetFanSpeed, ac=$targetAcPower)")
        } catch (e: Exception) {
            Log.e("DashboardAppNav", "sendToggleHvacPower failed", e)
        }
    }
}

internal fun sendToggleHvacAc(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAcToggleLock) {
        if (now < hvacAcToggleBlockedUntilMs) return
        hvacAcToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_POWER)
}

internal fun sendToggleHvacAuto(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAutoToggleLock) {
        if (now < hvacAutoToggleBlockedUntilMs) return
        hvacAutoToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE)
}

private val hvacSyncToggleLock = Any()
private var hvacSyncToggleBlockedUntilMs = 0L

internal fun sendToggleHvacSync(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacSyncToggleLock) {
        if (now < hvacSyncToggleBlockedUntilMs) return
        hvacSyncToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    val driverTemp = CanDataRepository.climateSetTemperature1.value
    if (driverTemp != null) {
        CanDataRepository.updateClimateSetTemperature2(driverTemp)
        val driverRaw = (driverTemp * MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_PER_CELSIUS).roundToInt()
        sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_FR_TEMPERATURE, driverRaw)
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH)
}

private val hvacPm25ToggleLock = Any()
private var hvacPm25ToggleBlockedUntilMs = 0L

internal fun sendToggleHvacPm25(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacPm25ToggleLock) {
        if (now < hvacPm25ToggleBlockedUntilMs) return
        hvacPm25ToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_PM25_MONITORING)
}

internal fun sendToggleHvacDefrosterFront(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacDefrosterFrontToggleLock) {
        if (now < hvacDefrosterFrontToggleBlockedUntilMs) return
        hvacDefrosterFrontToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION)
}

/**
 * Dual-zone HVAC temperature (OEM MB_ACSettings / MBACTempView):
 * raw = °C × 10, Lo=160 … Hi=300, step ±5.
 */
enum class HvacTempZone {
    Driver,
    Passenger,
}

/** Decode OEM HVAC setpoint raw → °C. Ignore out-of-range noise (was causing 25↔5 flicker). */
internal fun decodeHvacTemperatureRaw(raw: Int): Float? = when {
    raw in MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MIN..
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MAX ->
        (raw.toFloat() / MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_PER_CELSIUS).roundToInt().toFloat()
    // Some firmwares briefly report already-in-°C integers.
    raw in 16..30 -> raw.toFloat()
    else -> null
}

/**
 * OEM hard-key path ([HardKeyService] / MBACTempView): read raw HVAC temp, then ±10 (whole 1°C steps).
 */
internal fun sendAdjustHvacTemperature(
    context: Context,
    currentCelsius: Float?,
    deltaCelsius: Float,
    zone: HvacTempZone = HvacTempZone.Driver,
) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacTempAdjustLock) {
        if (now < hvacTempAdjustBlockedUntilMs) return
        hvacTempAdjustBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
        hvacTempRefreshSuppressedUntilMs = now + 2_500L
    }
    val scale = MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_PER_CELSIUS
    val stepDir = when {
        deltaCelsius > 0f -> 1
        deltaCelsius < 0f -> -1
        else -> return
    }
    // 1.0°C step in raw units = 10 units
    val stepRaw = stepDir * scale
    val propertyId = when (zone) {
        HvacTempZone.Driver -> MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE
        HvacTempZone.Passenger -> MbCanKnownVehiclePropertyId.HVAC_FR_TEMPERATURE
    }
    Thread {
        try {
            val got = MbCanEngineFacade.canGetVehicleParam(propertyId)
            val currentRaw = when {
                got != null && decodeHvacTemperatureRaw(got) != null && got >= 160 -> got
                currentCelsius != null -> (currentCelsius * scale).roundToInt()
                else -> 220
            }
            val rawRounded = (currentRaw / 10) * 10
            val nextRaw = (rawRounded + stepRaw).coerceIn(
                MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MIN,
                MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MAX,
            )
            val celsius = (nextRaw.toFloat() / scale).roundToInt().toFloat()
            when (zone) {
                HvacTempZone.Driver -> CanDataRepository.updateClimateSetTemperature1(celsius)
                HvacTempZone.Passenger -> CanDataRepository.updateClimateSetTemperature2(celsius)
            }
            // Write via engine directly — avoids BackgroundService start flicker on freeform.
            val written = MbCanEngineFacade.canSetVehicleParam(propertyId, nextRaw)
            if (written == null) {
                sendSetMbCanProperty(context, propertyId, nextRaw)
            }
        } catch (_: Exception) {
        }
    }.start()
}

@Volatile
private var hvacTempRefreshSuppressedUntilMs = 0L

/** Pull both zone setpoints from mbCAN into [CanDataRepository]. */
internal fun refreshHvacTemperaturesFromMbCan() {
    if (SystemClock.uptimeMillis() < hvacTempRefreshSuppressedUntilMs) return
    runCatching {
        MbCanEngineFacade
            .canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE)
            ?.let { decodeHvacTemperatureRaw(it) }
            ?.let { CanDataRepository.updateClimateSetTemperature1(it) }
        MbCanEngineFacade
            .canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_FR_TEMPERATURE)
            ?.let { decodeHvacTemperatureRaw(it) }
            ?.let { CanDataRepository.updateClimateSetTemperature2(it) }
    }
}

private val hvacFanSpeedAdjustLock = Any()
private var hvacFanSpeedAdjustBlockedUntilMs = 0L

private val hvacFanDirectionCycleLock = Any()
private var hvacFanDirectionCycleBlockedUntilMs = 0L

private val trunkOpenLock = Any()
private var trunkOpenBlockedUntilMs = 0L

/** Cycle fan speed 1..7 dots. When speed is 7, wraps around to 1. If currently 0 (off), goes to 1. */
internal fun sendCycleHvacFanSpeed(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacFanSpeedAdjustLock) {
        if (now < hvacFanSpeedAdjustBlockedUntilMs) return
        hvacFanSpeedAdjustBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED
            val current = UniversalCanRepository.hvacFanSpeedRawState.value
            val got = MbCanEngineFacade.canGetVehicleParam(propertyId) ?: current
            val next = if (got >= 7) 1 else (got.coerceAtLeast(0) + 1)
            val written = MbCanEngineFacade.canSetVehicleParam(propertyId, next)
            if (written == null) {
                sendSetMbCanProperty(context, propertyId, next)
            }
            UniversalCanRepository.updateHvacFanSpeedRaw(next)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(propertyId, next)
            )
            Log.w("DashboardAppNav", "sendCycleHvacFanSpeed got=$got -> next=$next written=$written")
        } catch (e: Exception) {
            Log.e("DashboardAppNav", "sendCycleHvacFanSpeed failed", e)
        }
    }
}

/**
 * Cycle blow modes: 1) Feet (2) -> 2) Feet & Face (3) -> 3) Face (1) -> 4) Feet & Windscreen (5)
 */
internal fun sendCycleHvacFanDirection(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacFanDirectionCycleLock) {
        if (now < hvacFanDirectionCycleBlockedUntilMs) return
        hvacFanDirectionCycleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION
            val got = MbCanEngineFacade.canGetVehicleParam(propertyId)
            val next = when (got) {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT ->
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT ->
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE ->
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT ->
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
                else -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
            }
            val written = MbCanEngineFacade.canSetVehicleParam(propertyId, next)
            if (written == null) {
                sendSetMbCanProperty(context, propertyId, next)
            }
            UniversalCanRepository.updateHvacFanDirectionRaw(next)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(propertyId, next)
            )
            Log.w("DashboardAppNav", "sendCycleHvacFanDirection got=$got -> next=$next written=$written")
        } catch (e: Exception) {
            Log.e("DashboardAppNav", "sendCycleHvacFanDirection failed", e)
        }
    }
}

/**
 * Power liftgate open/close pulse — stock [MBVehicleManager.openClosePlgControl]:
 * write 1, then reset to 0 after ~100ms.
 */
internal fun sendOpenCloseTrunk(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(trunkOpenLock) {
        if (now < trunkOpenBlockedUntilMs) return
        trunkOpenBlockedUntilMs = now + 1500L
    }
    sendSetMbCanProperty(
        context,
        MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL,
        MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL_OPEN_CLOSE,
    )
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL,
            MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL_RESET,
        )
    }, 100L)
}

internal fun sendSetMbCanProperty(context: Context, propertyId: Int, value: Int) {
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_MBCAN_COMMAND
                putExtra(
                    BackgroundService.EXTRA_MBCAN_COMMAND_TYPE,
                    BackgroundService.MBCAN_COMMAND_SET_PROPERTY
                )
                putExtra(BackgroundService.EXTRA_MBCAN_PROPERTY_ID, propertyId)
                putExtra(BackgroundService.EXTRA_MBCAN_VALUE, value)
            }
        )
    } catch (_: Exception) {
    }
}

internal fun requestHeadUnitReboot(context: Context) {
    sendSetMbCanProperty(
        context,
        MbCanKnownVehiclePropertyId.SYSTEM_REBOOT,
        MbCanKnownVehiclePropertyId.SYSTEM_REBOOT_VALUE,
    )
}

internal fun sendToggleDoorAutoLock(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK)

internal fun sendToggleDoorIgnOffUnlock(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK)

internal fun sendToggleMirrorAutofold(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD)

internal fun sendAdjustAudioVolume(context: Context, delta: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    val current = UniversalCanRepository.audioVolumeState.value
        ?: audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        ?: 10
    val max = audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 30
    val target = (current + delta).coerceIn(0, max)

    audioManager?.adjustStreamVolume(
        android.media.AudioManager.STREAM_MUSIC,
        if (delta > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
        0,
    )

    CoroutineScope(Dispatchers.IO).launch {
        UniversalCanRepository.setAudioVolume(target)
    }
}

internal fun sendToggleMirrorReverseTurn(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.MIRROR_REVERSE_TURN)

internal fun sendToggleFcw(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.FCW_SWITCH)

internal fun sendToggleAccAutobrake(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SW)

internal fun sendToggleHdc(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.HDC_SWITCH)

internal fun sendToggleEscOff(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH)

internal fun sendToggleAvh(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.AVH_SWITCH)

/** Cycle front seat heat: off → 3 → 2 → 1 → off (raw 1/4/3/2). */
/**
 * OEM [MBSeatView] heat cycle (`sea_hot_value`): 1→4→3→2→1 (off / heat3 / heat2 / heat1).
 * Values 2–4 = heat; 5–7 = vent (switching to heat from vent starts at heat3).
 */
internal fun sendCycleFrontSeatHeat(context: Context, propertyId: Int, currentRawHint: Int?) {
    val next = when (currentRawHint) {
        4 -> 3
        3 -> 2
        2 -> 1
        else -> 4
    }
    sendSetMbCanProperty(context, propertyId, next)
}

/**
 * OEM [MBSeatView] ventilation cycle (`seat_ventilation_value`): 1→7→6→5→1.
 */
internal fun sendCycleFrontSeatVent(context: Context, propertyId: Int, currentRawHint: Int?) {
    val next = when (currentRawHint) {
        7 -> 6
        6 -> 5
        5 -> 1
        else -> 7
    }
    sendSetMbCanProperty(context, propertyId, next)
}

internal fun sendToggleRearFogLight(context: Context) =
    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)

/** Cycle LKA sensitivity 1→2→3→1. */
internal fun sendCycleLasSensitivity(context: Context, current: Int?) {
    val next = when (current) {
        1 -> 2
        2 -> 3
        else -> 1
    }
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, next)
}

/** OEM light-control cycle: Off → Position → Low beam → Auto → Off. */
internal fun sendCycleHeadlightsSwitch(context: Context, current: Int?) {
    val next = when (current) {
        LIGHT_CONTROL_OFF -> LIGHT_CONTROL_POSITION
        LIGHT_CONTROL_POSITION -> LIGHT_CONTROL_LOW_BEAM
        LIGHT_CONTROL_LOW_BEAM -> LIGHT_CONTROL_AUTO
        else -> LIGHT_CONTROL_OFF
    }
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LIGHT_CONTROL, next)
}

/**
 * Toggle low beam on/off via the physical light-control property.
 */
internal fun sendToggleLowBeam(context: Context, current: Int?) {
    val next = if (current == LIGHT_CONTROL_LOW_BEAM) LIGHT_CONTROL_OFF else LIGHT_CONTROL_LOW_BEAM
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LIGHT_CONTROL, next)
}

internal const val LIGHT_CONTROL_AUTO = 1
internal const val LIGHT_CONTROL_POSITION = 2
internal const val LIGHT_CONTROL_LOW_BEAM = 3
internal const val LIGHT_CONTROL_OFF = 4

/** Cycle homelight delay 0→1→2→3→0. */
internal fun sendCycleHomelightDelay(context: Context, current: Int?) {
    val next = ((current ?: -1) + 1).mod(4)
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY, next)
}

/** OEM ambient static effect: Off → Mono → Full-color → Off. */
internal fun sendCycleStaticEffect(context: Context, current: Int?) {
    val next = when (current) {
        0 -> 1
        1 -> 3
        else -> 0
    }
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.STATIC_EFFECT, next)
}

/** OEM driver unlock mode: driver door ↔ all doors. */
internal fun sendCycleDriverUnlockMode(context: Context, current: Int?) {
    val next = if (current == 2) 1 else 2
    sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE, next)
}
