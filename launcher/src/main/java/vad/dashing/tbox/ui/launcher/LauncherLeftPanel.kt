package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import kotlin.math.abs

internal fun isDriveViewActive(
    speedKmh: Float,
    pasObstacle: Boolean = false,
    isParkGear: Boolean = false,
    isDriveGear: Boolean = false,
    accActive: Boolean = false,
    racing: Boolean = false,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
    currentSmartDriveView: Boolean = false,
): Boolean {
    if (isParkGear) return false
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
    onColorPickerOpen: () -> Unit = {},
    onColorPickerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val modelScaleRevision by LauncherAppConfigStore.carModelScaleRevisionFlow.collectAsStateWithLifecycle()
    val carModelScale = remember(context, modelScaleRevision) {
        LauncherAppConfigStore.carModelScale(context)
    }
    val adasAlertsPlacementRevision by LauncherAppConfigStore.adasAlertsPlacementRevisionFlow
        .collectAsStateWithLifecycle()
    val adasAlertsInLeftPanel = remember(context, adasAlertsPlacementRevision) {
        LauncherAppConfigStore.adasAlertsInLeftPanel(context)
    }
    val speedLimitPositionRevision by LauncherAppConfigStore.speedLimitPositionRevisionFlow
        .collectAsStateWithLifecycle()
    var speedLimitXRatio by remember(context, speedLimitPositionRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.speedLimitXRatio(context))
    }
    var speedLimitYRatio by remember(context, speedLimitPositionRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.speedLimitYRatio(context))
    }
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tires by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val motion = rememberLauncherVehicleMotion(tboxConnected, canViewModel)

    val racing = LauncherEggRace.active
    val raceSprites = rememberRaceOncomingSprites()
    val raceLane = LauncherEggRace.playerLane
    val raceCars = LauncherEggRace.cars
    LauncherEggRaceTicker()
    val carLaneShift = if (racing) eggRaceCarShiftDp(raceLane) else 0.dp

    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val effectiveSpeed = if (racing) LauncherEggRace.speedKmh else motion.speedKmh
    val effectiveSteer = rememberLauncherVisualSteer(motion)
    val steerPreview = motion.steerPreviewActive

    val effectiveBody = if (simulateEnabled) {
        LauncherDevVehicleState.bodyState()
    } else {
        vehicleBody
    }
    // Simulation overrides (hidden settings tab) take precedence over live TPMS.
    val effectiveTires = LauncherDevVehicleState.tireStateOrNull() ?: tires
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
        steeringDeg = effectiveSteer,
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
    val adasPanelsRevision by LauncherAppConfigStore.adasPanelsRevisionFlow
        .collectAsStateWithLifecycle()
    val adasTopPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasTopPanels(context)
    }
    val adasBottomPanels = remember(context, adasPanelsRevision) {
        LauncherAppConfigStore.adasBottomPanels(context)
    }
    var leftPanelHeightPx by remember { mutableFloatStateOf(0f) }
    val driveModeGlowRevision by LauncherAppConfigStore.driveModeGlowRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeGlowVisible = remember(context, driveModeGlowRevision) {
        LauncherAppConfigStore.driveModeGlowVisible(context)
    }
    val headlightBeamsRevision by LauncherAppConfigStore.headlightBeamsRevisionFlow
        .collectAsStateWithLifecycle()
    val headlightBeamsVisible = remember(context, headlightBeamsRevision) {
        LauncherAppConfigStore.headlightBeamsVisible(context)
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
    var smartDriveViewActive by remember { mutableStateOf(false) }
    var speedBelow5SinceMs by remember { mutableLongStateOf(0L) }

    val nowMs = SystemClock.elapsedRealtime()

    LaunchedEffect(effectiveSpeed, pasOn, pasObstacle, activeGear, accActive, racing, cameraSwitchMode) {
        if (activeGear == 'P' || pasObstacle) {
            smartDriveViewActive = false
            speedBelow5SinceMs = 0L
        } else if (racing || accActive || effectiveSpeed >= 15f) {
            smartDriveViewActive = true
            speedBelow5SinceMs = 0L
        } else if (effectiveSpeed < 5f && pasOn && smartDriveViewActive) {
            if (speedBelow5SinceMs == 0L) {
                speedBelow5SinceMs = nowMs
            }
        } else if (effectiveSpeed >= 5f || !pasOn) {
            speedBelow5SinceMs = 0L
        }
    }

    LaunchedEffect(smartDriveViewActive, speedBelow5SinceMs, pasOn) {
        if (smartDriveViewActive && speedBelow5SinceMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - speedBelow5SinceMs
            val remaining = 3_000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            if (effectiveSpeed < 5f && pasOn && activeGear != 'P') {
                smartDriveViewActive = false
                speedBelow5SinceMs = 0L
            }
        }
    }

    val inDriveGear = racing || activeGear == 'D' || effectiveSpeed > 0.5f
    val rawDriveView = isDriveViewActive(
        speedKmh = effectiveSpeed,
        pasObstacle = pasObstacle,
        isParkGear = activeGear == 'P',
        isDriveGear = inDriveGear,
        accActive = accActive,
        racing = racing,
        cameraSwitchMode = cameraSwitchMode,
        currentSmartDriveView = smartDriveViewActive,
    )

    // Hold drive view for up to 1800ms to prevent camera view jumping when CAN speed/gear momentarily drops
    var showDriveView by remember { mutableStateOf(rawDriveView) }
    var lastDriveViewTrueMs by remember { mutableLongStateOf(if (rawDriveView) SystemClock.elapsedRealtime() else 0L) }

    val nowViewMs = SystemClock.elapsedRealtime()
    if (rawDriveView) {
        showDriveView = true
        lastDriveViewTrueMs = nowViewMs
    } else {
        val elapsedView = nowViewMs - lastDriveViewTrueMs
        showDriveView = elapsedView < 1800L
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

    val isDrivingState = (inDriveGear || effectiveSpeed > 0.5f) && activeGear != 'P'

    val baseGlowColor = when {
        isEco -> Color(0xFF16A34A) // Green
        isNor -> Color(0xFF3B82F6) // Blue
        isSpt -> Color(0xFFEF4444) // Red
        else -> Color.Transparent
    }

    val targetGlowColor = if (isDrivingState && driveModeGlowVisible) baseGlowColor else baseGlowColor.copy(alpha = 0f)

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "driveModeGlowColor",
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
                LauncherEmbeddedBoundsState.leftPanelBounds = android.graphics.Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
                onCarBoundsChanged(rect)
            }
            .background(LauncherColors.LeftPanelBg),
    ) {
        if (roadVisible) {
            LauncherVirtualRoad(
                speedKmh = if (racing) effectiveSpeed * 0.45f else effectiveSpeed,
                steerAngleDeg = effectiveSteer,
                adas = if (racing) LauncherAdasState() else adas,
                steerPreview = steerPreview,
                inDriveGear = showDriveView,
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
        if (racing) {
            // Past the bumper: draw under the 3D so a dodge continues beside/behind the body.
            LauncherEggRaceCarsLayer(
                cars = raceCars,
                sprites = raceSprites,
                minDepth = LauncherEggRace.PASS_UNDER_DEPTH,
                modifier = Modifier.fillMaxSize(),
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
                else -> false
            }
        }

        val activeTopPanels = remember(adasTopPanels, driveModeBarVisible, assistPanelVisible) {
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
        val activeBottomPanels = remember(adasBottomPanels, activeTopPanels, driveModeBarVisible, assistPanelVisible) {
            val filtered = adasBottomPanels.filter { isPanelVisible(it) && it !in activeTopPanels }
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
            when (panelId) {
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
            }
        }

        // --- FIXED 3D CAR MODEL LAYER ---
        // Uses fillMaxSize with fixed padding so the 3D car position never shifts
        // when top/bottom overlay panels are hidden, shown, or moved.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 52.dp, bottom = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            val settingsProgress = settingsTransitionProgress.coerceIn(0f, 1f)
            val carHidden = isAvmActive || settingsProgress > 0.02f
            if (!carHidden) {
                LauncherCar3DModel(
                    rigState = rigState,
                    speedKmh = effectiveSpeed,
                    steeringDeg = effectiveSteer,
                    steerPreview = steerPreview,
                    inDriveGear = showDriveView,
                    modelRevision = modelRevision,
                    paintRevision = paintRevision,
                    paintId = paintId,
                    showRoad = false,
                    isAvmActive = isAvmActive,
                    settingsView = settingsProgress > 0.02f,
                    settingsProgress = settingsProgress,
                    settingsUserYawDeg = settingsUserYawDeg,
                    customModelScale = carModelScale,
                    onClick = if (!racing) onOpenVehicleSettings else null,
                    onLongClick = if (!racing) onColorPickerOpen else null,
                    onWheelAnchorsChanged = { wheelAnchors = it },
                    onPdcRingsChanged = { pdcRings = it },
                    onHeadlightFrameChanged = { headlightFrame = it },
                    onBodyRigAvailabilityChanged = { bodyRigAvailable = it },
                    projectPdcRings = !racing && (adas.pdc.hasAny || adas.rearThreats.hasBsd || pasOn),
                    projectHeadlights = !racing && headlightBeamsVisible && headlightBeams.any,
                    textureSurface = racing,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (racing) with(density) { carLaneShift.toPx() } else 0f
                        },
                )
                if (settingsProgress < 0.15f && !racing) {
                    LauncherTireBadges(
                        state = effectiveTires,
                        // ADAS strip shows a small pressure-only pill next to the wheel.
                        compact = true,
                        wheelAnchorsPx = wheelAnchors,
                        modifier = Modifier.fillMaxSize(),
                    )
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
            }

            // Overlay layer for 3D speed limit sign floating over the 3D car area
            if (!racing) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val signSizePx = with(density) { 48.dp.toPx() }
                    val containerWidthPx = with(density) { maxWidth.toPx() }
                    val containerHeightPx = with(density) { maxHeight.toPx() }

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

                    if (adasAlertsInLeftPanel) {
                        val alertsTopOffset = maxHeight * 0.24f

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
                }

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
            if (colorPickerVisible) {
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
                            topPanels = adasTopPanels,
                            bottomPanels = adasBottomPanels,
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
        val bottomHasPanel = !racing && (miniPlayerVisible || activeBottomPanels.isNotEmpty())

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
                    if (miniPlayerVisible) {
                        LauncherMediaMiniPlayer(modifier = Modifier.fillMaxWidth())
                    }
                    activeBottomPanels.forEach { panelId ->
                        DraggableAdasPanel(
                            panelId = panelId,
                            containerHeightPx = leftPanelHeightPx,
                            topPanels = adasTopPanels,
                            bottomPanels = adasBottomPanels,
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

    val warningText = when {
        isTakeover || isAeb -> stringResource(R.string.launcher_adas_takeover)
        else -> stringResource(R.string.launcher_adas_fcw)
    }

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

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(200f)
            .graphicsLayer { alpha = animatedAlpha }
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFDC2626).copy(alpha = 0.95f))
            .border(width = 2.5.dp, color = Color.White, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = Color.White,
            )
            Text(
                text = warningText,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private enum class AssistPresetsTab {
    Lanes,   // Page -1: LDW + LKA (Swipe Right from Main)
    Main,    // Page 0: ACC + NGP (Default Main Central Page)
    Presets, // Page 1: Speed Presets (Swipe Left from Main)
}

@Composable
private fun LauncherAssistAndPresetsPanel(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(AssistPresetsTab.Main) }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

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
                            // Swipe Left -> move right in pages
                            activeTab = when (activeTab) {
                                AssistPresetsTab.Lanes -> AssistPresetsTab.Main
                                AssistPresetsTab.Main -> AssistPresetsTab.Presets
                                AssistPresetsTab.Presets -> AssistPresetsTab.Presets
                            }
                        } else if (totalDragAmount > 30f) {
                            // Swipe Right -> move left in pages
                            activeTab = when (activeTab) {
                                AssistPresetsTab.Presets -> AssistPresetsTab.Main
                                AssistPresetsTab.Main -> AssistPresetsTab.Lanes
                                AssistPresetsTab.Lanes -> AssistPresetsTab.Lanes
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
                AssistPresetsTab.Lanes -> LauncherLanesControlBar(
                    adas = adas,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            }
        }
    }
}
