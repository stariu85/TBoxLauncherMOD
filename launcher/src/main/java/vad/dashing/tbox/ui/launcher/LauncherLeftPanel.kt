package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel

internal fun isDriveViewActive(
    speedKmh: Float,
    pasOn: Boolean = false,
    isParkGear: Boolean = false,
    isDriveGear: Boolean = false,
    accActive: Boolean = false,
    racing: Boolean = false,
    cameraSwitchMode: Int = CAMERA_SWITCH_MODE_PAS_AND_PARK,
): Boolean {
    if (isParkGear) return false
    if (racing || accActive) return true
    return when (cameraSwitchMode) {
        CAMERA_SWITCH_MODE_DRIVE_AND_PARK -> isDriveGear
        else -> if (pasOn) false else speedKmh > 0.5f
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
    val cruisePanelRevision by LauncherAppConfigStore.cruisePanelRevisionFlow
        .collectAsStateWithLifecycle()
    val cruisePanelVisible = remember(context, cruisePanelRevision) {
        LauncherAppConfigStore.cruisePanelVisible(context)
    }
    val cruisePositionRevision by LauncherAppConfigStore.cruisePanelPositionRevisionFlow
        .collectAsStateWithLifecycle()
    val cruisePosition = remember(context, cruisePositionRevision) {
        LauncherAppConfigStore.cruisePanelPosition(context)
    }
    val isCruiseTop = cruisePosition == "top"

    val swapPanelsRevision by LauncherAppConfigStore.swapTopBottomPanelsRevisionFlow
        .collectAsStateWithLifecycle()
    val swapPanels = remember(context, swapPanelsRevision) {
        LauncherAppConfigStore.swapTopBottomPanels(context)
    }

    val driveModeBarRevision by LauncherAppConfigStore.driveModeBarRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeBarVisible = remember(context, driveModeBarRevision) {
        LauncherAppConfigStore.driveModeBarVisible(context)
    }
    val driveModeGlowRevision by LauncherAppConfigStore.driveModeGlowRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeGlowVisible = remember(context, driveModeGlowRevision) {
        LauncherAppConfigStore.driveModeGlowVisible(context)
    }
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val pasOn = (parkingRadar is MbCanBinaryState.On) || adas.pdc.hasAny
    val accActive = adas.accActive ||
        (adas.frontObject.valid && (adas.fcwActive || adas.distanceWarning || adas.aebHint))
    val cameraSwitchModeRevision by LauncherAppConfigStore.cameraSwitchModeRevisionFlow
        .collectAsStateWithLifecycle()
    val cameraSwitchMode = remember(context, cameraSwitchModeRevision) {
        LauncherAppConfigStore.cameraSwitchMode(context)
    }
    val inDriveGear = racing || activeGear == 'D' || effectiveSpeed > 0.5f
    val showDriveView = isDriveViewActive(
        speedKmh = effectiveSpeed,
        pasOn = pasOn,
        isParkGear = activeGear == 'P',
        isDriveGear = inDriveGear,
        accActive = accActive,
        racing = racing,
        cameraSwitchMode = cameraSwitchMode,
    )

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

    val isMoving = effectiveSpeed > 0.5f

    val baseGlowColor = when {
        isEco -> Color(0xFF16A34A) // Green
        isNor -> Color(0xFF3B82F6) // Blue
        isSpt -> Color(0xFFEF4444) // Red
        else -> Color.Transparent
    }

    val targetGlowColor = if (isMoving && driveModeGlowVisible) baseGlowColor else baseGlowColor.copy(alpha = 0f)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val driveModeBarContent: @Composable () -> Unit = {
                if (driveModeBarVisible) {
                    LauncherDriveModeBar(
                        canViewModel = canViewModel,
                        onModeSelected = { pending -> driveModePending = pending },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val cruisePanelContent: @Composable () -> Unit = {
                if (cruisePanelVisible) {
                    LauncherCruisePresetControl(
                        canViewModel = canViewModel,
                        adas = adas,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (racing) {
                LauncherEggRaceCloseBar()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isCruiseTop) {
                        if (!swapPanels) {
                            driveModeBarContent()
                            cruisePanelContent()
                        } else {
                            cruisePanelContent()
                            driveModeBarContent()
                        }
                    } else {
                        if (!swapPanels) {
                            driveModeBarContent()
                        } else {
                            cruisePanelContent()
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = if (cruisePanelVisible) 12.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!carHidden) {
                    val settingsProgress = settingsTransitionProgress.coerceIn(0f, 1f)
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
                        projectPdcRings = !racing && (adas.pdc.hasAny || adas.rearThreats.hasBsd),
                        projectHeadlights = !racing && headlightBeams.any,
                        textureSurface = racing,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = with(density) { carLaneShift.toPx() }
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
                            val alertsTopOffset = maxHeight * 0.25f

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

            val miniPlayerRevision by LauncherAppConfigStore.mediaMiniPlayerRevisionFlow
                .collectAsStateWithLifecycle()
            val miniPlayerVisible = remember(context, miniPlayerRevision) {
                LauncherAppConfigStore.mediaMiniPlayerVisible(context)
            }

            val bottomHasContent = !racing && (
                miniPlayerVisible ||
                (isCruiseTop && driveModeBarVisible && swapPanels) ||
                (!isCruiseTop && (cruisePanelVisible || driveModeBarVisible))
            )

            if (bottomHasContent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (miniPlayerVisible) {
                        LauncherMediaMiniPlayer(modifier = Modifier.fillMaxWidth())
                    }
                    if (!isCruiseTop) {
                        if (!swapPanels) {
                            cruisePanelContent()
                        } else {
                            driveModeBarContent()
                        }
                    } else if (swapPanels) {
                        driveModeBarContent()
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
