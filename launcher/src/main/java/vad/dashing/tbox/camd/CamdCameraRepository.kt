package vad.dashing.tbox.camd

import android.content.Context
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsLatch
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.CAMD_LOW_SPEED_TRIGGER_BOTH
import vad.dashing.tbox.ui.launcher.CAMD_LOW_SPEED_TRIGGER_STEERING
import vad.dashing.tbox.ui.launcher.CAMD_LOW_SPEED_TRIGGER_TURN_SIGNALS
import vad.dashing.tbox.ui.launcher.LauncherActiveAppTracker
import vad.dashing.tbox.ui.launcher.LauncherAdasRepository
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import vad.dashing.tbox.ui.launcher.LauncherAppLaunchMode
import vad.dashing.tbox.ui.launcher.LauncherAvmRepository
import vad.dashing.tbox.ui.launcher.LauncherCar3DModelTrigger
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import vad.dashing.tbox.ui.launcher.LauncherPdcZones
import vad.dashing.tbox.ui.launcher.hasPhysicalPdcObstacle
import java.util.Locale

/**
 * Normalized window coordinates (0.0..1.0 fraction of display).
 * Per spec §3 & §9:
 * - Minimum side 0.05 (5%)
 * - x + w <= 1.02
 * - y + h <= 1.02
 */
data class CamdWindowBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    fun normalized(): CamdWindowBounds {
        val clampedW = w.coerceIn(0.05f, 1.0f)
        val clampedH = h.coerceIn(0.05f, 1.0f)
        val clampedX = x.coerceIn(0f, (1.02f - clampedW).coerceAtLeast(0f))
        val clampedY = y.coerceIn(0f, (1.02f - clampedH).coerceAtLeast(0f))
        return CamdWindowBounds(clampedX, clampedY, clampedW, clampedH)
    }
}

/**
 * Crop rectangle inside sensor frame (0.0..1.0 fraction).
 * Per spec §3: min side 0.05, cx + cw <= 1.02, cy + ch <= 1.02.
 * Requires Camd >= 0.4.0.
 */
data class CamdCrop(
    val cx: Float,
    val cy: Float,
    val cw: Float,
    val ch: Float,
) {
    fun normalized(): CamdCrop {
        val clampedW = cw.coerceIn(0.05f, 1.0f)
        val clampedH = ch.coerceIn(0.05f, 1.0f)
        val clampedX = cx.coerceIn(0f, (1.02f - clampedW).coerceAtLeast(0f))
        val clampedY = cy.coerceIn(0f, (1.02f - clampedH).coerceAtLeast(0f))
        return CamdCrop(clampedX, clampedY, clampedW, clampedH)
    }
}

/**
 * Bounds of circular or rectangular side camera windows in corner desktop coordinates.
 */
data class SideCameraWindowBounds(
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val isCircle: Boolean,
)

/**
 * Operating mode for the floating camera widget.
 */
enum class CamdWidgetCameraMode {
    NONE,
    LEFT_RIGHT,
    UP_DOWN,
    ALL_360,
}

/**
 * Manages high-level camera operations via Camd:
 * - 4-camera desktop view (Left, Right, Front, Rear)
 * - Turn-signal ADAS side camera display (Left #4, Right #5)
 * - Preset and circular shape support (Camd >= 0.5.0)
 * - Status monitoring and toggles
 */
object CamdCameraRepository {
    private const val TAG = "CamdCameraRepository"

    const val CAMERA_ID_CABIN = 0
    const val CAMERA_ID_OMS = 1
    const val CAMERA_ID_DMS = 3
    const val CAMERA_ID_LEFT = 4
    const val CAMERA_ID_RIGHT = 5
    const val CAMERA_ID_FRONT = 8
    const val CAMERA_ID_REAR = 9

    // Stock AVM views (camd >= 0.8.0, vendor mesh calibration /vendor/etc/mbhal/avm/...)
    const val CAMERA_ID_STOCK_FRONT = 40
    const val CAMERA_ID_STOCK_RIGHT = 41
    const val CAMERA_ID_STOCK_REAR = 42
    const val CAMERA_ID_STOCK_LEFT = 43
    const val CAMERA_ID_STOCK_WIDE_FRONT = 44
    const val CAMERA_ID_STOCK_WIDE_REAR = 45

    val RAW_CAMERA_IDS = listOf(
        CAMERA_ID_CABIN,
        CAMERA_ID_OMS,
        CAMERA_ID_DMS,
        CAMERA_ID_LEFT,
        CAMERA_ID_RIGHT,
        CAMERA_ID_FRONT,
        CAMERA_ID_REAR,
    )

    val STOCK_AVM_CAMERA_IDS = listOf(
        CAMERA_ID_STOCK_FRONT,
        CAMERA_ID_STOCK_RIGHT,
        CAMERA_ID_STOCK_REAR,
        CAMERA_ID_STOCK_LEFT,
        CAMERA_ID_STOCK_WIDE_FRONT,
        CAMERA_ID_STOCK_WIDE_REAR,
    )

    val ALL_KNOWN_CAMERA_IDS = RAW_CAMERA_IDS + STOCK_AVM_CAMERA_IDS

    fun getPhysicalCameraId(id: Int): Int = when (id) {
        CAMERA_ID_STOCK_FRONT, CAMERA_ID_STOCK_WIDE_FRONT -> CAMERA_ID_FRONT
        CAMERA_ID_STOCK_RIGHT -> CAMERA_ID_RIGHT
        CAMERA_ID_STOCK_REAR, CAMERA_ID_STOCK_WIDE_REAR -> CAMERA_ID_REAR
        CAMERA_ID_STOCK_LEFT -> CAMERA_ID_LEFT
        else -> id
    }

    fun isStockAvmCameraId(id: Int): Boolean = id in 40..45

    fun isSurroundCamera(id: Int): Boolean = when (id) {
        CAMERA_ID_LEFT, CAMERA_ID_RIGHT, CAMERA_ID_FRONT, CAMERA_ID_REAR,
        CAMERA_ID_STOCK_FRONT, CAMERA_ID_STOCK_RIGHT, CAMERA_ID_STOCK_REAR,
        CAMERA_ID_STOCK_LEFT, CAMERA_ID_STOCK_WIDE_FRONT, CAMERA_ID_STOCK_WIDE_REAR -> true
        else -> false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<CamdConnectionStatus>(CamdConnectionStatus.NotInstalled)
    val status: StateFlow<CamdConnectionStatus> = _status.asStateFlow()

    private val _isDesktopCamerasOpen = MutableStateFlow(false)
    val isDesktopCamerasOpen: StateFlow<Boolean> = _isDesktopCamerasOpen.asStateFlow()

    private val _isReverseCamerasOpen = MutableStateFlow(false)
    val isReverseCamerasOpen: StateFlow<Boolean> = _isReverseCamerasOpen.asStateFlow()

    private val _activeWidgetCameraMode = MutableStateFlow(CamdWidgetCameraMode.NONE)
    val activeWidgetCameraMode: StateFlow<CamdWidgetCameraMode> = _activeWidgetCameraMode.asStateFlow()

    private val _activeTurnSignalCamera = MutableStateFlow<Int?>(null)
    val activeTurnSignalCamera: StateFlow<Int?> = _activeTurnSignalCamera.asStateFlow()

    private val _activeTurnSignalSide = MutableStateFlow<TurnSignalSide?>(null)
    val activeTurnSignalSideFlow: StateFlow<TurnSignalSide?> = _activeTurnSignalSide.asStateFlow()
    val activeTurnSignalSide: TurnSignalSide?
        get() = _activeTurnSignalSide.value

    @Volatile
    private var appContext: Context? = null

    private val turnSignalsLatch = TurnSignalsLatch(
        scope = scope,
        holdMs = TurnSignalsLatch.HOLD_MS,
        onSideChanged = {
            val ctx = appContext
            if (ctx != null) {
                evaluateSideCameraState(ctx)
            }
        },
    )

    private var desktopOperationJob: Job? = null
    private var turnSignalJob: Job? = null
    private var reverseCamerasJob: Job? = null
    const val STEER_OFF_HOLD_MS = 3000L
    const val PDC_OFF_DELAY_MS = 3000L

    private var autoAvmJob: Job? = null
    private var sideCamerasMonitorJob: Job? = null
    private var steerOffJob: Job? = null
    private var pdcOffJob: Job? = null
    @Volatile
    private var _activeIsLowSpeed: Boolean = false
    @Volatile
    private var reverseCamerasTriggered: Boolean = false
    @Volatile
    private var pdcCamerasTriggered: Boolean = false
    @Volatile
    private var currentSteerSide: TurnSignalSide? = null
    @Volatile
    private var lastAutoAvmHoldOff: Boolean? = null
    private var avmVerificationJob: Job? = null
    private val cameraOperationMutex = Mutex()

    fun startSideCamerasMonitor(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        LauncherAvmRepository.startObserving(appCtx)
        startAutoAvmMonitor(appCtx)
        if (sideCamerasMonitorJob?.isActive == true) return

        sideCamerasMonitorJob = scope.launch {
            val combinedSignals = combine(
                UniversalCanRepository.carSpeedState,
                CanDataRepository.steerAngle,
                turnSignalsLatch.latchedSide,
            ) { speed, steerAngle, latchedTurn -> Triple(speed, steerAngle, latchedTurn) }

            combine(
                LauncherAppConfigStore.camdConfigRevisionFlow,
                combinedSignals,
                LauncherAvmRepository.isAvmActive,
                _activeWidgetCameraMode,
                _isDesktopCamerasOpen,
            ) { _, (speed, steerAngle, latchedTurn), _, _, _ ->
                Triple(speed, steerAngle, latchedTurn)
            }.collect { (_, steerAngle, _) ->
                updateSteerSideAndEvaluate(appCtx, steerAngle)
            }
        }
    }

    private suspend fun updateSteerSideAndEvaluate(context: Context, steerAngle: Float?) {
        val angle = steerAngle
        if (angle != null && angle.isFinite()) {
            val onAngle = LauncherAppConfigStore.camdLowSpeedSteerOnAngle(context).toFloat()
            val offAngle = LauncherAppConfigStore.camdLowSpeedSteerOffAngle(context).toFloat()
            when {
                angle >= onAngle -> {
                    steerOffJob?.cancel()
                    steerOffJob = null
                    currentSteerSide = TurnSignalSide.Left
                }
                angle <= -onAngle -> {
                    steerOffJob?.cancel()
                    steerOffJob = null
                    currentSteerSide = TurnSignalSide.Right
                }
                currentSteerSide == TurnSignalSide.Left -> {
                    if (angle < offAngle) {
                        if (steerOffJob == null) {
                            steerOffJob = scope.launch {
                                kotlinx.coroutines.delay(STEER_OFF_HOLD_MS)
                                currentSteerSide = null
                                steerOffJob = null
                                evaluateSideCameraState(context)
                            }
                        }
                    } else {
                        steerOffJob?.cancel()
                        steerOffJob = null
                    }
                }
                currentSteerSide == TurnSignalSide.Right -> {
                    if (angle > -offAngle) {
                        if (steerOffJob == null) {
                            steerOffJob = scope.launch {
                                kotlinx.coroutines.delay(STEER_OFF_HOLD_MS)
                                currentSteerSide = null
                                steerOffJob = null
                                evaluateSideCameraState(context)
                            }
                        }
                    } else {
                        steerOffJob?.cancel()
                        steerOffJob = null
                    }
                }
            }
        } else {
            steerOffJob?.cancel()
            steerOffJob = null
            currentSteerSide = null
        }

        updateSideCameraCanSignals(context)
        evaluateSideCameraState(context)
    }

    private suspend fun updateSideCameraCanSignals(context: Context) {
        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(context)
        val lowSpeedEnabled = LauncherAppConfigStore.camdLowSpeedSideCameras(context)
        val reverseCameras = LauncherAppConfigStore.camdDisableStockAvm(context) &&
            LauncherAppConfigStore.camdReverseCameras(context)
        val pdcCameras = LauncherAppConfigStore.camdDisableStockAvm(context) &&
            LauncherAppConfigStore.camdPdcCameras(context)
        val trigger = LauncherAppConfigStore.camdLowSpeedSideTrigger(context)
        val recorderAutoOff = LauncherAppConfigStore.camdRecorderEnabled(context) &&
            LauncherAppConfigStore.camdRecorderAutoOffBySpeed(context)

        val signals = mutableSetOf<MbCanSignal>()
        if (lowSpeedEnabled || turnSignalCameras || reverseCameras || pdcCameras || recorderAutoOff) {
            signals.add(MbCanSignal.CarSpeed)
        }
        if (lowSpeedEnabled && (trigger == CAMD_LOW_SPEED_TRIGGER_STEERING || trigger == CAMD_LOW_SPEED_TRIGGER_BOTH)) {
            signals.add(MbCanSignal.SteeringAngle)
        }
        UniversalCanRepository.setSourceSignals("camd_side_cameras", signals)
    }

    private data class AutoAvmInputs(
        val speed: Float?,
        val activeGear: Char?,
        val pdc: LauncherPdcZones,
        val isAvmActive: Boolean,
    )

    fun startAutoAvmMonitor(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        LauncherAvmRepository.startObserving(appCtx)
        if (autoAvmJob?.isActive == true) return

        val activeGearFlow = combine(
            CanDataRepository.gearBoxMode,
            CanDataRepository.gearBoxCurrentGear,
        ) { gearMode, currentGear ->
            vad.dashing.tbox.ui.launcher.LauncherDevVehicleState.gearSlotOverrideFlow.value
                ?: vad.dashing.tbox.ui.launcher.resolveActiveGearSlot(gearMode, currentGear)
        }

        autoAvmJob = scope.launch {
            var lastAutoAvmGear: Char? = null
            combine(
                LauncherAppConfigStore.camdConfigRevisionFlow,
                UniversalCanRepository.carSpeedState,
                activeGearFlow,
                LauncherAdasRepository.state,
                LauncherAvmRepository.isAvmActive,
            ) { _, speed, activeGear, adasLive, isAvmActive ->
                val pdc = vad.dashing.tbox.ui.launcher.LauncherDevVehicleState.adasStateOrNull()?.pdc ?: adasLive.pdc
                AutoAvmInputs(speed, activeGear, pdc, isAvmActive)
            }.collect { inputs ->
                if (inputs.activeGear == 'P' && lastAutoAvmGear != null && lastAutoAvmGear != 'P') {
                    LauncherCar3DModelTrigger.notifyParkEngaged()
                }
                lastAutoAvmGear = inputs.activeGear
                checkAutoAvmSpeed(appCtx, inputs.speed, inputs.activeGear)
                check360Cameras(appCtx, inputs.speed, inputs.activeGear, inputs.pdc, inputs.isAvmActive)
                evaluateSideCameraState(appCtx)
                checkReverseAvmRetry(appCtx, inputs.activeGear, inputs.isAvmActive)
                checkDvrAndAvmCoordination(appCtx, inputs.speed, inputs.isAvmActive)
            }
        }
    }

    internal fun resolveDvrTargetState(
        isDvrEnabled: Boolean,
        isAutoDvr: Boolean,
        isAvmActive: Boolean,
        speed: Float?,
        activeGear: Char?,
        autoAvmEnabled: Boolean,
        onSpeed: Int,
        offSpeed: Int,
        disableStockAvm: Boolean,
        isCurrentlyRecording: Boolean,
    ): DvrTargetState {
        if (!isDvrEnabled) {
            return DvrTargetState(
                targetRecording = false,
                shouldDisableStockAvm = false,
                scheduleAvmVerification = false,
            )
        }

        if (!isAutoDvr) {
            // Manual DVR mode: DVR is on, auto-off is disabled.
            // Stock AVM must be automatically disabled so DVR can work.
            return DvrTargetState(
                targetRecording = true,
                shouldDisableStockAvm = !disableStockAvm,
                scheduleAvmVerification = false,
            )
        }

        // Auto DVR mode:
        // If AVM is active on screen, DVR must stop recording.
        if (isAvmActive) {
            return DvrTargetState(
                targetRecording = false,
                shouldDisableStockAvm = false,
                scheduleAvmVerification = false,
            )
        }

        // AVM is not active. Check speed / gear requirement.
        val shouldHoldOff = if (autoAvmEnabled) {
            resolveAutoAvmHoldOff(speed, activeGear, onSpeed, offSpeed)
        } else {
            disableStockAvm
        }

        return when (shouldHoldOff) {
            false -> {
                // Stock AVM should be on (AVM active). Stop DVR so AVM can work.
                DvrTargetState(
                    targetRecording = false,
                    shouldDisableStockAvm = false,
                    scheduleAvmVerification = isCurrentlyRecording,
                )
            }
            true -> {
                // Stock AVM is held off. DVR should record.
                DvrTargetState(
                    targetRecording = true,
                    shouldDisableStockAvm = false,
                    scheduleAvmVerification = false,
                )
            }
            null -> {
                // In hysteresis gap. Retain recording if AVM was already disabled.
                val keepRecording = isCurrentlyRecording || disableStockAvm
                DvrTargetState(
                    targetRecording = keepRecording,
                    shouldDisableStockAvm = false,
                    scheduleAvmVerification = false,
                )
            }
        }
    }

    data class DvrTargetState(
        val targetRecording: Boolean,
        val shouldDisableStockAvm: Boolean,
        val scheduleAvmVerification: Boolean,
    )

    private suspend fun checkDvrAndAvmCoordination(context: Context, speed: Float?, isAvmActive: Boolean) {
        val recorderEnabled = LauncherAppConfigStore.camdRecorderEnabled(context)
        if (!recorderEnabled) {
            avmVerificationJob?.cancel()
            avmVerificationJob = null
            return
        }

        if (!CamdSocketClient.isInstalled(context)) return

        val autoDvr = LauncherAppConfigStore.camdRecorderAutoOffBySpeed(context)
        val disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context)
        val autoAvmEnabled = LauncherAppConfigStore.camdAutoDisableStockAvm(context)
        val onSpeed = LauncherAppConfigStore.camdAutoAvmOnSpeed(context)
        val offSpeed = LauncherAppConfigStore.camdAutoAvmOffSpeed(context)
        val activeGear = getCurrentActiveGear()

        val s = _status.value as? CamdConnectionStatus.Connected
        val isCurrentlyRecording = s?.recActive == true || s?.recEnabled == true

        val target = resolveDvrTargetState(
            isDvrEnabled = recorderEnabled,
            isAutoDvr = autoDvr,
            isAvmActive = isAvmActive,
            speed = speed,
            activeGear = activeGear,
            autoAvmEnabled = autoAvmEnabled,
            onSpeed = onSpeed,
            offSpeed = offSpeed,
            disableStockAvm = disableStockAvm,
            isCurrentlyRecording = isCurrentlyRecording,
        )

        if (target.shouldDisableStockAvm) {
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "DVR is enabled without auto-DVR -> turning OFF stock AVM (setHoldStockAvm=true)"
            )
            LauncherAppConfigStore.setCamdDisableStockAvm(context, true)
            setHoldStockAvm(context, true)
        }

        if (target.scheduleAvmVerification) {
            scheduleAvmVerification(context)
        } else if (target.targetRecording) {
            avmVerificationJob?.cancel()
            avmVerificationJob = null
        }

        if (target.targetRecording != isCurrentlyRecording) {
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "checkDvrAndAvmCoordination: targetRecording=${target.targetRecording} (was=$isCurrentlyRecording, isAvmActive=$isAvmActive, speed=$speed)"
            )
            setRecorderEnabled(context, target.targetRecording)
        }
    }

    private fun scheduleAvmVerification(context: Context) {
        if (avmVerificationJob?.isActive == true) return
        avmVerificationJob = scope.launch {
            delay(1500L)
            val autoAvmEnabled = LauncherAppConfigStore.camdAutoDisableStockAvm(context)
            val onSpeed = LauncherAppConfigStore.camdAutoAvmOnSpeed(context)
            val offSpeed = LauncherAppConfigStore.camdAutoAvmOffSpeed(context)
            val speed = UniversalCanRepository.carSpeedState.value
            val activeGear = getCurrentActiveGear()

            val shouldHoldOff = if (autoAvmEnabled) {
                resolveAutoAvmHoldOff(speed, activeGear, onSpeed, offSpeed)
            } else {
                LauncherAppConfigStore.camdDisableStockAvm(context)
            }

            if (shouldHoldOff == false && !LauncherAvmRepository.isAvmActive.value) {
                vad.dashing.tbox.DclLogRecorder.log(
                    "CamdCameraRepository",
                    "Auto-DVR verification: Stock AVM did not activate after stopping DVR -> launching stock AVM"
                )
                launchStockAvm(context)
            }
        }
    }

    internal fun getCurrentActiveGear(): Char? {
        return vad.dashing.tbox.ui.launcher.LauncherDevVehicleState.gearSlotOverrideFlow.value
            ?: vad.dashing.tbox.ui.launcher.resolveActiveGearSlot(
                CanDataRepository.gearBoxMode.value,
                CanDataRepository.gearBoxCurrentGear.value,
            )
    }

    internal fun resolveAutoAvmHoldOff(
        speed: Float?,
        activeGear: Char?,
        onSpeed: Int,
        offSpeed: Int,
    ): Boolean? {
        if (onSpeed == 0) {
            // Mode "R": AVM is enabled only in reverse gear
            return if (activeGear == 'R') {
                false // Enable OEM AVM (AVM OFF)
            } else {
                true  // Disable OEM AVM (AVM ON)
            }
        }

        if (speed == null || !speed.isFinite() || speed < 0f) return null

        val onSpeedF = onSpeed.toFloat()
        val offSpeedF = offSpeed.toFloat()

        return when {
            speed > offSpeedF -> true
            speed < onSpeedF -> false
            else -> null
        }
    }

    private suspend fun checkAutoAvmSpeed(context: Context, speed: Float?, activeGear: Char?) {
        val enabled = LauncherAppConfigStore.camdAutoDisableStockAvm(context)
        if (!enabled) {
            if (lastAutoAvmHoldOff != null) {
                lastAutoAvmHoldOff = null
                UniversalCanRepository.setSourceSignals("camd_auto_avm", emptySet())
            }
            return
        }

        val onSpeed = LauncherAppConfigStore.camdAutoAvmOnSpeed(context)
        val offSpeed = LauncherAppConfigStore.camdAutoAvmOffSpeed(context)

        val signals = if (onSpeed == 0 && !(LauncherAppConfigStore.camdRecorderEnabled(context) && LauncherAppConfigStore.camdRecorderAutoOffBySpeed(context))) {
            emptySet()
        } else {
            setOf(MbCanSignal.CarSpeed)
        }
        UniversalCanRepository.setSourceSignals("camd_auto_avm", signals)

        if (!CamdSocketClient.isInstalled(context)) return

        // > offSpeed: disable OEM AVM (holdOff = true, command "AVM ON")
        // < onSpeed (or gear == 'R' if onSpeed == 0): enable OEM AVM (holdOff = false, command "AVM OFF")
        val targetHoldOff = resolveAutoAvmHoldOff(speed, activeGear, onSpeed, offSpeed) ?: return

        if (lastAutoAvmHoldOff != targetHoldOff) {
            lastAutoAvmHoldOff = targetHoldOff
            vad.dashing.tbox.DclLogRecorder.log(
                "CAMD_AUTO_AVM",
                "Speed=$speed km/h gear=$activeGear (on<$onSpeed, off>$offSpeed) -> targetHoldOff=$targetHoldOff"
            )
            setHoldStockAvm(context, targetHoldOff)
            LauncherAppConfigStore.setCamdDisableStockAvm(context, targetHoldOff)
        }
    }

    fun refreshStatus(context: Context) {
        scope.launch {
            val s = CamdSocketClient.checkStatus(context)
            _status.value = s
            vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "refreshStatus: status=$s")
            if (s is CamdConnectionStatus.Connected) {
                val physicalWindows = s.openWindows.map { getPhysicalCameraId(it) }
                val hasAllFour = listOf(CAMERA_ID_LEFT, CAMERA_ID_RIGHT, CAMERA_ID_FRONT, CAMERA_ID_REAR)
                    .all { it in physicalWindows }
                _isDesktopCamerasOpen.value = hasAllFour
                val currentRec = s.recActive || s.recEnabled
                LauncherAppConfigStore.setCamdRecorderEnabled(context, currentRec)
            }
        }
    }

    suspend fun setHoldStockAvm(context: Context, holdOff: Boolean): Result<String> {
        val res = CamdSocketClient.setAvmHold(context, holdOff)
        refreshStatus(context)
        return res
    }

    suspend fun setRecorderEnabled(context: Context, enabled: Boolean): Result<String> {
        val res = CamdSocketClient.setRecorder(context, enabled)
        LauncherAppConfigStore.setCamdRecorderEnabled(context, enabled)
        if (enabled && !LauncherAppConfigStore.camdRecorderAutoOffBySpeed(context)) {
            // "Если тумблер видеорегистратора включен а тумблер автоматического включения выключен-
            // то автоматически должна выключаться штатная AVM (тк видеорегистратор не может работать когда включена AVM)"
            if (!LauncherAppConfigStore.camdDisableStockAvm(context)) {
                LauncherAppConfigStore.setCamdDisableStockAvm(context, true)
                CamdSocketClient.setAvmHold(context, true)
            }
        }
        refreshStatus(context)
        return res
    }

    suspend fun triggerRecorderSos(context: Context): Result<String> {
        val res = CamdSocketClient.triggerRecorderSos(context)
        refreshStatus(context)
        return res
    }

    fun check360Cameras(
        context: Context,
        speed: Float?,
        activeGear: Char?,
        pdc: LauncherPdcZones,
        isAvmActive: Boolean,
    ) {
        val disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context)
        val reverseCamerasEnabled = LauncherAppConfigStore.camdReverseCameras(context)
        val pdcCamerasEnabled = LauncherAppConfigStore.camdPdcCameras(context)
        val isInstalled = CamdSocketClient.isInstalled(context)

        if (!isInstalled || !disableStockAvm || isAvmActive) {
            pdcOffJob?.cancel()
            pdcOffJob = null
            reverseCamerasTriggered = false
            pdcCamerasTriggered = false
            apply360CamerasState(context, false)
            return
        }

        if (pdcCamerasEnabled) {
            LauncherAdasRepository.ensureActive()
        }

        reverseCamerasTriggered = resolveReverseCamerasTarget(
            currentTriggered = reverseCamerasTriggered,
            activeGear = activeGear,
            speed = speed,
            enabled = reverseCamerasEnabled,
        )

        val hasObstacle = hasPhysicalPdcObstacle(pdc)
        val pdcState = resolvePdcCamerasState(
            currentPdcTriggered = pdcCamerasTriggered,
            hasObstacle = hasObstacle,
            activeGear = activeGear,
            enabled = pdcCamerasEnabled,
        )

        if (pdcState.cancelTurnOff) {
            pdcOffJob?.cancel()
            pdcOffJob = null
        }

        pdcCamerasTriggered = pdcState.pdcTriggered

        if (pdcState.shouldScheduleTurnOff) {
            if (pdcOffJob == null) {
                pdcOffJob = scope.launch {
                    delay(PDC_OFF_DELAY_MS)
                    pdcOffJob = null
                    pdcCamerasTriggered = false
                    val target = reverseCamerasTriggered
                    apply360CamerasState(context, target)
                    evaluateSideCameraState(context)
                }
            }
        }

        val target360 = reverseCamerasTriggered || pdcCamerasTriggered
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "check360Cameras: target360=$target360 (revTriggered=$reverseCamerasTriggered, pdcTriggered=$pdcCamerasTriggered, gear=$activeGear, speed=$speed)"
        )
        apply360CamerasState(context, target360)
    }

    data class PdcCameraTransition(
        val pdcTriggered: Boolean,
        val shouldScheduleTurnOff: Boolean,
        val cancelTurnOff: Boolean,
    )

    internal fun resolvePdcCamerasState(
        currentPdcTriggered: Boolean,
        hasObstacle: Boolean,
        activeGear: Char?,
        enabled: Boolean,
    ): PdcCameraTransition {
        if (!enabled) {
            return PdcCameraTransition(
                pdcTriggered = false,
                shouldScheduleTurnOff = false,
                cancelTurnOff = true,
            )
        }

        if (hasObstacle) {
            return PdcCameraTransition(
                pdcTriggered = true,
                shouldScheduleTurnOff = false,
                cancelTurnOff = true,
            )
        }

        if (currentPdcTriggered) {
            if (activeGear == 'R') {
                return PdcCameraTransition(
                    pdcTriggered = true,
                    shouldScheduleTurnOff = false,
                    cancelTurnOff = true,
                )
            }
            return PdcCameraTransition(
                pdcTriggered = true,
                shouldScheduleTurnOff = true,
                cancelTurnOff = false,
            )
        }

        return PdcCameraTransition(
            pdcTriggered = false,
            shouldScheduleTurnOff = false,
            cancelTurnOff = false,
        )
    }

    private var reverseAvmRetryJob: Job? = null
    internal var stockAvmLauncher: ((Context) -> Unit)? = null

    internal fun launchStockAvm(context: Context) {
        val custom = stockAvmLauncher
        if (custom != null) {
            custom.invoke(context)
            return
        }
        val targetCtx = vad.dashing.tbox.LauncherHomeActivityHolder.instance ?: context.applicationContext
        scope.launch(Dispatchers.Main.immediate) {
            try {
                vad.dashing.tbox.ui.launcher.launchLauncherApp(targetCtx, "com.mengbo.avm")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to launch stock AVM com.mengbo.avm", t)
            }
        }
    }

    internal fun shouldCloseCamerasForStockAvm(
        disableStockAvm: Boolean,
        activeGear: Char?,
        speed: Float?,
        minSpeed: Float = 1f,
    ): Boolean {
        if (disableStockAvm) return false
        val isBlockedGear = activeGear == 'R' || activeGear == 'P'
        val isBelowMinSpeed = speed == null || speed < minSpeed
        return isBlockedGear || isBelowMinSpeed
    }

    internal fun shouldTriggerReverseAvmRetry(
        disableStockAvm: Boolean,
        activeGear: Char?,
        isAvmActive: Boolean,
    ): Boolean {
        return activeGear == 'R' && !disableStockAvm && !isAvmActive
    }

    internal fun checkReverseAvmRetry(context: Context, activeGear: Char?, isAvmActive: Boolean) {
        val disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context)
        if (!shouldTriggerReverseAvmRetry(disableStockAvm, activeGear, isAvmActive)) {
            reverseAvmRetryJob?.cancel()
            reverseAvmRetryJob = null
            return
        }

        if (reverseAvmRetryJob?.isActive == true) return

        reverseAvmRetryJob = scope.launch {
            val hadActiveCameras = hasActiveCameras() ||
                turnSignalJob?.isActive == true ||
                desktopOperationJob?.isActive == true ||
                reverseCamerasJob?.isActive == true

            if (hadActiveCameras) {
                closeAll(context)
                if (shouldTriggerReverseAvmRetry(
                        disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context),
                        activeGear = getCurrentActiveGear(),
                        isAvmActive = LauncherAvmRepository.isAvmActive.value,
                    )
                ) {
                    vad.dashing.tbox.DclLogRecorder.log(
                        "CAMD_CAMERA",
                        "Cameras closed in R gear, invoking stock AVM immediately"
                    )
                    launchStockAvm(context)
                }
            }

            while (isActive &&
                shouldTriggerReverseAvmRetry(
                    disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context),
                    activeGear = getCurrentActiveGear(),
                    isAvmActive = LauncherAvmRepository.isAvmActive.value,
                )
            ) {
                delay(1000L)
                if (!shouldTriggerReverseAvmRetry(
                        disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context),
                        activeGear = getCurrentActiveGear(),
                        isAvmActive = LauncherAvmRepository.isAvmActive.value,
                    )
                ) {
                    break
                }
                vad.dashing.tbox.DclLogRecorder.log(
                    "CAMD_CAMERA",
                    "R gear active but stock AVM not active -> retrying stock AVM call (1s interval)"
                )
                launchStockAvm(context)
            }
        }
    }

    internal fun checkReverseGearCameras(context: Context, speed: Float?, activeGear: Char?) {
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "checkReverseGearCameras: speed=$speed activeGear=$activeGear"
        )
        val pdc = vad.dashing.tbox.ui.launcher.LauncherDevVehicleState.adasStateOrNull()?.pdc
            ?: LauncherAdasRepository.state.value.pdc
        val isAvmActive = LauncherAvmRepository.isAvmActive.value
        check360Cameras(context, speed, activeGear, pdc, isAvmActive)
        checkReverseAvmRetry(context, activeGear, isAvmActive)
    }

    internal fun resolveReverseCamerasTarget(
        currentTriggered: Boolean,
        activeGear: Char?,
        speed: Float?,
        enabled: Boolean,
    ): Boolean {
        if (!enabled) return false

        if (activeGear == 'R') {
            return true
        }

        if (currentTriggered) {
            if (activeGear == 'P') {
                return false
            }
            val s = speed ?: 0f
            return s < 15f
        }

        return false
    }

    private fun apply360CamerasState(context: Context, targetActive: Boolean) {
        if (targetActive == _isReverseCamerasOpen.value && reverseCamerasJob?.isActive != true) {
            return
        }
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "apply360CamerasState: targetActive=$targetActive currentOpen=${_isReverseCamerasOpen.value}"
        )

        if (targetActive) {
            if (!_isReverseCamerasOpen.value && reverseCamerasJob?.isActive != true) {
                turnSignalJob?.cancel()
                val active = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                _activeIsLowSpeed = false

                reverseCamerasJob = scope.launch {
                    if (active != null) closeCamera(context, active)
                    openReverseCameras(context)
                }
            }
        } else {
            if (_isReverseCamerasOpen.value || reverseCamerasJob?.isActive == true) {
                reverseCamerasJob?.cancel()
                reverseCamerasJob = scope.launch {
                    closeReverseCameras(context)
                }
            }
        }
    }

    private suspend fun openReverseCameras(context: Context) = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()

        val appRect = LauncherEmbeddedBoundsState.fullWidthBounds()
            ?: LauncherEmbeddedBoundsState.embeddedBounds()
            ?: Rect(0, 51, 1920, 998)

        val commands = build360CameraCommands(context, appRect, dispW, dispH)
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "openReverseCameras: commands=$commands bounds=$appRect"
        )
        if (commands.isEmpty()) {
            _isReverseCamerasOpen.value = false
            return@withLock
        }

        var successCount = 0
        for ((idx, cmd) in commands.withIndex()) {
            val res = if (idx == 0) {
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            } else {
                CamdSocketClient.sendCommand(cmd)
            }
            val responseText = res.getOrNull().orEmpty()
            val isSuccess = res.isSuccess && CamdSocketClient.isSuccessResponse(responseText)
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "openReverseCameras: cmd='$cmd' success=$isSuccess resp='$responseText' err=${res.exceptionOrNull()?.message}"
            )
            if (isSuccess) {
                successCount++
            } else {
                Log.w(TAG, "Failed reverse camera command '$cmd': $responseText err=${res.exceptionOrNull()?.message}")
            }
        }

        _isReverseCamerasOpen.value = (successCount > 0)
    }

    private suspend fun closeReverseCameras(context: Context) = cameraOperationMutex.withLock {
        vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "closeReverseCameras: sending CLOSE")
        val res = CamdSocketClient.sendCommand("CLOSE")
        if (res.isFailure) {
            CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
        }
        _isReverseCamerasOpen.value = false
        LauncherCar3DModelTrigger.notifyReverseCamerasClosed()
        LauncherCar3DModelTrigger.notifyCamdCamerasClosed()
    }

    internal fun build360CameraCommands(
        context: Context,
        appRect: Rect,
        dispW: Float,
        dispH: Float,
    ): List<String> {
        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge

        val connectedPresets = (_status.value as? CamdConnectionStatus.Connected)?.presets

        val lrLeftPreset = LauncherAppConfigStore.camdWidgetLrLeftPreset(context).trim()
        val lrRightPreset = LauncherAppConfigStore.camdWidgetLrRightPreset(context).trim()
        val udTopPreset = LauncherAppConfigStore.camdWidgetUdTopPreset(context).trim()
        val udBottomPreset = LauncherAppConfigStore.camdWidgetUdBottomPreset(context).trim()

        fun buildCameraCommand(presetName: String, fallbackCamId: Int, x: Float, y: Float, w: Float, h: Float): String {
            val numId = presetName.toIntOrNull()
            return if (numId != null) {
                formatCmd(numId, x, y, w, h)
            } else if (presetName.isNotEmpty() && connectedPresets?.any { it.name == presetName } == true) {
                formatPresetCmd(presetName, x, y, w, h)
            } else if (presetName.isNotEmpty()) {
                val targetId = connectedPresets?.firstOrNull { it.name == presetName }?.cameraId ?: fallbackCamId
                formatCmd(targetId, x, y, w, h)
            } else {
                formatCmd(fallbackCamId, x, y, w, h)
            }
        }

        val colLeftX1 = leftEdge
        val colLeftX2 = leftEdge + appWidth * 0.30f

        val colRightX1 = rightEdge - appWidth * 0.30f
        val colRightX2 = rightEdge

        val colMidX1 = colLeftX2
        val colMidX2 = colRightX1
        val midY = topEdge + (appHeight / 2f)

        val leftX = colLeftX1 / dispW
        val leftY = topEdge / dispH
        val leftW = (colLeftX2 - colLeftX1) / dispW
        val leftH = appHeight / dispH

        val rightX = colRightX1 / dispW
        val rightY = topEdge / dispH
        val rightW = (colRightX2 - colRightX1) / dispW
        val rightH = appHeight / dispH

        val frontX = colMidX1 / dispW
        val frontY = topEdge / dispH
        val frontW = (colMidX2 - colMidX1) / dispW
        val frontH = (midY - topEdge) / dispH

        val rearX = colMidX1 / dispW
        val rearY = midY / dispH
        val rearW = (colMidX2 - colMidX1) / dispW
        val rearH = (bottomEdge - midY) / dispH

        val leftCmd = buildCameraCommand(lrLeftPreset, CAMERA_ID_LEFT, leftX, leftY, leftW, leftH)
        val rightCmd = buildCameraCommand(lrRightPreset, CAMERA_ID_RIGHT, rightX, rightY, rightW, rightH)
        val frontCmd = buildCameraCommand(udTopPreset, CAMERA_ID_FRONT, frontX, frontY, frontW, frontH)
        val rearCmd = buildCameraCommand(udBottomPreset, CAMERA_ID_REAR, rearX, rearY, rearW, rearH)

        return listOf(leftCmd, rightCmd, frontCmd, rearCmd)
    }

    /**
     * Toggles 4 desktop cameras in the desktop / app window area:
     * - Left camera (#4) on the left
     * - Right camera (#5) on the right
     * - Front (#8) & Rear (#9) in the middle up to half height
     */
    fun toggleDesktopCameras(context: Context) {
        val installed = CamdSocketClient.isInstalled(context)
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "toggleDesktopCameras: isDesktopOpen=${_isDesktopCamerasOpen.value} isInstalled=$installed"
        )
        desktopOperationJob?.cancel()
        desktopOperationJob = scope.launch {
            if (_isDesktopCamerasOpen.value) {
                closeAll(context)
                _isDesktopCamerasOpen.value = false
            } else {
                openDesktopCameras(context)
            }
        }
    }

    fun hasActiveCameras(): Boolean =
        _isDesktopCamerasOpen.value ||
            _activeWidgetCameraMode.value != CamdWidgetCameraMode.NONE ||
            _isReverseCamerasOpen.value ||
            _activeTurnSignalCamera.value != null ||
            _activeTurnSignalSide.value != null ||
            ((_status.value as? CamdConnectionStatus.Connected)?.openWindows?.isNotEmpty() == true)

    /**
     * Closes any active Camd cameras (desktop 4-camera grid, side cameras, or test cameras).
     */
    fun closeAllIfOpen(context: Context) {
        if (!CamdSocketClient.isInstalled(context)) return
        if (hasActiveCameras() || desktopOperationJob?.isActive == true || turnSignalJob?.isActive == true || reverseCamerasJob?.isActive == true) {
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "closeAllIfOpen: closing cameras (active=${hasActiveCameras()})"
            )
            desktopOperationJob?.cancel()
            turnSignalJob?.cancel()
            reverseCamerasJob?.cancel()
            desktopOperationJob = scope.launch {
                closeAll(context)
            }
        }
    }

    /**
     * Closes desktop cameras if they are currently active.
     */
    fun closeDesktopCamerasIfOpen(context: Context) {
        closeAllIfOpen(context)
    }

    /**
     * Toggles cameras requested by the floating camera widget (Left/Right, Up/Down, 360).
     */
    fun toggleWidgetCameras(context: Context, mode: CamdWidgetCameraMode) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val isInstalled = CamdSocketClient.isInstalled(appCtx)
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "toggleWidgetCameras: mode=$mode activeMode=${_activeWidgetCameraMode.value} isInstalled=$isInstalled"
        )
        if (!isInstalled) return

        if (_activeWidgetCameraMode.value == mode) {
            closeAllIfOpen(appCtx)
            return
        }

        desktopOperationJob?.cancel()
        turnSignalJob?.cancel()
        desktopOperationJob = scope.launch {
            closeAll(appCtx)
            openWidgetCameras(appCtx, mode)
            evaluateSideCameraState(appCtx)
        }
    }

    private suspend fun openWidgetCameras(context: Context, mode: CamdWidgetCameraMode) = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()

        val appRect = LauncherEmbeddedBoundsState.embeddedBounds()
            ?: Rect(344, 51, 1920, 998)

        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge

        val connectedPresets = (_status.value as? CamdConnectionStatus.Connected)?.presets

        val lrLeftPreset = LauncherAppConfigStore.camdWidgetLrLeftPreset(context).trim()
        val lrRightPreset = LauncherAppConfigStore.camdWidgetLrRightPreset(context).trim()
        val udTopPreset = LauncherAppConfigStore.camdWidgetUdTopPreset(context).trim()
        val udBottomPreset = LauncherAppConfigStore.camdWidgetUdBottomPreset(context).trim()

        fun buildCameraCommand(presetName: String, fallbackCamId: Int, x: Float, y: Float, w: Float, h: Float): String {
            val numId = presetName.toIntOrNull()
            return if (numId != null) {
                formatCmd(numId, x, y, w, h)
            } else if (presetName.isNotEmpty() && connectedPresets?.any { it.name == presetName } == true) {
                formatPresetCmd(presetName, x, y, w, h)
            } else if (presetName.isNotEmpty()) {
                val targetId = connectedPresets?.firstOrNull { it.name == presetName }?.cameraId ?: fallbackCamId
                formatCmd(targetId, x, y, w, h)
            } else {
                formatCmd(fallbackCamId, x, y, w, h)
            }
        }

        val commands = when (mode) {
            CamdWidgetCameraMode.LEFT_RIGHT -> {
                // Splits width exactly in half: left 50%, right 50%, full height
                val halfW = appWidth / 2f
                val leftX = leftEdge / dispW
                val leftY = topEdge / dispH
                val leftW = halfW / dispW
                val leftH = appHeight / dispH

                val rightX = (leftEdge + halfW) / dispW
                val rightY = topEdge / dispH
                val rightW = halfW / dispW
                val rightH = appHeight / dispH

                val leftCmd = buildCameraCommand(lrLeftPreset, CAMERA_ID_LEFT, leftX, leftY, leftW, leftH)
                val rightCmd = buildCameraCommand(lrRightPreset, CAMERA_ID_RIGHT, rightX, rightY, rightW, rightH)
                listOf(leftCmd, rightCmd)
            }
            CamdWidgetCameraMode.UP_DOWN -> {
                // Splits height exactly in half: front (top) 50%, rear (bottom) 50%, full width
                val halfH = appHeight / 2f
                val frontX = leftEdge / dispW
                val frontY = topEdge / dispH
                val frontW = appWidth / dispW
                val frontH = halfH / dispH

                val rearX = leftEdge / dispW
                val rearY = (topEdge + halfH) / dispH
                val rearW = appWidth / dispW
                val rearH = halfH / dispH

                val frontCmd = buildCameraCommand(udTopPreset, CAMERA_ID_FRONT, frontX, frontY, frontW, frontH)
                val rearCmd = buildCameraCommand(udBottomPreset, CAMERA_ID_REAR, rearX, rearY, rearW, rearH)
                listOf(frontCmd, rearCmd)
            }
            CamdWidgetCameraMode.ALL_360 -> {
                build360CameraCommands(context, appRect, dispW, dispH)
            }
            CamdWidgetCameraMode.NONE -> emptyList()
        }

        if (commands.isEmpty()) {
            vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "openWidgetCameras: mode=$mode has empty commands")
            _activeWidgetCameraMode.value = CamdWidgetCameraMode.NONE
            return@withLock
        }
        vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "openWidgetCameras: mode=$mode commands=$commands")

        var successCount = 0
        for ((idx, cmd) in commands.withIndex()) {
            val res = if (idx == 0) {
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            } else {
                CamdSocketClient.sendCommand(cmd)
            }
            val responseText = res.getOrNull().orEmpty()
            val isSuccess = res.isSuccess && CamdSocketClient.isSuccessResponse(responseText)
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "openWidgetCameras: cmd='$cmd' success=$isSuccess resp='$responseText' err=${res.exceptionOrNull()?.message}"
            )
            if (isSuccess) {
                successCount++
            } else {
                Log.w(TAG, "Failed widget command '$cmd': $responseText err=${res.exceptionOrNull()?.message}")
            }
        }

        if (successCount > 0) {
            _activeWidgetCameraMode.value = mode
            _isDesktopCamerasOpen.value = (mode == CamdWidgetCameraMode.ALL_360)
        } else {
            _activeWidgetCameraMode.value = CamdWidgetCameraMode.NONE
        }
    }

    private suspend fun openDesktopCameras(context: Context) = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()

        val appRect = LauncherEmbeddedBoundsState.embeddedBounds()
            ?: Rect(344, 51, 1920, 998)

        val leftEdge = appRect.left.toFloat()
        val rightEdge = appRect.right.toFloat()
        val topEdge = appRect.top.toFloat()
        val bottomEdge = appRect.bottom.toFloat()

        val appWidth = rightEdge - leftEdge
        val appHeight = bottomEdge - topEdge
        val midY = topEdge + (appHeight / 2f)

        // Geometry:
        // Left camera takes left 30% of app area
        val colLeftX1 = leftEdge
        val colLeftX2 = leftEdge + appWidth * 0.30f

        // Right camera takes right 30% of app area
        val colRightX1 = rightEdge - appWidth * 0.30f
        val colRightX2 = rightEdge

        // Middle column takes remaining 40%
        val colMidX1 = colLeftX2
        val colMidX2 = colRightX1

        // 1. Left Camera (#4)
        val leftX = colLeftX1 / dispW
        val leftY = topEdge / dispH
        val leftW = (colLeftX2 - colLeftX1) / dispW
        val leftH = appHeight / dispH

        // 2. Right Camera (#5)
        val rightX = colRightX1 / dispW
        val rightY = topEdge / dispH
        val rightW = (colRightX2 - colRightX1) / dispW
        val rightH = appHeight / dispH

        // 3. Front Camera (#8) - upper half of middle column
        val frontX = colMidX1 / dispW
        val frontY = topEdge / dispH
        val frontW = (colMidX2 - colMidX1) / dispW
        val frontH = (midY - topEdge) / dispH

        // 4. Rear Camera (#9) - lower half of middle column
        val rearX = colMidX1 / dispW
        val rearY = midY / dispH
        val rearW = (colMidX2 - colMidX1) / dispW
        val rearH = (bottomEdge - midY) / dispH

        val commands = listOf(
            formatCmd(CAMERA_ID_LEFT, leftX, leftY, leftW, leftH),
            formatCmd(CAMERA_ID_RIGHT, rightX, rightY, rightW, rightH),
            formatCmd(CAMERA_ID_FRONT, frontX, frontY, frontW, frontH),
            formatCmd(CAMERA_ID_REAR, rearX, rearY, rearW, rearH),
        )
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "openDesktopCameras: commands=$commands bounds=$appRect"
        )

        var successCount = 0
        for ((idx, cmd) in commands.withIndex()) {
            val res = if (idx == 0) {
                // First command wakes up service if sleeping
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            } else {
                CamdSocketClient.sendCommand(cmd)
            }
            val responseText = res.getOrNull().orEmpty()
            val isSuccess = res.isSuccess && CamdSocketClient.isSuccessResponse(responseText)
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "openDesktopCameras: cmd='$cmd' success=$isSuccess resp='$responseText' err=${res.exceptionOrNull()?.message}"
            )
            if (isSuccess) {
                successCount++
            } else {
                Log.w(TAG, "Failed command '$cmd': $responseText err=${res.exceptionOrNull()?.message}")
            }
        }

        _isDesktopCamerasOpen.value = (successCount > 0)
    }

    suspend fun closeAll(context: Context) {
        vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "closeAll")
        steerOffJob?.cancel()
        steerOffJob = null
        turnSignalJob?.cancel()
        reverseCamerasJob?.cancel()
        cameraOperationMutex.withLock {
            val res = CamdSocketClient.sendCommand("CLOSE")
            if (res.isFailure) {
                CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
            }
            _isDesktopCamerasOpen.value = false
            _activeWidgetCameraMode.value = CamdWidgetCameraMode.NONE
            _isReverseCamerasOpen.value = false
            reverseCamerasTriggered = false
            _activeTurnSignalCamera.value = null
            _activeTurnSignalSide.value = null
            turnSignalsLatch.reset()
            LauncherCar3DModelTrigger.notifyReverseCamerasClosed()
            LauncherCar3DModelTrigger.notifyCamdCamerasClosed()
        }
    }

    suspend fun closeCamera(context: Context, id: Int?) {
        cameraOperationMutex.withLock {
            val cmd = if (id != null) "CLOSE $id" else "CLOSE"
            vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "closeCamera cmd='$cmd' id=$id")
            val res = CamdSocketClient.sendCommand(cmd)
            if (res.isFailure) {
                CamdSocketClient.sendCommandWithWakeup(context, cmd)
            }
            if (id != null && (res.isFailure || CamdSocketClient.isErrorResponse(res.getOrNull().orEmpty()))) {
                vad.dashing.tbox.DclLogRecorder.log("CamdCameraRepository", "closeCamera id=$id failed (${res.getOrNull()}), fallback to CLOSE")
                CamdSocketClient.sendCommandWithWakeup(context, "CLOSE")
            }
            if (id == null) {
                _isDesktopCamerasOpen.value = false
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
            } else {
                if (_activeTurnSignalCamera.value == id) {
                    _activeTurnSignalCamera.value = null
                    _activeTurnSignalSide.value = null
                }
            }
            LauncherCar3DModelTrigger.notifyCamdCamerasClosed()
        }
    }

    /**
     * Handles turn-light signals from mbCAN (type 2, eMBCAN_VEHICLE_TURNLIGHT):
     * If turn-signal cameras or low-speed side cameras option is enabled,
     * opens the corresponding side camera (#4 left, #5 right).
     * Uses [TurnSignalsLatch] with 2.5s hold time.
     */
    fun onTurnLightSignal(context: Context, leftLight: Byte, rightLight: Byte) {
        val appCtx = context.applicationContext
        appContext = appCtx

        startSideCamerasMonitor(appCtx)

        // Do not process turn signal cameras during cold startup before initial draw is finished
        if (!vad.dashing.tbox.LauncherHomeActivityHolder.isFirstDrawComplete) {
            return
        }

        if (_status.value !is CamdConnectionStatus.Connected) {
            refreshStatus(appCtx)
        }

        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(appCtx)
        val lowSpeedEnabled = LauncherAppConfigStore.camdLowSpeedSideCameras(appCtx)
        val trigger = LauncherAppConfigStore.camdLowSpeedSideTrigger(appCtx)

        val turnSignalsUsed = turnSignalCameras || (lowSpeedEnabled && trigger != CAMD_LOW_SPEED_TRIGGER_STEERING)

        if (!turnSignalsUsed) {
            turnSignalsLatch.reset()
            evaluateSideCameraState(appCtx)
            return
        }

        turnSignalsLatch.onRawTurnLights(leftLight.toInt(), rightLight.toInt())
    }

    fun evaluateSideCameraState(context: Context) {
        if (!vad.dashing.tbox.LauncherHomeActivityHolder.isFirstDrawComplete) return

        val speed = UniversalCanRepository.carSpeedState.value ?: 0f
        val disableStockAvm = LauncherAppConfigStore.camdDisableStockAvm(context)
        val activeGear = getCurrentActiveGear()
        val isReverseGear = activeGear == 'R'
        val isAvmActive = LauncherAvmRepository.isAvmActive.value
        val minSpeed = LauncherAppConfigStore.camdStockAvmSideCamerasMinSpeed(context).toFloat()

        checkReverseAvmRetry(context, activeGear, isAvmActive)

        if (shouldCloseCamerasForStockAvm(disableStockAvm, activeGear, speed, minSpeed)) {
            vad.dashing.tbox.DclLogRecorder.log(
                "CamdCameraRepository",
                "evaluateSideCameraState: suppressed by shouldCloseCamerasForStockAvm (disableStockAvm=$disableStockAvm, activeGear=$activeGear, speed=$speed, minSpeed=$minSpeed)"
            )
            if (hasActiveCameras() || desktopOperationJob?.isActive == true || turnSignalJob?.isActive == true || reverseCamerasJob?.isActive == true) {
                closeAllIfOpen(context)
            }
            if (_activeTurnSignalSide.value != null || _activeTurnSignalCamera.value != null || _activeIsLowSpeed) {
                _activeTurnSignalSide.value = null
                _activeTurnSignalCamera.value = null
                _activeIsLowSpeed = false
            }
            return
        }

        val turnSignalCameras = LauncherAppConfigStore.camdTurnSignalCameras(context)
        val lowSpeedEnabled = LauncherAppConfigStore.camdLowSpeedSideCameras(context)

        if (!turnSignalCameras && !lowSpeedEnabled) {
            turnSignalJob?.cancel()
            if (_activeTurnSignalCamera.value != null || _activeTurnSignalSide.value != null) {
                val active = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                _activeIsLowSpeed = false
                turnSignalJob = scope.launch {
                    if (active != null) closeCamera(context, active) else closeCamera(context, null)
                }
            }
            return
        }

        val lowSpeedThreshold = LauncherAppConfigStore.camdLowSpeedThreshold(context).toFloat()

        val turnSignalSide = turnSignalsLatch.latchedSide.value
        val steerSide = currentSteerSide
        val triggerMode = LauncherAppConfigStore.camdLowSpeedSideTrigger(context)
        val isReverseActive = _isReverseCamerasOpen.value || reverseCamerasTriggered || pdcCamerasTriggered
        val isWidgetActive = _activeWidgetCameraMode.value != CamdWidgetCameraMode.NONE || _isDesktopCamerasOpen.value
        val reverseCamerasEnabled = LauncherAppConfigStore.camdReverseCameras(context)
        val invertSteerOnReverse = disableStockAvm && !reverseCamerasEnabled

        val (targetSide, targetIsLowSpeed) = resolveTargetSideCamera(
            speed = speed,
            lowSpeedThreshold = lowSpeedThreshold,
            lowSpeedEnabled = lowSpeedEnabled,
            turnSignalCamerasEnabled = turnSignalCameras,
            triggerMode = triggerMode,
            turnSignalSide = turnSignalSide,
            steerSide = steerSide,
            activeSide = _activeTurnSignalSide.value,
            activeIsLowSpeed = _activeIsLowSpeed,
            isAvmActive = isAvmActive,
            isReverseActive = isReverseActive,
            isReverseGear = isReverseGear,
            invertSteerOnReverse = invertSteerOnReverse,
            isWidgetActive = isWidgetActive,
            disableStockAvm = disableStockAvm,
            activeGear = activeGear,
            minSpeed = minSpeed,
        )

        applyTargetSideCamera(context, targetSide, targetIsLowSpeed)
    }

    internal fun resolveTargetSideCamera(
        speed: Float,
        lowSpeedThreshold: Float,
        lowSpeedEnabled: Boolean,
        turnSignalCamerasEnabled: Boolean,
        triggerMode: String,
        turnSignalSide: TurnSignalSide?,
        steerSide: TurnSignalSide?,
        activeSide: TurnSignalSide?,
        activeIsLowSpeed: Boolean,
        isAvmActive: Boolean = false,
        isReverseActive: Boolean = false,
        isReverseGear: Boolean = false,
        invertSteerOnReverse: Boolean = false,
        isWidgetActive: Boolean = false,
        disableStockAvm: Boolean = invertSteerOnReverse,
        activeGear: Char? = if (isReverseGear) 'R' else null,
        minSpeed: Float = 1f,
    ): Pair<TurnSignalSide?, Boolean> {
        if (isAvmActive || isReverseActive || isWidgetActive) {
            return null to false
        }

        // When stock OEM AVM is enabled (!disableStockAvm):
        // 1. In gears R or P, side cameras must close / not activate
        // 2. Below minSpeed, side cameras must close / not activate
        if (shouldCloseCamerasForStockAvm(disableStockAvm, activeGear, speed, minSpeed)) {
            return null to false
        }

        // If blind-spot camera is currently active and turn signal is still active on the same side,
        // do not interrupt/switch it to low-speed camera when speed drops.
        if (turnSignalCamerasEnabled && activeSide != null && !activeIsLowSpeed && turnSignalSide == activeSide) {
            return activeSide to false
        }

        val isLowSpeed = speed < lowSpeedThreshold

        val effectiveSteerSide = if (isReverseGear && invertSteerOnReverse) {
            when (steerSide) {
                TurnSignalSide.Left -> TurnSignalSide.Right
                TurnSignalSide.Right -> TurnSignalSide.Left
                else -> null
            }
        } else {
            steerSide
        }

        return if (lowSpeedEnabled && isLowSpeed) {
            val side = when (triggerMode) {
                CAMD_LOW_SPEED_TRIGGER_TURN_SIGNALS -> turnSignalSide
                CAMD_LOW_SPEED_TRIGGER_STEERING -> effectiveSteerSide
                CAMD_LOW_SPEED_TRIGGER_BOTH -> {
                    if (isReverseGear && effectiveSteerSide != null) {
                        effectiveSteerSide
                    } else {
                        turnSignalSide ?: effectiveSteerSide
                    }
                }
                else -> {
                    if (isReverseGear && effectiveSteerSide != null) {
                        effectiveSteerSide
                    } else {
                        turnSignalSide ?: effectiveSteerSide
                    }
                }
            }
            if (side != null) side to true else null to false
        } else if (turnSignalCamerasEnabled) {
            if (turnSignalSide != null) turnSignalSide to false else null to false
        } else {
            null to false
        }
    }

    private fun applyTargetSideCamera(context: Context, targetSide: TurnSignalSide?, isLowSpeed: Boolean) {
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "applyTargetSideCamera target=$targetSide isLowSpeed=$isLowSpeed active=${_activeTurnSignalSide.value} activeLow=$_activeIsLowSpeed"
        )
        if (_activeTurnSignalSide.value == targetSide && _activeIsLowSpeed == isLowSpeed) {
            return
        }

        turnSignalJob?.cancel()
        val previousIsLowSpeed = _activeIsLowSpeed
        val activeCamera = _activeTurnSignalCamera.value

        if (targetSide == null) {
            _activeTurnSignalSide.value = null
            _activeTurnSignalCamera.value = null
            _activeIsLowSpeed = false
            turnSignalJob = scope.launch {
                kotlinx.coroutines.delay(50L)
                if (activeCamera != null) closeCamera(context, activeCamera) else closeCamera(context, null)
            }
            return
        }

        _activeTurnSignalSide.value = targetSide
        _activeIsLowSpeed = isLowSpeed
        val targetCamId = if (targetSide == TurnSignalSide.Left) CAMERA_ID_LEFT else CAMERA_ID_RIGHT

        turnSignalJob = scope.launch {
            kotlinx.coroutines.delay(50L)
            val current = _activeTurnSignalCamera.value ?: activeCamera
            if (current != null && (current != targetCamId || previousIsLowSpeed != isLowSpeed)) {
                closeCamera(context, current)
                _activeTurnSignalCamera.value = null
            }
            val openedCamId = openTurnSignalSideCamera(context, targetCamId, isLowSpeed = isLowSpeed)
            if (!isActive || _activeTurnSignalSide.value != targetSide || _activeIsLowSpeed != isLowSpeed) {
                Log.i(TAG, "Side camera target changed during open, closing immediately")
                if (openedCamId != null) closeCamera(context, openedCamId) else closeCamera(context, null)
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                _activeIsLowSpeed = false
                return@launch
            }
            _activeTurnSignalCamera.value = openedCamId
        }
    }

    /**
     * Toggles a side camera test on/off.
     */
    fun testSideCamera(context: Context, cameraId: Int) {
        val side = if (cameraId == CAMERA_ID_LEFT) TurnSignalSide.Left else TurnSignalSide.Right
        val lowSpeed = LauncherAppConfigStore.camdLowSpeedSideCameras(context)
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "testSideCamera: cameraId=$cameraId side=$side lowSpeed=$lowSpeed activeSide=${_activeTurnSignalSide.value} activeCam=${_activeTurnSignalCamera.value}"
        )
        turnSignalJob?.cancel()
        turnSignalJob = scope.launch {
            if (_activeTurnSignalSide.value == side) {
                val current = _activeTurnSignalCamera.value
                _activeTurnSignalCamera.value = null
                _activeTurnSignalSide.value = null
                _activeIsLowSpeed = false
                if (current != null) {
                    closeCamera(context, current)
                } else {
                    closeCamera(context, null)
                }
            } else {
                val current = _activeTurnSignalCamera.value
                if (current != null) {
                    closeCamera(context, current)
                    _activeTurnSignalCamera.value = null
                }
                _activeTurnSignalSide.value = side
                _activeIsLowSpeed = lowSpeed
                val openedCamId = openTurnSignalSideCamera(context, cameraId, isLowSpeed = lowSpeed)
                _activeTurnSignalCamera.value = openedCamId
                _activeTurnSignalSide.value = side
            }
        }
    }

    private suspend fun openTurnSignalSideCamera(context: Context, cameraId: Int, isLowSpeed: Boolean = false): Int? = cameraOperationMutex.withLock {
        val screen = LauncherEmbeddedBoundsState.screenBounds
        val dispW = (screen?.width()?.takeIf { it > 0 } ?: 1920).toFloat()
        val dispH = (screen?.height()?.takeIf { it > 0 } ?: 1080).toFloat()
        if (dispW <= 0f || dispH <= 0f) return null

        val shapeCircle: Boolean
        val effectiveShapeCircle: Boolean
        val presetName: String?
        val targetCameraId: Int
        val x: Float
        val y: Float
        val w: Float
        val h: Float

        if (isLowSpeed) {
            shapeCircle = false
            effectiveShapeCircle = false
            presetName = when (cameraId) {
                CAMERA_ID_LEFT -> LauncherAppConfigStore.camdLowSpeedLeftPreset(context).ifBlank { null }
                CAMERA_ID_RIGHT -> LauncherAppConfigStore.camdLowSpeedRightPreset(context).ifBlank { null }
                else -> null
            }
            targetCameraId = if (!presetName.isNullOrBlank()) {
                val connectedPresets = (_status.value as? CamdConnectionStatus.Connected)?.presets
                connectedPresets?.firstOrNull { it.name == presetName }?.cameraId ?: cameraId
            } else {
                cameraId
            }

            val widthPercent = LauncherAppConfigStore.camdLowSpeedSideWidthPercent(context).coerceIn(10, 50)

            val hasActiveApp = LauncherActiveAppTracker.hasActiveApp()
            val activePkg = LauncherActiveAppTracker.activeAppPackage().orEmpty()
            val trackerMode = LauncherActiveAppTracker.launchMode()

            val isFullscreen = hasActiveApp && (
                LauncherActiveAppTracker.isFullscreen() ||
                trackerMode == LauncherAppLaunchMode.FULLSCREEN ||
                (activePkg.isNotBlank() && LauncherAppConfigStore.appLaunchMode(context, activePkg) == LauncherAppLaunchMode.FULLSCREEN)
            )

            val isFullWidth = !isFullscreen && (
                (hasActiveApp && (
                    LauncherActiveAppTracker.isFullWidth() ||
                    trackerMode == LauncherAppLaunchMode.FULL_WIDTH ||
                    (activePkg.isNotBlank() && LauncherAppConfigStore.appLaunchMode(context, activePkg) == LauncherAppLaunchMode.FULL_WIDTH)
                )) ||
                LauncherEmbeddedBoundsState.leftPanelBounds == null
            )

            val rectW: Float
            val rectH: Float
            val xPx: Float
            val yPx: Float

            if (isFullscreen) {
                // При открытом окне на фоне в режиме фуллскрин - окна камер показывай на всю высоту экрана по краям экрана, ширину окон бери от ширины экрана
                rectW = dispW * (widthPercent / 100f)
                rectH = dispH
                yPx = 0f
                xPx = if (cameraId == CAMERA_ID_RIGHT) {
                    dispW - rectW
                } else {
                    0f
                }
            } else if (isFullWidth) {
                // Если текущее приложение на экране открыто на всю ширину - то левую камеру запускать в окне прижатым к левому краю экрана (правое запускать как и в обычном режиме). Ширину окон бери от ширины экрана.
                val desktopRect = LauncherEmbeddedBoundsState.fullWidthBounds()
                    ?: LauncherEmbeddedBoundsState.embeddedBounds()
                    ?: LauncherEmbeddedBoundsState.contentRowBounds
                    ?: Rect(0, 51, 1920, 998)
                val desktopHeight = (desktopRect.bottom - desktopRect.top).toFloat().coerceAtLeast(100f)

                rectW = dispW * (widthPercent / 100f)
                rectH = desktopHeight
                yPx = desktopRect.top.toFloat()
                xPx = if (cameraId == CAMERA_ID_RIGHT) {
                    dispW - rectW
                } else {
                    0f
                }
            } else {
                // Обычный режим (рабочий стол с левой панелью ADAS или приложение в правой зоне)
                val desktopRect = LauncherEmbeddedBoundsState.embeddedBounds()
                    ?: LauncherEmbeddedBoundsState.contentRowBounds
                    ?: Rect(344, 51, 1920, 998)

                val desktopWidth = (desktopRect.right - desktopRect.left).toFloat().coerceAtLeast(100f)
                val desktopHeight = (desktopRect.bottom - desktopRect.top).toFloat().coerceAtLeast(100f)

                rectW = desktopWidth * (widthPercent / 100f)
                rectH = desktopHeight
                yPx = desktopRect.top.toFloat()
                xPx = if (cameraId == CAMERA_ID_RIGHT) {
                    desktopRect.right.toFloat() - rectW
                } else {
                    desktopRect.left.toFloat()
                }
            }

            x = xPx / dispW
            y = yPx / dispH
            w = rectW / dispW
            h = rectH / dispH
        } else {
            shapeCircle = LauncherAppConfigStore.camdSideCamerasShapeCircle(context)
            val rectCorners = LauncherAppConfigStore.camdSideCamerasRectCorners(context)
            effectiveShapeCircle = shapeCircle && !rectCorners

            presetName = when (cameraId) {
                CAMERA_ID_LEFT -> LauncherAppConfigStore.camdTurnSignalLeftPreset(context).ifBlank { null }
                CAMERA_ID_RIGHT -> LauncherAppConfigStore.camdTurnSignalRightPreset(context).ifBlank { null }
                else -> null
            }

            targetCameraId = if (!presetName.isNullOrBlank()) {
                val connectedPresets = (_status.value as? CamdConnectionStatus.Connected)?.presets
                connectedPresets?.firstOrNull { it.name == presetName }?.cameraId ?: cameraId
            } else {
                cameraId
            }

            if (effectiveShapeCircle || rectCorners) {
                val windowBounds = calculateSideCameraWindowBounds(
                    context = context,
                    isRightSide = (cameraId == CAMERA_ID_RIGHT),
                    shapeCircleOverride = shapeCircle,
                    rectCornersOverride = rectCorners,
                )

                if (windowBounds != null) {
                    x = windowBounds.xPx / dispW
                    y = windowBounds.yPx / dispH
                    w = windowBounds.widthPx / dispW
                    h = windowBounds.heightPx / dispH
                } else {
                    val adasRect = LauncherEmbeddedBoundsState.leftPanelBounds
                        ?: Rect(0, 51, 344, 998)

                    x = adasRect.left.toFloat() / dispW
                    y = adasRect.top.toFloat() / dispH
                    w = adasRect.width().toFloat() / dispW
                    h = adasRect.height().toFloat() / dispH
                }
            } else {
                val adasRect = LauncherEmbeddedBoundsState.leftPanelBounds
                    ?: Rect(0, 51, 344, 998)

                x = adasRect.left.toFloat() / dispW
                y = adasRect.top.toFloat() / dispH
                w = adasRect.width().toFloat() / dispW
                h = adasRect.height().toFloat() / dispH
            }
        }

        if (!x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite()) return null

        val isNumericPreset = presetName?.toIntOrNull() != null
        val cmd = if (!presetName.isNullOrBlank() && !isNumericPreset) {
            formatPresetCmd(presetName, x, y, w, h, shapeCircle = effectiveShapeCircle)
        } else {
            formatCmd(targetCameraId, x, y, w, h, shapeCircle = effectiveShapeCircle)
        }

        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "openTurnSignalSideCamera: cameraId=$cameraId isLowSpeed=$isLowSpeed presetName='$presetName' targetCameraId=$targetCameraId cmd='$cmd'"
        )

        val res = CamdSocketClient.sendCommandWithWakeup(context, cmd)
        val responseText = res.getOrNull().orEmpty()
        val isSuccess = res.isSuccess && CamdSocketClient.isSuccessResponse(responseText)
        vad.dashing.tbox.DclLogRecorder.log(
            "CamdCameraRepository",
            "openTurnSignalSideCamera: result success=$isSuccess resp='$responseText' err=${res.exceptionOrNull()?.message}"
        )
        if (CamdSocketClient.isErrorResponse(responseText)) {
            Log.w(TAG, "openTurnSignalSideCamera command failed: $responseText")
            // Fallback: if preset failed with ERR unknown_preset, retry with raw camera
            if (!presetName.isNullOrBlank()) {
                val fallbackCmd = formatCmd(cameraId, x, y, w, h, shapeCircle = effectiveShapeCircle)
                vad.dashing.tbox.DclLogRecorder.log(
                    "CamdCameraRepository",
                    "openTurnSignalSideCamera: preset failed, retrying fallback raw cameraId=$cameraId fallbackCmd='$fallbackCmd'"
                )
                val fallbackRes = CamdSocketClient.sendCommand(fallbackCmd)
                vad.dashing.tbox.DclLogRecorder.log(
                    "CamdCameraRepository",
                    "openTurnSignalSideCamera: fallback result resp='${fallbackRes.getOrNull()}' err=${fallbackRes.exceptionOrNull()?.message}"
                )
                return cameraId
            }
        }
        return targetCameraId
    }

    /**
     * Pure math function to calculate pixel bounds for circular or rectangular side camera windows.
     */
    fun computeSideCameraWindowBounds(
        desktopLeft: Float,
        desktopTop: Float,
        desktopRight: Float,
        desktopBottom: Float,
        density: Float,
        isRightSide: Boolean,
        shapeCircle: Boolean,
        rectCorners: Boolean,
        sizeAdjustment: Int = 0,
    ): SideCameraWindowBounds? {
        if (!shapeCircle && !rectCorners) return null
        val isCircle = shapeCircle && !rectCorners

        val desktopHeight = (desktopBottom - desktopTop).coerceAtLeast(100f)
        val baseDiameter = desktopHeight / 3f
        val margin = 30f * density

        val yPx = desktopTop + margin
        val rectW: Float
        val rectH: Float

        if (rectCorners) {
            rectW = baseDiameter * 1.30f * 1.40f
            rectH = rectW * (3f / 4f)
        } else {
            val circleDiameter = baseDiameter * 1.30f * 1.30f
            rectW = circleDiameter
            rectH = circleDiameter
        }

        val scaleMultiplier = (1f + sizeAdjustment / 100f).coerceIn(0.5f, 2.0f)
        val adjustedW = rectW * scaleMultiplier
        val adjustedH = rectH * scaleMultiplier

        val xPx = if (isRightSide) {
            desktopRight - margin - adjustedW
        } else {
            desktopLeft + margin
        }

        return SideCameraWindowBounds(
            xPx = xPx,
            yPx = yPx,
            widthPx = adjustedW,
            heightPx = adjustedH,
            isCircle = isCircle,
        )
    }

    /**
     * Calculates pixel bounds of the side camera window using current display metrics and config.
     */
    fun calculateSideCameraWindowBounds(
        context: Context,
        isRightSide: Boolean,
        customDesktopRect: Rect? = null,
        shapeCircleOverride: Boolean? = null,
        rectCornersOverride: Boolean? = null,
        sizeAdjustmentOverride: Int? = null,
    ): SideCameraWindowBounds? {
        val shapeCircle = shapeCircleOverride ?: LauncherAppConfigStore.camdSideCamerasShapeCircle(context)
        val rectCorners = rectCornersOverride ?: LauncherAppConfigStore.camdSideCamerasRectCorners(context)
        if (!shapeCircle && !rectCorners) return null

        val desktopRect = customDesktopRect
            ?: LauncherEmbeddedBoundsState.embeddedBounds()
            ?: LauncherEmbeddedBoundsState.contentRowBounds
            ?: Rect(344, 51, 1920, 998)

        val density = context.resources.displayMetrics.density
        val sizeAdjustment = sizeAdjustmentOverride ?: LauncherAppConfigStore.camdSideCamerasSizeAdjustment(context)

        return computeSideCameraWindowBounds(
            desktopLeft = desktopRect.left.toFloat(),
            desktopTop = desktopRect.top.toFloat(),
            desktopRight = desktopRect.right.toFloat(),
            desktopBottom = desktopRect.bottom.toFloat(),
            density = density,
            isRightSide = isRightSide,
            shapeCircle = shapeCircle,
            rectCorners = rectCorners,
            sizeAdjustment = sizeAdjustment,
        )
    }

    /**
     * Whether BSD ALERT warnings should blink the camera window outline instead of screen edge stripes.
     * True if Camd is installed, turn signal cameras are enabled, and cameras are in circles or rectangles.
     */
    fun shouldBlinkCameraWindowBorder(
        isCamdInstalled: Boolean,
        turnSignalCameras: Boolean,
        shapeCircle: Boolean,
        rectCorners: Boolean,
    ): Boolean {
        return isCamdInstalled && turnSignalCameras && (shapeCircle || rectCorners)
    }

    /**
     * Formats OPEN command according to DCL_CAMD_CAMERA_API_FOR_VENDOR_RU §3.1:
     * OPEN <id> <x> <y> <w> <h> [rotation [flipH [flipV]]] [crop=cx,cy,cw,ch] [shape=circle]
     * - id: int (0, 1, 3, 4, 5, 8, 9)
     * - x, y, w, h: float 0..1, min side 0.05, x+w <= 1.02, y+h <= 1.02
     * - rotation: 0, 90, 180, 270 (clockwise, default 0)
     * - flipH: 0 or 1 (horizontal mirror after rotation, default 0)
     * - flipV: 0 or 1 (vertical mirror, default 0)
     * - crop=cx,cy,cw,ch: optional named token (min side 0.05, cx+cw <= 1.02)
     * - shape=circle: optional named token for circular window
     * Note: Per-camera fisheye correction and keystone tilt are handled internally by camd shader.
     */
    fun formatCmd(
        id: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rotation: Int = getCameraRotation(id),
        flipH: Int = getCameraFlipH(id),
        flipV: Int = getCameraFlipV(id),
        crop: CamdCrop? = null,
        shapeCircle: Boolean = false,
    ): String {
        val bounds = CamdWindowBounds(x, y, w, h).normalized()
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "OPEN %d %.4f %.4f %.4f %.4f", id, bounds.x, bounds.y, bounds.w, bounds.h))

        val normRotation = ((rotation % 360) + 360) % 360
        val hasFlip = (flipH != 0 || flipV != 0)
        if (hasFlip) {
            // Per spec §3.1: if flip is used, rotation token is MANDATORY
            sb.append(String.format(Locale.US, " %d %d %d", normRotation, flipH, flipV))
        } else if (normRotation != 0) {
            sb.append(String.format(Locale.US, " %d", normRotation))
        }

        if (crop != null) {
            val normCrop = crop.normalized()
            sb.append(String.format(Locale.US, " crop=%.4f,%.4f,%.4f,%.4f", normCrop.cx, normCrop.cy, normCrop.cw, normCrop.ch))
        }

        if (shapeCircle) {
            sb.append(" shape=circle")
        }

        return sb.toString()
    }

    /**
     * Formats preset OPEN command according to DCL_CAMD_CAMERA_API_FOR_VENDOR_RU §3.1:
     * OPEN preset=<name> <x> <y> <w> <h> [shape=circle]
     * Positional tokens: exactly 4 coordinates (x, y, w, h).
     * Cannot mix preset= with rotation/flip/crop=.
     */
    fun formatPresetCmd(
        presetName: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        shapeCircle: Boolean = false,
    ): String {
        val bounds = CamdWindowBounds(x, y, w, h).normalized()
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "OPEN preset=%s %.4f %.4f %.4f %.4f", presetName.trim(), bounds.x, bounds.y, bounds.w, bounds.h))
        if (shapeCircle) {
            sb.append(" shape=circle")
        }
        return sb.toString()
    }

    fun getCameraRotation(cameraId: Int): Int = 0

    fun getCameraFlipH(cameraId: Int): Int = 0

    fun getCameraFlipV(cameraId: Int): Int = 0
}
