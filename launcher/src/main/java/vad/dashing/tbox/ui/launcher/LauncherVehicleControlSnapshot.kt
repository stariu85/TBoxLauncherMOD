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
import kotlinx.coroutines.flow.update
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
        val wasActive = activeCollectors.getAndIncrement() > 0
        if (includeExperimental) {
            experimentalCollectors.incrementAndGet()
        }
        ensurePollingStarted()
        if (!wasActive || vad.dashing.tbox.ui.launcher.LauncherAdasQuickSettingsWindow.isShowing()) {
            refreshNow()
        }
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

    fun notifyVehicleParamChanged(propertyId: Int, value: Int) {
        fun onOff12(raw: Int): Boolean? = when (raw) {
            2 -> true
            1 -> false
            else -> null
        }
        fun onOff21(raw: Int): Boolean? = when (raw) {
            1 -> true
            2 -> false
            else -> null
        }
        _snapshot.update { cur ->
            when (propertyId) {
                MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK -> cur.copy(doorAutoLock = onOff12(value))
                MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK -> cur.copy(doorIgnOffUnlock = onOff21(value))
                MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD -> cur.copy(mirrorAutofold = onOff12(value))
                MbCanKnownVehiclePropertyId.MIRROR_REVERSE_TURN -> cur.copy(mirrorReverseTurn = onOff12(value))
                MbCanKnownVehiclePropertyId.FCW_SWITCH -> cur.copy(fcw = onOff12(value))
                MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SW -> cur.copy(accAutobrake = onOff12(value))
                MbCanKnownVehiclePropertyId.HDC_SWITCH -> cur.copy(hdc = onOff12(value))
                MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH -> cur.copy(escOff = onOff12(value))
                MbCanKnownVehiclePropertyId.AVH_SWITCH -> cur.copy(avh = onOff12(value))
                MbCanKnownVehiclePropertyId.HEADLIGHTS_SWITCH -> cur.copy(
                    headlightsSwitch = value.takeIf { it in 0..3 },
                    hmaOn = onOff12(value),
                )
                MbCanKnownVehiclePropertyId.LIGHT_CONTROL -> cur.copy(lightControlRaw = value.takeIf { it in 1..4 })
                MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT -> cur.copy(rearFogLight = onOff12(value))
                MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH -> cur.copy(parkingRadar = onOff12(value))
                MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY -> cur.copy(homelightDelay = value.takeIf { it in 0..3 })
                MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL -> cur.copy(lasSensitivity = value.takeIf { it in 0..1 })
                MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION -> cur.copy(lasMode = value.takeIf { it in 1..3 })
                MbCanKnownVehiclePropertyId.FCW_OPTION -> cur.copy(fcwOption = value.takeIf { it in 1..3 })
                MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY -> cur.copy(wiperSensitivity = value.takeIf { it in 1..4 })
                MbCanKnownVehiclePropertyId.BSD_SWITCH -> cur.copy(bsd = onOff12(value))
                MbCanKnownVehiclePropertyId.DOW_SWITCH -> cur.copy(dow = onOff12(value))
                MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH -> cur.copy(wirelessCharging = onOff12(value))
                else -> cur
            }
        }
        if (experimentalCollectors.get() > 0) {
            _experimentalSnapshot.value = _snapshot.value
        }
    }

    @Synchronized
    private fun ensurePollingStarted() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            var startupGraceApplied = false
            var initialQuickPollDone = false
            while (isActive) {
                val hasCollectors = activeCollectors.get() > 0
                val isOverlayOpen = vad.dashing.tbox.ui.launcher.LauncherAdasQuickSettingsWindow.isShowing()
                val isUiActive = LauncherHomeActivityHolder.isResumed || isOverlayOpen

                if (!MbCanEngineFacade.isInitialized() ||
                    !LauncherHomeActivityHolder.isFirstDrawComplete ||
                    !isUiActive ||
                    !hasCollectors
                ) {
                    delay(1_000L)
                    continue
                }

                // If user opened quick settings overlay, skip startup grace period completely
                if (!startupGraceApplied && !isOverlayOpen) {
                    delay(2_000L)
                    startupGraceApplied = true
                    if (!isActive) break
                } else {
                    startupGraceApplied = true
                }

                val hasExperimental = experimentalCollectors.get() > 0

                // Option 4: On first iteration right after first frame, do a fast quick-controls read first
                val quickOnly = !initialQuickPollDone && !hasExperimental
                initialQuickPollDone = true

                runCatching {
                    val s = readVehicleControlSnapshot(
                        current = _snapshot.value,
                        includeExperimental = hasExperimental,
                        quickOnly = quickOnly,
                    )
                    _snapshot.value = s
                    if (hasExperimental) {
                        _experimentalSnapshot.value = s
                    }
                }

                val intervalMs = if (quickOnly) 1_000L else if (hasExperimental) 2_500L else 1_000L
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

private fun readVehicleControlSnapshot(
    current: LauncherVehicleControlSnapshot = LauncherVehicleControlSnapshot(),
    includeExperimental: Boolean = false,
    quickOnly: Boolean = false,
): LauncherVehicleControlSnapshot {
    if (!MbCanEngineFacade.isInitialized()) return current
    fun get(id: Int, fallback: Int? = null): Int? {
        val cached = MbCanEngineFacade.getCachedVehicleParam(id)
        if (cached != null) return cached
        return runCatching { MbCanEngineFacade.canGetVehicleParam(id) }.getOrNull() ?: fallback
    }
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

    val pushLowBeam = vad.dashing.tbox.mbcan.UniversalCanRepository.bcmLowBeamState.value
    val pushHighBeam = vad.dashing.tbox.mbcan.UniversalCanRepository.bcmHighBeamState.value
    val actualLowBeam = pushLowBeam ?: current.bcmLowBeam
    val actualHighBeam = pushHighBeam ?: current.bcmHighBeam

    val pushRadar = vad.dashing.tbox.mbcan.UniversalCanRepository.parkingRadarState.value
    val radarFromPush = when (pushRadar) {
        is vad.dashing.tbox.mbcan.MbCanBinaryState.On -> true
        is vad.dashing.tbox.mbcan.MbCanBinaryState.Off -> false
        else -> null
    }

    val pushLasMode = vad.dashing.tbox.mbcan.UniversalCanRepository.lasModeRaw.value

    val quickLasMode = pushLasMode ?: get(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, current.lasMode)?.takeIf { it in 1..3 }
    val quickLasSensitivity = get(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, current.lasSensitivity)?.takeIf { it in 0..1 }
    val quickFcw = onOff12(get(MbCanKnownVehiclePropertyId.FCW_SWITCH, current.fcw?.let { if (it) 2 else 1 }))
    val quickFcwOption = get(MbCanKnownVehiclePropertyId.FCW_OPTION, current.fcwOption)?.takeIf { it in 1..3 }
    val quickParkingRadar = radarFromPush ?: onOff12(get(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH, current.parkingRadar?.let { if (it) 2 else 1 }))
    val quickHdc = onOff12(get(MbCanKnownVehiclePropertyId.HDC_SWITCH, current.hdc?.let { if (it) 2 else 1 }))
    val quickEscOff = onOff12(get(MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH, current.escOff?.let { if (it) 2 else 1 }))
    val quickAvh = onOff12(get(MbCanKnownVehiclePropertyId.AVH_SWITCH, current.avh?.let { if (it) 2 else 1 }))
    val quickWiperSensitivity = get(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY, current.wiperSensitivity)?.takeIf { it in 1..4 }
    val quickBsd = onOff12(get(MbCanKnownVehiclePropertyId.BSD_SWITCH, current.bsd?.let { if (it) 2 else 1 }))
    val quickDow = onOff12(get(MbCanKnownVehiclePropertyId.DOW_SWITCH, current.dow?.let { if (it) 2 else 1 }))
    val quickLightControl = get(MbCanKnownVehiclePropertyId.LIGHT_CONTROL, current.lightControlRaw)?.takeIf { it in 1..4 }
    val quickHeadlights = get(MbCanKnownVehiclePropertyId.HEADLIGHTS_SWITCH, current.headlightsSwitch)?.takeIf { it in 0..3 }
    val quickHma = onOff12(get(MbCanKnownVehiclePropertyId.HMA_SWITCH, current.hmaOn?.let { if (it) 2 else 1 }))
    val quickRearFog = onOff12(get(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT, current.rearFogLight?.let { if (it) 2 else 1 }))

    if (quickOnly) {
        return current.copy(
            lasMode = quickLasMode,
            lasSensitivity = quickLasSensitivity,
            fcw = quickFcw,
            fcwOption = quickFcwOption,
            parkingRadar = quickParkingRadar,
            hdc = quickHdc,
            escOff = quickEscOff,
            avh = quickAvh,
            wiperSensitivity = quickWiperSensitivity,
            bsd = quickBsd,
            dow = quickDow,
            lightControlRaw = quickLightControl,
            headlightsSwitch = quickHeadlights,
            bcmLowBeam = actualLowBeam,
            bcmHighBeam = actualHighBeam,
            hmaOn = quickHma,
            rearFogLight = quickRearFog,
        )
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

    val resolvedLowBeam = pushLowBeam ?: bcmLights?.first ?: current.bcmLowBeam
    val resolvedHighBeam = pushHighBeam ?: bcmLights?.second ?: current.bcmHighBeam

    val bcmWiper = runCatching {
        val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
        val bcm = MbCanEngineFacade.getMbCanData(21, bcmCls) ?: return@runCatching null
        val rain = (bcmCls.getMethod("getRainDetectedSts").invoke(bcm) as? Number)?.toInt()
        if (rain != null) {
            vad.dashing.tbox.CanDataRepository.updateRainSensorDetected(rain >= 2)
        }
        (bcmCls.getMethod("getWiperSts").invoke(bcm) as? Number)?.toInt()
    }.getOrNull() ?: current.bcmWiperStatus

    val core = current.copy(
        doorAutoLock = onOff12(get(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK, current.doorAutoLock?.let { if (it) 2 else 1 })),
        doorIgnOffUnlock = onOff21(get(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK, current.doorIgnOffUnlock?.let { if (it) 1 else 2 })),
        mirrorAutofold = onOff12(get(MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD, current.mirrorAutofold?.let { if (it) 2 else 1 })),
        mirrorReverseTurn = onOff12(get(MbCanKnownVehiclePropertyId.MIRROR_REVERSE_TURN, current.mirrorReverseTurn?.let { if (it) 2 else 1 })),
        fcw = quickFcw,
        accAutobrake = onOff12(get(MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SW, current.accAutobrake?.let { if (it) 2 else 1 })),
        hdc = quickHdc,
        escOff = quickEscOff,
        avh = quickAvh,
        headlightsSwitch = quickHeadlights,
        lightControlRaw = quickLightControl,
        bcmLowBeam = resolvedLowBeam,
        bcmHighBeam = resolvedHighBeam,
        bcmWiperStatus = bcmWiper,
        hmaOn = quickHma,
        rearFogLight = quickRearFog,
        parkingRadar = quickParkingRadar,
        homelightDelay = get(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY, current.homelightDelay)?.takeIf { it in 0..3 },
        lasSensitivity = quickLasSensitivity,
        lasMode = quickLasMode,
        fcwOption = quickFcwOption,
        wiperSensitivity = quickWiperSensitivity,
        bsd = quickBsd,
        dow = quickDow,
    )

    if (!includeExperimental) return core
    return core.copy(
        welcomeLamp = onOff12(get(MbCanKnownVehiclePropertyId.WELCOME_LAMP, current.welcomeLamp?.let { if (it) 2 else 1 })),
        musicalRhythm = onOff12(get(MbCanKnownVehiclePropertyId.MUSICAL_RHYTHM, current.musicalRhythm?.let { if (it) 2 else 1 })),
        atmoLightAssociateDrivingMode = onOff12(get(MbCanKnownVehiclePropertyId.ATMO_LIGHT_ASSOCIATE_DRIVING_MODE, current.atmoLightAssociateDrivingMode?.let { if (it) 2 else 1 })),
        breathingUnlock = onOff12(get(MbCanKnownVehiclePropertyId.BREATHING_UNLOCK, current.breathingUnlock?.let { if (it) 2 else 1 })),
        breathingLock = onOff12(get(MbCanKnownVehiclePropertyId.BREATHING_LOCK, current.breathingLock?.let { if (it) 2 else 1 })),
        unlockAnimation = onOff12(get(MbCanKnownVehiclePropertyId.UNLOCK_ANIMATION, current.unlockAnimation?.let { if (it) 2 else 1 })),
        lockAnimation = onOff12(get(MbCanKnownVehiclePropertyId.LOCK_ANIMATION, current.lockAnimation?.let { if (it) 2 else 1 })),
        windowAutoclose = onOff12(get(MbCanKnownVehiclePropertyId.WINDOW_AUTOCLOSE_SWITCH, current.windowAutoclose?.let { if (it) 2 else 1 })),
        domeDoorCtrl = onOff12(get(MbCanKnownVehiclePropertyId.LIGHT_DOME_DOORCTRL_SWITCH, current.domeDoorCtrl?.let { if (it) 2 else 1 })),
        fragrance = onOff12(get(MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH, current.fragrance?.let { if (it) 2 else 1 })),
        iss = onOff12(get(MbCanKnownVehiclePropertyId.ISS_SWITCH, current.iss?.let { if (it) 2 else 1 })),
        welcomeSeat = onOff12(get(MbCanKnownVehiclePropertyId.WELCOME_SEAT, current.welcomeSeat?.let { if (it) 2 else 1 })),
        doorknob = onOff12(get(MbCanKnownVehiclePropertyId.DOORKNOB_SWITCH, current.doorknob?.let { if (it) 2 else 1 })),
        wirelessCharging = onOff12(get(MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH, current.wirelessCharging?.let { if (it) 2 else 1 })),
        vehWashMode = onOff12(get(MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET, current.vehWashMode?.let { if (it) 2 else 1 })),
        staticEffect = get(MbCanKnownVehiclePropertyId.STATIC_EFFECT, current.staticEffect)?.takeIf { it in setOf(0, 1, 3) },
        driverUnlockMode = get(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE, current.driverUnlockMode)?.takeIf { it in 1..2 },
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
