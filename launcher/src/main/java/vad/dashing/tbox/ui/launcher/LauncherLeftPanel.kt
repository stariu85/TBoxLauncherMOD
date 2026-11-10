package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import kotlin.math.abs

internal fun isDriveViewActive(
    speedKmh: Float,
    pasObstacle: Boolean = false,
    hasBodyOpen: Boolean = false,
    isParkGear: Boolean = false,
    isDriveGear: Boolean = false,
    accActive: Boolean = false,
    racing: Boolean = false,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    currentSmartDriveView: Boolean = false,
): Boolean {
    if (isParkGear || hasBodyOpen) return false
    if (racing || accActive) return true
    return when (cameraSwitchMode) {
        CAMERA_SWITCH_MODE_DRIVE_AND_PARK -> isDriveGear
        else -> if (pasObstacle) false else currentSmartDriveView
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherLeftPanel(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    onOpenVehicleSettings: () -> Unit,
    modelRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    paintRevision: Int = 0,
    onPaintChanged: (String) -> Unit = {},
    onCarBoundsChanged: (Rect) -> Unit = {},
    colorPickerVisible: Boolean = false,
    roadVisible: Boolean = true,
    carHidden: Boolean = false,
    settingsTransitionProgress: Float = 0f,
    settingsUserYawDeg: Float = 0f,
    @Suppress("UNUSED_PARAMETER") onColorPickerOpen: () -> Unit = {},
    onColorPickerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val modelScaleRevision by LauncherAppConfigStore.carModelScaleRevisionFlow.collectAsStateWithLifecycle()
    val carModelScale = remember(context, modelScaleRevision) {
        LauncherAppConfigStore.carModelScale(context)
    }
    val modelDriveScaleRevision by LauncherAppConfigStore.carModelDriveScaleRevisionFlow.collectAsStateWithLifecycle()
    val carModelDriveScale = remember(context, modelDriveScaleRevision) {
        LauncherAppConfigStore.carModelDriveScale(context)
    }
    val speedLimitPositionRevision by LauncherAppConfigStore.speedLimitPositionRevisionFlow
        .collectAsStateWithLifecycle()
    var speedLimitXRatio by remember {
        mutableFloatStateOf(LauncherAppConfigStore.speedLimitXRatio(context))
    }
    var speedLimitYRatio by remember {
        mutableFloatStateOf(LauncherAppConfigStore.speedLimitYRatio(context))
    }
    LaunchedEffect(speedLimitPositionRevision) {
        speedLimitXRatio = LauncherAppConfigStore.speedLimitXRatio(context)
        speedLimitYRatio = LauncherAppConfigStore.speedLimitYRatio(context)
    }
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tires by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelsTemperature by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()
    val tirePressureConfigRevision by LauncherAppConfigStore.tirePressureConfigRevisionFlow.collectAsStateWithLifecycle()
    val tirePressureOnlyLow = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureOnlyLow(context)
    }
    val tirePressureOnlyTopView = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureOnlyTopView(context)
    }
    val tirePressureLowThreshold = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureLowThreshold(context)
    }
    val tirePressureInertiaEnabled = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureInertiaAnimationEnabled(context)
    }
    val tirePressureBadgeShape = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureBadgeShape(context)
    }
    val tirePressureShowHundredths = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureShowHundredths(context)
    }
    val tirePressureShowTemperature = remember(context, tirePressureConfigRevision) {
        LauncherAppConfigStore.tirePressureShowTemperature(context)
    }

    LaunchedEffect(context) {
        LauncherTireRepository.init(context)
    }
    val motion = rememberLauncherVehicleMotion(tboxConnected, canViewModel)

    val racing = LauncherEggRace.active
    val raceSprites = rememberRaceOncomingSprites()
    val raceLane = LauncherEggRace.playerLane
    val raceCars = LauncherEggRace.cars
    LauncherEggRaceTicker()
    val carLaneShift = if (racing) eggRaceCarShiftDp(raceLane) else 0.dp

    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val effectiveSpeed = if (racing) LauncherEggRace.speedKmh else motion.speedKmh
    val steerPreview = motion.steerPreviewActive

    val effectiveBody = if (simulateEnabled) {
        LauncherDevVehicleState.bodyState()
    } else {
        vehicleBody
    }
    val mergedTires = remember(tires, wheelsPressure, wheelsTemperature, context) {
        val merged = mergeTireStateWithCanData(tires, wheelsPressure, wheelsTemperature)
        if (merged.hasAvailableData) {
            LauncherAppConfigStore.saveTirePressures(context, merged)
        }
        merged
    }
    // Simulation overrides (hidden settings tab) take precedence over live TPMS.
    val effectiveTires = LauncherDevVehicleState.tireStateOrNull() ?: mergedTires
    // Same for ADAS: cruise/BSD/PDC sim replaces the live mbCAN state.
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
    val headlightBeams = rememberHeadlightBeams()
    LauncherAdasRepository.updateMotion(effectiveSpeed)

    val rigState = LauncherCarRigState(
        doorFlOpen = effectiveBody.doorFlOpen,
        doorFrOpen = effectiveBody.doorFrOpen,
        doorRlOpen = effectiveBody.doorRlOpen,
        doorRrOpen = effectiveBody.doorRrOpen,
        tailgateOpen = effectiveBody.tailgateOpen,
        speedKmh = effectiveSpeed,
        steeringDeg = 0f,
    )
    var bodyRigAvailable by remember { mutableStateOf(false) }
    var wheelAnchors by remember {
        mutableStateOf<Map<LauncherWheelCorner, Offset>>(emptyMap())
    }
    var pdcRings by remember { mutableStateOf<LauncherPdcRingFrame?>(null) }
    var headlightFrame by remember { mutableStateOf<LauncherHeadlightFrame?>(null) }

    // Simulation gear override (hidden settings tab) takes precedence over live gearbox.
    val parsedGear = LauncherDevVehicleState.gearSlotOverride
        ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    var latchedGear by remember { mutableStateOf<Char?>(null) }
    SideEffect {
        if (parsedGear != null) latchedGear = parsedGear
    }
    val activeGear = parsedGear ?: latchedGear
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val miniPlayerRevision by LauncherAppConfigStore.mediaMiniPlayerRevisionFlow
        .collectAsStateWithLifecycle()
    val miniPlayerVisible = remember(context, miniPlayerRevision) {
        LauncherAppConfigStore.mediaMiniPlayerVisible(context)
    }


    val driveModeBarRevision by LauncherAppConfigStore.driveModeBarRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeBarVisible = remember(context, driveModeBarRevision) {
        LauncherAppConfigStore.driveModeBarVisible(context)
    }
    val assistPanelRevision by LauncherAppConfigStore.assistPanelRevisionFlow
        .collectAsStateWithLifecycle()
    val assistPanelVisible = remember(context, assistPanelRevision) {
        LauncherAppConfigStore.assistPanelVisible(context)
    }
    val bodyControlsRevision by LauncherAppConfigStore.bodyControlsPanelRevisionFlow
        .collectAsStateWithLifecycle()
    val bodyControlsPanelVisible = remember(context, bodyControlsRevision) {
        LauncherAppConfigStore.bodyControlsPanelVisible(context)
    }
    val carColorRevision by LauncherAppConfigStore.carColorPanelRevisionFlow
        .collectAsStateWithLifecycle()
    val carColorPanelVisible = remember(context, carColorRevision) {
        LauncherAppConfigStore.carColorPanelVisible(context)
    }
    val navSpeedRevision by LauncherAppConfigStore.navSpeedRevisionFlow
        .collectAsStateWithLifecycle()
    val navSpeedVisible = remember(context, navSpeedRevision) {
        LauncherAppConfigStore.navSpeedVisible(context)
    }
    val navTrafficLightRevision by LauncherAppConfigStore.navTrafficLightRevisionFlow
        .collectAsStateWithLifecycle()
    val navTrafficLightVisible = remember(context, navTrafficLightRevision) {
        LauncherAppConfigStore.navTrafficLightVisible(context)
    }
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()

    val navSpeedPositionRevision by LauncherAppConfigStore.navSpeedPositionRevisionFlow.collectAsStateWithLifecycle()
    var navSpeedXRatio by remember { mutableFloatStateOf(LauncherAppConfigStore.navSpeedXRatio(context)) }
    var navSpeedYRatio by remember { mutableFloatStateOf(LauncherAppConfigStore.navSpeedYRatio(context)) }
    LaunchedEffect(navSpeedPositionRevision) {
        navSpeedXRatio = LauncherAppConfigStore.navSpeedXRatio(context)
        navSpeedYRatio = LauncherAppConfigStore.navSpeedYRatio(context)
    }

    val navTrafficLightPositionRevision by LauncherAppConfigStore.navTrafficLightPositionRevisionFlow.collectAsStateWithLifecycle()
    var navTrafficLightXRatio by remember { mutableFloatStateOf(LauncherAppConfigStore.navTrafficLightXRatio(context)) }
    var navTrafficLightYRatio by remember { mutableFloatStateOf(LauncherAppConfigStore.navTrafficLightYRatio(context)) }
    LaunchedEffect(navTrafficLightPositionRevision) {
        navTrafficLightXRatio = LauncherAppConfigStore.navTrafficLightXRatio(context)
        navTrafficLightYRatio = LauncherAppConfigStore.navTrafficLightYRatio(context)
    }
    val smartPanelRevision by LauncherAppConfigStore.smartPanelRevisionFlow
        .collectAsStateWithLifecycle()
    val smartPanelEnabled = remember(context, smartPanelRevision) {
        LauncherAppConfigStore.smartPanelEnabled(context)
    }
    val adasPanelsRevision by LauncherAppConfigStore.adasPanelsRevisionFlow
        .collectAsStateWithLifecycle()
    val adasTopPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasTopPanels(context)
    }
    val adasBottomPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasBottomPanels(context)
    }
    var leftPanelHeightPx by remember { mutableFloatStateOf(0f) }
    var leftPanelWindowTopPx by remember { mutableFloatStateOf(0f) }
    val driveModeGlowRevision by LauncherAppConfigStore.driveModeGlowRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeGlowVisible = remember(context, driveModeGlowRevision) {
        LauncherAppConfigStore.driveModeGlowVisible(context)
    }
    val launcherSettingsOnDoubleTapRevision by LauncherAppConfigStore.launcherSettingsOnDoubleTapRevisionFlow
        .collectAsStateWithLifecycle()
    val launcherSettingsOnDoubleTap = remember(context, launcherSettingsOnDoubleTapRevision) {
        LauncherAppConfigStore.launcherSettingsOnDoubleTap(context)
    }
    val isDarkTheme = LauncherThemeState.darkTheme

    val baseLeftPanelBg = LauncherColors.LeftPanelBg
    val solarDaytime = rememberSolarDaytimeState()
    val isNight = if (solarDaytime != null) !solarDaytime else {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        hour < 6 || hour >= 21
    }

    val headlightBeamsRevision by LauncherAppConfigStore.headlightBeamsRevisionFlow
        .collectAsStateWithLifecycle()
    val headlightBeamsMode = remember(context, headlightBeamsRevision) {
        LauncherAppConfigStore.headlightBeamsMode(context)
    }

    val projectHeadlightsEnabled = when (headlightBeamsMode) {
        HEADLIGHT_BEAMS_OFF -> false
        HEADLIGHT_BEAMS_DARK_THEME -> isDarkTheme
        HEADLIGHT_BEAMS_NIGHT_ONLY -> isNight
        else -> false
    }
    val isAvmActive by LauncherAvmRepository.isAvmActive.collectAsStateWithLifecycle()
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val pasOn = (parkingRadar is MbCanBinaryState.On) || adas.pdcActive || adas.pdc.hasAny || (simulateEnabled && LauncherDevVehicleState.showAllIndicators)
    val pasObstacle = adas.pdc.hasAny
    val accActive = adas.accActive ||
        (adas.frontObject.valid && (adas.fcwActive || adas.distanceWarning || adas.aebHint))
    val cameraSwitchModeRevision by LauncherAppConfigStore.cameraSwitchModeRevisionFlow
        .collectAsStateWithLifecycle()
    val cameraSwitchMode = remember(context, cameraSwitchModeRevision) {
        LauncherAppConfigStore.cameraSwitchMode(context)
    }
    val cameraAnimRevision by LauncherAppConfigStore.cameraAnimationRevisionFlow
        .collectAsStateWithLifecycle()
    val cameraAnimEnabled = remember(context, cameraAnimRevision) {
        LauncherAppConfigStore.cameraAnimationEnabled(context)
    }
    val trunkLongPressRevision by LauncherAppConfigStore.trunkLongPressRevisionFlow
        .collectAsStateWithLifecycle()
    val trunkLongPressEnabled = remember(context, trunkLongPressRevision) {
        LauncherAppConfigStore.trunkLongPressEnabled(context)
    }
    var smartDriveViewActive by remember { mutableStateOf(false) }
    var trunkParkWarningUntilMs by remember { mutableLongStateOf(0L) }
    val hasBodyOpen = effectiveBody.anyDoorOpen()

    val launcherBootMs = remember { SystemClock.elapsedRealtime() }
    var nowViewMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(100L)
            nowViewMs = SystemClock.elapsedRealtime()
        }
    }

    val isStartupGracePeriod = cameraAnimEnabled && (nowViewMs - launcherBootMs < 5000L)

    LaunchedEffect(effectiveSpeed, pasObstacle, hasBodyOpen, activeGear, accActive, racing, cameraSwitchMode, isStartupGracePeriod) {
        if (isStartupGracePeriod || activeGear == 'P' || pasObstacle || hasBodyOpen) {
            smartDriveViewActive = false
        } else if (racing || accActive || effectiveSpeed >= 20f) {
            smartDriveViewActive = true
        }
    }

    val inDriveGear = racing || activeGear == 'D' || effectiveSpeed > 0.5f
    val rawDriveView = if (isStartupGracePeriod) {
        false
    } else {
        isDriveViewActive(
            speedKmh = effectiveSpeed,
            pasObstacle = pasObstacle,
            hasBodyOpen = hasBodyOpen,
            isParkGear = activeGear == 'P',
            isDriveGear = inDriveGear,
            accActive = accActive,
            racing = racing,
            cameraSwitchMode = cameraSwitchMode,
            currentSmartDriveView = smartDriveViewActive,
        )
    }

    // Hold drive view for up to 5000ms (5s) to prevent camera view jumping when stopping or CAN speed/gear drops
    var showDriveView by remember { mutableStateOf(false) }
    var lastDriveViewTrueMs by remember { mutableLongStateOf(0L) }

    if (isStartupGracePeriod) {
        showDriveView = false
        lastDriveViewTrueMs = 0L
    } else if (rawDriveView) {
        showDriveView = true
        lastDriveViewTrueMs = nowViewMs
    } else if (activeGear == 'P' || hasBodyOpen) {
        showDriveView = false
        lastDriveViewTrueMs = 0L
    } else {
        val elapsedView = nowViewMs - lastDriveViewTrueMs
        showDriveView = elapsedView < 5000L
    }

    // Drive mode glow highlighting
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()

    val resolvedDriveMode = remember(driveModeRaw, driveModeWetRaw, gearBoxDriveMode) {
        resolveDriveModeDisplayLabel(
            driveModeRaw = driveModeRaw,
            driveModeWetRaw = driveModeWetRaw,
            gearBoxDriveMode = gearBoxDriveMode,
        ).uppercase()
    }

    var driveModePending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resolvedDriveMode) {
        driveModePending = null
    }
    val activeDriveMode = driveModePending ?: resolvedDriveMode

    val isEco = activeDriveMode == "ECO"
    val isNor = activeDriveMode == "NOR" || activeDriveMode == "NORMAL" || activeDriveMode == "COMFORT"
    val isSpt = activeDriveMode == "SPT" || activeDriveMode == "SPORT"
    val isSnow = activeDriveMode == "SNOW" || activeDriveMode == "СНЕГ"
    val isMud = activeDriveMode == "MUD" || activeDriveMode == "ГРЯЗЬ"
    val isSand = activeDriveMode == "SAND" || activeDriveMode == "ПЕСОК"

    val isDrivingState = (inDriveGear || effectiveSpeed > 0.5f) && activeGear != 'P'

    val baseGlowColor = when {
        isEco -> Color(0xFF16A34A) // Green
        isNor -> Color(0xFF3B82F6) // Blue
        isSpt -> Color(0xFFEF4444) // Red
        isSnow -> Color(0xFF38BDF8) // Bright blue
        isMud -> Color(0xFFF97316) // Orange
        isSand -> Color(0xFFEAB308) // Yellow
        else -> Color.Transparent
    }

    val targetGlowColor = if (isDrivingState && driveModeGlowVisible) baseGlowColor else baseGlowColor.copy(alpha = 0f)

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "driveModeGlowColor",
    )

    val carOffsetDriveY by animateDpAsState(
        targetValue = if (showDriveView) (-40).dp else 0.dp,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "carOffsetDriveY",
    )

    val alertsDriveViewOffsetY by animateDpAsState(
        targetValue = if (showDriveView) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "alertsDriveViewOffsetY",
    )

    DisposableEffect(Unit) {
        onDispose {
            LauncherEmbeddedBoundsState.leftPanelBounds = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                leftPanelHeightPx = coordinates.size.height.toFloat()
                val rect = coordinates.boundsInWindow()
                leftPanelWindowTopPx = rect.top
                val boundsRect = android.graphics.Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
                LauncherEmbeddedBoundsState.leftPanelBounds = boundsRect
                vad.dashing.tbox.DclLogRecorder.log("LeftPanel", "onGloballyPositioned leftPanelBounds=$boundsRect carModelScale=$carModelScale")
                onCarBoundsChanged(rect)
            }
            .background(baseLeftPanelBg),
    ) {
        if (roadVisible) {
            LauncherVirtualRoad(
                speedKmh = if (racing) effectiveSpeed * 0.45f else effectiveSpeed,
                steerAngleDeg = if (racing) 0f else motion.steerAngleDeg,
                adas = if (racing) LauncherAdasState() else adas,
                steerPreview = steerPreview,
                inDriveGear = showDriveView,
                isSnow = isSnow,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (animatedGlowColor.alpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.30f)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                animatedGlowColor.copy(alpha = animatedGlowColor.alpha * 0.35f),
                                animatedGlowColor.copy(alpha = animatedGlowColor.alpha * 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        val driveModeBarContent: @Composable () -> Unit = {
            if (driveModeBarVisible) {
                LauncherDriveModeBar(
                    canViewModel = canViewModel,
                    onModeSelected = { pending -> driveModePending = pending },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val isPanelVisible: (String) -> Boolean = { panelId ->
            when (panelId) {
                "drive_mode" -> driveModeBarVisible
                "assist", "presets" -> assistPanelVisible
                "body_controls" -> bodyControlsPanelVisible
                "media_player" -> miniPlayerVisible
                "car_color" -> carColorPanelVisible
                "smart_panel" -> smartPanelEnabled
                "lanes" -> true
                else -> true
            }
        }

        val activeTopPanels = remember(adasTopPanels, driveModeBarVisible, assistPanelVisible, bodyControlsPanelVisible, miniPlayerVisible, carColorPanelVisible, smartPanelEnabled, navSpeedVisible, navTrafficLightVisible) {
            val filtered = adasTopPanels.filter { isPanelVisible(it) }
            val deduplicated = mutableListOf<String>()
            var assistOrPresetsAdded = false
            for (p in filtered) {
                if (p == "assist" || p == "presets") {
                    if (!assistOrPresetsAdded) {
                        deduplicated.add("assist")
                        assistOrPresetsAdded = true
                    }
                } else {
                    deduplicated.add(p)
                }
            }
            deduplicated
        }
        val activeBottomPanels = remember(adasBottomPanels, activeTopPanels, driveModeBarVisible, assistPanelVisible, bodyControlsPanelVisible, miniPlayerVisible, carColorPanelVisible, smartPanelEnabled, navSpeedVisible, navTrafficLightVisible) {
            val filtered = adasBottomPanels.filter { isPanelVisible(it) && it !in activeTopPanels }.toMutableList()
            if (smartPanelEnabled && "smart_panel" !in activeTopPanels && "smart_panel" !in filtered) {
                filtered.add(0, "smart_panel")
            }
            val deduplicated = mutableListOf<String>()
            var assistOrPresetsAdded = "assist" in activeTopPanels
            for (p in filtered) {
                if (p == "assist" || p == "presets") {
                    if (!assistOrPresetsAdded) {
                        deduplicated.add("assist")
                        assistOrPresetsAdded = true
                    }
                } else {
                    deduplicated.add(p)
                }
            }
            deduplicated
        }

        val renderPanel: @Composable (String) -> Unit = { panelId ->
            RenderSwipeGroupedPanel(panelId = panelId) { actualPanelId ->
                when (actualPanelId) {
                    "drive_mode" -> driveModeBarContent()
                    "assist", "presets" -> {
                        if (assistPanelVisible) {
                            LauncherAssistAndPresetsPanel(
                                canViewModel = canViewModel,
                                adas = adas,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    "lanes" -> {
                        LauncherLanesControlBar(
                            adas = adas,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "radars" -> {
                        LauncherRadarControlBar(
                            adas = adas,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "chassis" -> {
                        LauncherChassisControlBar(
                            adas = adas,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "lights" -> {
                        LauncherLightsControlBar(
                            adas = adas,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "wipers" -> {
                        LauncherWiperControlBar(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "car_color" -> {
                        LauncherCarColorPickerBar(
                            modifier = Modifier.fillMaxWidth(),
                            heightDp = 60.dp,
                        )
                    }
                    "smart_panel" -> {
                        LauncherSmartPanel(
                            activeGear = activeGear,
                            canViewModel = canViewModel,
                            adas = adas,
                            onShowTrunkParkWarning = {
                                trunkParkWarningUntilMs = SystemClock.uptimeMillis() + 3000L
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "body_controls" -> {
                        LauncherVehicleBodyControlsBar(
                            activeGear = activeGear,
                            onShowTrunkParkWarning = {
                                trunkParkWarningUntilMs = SystemClock.uptimeMillis() + 3000L
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "media_player" -> {
                        if (miniPlayerVisible) {
                            LauncherMediaMiniPlayer(
                                modifier = Modifier.fillMaxWidth(),
                                sourceId = "launcher_mini_player_main",
                            )
                        }
                    }

                }
            }
        }

        // --- FIXED 3D CAR MODEL LAYER ---
        // Uses fillMaxSize with fixed padding so the 3D car position never shifts
        // when top/bottom overlay panels are hidden, shown, or moved.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = carOffsetDriveY.toPx() }
                .padding(top = 52.dp, bottom = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isQuickSettingsOpen by LauncherAdasQuickSettingsWindow.isShowingFlow.collectAsStateWithLifecycle()
            val settingsProgress = settingsTransitionProgress.coerceIn(0f, 1f)
            val isVehicleSettingsOpen = LauncherVehicleSettingsUiState.open || settingsProgress > 0.001f
            val isAboutSettingsOpen = LauncherAboutOverlayWindow.isShowing()
            val carHidden = isAvmActive || isVehicleSettingsOpen || isAboutSettingsOpen || isQuickSettingsOpen
            val showCarInComposition = cameraAnimEnabled || !carHidden
            if (showCarInComposition) {
                LauncherCar3DModel(
                    rigState = rigState,
                    speedKmh = effectiveSpeed,
                    steeringDeg = 0f,
                    steerPreview = steerPreview,
                    inDriveGear = showDriveView,
                    modelRevision = modelRevision,
                    paintRevision = paintRevision,
                    paintId = paintId,
                    showRoad = false,
                    isAvmActive = isAvmActive,
                    carHidden = carHidden,
                    settingsView = settingsProgress > 0.02f,
                    settingsProgress = settingsProgress,
                    settingsUserYawDeg = settingsUserYawDeg,
                    customModelScale = carModelScale,
                    customModelDriveScale = carModelDriveScale,
                    cameraSwitchMode = cameraSwitchMode,
                    onClick = if (!racing) {
                        if (launcherSettingsOnDoubleTap) {
                            { LauncherAdasQuickSettingsWindow.toggle(context, canViewModel, tboxViewModel); Unit }
                        } else {
                            onOpenVehicleSettings
                        }
                    } else null,
                    onDoubleClick = if (!racing) {
                        if (launcherSettingsOnDoubleTap) {
                            onOpenVehicleSettings
                        } else {
                            { LauncherAdasQuickSettingsWindow.toggle(context, canViewModel, tboxViewModel); Unit }
                        }
                    } else null,
                    onLongClick = null,
                    onTrunkClick = if (!racing) onOpenVehicleSettings else null,
                    onTrunkLongClick = if (!racing && trunkLongPressEnabled) {
                        {
                            if (activeGear == 'P') {
                                sendOpenCloseTrunk(context)
                            } else {
                                trunkParkWarningUntilMs = SystemClock.uptimeMillis() + 3000L
                            }
                        }
                    } else null,
                    onWheelAnchorsChanged = { wheelAnchors = it },
                    onPdcRingsChanged = { pdcRings = it },
                    onHeadlightFrameChanged = { headlightFrame = it },
                    onBodyRigAvailabilityChanged = { bodyRigAvailable = it },
                    projectPdcRings = !racing,
                    projectHeadlights = !racing && projectHeadlightsEnabled,
                    textureSurface = racing,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (racing) with(density) { carLaneShift.toPx() } else 0f
                            alpha = if (carHidden) 0f else 1f
                        },
                )
            }
            val showTireBadgesAdas = !tirePressureOnlyTopView || !showDriveView
            if (!carHidden && !racing && showTireBadgesAdas) {
                LauncherTireBadges(
                    state = effectiveTires,
                    // ADAS strip shows a small pressure-only pill next to the wheel.
                    compact = true,
                    wheelAnchorsPx = wheelAnchors,
                    showAll = !tirePressureOnlyLow,
                    lowPressureThreshold = tirePressureLowThreshold,
                    speedKmh = effectiveSpeed,
                    steerAngleDeg = 0f,
                    isRearView = showDriveView,
                    inertiaEnabled = tirePressureInertiaEnabled,
                    badgeShape = tirePressureBadgeShape,
                    showHundredths = tirePressureShowHundredths,
                    showTemperature = tirePressureShowTemperature,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!carHidden && !racing) {
                    if (!bodyRigAvailable) {
                        LauncherDoorBadges(
                            body = effectiveBody,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    LauncherRearThreatOverlay(
                        threats = adas.rearThreats,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LauncherPdcOverlay(
                        pdc = adas.pdc,
                        rings = pdcRings,
                        driving = inDriveGear || steerPreview,
                        pasOn = pasOn,
                        topView = !showDriveView,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LauncherBsdOverlay(
                        threats = adas.rearThreats,
                        rings = pdcRings,
                        driving = inDriveGear || steerPreview,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LauncherHeadlightOverlay(
                        beams = headlightBeams,
                        hma = adas.hma,
                        frame = headlightFrame,
                        modifier = Modifier.fillMaxSize(),
                    )
            }

            val classicSpeedLimitRevision by LauncherAppConfigStore.classicSpeedLimitRevisionFlow.collectAsStateWithLifecycle()
            val classicSpeedLimitVisible = remember(context, classicSpeedLimitRevision) {
                LauncherAppConfigStore.classicSpeedLimitVisible(context)
            }
            val tsrSystemRevision by LauncherAppConfigStore.tsrSystemRevisionFlow.collectAsStateWithLifecycle()
            val tsrSystemEnabled = remember(context, tsrSystemRevision) {
                LauncherAppConfigStore.tsrSystemEnabled(context)
            }

            // Overlay layer for 3D speed limit sign floating over the 3D car area
            if (!racing) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val signSizePx = with(density) { 48.dp.toPx() }
                    val containerWidthPx = with(density) { maxWidth.toPx() }
                    val containerHeightPx = with(density) { maxHeight.toPx() }

                    if (showDriveView) {
                        if (classicSpeedLimitVisible && tsrSystemEnabled) {
                            val centerX = containerWidthPx * speedLimitXRatio
                            val centerY = containerHeightPx * speedLimitYRatio

                            val leftPx = (centerX - signSizePx / 2f).coerceIn(0f, (containerWidthPx - signSizePx).coerceAtLeast(0f))
                            val topPx = (centerY - signSizePx / 2f).coerceIn(0f, (containerHeightPx - signSizePx).coerceAtLeast(0f))

                            val leftDp = with(density) { leftPx.toDp() }
                            val topDp = with(density) { topPx.toDp() }

                            LauncherSpeedLimitOverlay(
                                adas = adas,
                                onDrag = { delta ->
                                    if (containerWidthPx > 0f && containerHeightPx > 0f) {
                                        val currentCenterX = containerWidthPx * speedLimitXRatio
                                        val currentCenterY = containerHeightPx * speedLimitYRatio
                                        val newCenterX = (currentCenterX + delta.x).coerceIn(signSizePx / 2f, containerWidthPx - signSizePx / 2f)
                                        val newCenterY = (currentCenterY + delta.y).coerceIn(signSizePx / 2f, containerHeightPx - signSizePx / 2f)
                                        speedLimitXRatio = newCenterX / containerWidthPx
                                        speedLimitYRatio = newCenterY / containerHeightPx
                                    }
                                },
                                onDragEnd = {
                                    LauncherAppConfigStore.setSpeedLimitPosition(context, speedLimitXRatio, speedLimitYRatio)
                                },
                                modifier = Modifier
                                    .offset(x = leftDp, y = topDp)
                                    .zIndex(160f),
                            )
                        }

                        // Freely Movable Navigation Overlay 3: Current Speed & Speed Limit
                        if (navSpeedVisible) {
                            val yandexLimit = navState.speedLimit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
                            val cameraLimitRaw = (adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid })?.toString()
                            val cameraLimit = if (tsrSystemEnabled && !classicSpeedLimitVisible && yandexLimit == null) cameraLimitRaw else null
                            val effectiveSpeedLimit = yandexLimit ?: cameraLimit
                            val spWidthDp = if (effectiveSpeedLimit != null) 114.dp else 60.dp
                            val spHeightDp = 60.dp
                            val spWidthPx = with(density) { spWidthDp.toPx() }
                            val spHeightPx = with(density) { spHeightDp.toPx() }
                            val spCenterX = containerWidthPx * navSpeedXRatio
                            val spCenterY = containerHeightPx * navSpeedYRatio
                            val spLeftPx = (spCenterX - spWidthPx / 2f).coerceIn(0f, (containerWidthPx - spWidthPx).coerceAtLeast(0f))
                            val spTopPx = (spCenterY - spHeightPx / 2f).coerceIn(0f, (containerHeightPx - spHeightPx).coerceAtLeast(0f))

                            LauncherNavSpeedOverlay(
                                adas = adas,
                                onDrag = { delta ->
                                    if (containerWidthPx > 0f && containerHeightPx > 0f) {
                                        val currentX = containerWidthPx * navSpeedXRatio
                                        val currentY = containerHeightPx * navSpeedYRatio
                                        val newX = (currentX + delta.x).coerceIn(spWidthPx / 2f, containerWidthPx - spWidthPx / 2f)
                                        val newY = (currentY + delta.y).coerceIn(spHeightPx / 2f, containerHeightPx - spHeightPx / 2f)
                                        navSpeedXRatio = newX / containerWidthPx
                                        navSpeedYRatio = newY / containerHeightPx
                                    }
                                },
                                onDragEnd = {
                                    LauncherAppConfigStore.setNavSpeedPosition(context, navSpeedXRatio, navSpeedYRatio)
                                },
                                modifier = Modifier
                                    .offset(x = with(density) { spLeftPx.toDp() }, y = with(density) { spTopPx.toDp() })
                                    .zIndex(167f),
                            )
                        }

                        // Freely Movable Navigation Overlay 4: Traffic Light
                        if (navTrafficLightVisible) {
                            val tlWidthPx = with(density) { 80.dp.toPx() }
                            val tlHeightPx = with(density) { 36.dp.toPx() }
                            val tlCenterX = containerWidthPx * navTrafficLightXRatio
                            val tlCenterY = containerHeightPx * navTrafficLightYRatio
                            val tlLeftPx = (tlCenterX - tlWidthPx / 2f).coerceIn(0f, (containerWidthPx - tlWidthPx).coerceAtLeast(0f))
                            val tlTopPx = (tlCenterY - tlHeightPx / 2f).coerceIn(0f, (containerHeightPx - tlHeightPx).coerceAtLeast(0f))

                            LauncherNavTrafficLightOverlay(
                                onDrag = { delta ->
                                    if (containerWidthPx > 0f && containerHeightPx > 0f) {
                                        val currentX = containerWidthPx * navTrafficLightXRatio
                                        val currentY = containerHeightPx * navTrafficLightYRatio
                                        val newX = (currentX + delta.x).coerceIn(tlWidthPx / 2f, containerWidthPx - tlWidthPx / 2f)
                                        val newY = (currentY + delta.y).coerceIn(tlHeightPx / 2f, containerHeightPx - tlHeightPx / 2f)
                                        navTrafficLightXRatio = newX / containerWidthPx
                                        navTrafficLightYRatio = newY / containerHeightPx
                                    }
                                },
                                onDragEnd = {
                                    LauncherAppConfigStore.setNavTrafficLightPosition(context, navTrafficLightXRatio, navTrafficLightYRatio)
                                },
                                modifier = Modifier
                                    .offset(x = with(density) { tlLeftPx.toDp() }, y = with(density) { tlTopPx.toDp() })
                                    .zIndex(168f),
                            )
                        }
                    }



                    val alertsTopOffset = maxHeight * 0.24f + alertsDriveViewOffsetY

                    // Left vertical section: Telltale alerts (Индикаторы предупреждений)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = alertsTopOffset)
                            .zIndex(150f),
                    ) {
                        LauncherVehicleAlertsStrip(isVertical = true)
                    }

                    // Right vertical section: ADAS (Системы помощи водителю и безопасности)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp, top = alertsTopOffset)
                            .zIndex(150f),
                    ) {
                        LauncherAdasStrip(
                            canViewModel = canViewModel,
                            isVertical = true,
                        )
                    }
                }

                val nowMs = SystemClock.uptimeMillis()
                LauncherTrunkGearWarningBanner(
                    showWarning = nowMs < trunkParkWarningUntilMs,
                )

                LauncherCriticalCollisionWarningBanner(
                    adas = adas,
                    showAll = simulateEnabled && LauncherDevVehicleState.showAllIndicators,
                )
            }
            if (racing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    LauncherEggRaceControls(modifier = Modifier.fillMaxWidth())
                }
            }
            if (colorPickerVisible && carColorPanelVisible) {
                LauncherCarColorPicker(
                    selectedId = paintId,
                    onSelect = { id ->
                        onPaintChanged(id)
                        LauncherAppConfigStore.setCarPaintId(context, id)
                    },
                    onDismiss = onColorPickerDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }

        var showAdasBadges by remember { mutableStateOf(false) }
        var hideAdasBadgesTimerJob by remember { mutableStateOf<Job?>(null) }

        val onAdasDragStarted = {
            hideAdasBadgesTimerJob?.cancel()
            showAdasBadges = true
        }

        val onAdasDragEnded = {
            hideAdasBadgesTimerJob?.cancel()
            hideAdasBadgesTimerJob = scope.launch {
                delay(5000L)
                showAdasBadges = false
            }
        }

        // --- TOP OVERLAY PANELS LAYER ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, top = 10.dp, end = 8.dp)
                .zIndex(200f),
        ) {
            if (racing) {
                LauncherEggRaceCloseBar()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    activeTopPanels.forEach { panelId ->
                        DraggableAdasPanel(
                            panelId = panelId,
                            containerHeightPx = leftPanelHeightPx,
                            containerWindowTopPx = leftPanelWindowTopPx,
                            topPanels = adasTopPanels,
                            bottomPanels = adasBottomPanels,
                            showBadges = showAdasBadges,
                            onDragStarted = onAdasDragStarted,
                            onDragEnded = onAdasDragEnded,
                            onLayoutChanged = { top, bottom ->
                                LauncherAppConfigStore.setAdasPanelsLayout(context, top, bottom)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            renderPanel(panelId)
                        }
                    }
                }
            }
        }

        // --- BOTTOM OVERLAY PANELS LAYER ---
        val bottomHasPanel = !racing && activeBottomPanels.isNotEmpty()

        if (bottomHasPanel) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .zIndex(200f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    activeBottomPanels.forEach { panelId ->
                        DraggableAdasPanel(
                            panelId = panelId,
                            containerHeightPx = leftPanelHeightPx,
                            containerWindowTopPx = leftPanelWindowTopPx,
                            topPanels = adasTopPanels,
                            bottomPanels = adasBottomPanels,
                            showBadges = showAdasBadges,
                            onDragStarted = onAdasDragStarted,
                            onDragEnded = onAdasDragEnded,
                            onLayoutChanged = { top, bottom ->
                                LauncherAppConfigStore.setAdasPanelsLayout(context, top, bottom)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            renderPanel(panelId)
                        }
                    }
                }
            }
        }
        if (racing) {
            LauncherEggRaceCarsLayer(
                cars = raceCars,
                sprites = raceSprites,
                maxDepth = LauncherEggRace.PASS_UNDER_DEPTH,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.LauncherCriticalCollisionWarningBanner(
    adas: LauncherAdasState,
    showAll: Boolean,
) {
    val isFcw = adas.fcwActive || adas.distanceWarning
    val isAeb = adas.aebHint
    val isTakeover = adas.accTakeOver || adas.adasTakeOver || showAll

    if (!isFcw && !isAeb && !isTakeover) return

    val isCritical = isTakeover || isAeb
    val warningText = when {
        isCritical -> stringResource(R.string.launcher_adas_takeover)
        else -> stringResource(R.string.launcher_adas_fcw)
    }

    val bannerBgColor = if (isCritical) Color(0xFFDC2626) else Color(0xFFEAB308)
    val contentColor = if (isCritical) Color.White else Color(0xFF18181B)

    val infiniteTransition = rememberInfiniteTransition(label = "collision_warning_pulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "collision_warning_alpha",
    )

    if (isTakeover) {
        val takeoverBgColor = Color(0xFFEAB308)
        val takeoverContentColor = Color(0xFF18181B)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(200f)
                .graphicsLayer { alpha = animatedAlpha }
                .clip(CircleShape)
                .background(takeoverBgColor.copy(alpha = 0.95f))
                .border(width = 2.5.dp, color = Color.White, shape = CircleShape)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_steering_wheel_v12ubmkkangi),
                contentDescription = stringResource(R.string.launcher_adas_takeover),
                modifier = Modifier.size(88.dp),
                tint = takeoverContentColor,
            )
        }
        return
    }

    var fontScale by remember(warningText) { mutableFloatStateOf(1.0f) }
    var readyToDraw by remember(warningText) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .zIndex(200f)
            .graphicsLayer { alpha = animatedAlpha }
            .clip(RoundedCornerShape(18.dp))
            .background(bannerBgColor.copy(alpha = 0.95f))
            .border(width = 2.5.dp, color = Color.White, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSizeDp = (36 * fontScale).coerceAtLeast(20f).dp
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(iconSizeDp),
                tint = contentColor,
            )
            Text(
                text = warningText,
                color = contentColor,
                fontSize = (28 * fontScale).sp,
                lineHeight = (32 * fontScale).sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = if (warningText.contains('\n')) 2 else 1,
                softWrap = false,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow && fontScale > 0.35f) {
                        fontScale *= 0.90f
                    } else {
                        readyToDraw = true
                    }
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .drawWithContent {
                        if (readyToDraw) {
                            drawContent()
                        }
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RenderSwipeGroupedPanel(
    panelId: String,
    renderContent: @Composable (String) -> Unit,
) {
    val context = LocalContext.current
    val leftPanel = remember(context, panelId) { LauncherAppConfigStore.panelSwipeLeft(context, panelId) }
    val rightPanel = remember(context, panelId) { LauncherAppConfigStore.panelSwipeRight(context, panelId) }

    if (leftPanel == null && rightPanel == null) {
        renderContent(panelId)
    } else {
        val pages = listOfNotNull(leftPanel, panelId, rightPanel)
        val initialPageIndex = pages.indexOf(panelId).coerceAtLeast(0)
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPageIndex) { pages.size }
        LaunchedEffect(pagerState.currentPage, pages) {
            val currentSub = pages.getOrNull(pagerState.currentPage) ?: panelId
            LauncherAppConfigStore.setVisibleSubPanel(panelId, currentSub)
        }
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            renderContent(pages[page])
        }
    }
}

private enum class AssistPresetsTab {
    Main,         // Page 0: ACC + NGP (Default Main Central Page)
    Presets,      // Page 1: Speed Presets (Swipe Left from Main)
    SmartControl, // Page 2: Smart ACC & DIST (Swipe Left from Presets)
}

@Composable
internal fun LauncherAssistAndPresetsPanel(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(AssistPresetsTab.Main) }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activeTab) {
        val visibleSub = when (activeTab) {
            AssistPresetsTab.Main -> "assist"
            AssistPresetsTab.Presets -> "presets"
            AssistPresetsTab.SmartControl -> "smart_control"
        }
        LauncherAppConfigStore.setVisibleSubPanel("assist", visibleSub)
    }

    // Auto-flip logic on cruise activation / deactivation
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val isCruiseEngaged = adas.accActive || adas.accStandby || (tboxCruise ?: 0u) > 0u

    var prevEngaged by remember { mutableStateOf(isCruiseEngaged) }

    LaunchedEffect(isCruiseEngaged) {
        if (isCruiseEngaged && !prevEngaged) {
            // Cruise activated from ANY source -> wait 1s then flip to Presets page
            delay(1000L)
            if (isCruiseEngaged) {
                activeTab = AssistPresetsTab.Presets
            }
        } else if (!isCruiseEngaged && prevEngaged) {
            // Cruise deactivated -> immediately return to Central Main page (ACC + NGP)
            activeTab = AssistPresetsTab.Main
        }
        prevEngaged = isCruiseEngaged
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragAmount = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        if (abs(dragAmount) > 2f) {
                            change.consume()
                        }
                        totalDragAmount += dragAmount
                    },
                    onDragEnd = {
                        if (totalDragAmount < -30f) {
                            // Swipe Left -> next page
                            activeTab = when (activeTab) {
                                AssistPresetsTab.Main -> AssistPresetsTab.Presets
                                AssistPresetsTab.Presets -> AssistPresetsTab.SmartControl
                                AssistPresetsTab.SmartControl -> AssistPresetsTab.SmartControl
                            }
                        } else if (totalDragAmount > 30f) {
                            // Swipe Right -> previous page
                            activeTab = when (activeTab) {
                                AssistPresetsTab.SmartControl -> AssistPresetsTab.Presets
                                AssistPresetsTab.Presets -> AssistPresetsTab.Main
                                AssistPresetsTab.Main -> AssistPresetsTab.Main
                            }
                        }
                        totalDragAmount = 0f
                    },
                    onDragCancel = {
                        totalDragAmount = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                val isMovingRight = targetState.ordinal > initialState.ordinal
                if (isMovingRight) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "assist_presets_swipe_transition",
        ) { tab ->
            when (tab) {
                AssistPresetsTab.Main -> LauncherAssistControlBar(
                    canViewModel = canViewModel,
                    adas = adas,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssistPresetsTab.Presets -> LauncherCruisePresetControl(
                    canViewModel = canViewModel,
                    adas = adas,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssistPresetsTab.SmartControl -> LauncherSmartAccAndDistControl(
                    canViewModel = canViewModel,
                    adas = adas,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LauncherTrunkGearWarningBanner(
    showWarning: Boolean,
) {
    if (!showWarning) return

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(180f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEAB308).copy(alpha = 0.95f))
            .border(1.5.dp, Color(0xFFCA8A04), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_trunk),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Color(0xFF18181B)),
            )
            Text(
                text = "Переключите передачу в P",
                color = Color(0xFF18181B),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
