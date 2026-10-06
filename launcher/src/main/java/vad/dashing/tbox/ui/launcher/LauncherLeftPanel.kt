package vad.dashing.tbox.ui.launcher

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

private const val ROAD_ANIMATION_SPEED_THRESHOLD_KMH = 15f

internal fun isDriveViewActive(
    speedKmh: Float,
    cruiseOn: Boolean = false,
    racing: Boolean = false,
    speedThresholdKmh: Float = ROAD_ANIMATION_SPEED_THRESHOLD_KMH,
): Boolean {
    return racing || speedKmh >= speedThresholdKmh
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
    val cruiseOn = adas.accActive || adas.accStandby ||
        (adas.accSetSpeedKmh ?: 0) > 0 ||
        (cruiseSpeed ?: 0u) > 0u
    val inDriveGear = racing || activeGear == 'D' || effectiveSpeed > 0.5f
    val showDriveView = isDriveViewActive(effectiveSpeed, cruiseOn, racing)

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (racing) {
                    LauncherEggRaceCloseBar()
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier.heightIn(min = 28.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                LauncherAdasStrip(canViewModel = canViewModel)
                            }
                            LauncherVehicleAlertsStrip(modifier = Modifier.fillMaxWidth())
                        }
                        LauncherSpeedLimitOverlay(
                            adas = adas,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = if (cruisePanelVisible) 12.dp else 4.dp),
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

            if (!racing && (miniPlayerVisible || cruisePanelVisible)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (miniPlayerVisible) {
                        LauncherMediaMiniPlayer(modifier = Modifier.fillMaxWidth())
                    }
                    if (cruisePanelVisible) {
                        LauncherCruisePresetControl(
                            canViewModel = canViewModel,
                            adas = adas,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
