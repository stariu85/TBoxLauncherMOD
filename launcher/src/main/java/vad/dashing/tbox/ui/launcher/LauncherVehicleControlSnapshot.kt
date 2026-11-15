package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import java.util.concurrent.atomic.AtomicInteger

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
    val bcmWiperStatus: Int? = null,
    val hmaOn: Boolean? = null,
    val rearFogLight: Boolean? = null,
    val parkingRadar: Boolean? = null,
    val homelightDelay: Int? = null,
    val lasSensitivity: Int? = null,
    val lasMode: Int? = null,
    val fcwOption: Int? = null,
    val wiperSensitivity: Int? = null,
    val bsd: Boolean? = null,
    val dow: Boolean? = null,
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

/**
 * Shared singleton repository for vehicle control properties to prevent thread pool exhaustion.
 * Polling is completely suspended until the first HOME frame has been drawn.
 */
object LauncherVehicleControlRepository {
    private const val TAG = "VehicleControlRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(LauncherVehicleControlSnapshot())
    val snapshot: StateFlow<LauncherVehicleControlSnapshot> = _snapshot.asStateFlow()

    private val _experimentalSnapshot = MutableStateFlow(LauncherVehicleControlSnapshot())
    val experimentalSnapshot: StateFlow<LauncherVehicleControlSnapshot> = _experimentalSnapshot.asStateFlow()

    private val activeCollectors = AtomicInteger(0)
    private val experimentalCollectors = AtomicInteger(0)
    private val refreshChannel = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var pollingJob: Job? = null

    fun registerConsumer(includeExperimental: Boolean) {
        activeCollectors.incrementAndGet()
        if (includeExperimental) {
            experimentalCollectors.incrementAndGet()
        }
        ensurePollingStarted()
    }

    fun unregisterConsumer(includeExperimental: Boolean) {
        activeCollectors.decrementAndGet()
        if (includeExperimental) {
            experimentalCollectors.decrementAndGet()
        }
    }

    fun refreshNow() {
        refreshChannel.trySend(Unit)
    }

    @Synchronized
    private fun ensurePollingStarted() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            var startupGraceApplied = false
            while (isActive) {
                if (!MbCanEngineFacade.isInitialized() ||
                    !LauncherHomeActivityHolder.isFirstDrawComplete ||
                    !LauncherHomeActivityHolder.isResumed ||
                    activeCollectors.get() <= 0
                ) {
                    delay(1_000L)
                    continue
                }

                if (!startupGraceApplied) {
                    delay(2_000L)
                    startupGraceApplied = true
                    if (!isActive) break
                }

                val hasExperimental = experimentalCollectors.get() > 0
                runCatching {
                    val s = readVehicleControlSnapshot(includeExperimental = hasExperimental)
                    _snapshot.value = s
                    if (hasExperimental) {
                        _experimentalSnapshot.value = s
                    }
                }

                val intervalMs = if (hasExperimental) 2_500L else 1_000L
                withTimeoutOrNull(intervalMs) {
                    refreshChannel.receive()
                }
            }
        }
    }
}

@Composable
fun rememberLauncherVehicleControlSnapshot(
    enabled: Boolean,
    includeExperimental: Boolean = false,
): LauncherVehicleControlSnapshot {
    if (!enabled) return remember { LauncherVehicleControlSnapshot() }

    DisposableEffect(includeExperimental) {
        LauncherVehicleControlRepository.registerConsumer(includeExperimental)
        onDispose {
            LauncherVehicleControlRepository.unregisterConsumer(includeExperimental)
        }
    }

    val stateFlow = if (includeExperimental) {
        LauncherVehicleControlRepository.experimentalSnapshot
    } else {
        LauncherVehicleControlRepository.snapshot
    }

    val snapshot by stateFlow.collectAsStateWithLifecycle()
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
    // Android 9 mbCAN: MBCanVehicleBcmStatus -> getLightStatus() -> getLowBeamSts() / getHighBeamSts()
    // 2 (or 1) = ON
    val bcmLights = runCatching {
        val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
        val bcm = MbCanEngineFacade.getMbCanData(21, bcmCls) ?: return@runCatching null
        val lightSts = bcmCls.getMethod("getLightStatus").invoke(bcm) ?: return@runCatching null

        fun readProp(obj: Any, name: String): Int? {
            val getterName = "get" + name.replaceFirstChar { it.uppercase() }
            runCatching {
                val method = obj.javaClass.getMethod(getterName)
                return (method.invoke(obj) as? Number)?.toInt()
            }
            runCatching {
                val field = obj.javaClass.getDeclaredField(name)
                field.isAccessible = true
                return (field.get(obj) as? Number)?.toInt()
            }
            return null
        }

        val low = readProp(lightSts, "lowBeamSts") ?: readProp(lightSts, "nLowBeamSts") ?: 0
        val high = readProp(lightSts, "highBeamSts") ?: readProp(lightSts, "nHighBeamSts") ?: 0
        // OEM CEM mbCAN: 2 = ON, 1 / 0 = OFF (High beam flash pulses 1 -> 2 -> 1)
        (low == 2) to (high == 2)
    }.getOrNull()

    val pushLowBeam = vad.dashing.tbox.mbcan.UniversalCanRepository.bcmLowBeamState.value
    val pushHighBeam = vad.dashing.tbox.mbcan.UniversalCanRepository.bcmHighBeamState.value

    val actualLowBeam = pushLowBeam ?: bcmLights?.first
    val actualHighBeam = pushHighBeam ?: bcmLights?.second

    val bcmWiper = runCatching {
        val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
        val bcm = MbCanEngineFacade.getMbCanData(21, bcmCls) ?: return@runCatching null
        (bcmCls.getMethod("getWiperSts").invoke(bcm) as? Number)?.toInt()
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
        bcmLowBeam = actualLowBeam,
        bcmHighBeam = actualHighBeam,
        bcmWiperStatus = bcmWiper,
        hmaOn = onOff12(get(MbCanKnownVehiclePropertyId.HMA_SWITCH)),
        rearFogLight = onOff12(get(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)),
        parkingRadar = onOff12(get(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH)),
        homelightDelay = get(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY)?.takeIf { it in 0..3 },
        lasSensitivity = get(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL)?.takeIf { it in 0..1 },
        lasMode = get(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION)?.takeIf { it in 1..3 },
        fcwOption = get(MbCanKnownVehiclePropertyId.FCW_OPTION)?.takeIf { it in 1..3 },
        wiperSensitivity = get(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY)?.takeIf { it in 1..4 },
        bsd = onOff12(get(MbCanKnownVehiclePropertyId.BSD_SWITCH)),
        dow = onOff12(get(MbCanKnownVehiclePropertyId.DOW_SWITCH)),
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
