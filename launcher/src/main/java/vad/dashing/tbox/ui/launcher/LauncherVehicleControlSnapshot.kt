package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/** Polled mbCAN get for settings that don't yet have dedicated StateFlows. */
data class LauncherVehicleControlSnapshot(
    val doorAutoLock: Boolean? = null,
    val doorIgnOffUnlock: Boolean? = null,
    val mirrorAutofold: Boolean? = null,
    val mirrorReverseTurn: Boolean? = null,
    val fcw: Boolean? = null,
    val accAutobrake: Boolean? = null,
    val hdc: Boolean? = null,
    val escOff: Boolean? = null,
    val avh: Boolean? = null,
    val headlightsSwitch: Int? = null,
    val lightControlRaw: Int? = null,
    val bcmLowBeam: Boolean? = null,
    val bcmHighBeam: Boolean? = null,
    val rearFogLight: Boolean? = null,
    val homelightDelay: Int? = null,
    val lasSensitivity: Int? = null,
    // Experimental — polled only when [includeExperimental] is true.
    val welcomeLamp: Boolean? = null,
    val musicalRhythm: Boolean? = null,
    val atmoLightAssociateDrivingMode: Boolean? = null,
    val breathingUnlock: Boolean? = null,
    val breathingLock: Boolean? = null,
    val unlockAnimation: Boolean? = null,
    val lockAnimation: Boolean? = null,
    val windowAutoclose: Boolean? = null,
    val domeDoorCtrl: Boolean? = null,
    val fragrance: Boolean? = null,
    val iss: Boolean? = null,
    val welcomeSeat: Boolean? = null,
    val doorknob: Boolean? = null,
    val wirelessCharging: Boolean? = null,
    val vehWashMode: Boolean? = null,
    val staticEffect: Int? = null,
    val driverUnlockMode: Int? = null,
)

@Composable
fun rememberLauncherVehicleControlSnapshot(
    enabled: Boolean,
    includeExperimental: Boolean = false,
): LauncherVehicleControlSnapshot {
    var snapshot by remember { mutableStateOf(LauncherVehicleControlSnapshot()) }
    LaunchedEffect(enabled, includeExperimental) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            snapshot = withContext(Dispatchers.IO) {
                readVehicleControlSnapshot(includeExperimental = includeExperimental)
            }
            delay(if (includeExperimental) 2_500L else 400L)
        }
    }
    return snapshot
}

private fun readVehicleControlSnapshot(includeExperimental: Boolean): LauncherVehicleControlSnapshot {
    if (!MbCanEngineFacade.isInitialized()) return LauncherVehicleControlSnapshot()
    fun get(id: Int): Int? = runCatching { MbCanEngineFacade.canGetVehicleParam(id) }.getOrNull()
    fun onOff12(raw: Int?): Boolean? = when (raw) {
        2 -> true
        1 -> false
        else -> null
    }
    // DOOR_IGNOFF_UNLOCK: 1 = on, 2 = off
    fun onOff21(raw: Int?): Boolean? = when (raw) {
        1 -> true
        2 -> false
        else -> null
    }
    val bcmLights = runCatching {
        val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
        val bcm = MbCanEngineFacade.getMbCanData(21, bcmCls) ?: return@runCatching null
        val lightSts = bcmCls.getMethod("getLightStatus").invoke(bcm) ?: return@runCatching null
        val lightCls = Class.forName("com.mengbo.mbCan.entity.MBCanLightStatus")
        val low = (lightCls.getMethod("getLowBeamSts").invoke(lightSts) as? Number)?.toInt() ?: 0
        val high = (lightCls.getMethod("getHighBeamSts").invoke(lightSts) as? Number)?.toInt() ?: 0
        (low != 0) to (high != 0)
    }.getOrNull()

    val core = LauncherVehicleControlSnapshot(
        doorAutoLock = onOff12(get(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK)),
        doorIgnOffUnlock = onOff21(get(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK)),
        mirrorAutofold = onOff12(get(MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD)),
        mirrorReverseTurn = onOff12(get(MbCanKnownVehiclePropertyId.MIRROR_REVERSE_TURN)),
        fcw = onOff12(get(MbCanKnownVehiclePropertyId.FCW_SWITCH)),
        accAutobrake = onOff12(get(MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SW)),
        hdc = onOff12(get(MbCanKnownVehiclePropertyId.HDC_SWITCH)),
        escOff = onOff12(get(MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH)),
        avh = onOff12(get(MbCanKnownVehiclePropertyId.AVH_SWITCH)),
        headlightsSwitch = get(MbCanKnownVehiclePropertyId.HEADLIGHTS_SWITCH)?.takeIf { it in 0..3 },
        lightControlRaw = get(MbCanKnownVehiclePropertyId.LIGHT_CONTROL)?.takeIf { it in 1..4 },
        bcmLowBeam = bcmLights?.first,
        bcmHighBeam = bcmLights?.second,
        rearFogLight = onOff12(get(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)),
        homelightDelay = get(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY)?.takeIf { it in 0..3 },
        lasSensitivity = get(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL)?.takeIf { it in 1..3 },
    )
    if (!includeExperimental) return core
    return core.copy(
        welcomeLamp = onOff12(get(MbCanKnownVehiclePropertyId.WELCOME_LAMP)),
        musicalRhythm = onOff12(get(MbCanKnownVehiclePropertyId.MUSICAL_RHYTHM)),
        atmoLightAssociateDrivingMode = onOff12(get(MbCanKnownVehiclePropertyId.ATMO_LIGHT_ASSOCIATE_DRIVING_MODE)),
        breathingUnlock = onOff12(get(MbCanKnownVehiclePropertyId.BREATHING_UNLOCK)),
        breathingLock = onOff12(get(MbCanKnownVehiclePropertyId.BREATHING_LOCK)),
        unlockAnimation = onOff12(get(MbCanKnownVehiclePropertyId.UNLOCK_ANIMATION)),
        lockAnimation = onOff12(get(MbCanKnownVehiclePropertyId.LOCK_ANIMATION)),
        windowAutoclose = onOff12(get(MbCanKnownVehiclePropertyId.WINDOW_AUTOCLOSE_SWITCH)),
        domeDoorCtrl = onOff12(get(MbCanKnownVehiclePropertyId.LIGHT_DOME_DOORCTRL_SWITCH)),
        fragrance = onOff12(get(MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH)),
        iss = onOff12(get(MbCanKnownVehiclePropertyId.ISS_SWITCH)),
        welcomeSeat = onOff12(get(MbCanKnownVehiclePropertyId.WELCOME_SEAT)),
        doorknob = onOff12(get(MbCanKnownVehiclePropertyId.DOORKNOB_SWITCH)),
        wirelessCharging = onOff12(get(MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH)),
        vehWashMode = onOff12(get(MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET)),
        staticEffect = get(MbCanKnownVehiclePropertyId.STATIC_EFFECT)?.takeIf { it in setOf(0, 1, 3) },
        driverUnlockMode = get(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE)?.takeIf { it in 1..2 },
    )
}

@Composable
fun rememberLauncherSystemSettings(context: android.content.Context): LauncherSystemSettingsController {
    val controller = remember(context) { LauncherSystemSettingsController(context.applicationContext) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    return controller
}
