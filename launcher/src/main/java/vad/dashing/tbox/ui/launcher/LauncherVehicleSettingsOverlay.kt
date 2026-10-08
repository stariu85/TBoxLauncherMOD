package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleDriverUnlockMode
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendCycleHomelightDelay
import vad.dashing.tbox.ui.sendCycleLasSensitivity
import vad.dashing.tbox.ui.sendCycleStaticEffect
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendSetMbCanProperty
import vad.dashing.tbox.ui.sendToggleAccAutobrake
import vad.dashing.tbox.ui.sendToggleAvh
import vad.dashing.tbox.ui.sendToggleDoorAutoLock
import vad.dashing.tbox.ui.sendToggleDoorIgnOffUnlock
import vad.dashing.tbox.ui.sendToggleEscOff
import vad.dashing.tbox.ui.sendToggleFcw
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleHdc
import vad.dashing.tbox.ui.sendToggleHvacAc
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleMbCanProperty
import vad.dashing.tbox.ui.sendToggleMirrorAutofold
import vad.dashing.tbox.ui.sendToggleMirrorReverseTurn
import vad.dashing.tbox.ui.sendToggleParkingRadar
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.sendToggleWiperMaintenance
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString
import kotlin.math.roundToInt

@Composable
fun LauncherVehicleSettingsOverlay(
    visible: Boolean,
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") bodyState: LauncherCarRigState = LauncherCarRigState(),
    @Suppress("UNUSED_PARAMETER") onBodyStateChanged: (LauncherCarRigState) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") modelRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    @Suppress("UNUSED_PARAMETER") paintRevision: Int = 0,
    onPaintChanged: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onCarPaneBoundsChanged: (Rect) -> Unit = {},
    transitionProgress: Float = 1f,
    @Suppress("UNUSED_PARAMETER") settingsUserYawDeg: Float = 0f,
    onCarRotate: (Float) -> Unit = {},
) {
    if (!visible && transitionProgress <= 0.001f) return

    val context = LocalContext.current
    var expandedSection by remember { mutableStateOf<VehicleSettingsSection?>(VehicleSettingsSection.Status) }
    val progress = transitionProgress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
    ) {
        // Dim only the right/content side so the left-panel car stays visible
        // and morphs via shared settingsTransitionProgress (single 3D model).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.58f)
                .alpha(0.55f * progress)
                .background(LauncherColors.CanvasDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onCarRotate(dragAmount.x / 4f)
                        }
                    },
            )
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = progress
                        translationX = 56f * (1f - progress)
                        scaleX = 0.96f + 0.04f * progress
                        scaleY = 0.96f + 0.04f * progress
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(LauncherColors.SurfaceDark)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.launcher_vehicle_settings_title),
                                style = MaterialTheme.typography.tboxCaption,
                                color = LauncherColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "v${vad.dashing.tbox.BuildConfig.VERSION_NAME.substringBefore('-')}",
                                color = LauncherColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                            )
                            IconButton(
                                onClick = { launchTBoxSettings(context) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = stringResource(R.string.action_configure),
                                    tint = LauncherColors.AccentCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.launcher_vs_overlay_hint),
                            color = LauncherColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, null, tint = LauncherColors.TextSecondary)
                    }
                }
                Spacer(modifier.height(10.dp))
                LauncherVehicleSettingsContent(
                    canViewModel = canViewModel,
                    tboxViewModel = tboxViewModel,
                    paintId = paintId,
                    onPaintChanged = onPaintChanged,
                    expandedSection = expandedSection,
                    onSectionToggle = { section ->
                        expandedSection = if (expandedSection == section) null else section
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun LauncherVehicleSettingsContent(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    paintId: String,
    onPaintChanged: (String) -> Unit,
    expandedSection: VehicleSettingsSection?,
    onSectionToggle: (VehicleSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    simulationUnlocked: Boolean = false,
) {
    val context = LocalContext.current
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val fuelPctFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val fuelPctRaw by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    // Filtered % считается только в активной поездке; вне поездки показываем сырой процент.
    val fuelPct = fuelPctFiltered ?: fuelPctRaw
    val rangeKm by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val insideTemp by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val climateSet by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val climateSetPassenger by canViewModel.climateSetTemperature2.collectAsStateWithLifecycle()
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val driveMode = vad.dashing.tbox.resolveDriveModeDisplayLabel(
        driveModeRaw = driveModeRaw,
        driveModeWetRaw = driveModeWetRaw,
        gearBoxDriveMode = gearBoxDriveMode,
    )
    val wheelPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelTemp by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val pasOn = parkingRadar is MbCanBinaryState.On
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val windscreenHeat by UniversalCanRepository.frontWindscreenHeatState.collectAsStateWithLifecycle()
    val hvacAc by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val leftSeatMode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val rightSeatMode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val vehicleControls = rememberLauncherVehicleControlSnapshot(
        enabled = true,
        includeExperimental = expandedSection == VehicleSettingsSection.Experimental,
    )
    val wiperMaintenance by UniversalCanRepository.wiperMaintenanceState.collectAsStateWithLifecycle()
    var headlightsMode by remember { mutableStateOf(LIGHT_CONTROL_OFF) }
    var rearFogActive by remember { mutableStateOf(false) }
    val systemSettings = rememberLauncherSystemSettings(context)
    val dash = stringResource(R.string.launcher_vs_value_unknown)

    LaunchedEffect(vehicleControls.lightControlRaw) {
        vehicleControls.lightControlRaw?.let { headlightsMode = it }
    }
    LaunchedEffect(vehicleControls.rearFogLight) {
        vehicleControls.rearFogLight?.let { rearFogActive = it }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_status),
            subtitle = stringResource(R.string.launcher_vs_section_status_sub),
            icon = VehicleSettingsSectionIcons.Status,
            expanded = expandedSection == VehicleSettingsSection.Status,
            onToggle = { onSectionToggle(VehicleSettingsSection.Status) },
        ) {
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_tbox),
                stringResource(if (tboxConnected) R.string.value_connected else R.string.value_disconnected),
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_metric_range),
                rangeKm?.let { "$it km" } ?: dash,
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_fuel),
                fuelPct?.let { "$it%" } ?: dash,
            )
            if (driveMode.isNotBlank()) {
                LauncherSettingsValueRow(stringResource(R.string.launcher_drive_mode_label), driveMode)
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_wheels),
            subtitle = stringResource(R.string.launcher_vs_section_wheels_sub),
            icon = VehicleSettingsSectionIcons.Wheels,
            expanded = expandedSection == VehicleSettingsSection.Wheels,
            onToggle = { onSectionToggle(VehicleSettingsSection.Wheels) },
        ) {
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_fl), wheelPressure.wheel1, wheelTemp.wheel1)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_fr), wheelPressure.wheel2, wheelTemp.wheel2)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_rl), wheelPressure.wheel3, wheelTemp.wheel3)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_rr), wheelPressure.wheel4, wheelTemp.wheel4)
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_body),
            subtitle = stringResource(R.string.launcher_vs_section_body_sub),
            icon = VehicleSettingsSectionIcons.Body,
            expanded = expandedSection == VehicleSettingsSection.Body,
            onToggle = { onSectionToggle(VehicleSettingsSection.Body) },
        ) {
            val simulateEnabled = LauncherDevVehicleState.simulateEnabled
            val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
            val displayBody = if (simulateEnabled) LauncherDevVehicleState.bodyState() else vehicleBody
            val togglesEnabled = simulateEnabled

            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_trunk_action),
                stringResource(R.string.launcher_vs_tailgate),
                onClick = { sendOpenCloseTrunk(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_tailgate),
                active = displayBody.tailgateOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.tailgateOpen = !LauncherDevVehicleState.tailgateOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_fl),
                active = displayBody.doorFlOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorFlOpen = !LauncherDevVehicleState.doorFlOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_fr),
                active = displayBody.doorFrOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorFrOpen = !LauncherDevVehicleState.doorFrOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_rl),
                active = displayBody.doorRlOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorRlOpen = !LauncherDevVehicleState.doorRlOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_rr),
                active = displayBody.doorRrOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorRrOpen = !LauncherDevVehicleState.doorRrOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_auto_lock),
                active = vehicleControls.doorAutoLock == true,
                onClick = { sendToggleDoorAutoLock(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_ignoff_unlock),
                active = vehicleControls.doorIgnOffUnlock == true,
                onClick = { sendToggleDoorIgnOffUnlock(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_mirror_autofold),
                active = vehicleControls.mirrorAutofold == true,
                onClick = { sendToggleMirrorAutofold(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_mirror_reverse),
                active = vehicleControls.mirrorReverseTurn == true,
                onClick = { sendToggleMirrorReverseTurn(context) },
            )
            Text(
                text = stringResource(R.string.launcher_paint_picker_title),
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LauncherCarPaint.options.forEach { option ->
                    val selected = option.id == paintId
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(option.colorArgb))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    LauncherColors.AccentCyan
                                } else {
                                    LauncherColors.TextMuted.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(15.dp),
                            )
                            .clickable {
                                onPaintChanged(option.id)
                                LauncherAppConfigStore.setCarPaintId(context, option.id)
                            },
                    )
                }
            }
            Text(
                text = stringResource(
                    if (simulateEnabled) {
                        R.string.launcher_vs_body_sim_note
                    } else {
                        R.string.launcher_vs_body_can_note
                    },
                ),
                color = LauncherColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_lights),
            subtitle = stringResource(R.string.launcher_vs_section_lights_sub),
            icon = VehicleSettingsSectionIcons.Lights,
            expanded = expandedSection == VehicleSettingsSection.Lights,
            onToggle = { onSectionToggle(VehicleSettingsSection.Lights) },
        ) {
            LauncherHeadlightsModeSelector(
                selected = headlightsMode,
                onSelect = { mode ->
                    headlightsMode = mode
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.LIGHT_CONTROL,
                        mode,
                    )
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_rear_fog),
                active = rearFogActive,
                onClick = {
                    rearFogActive = !rearFogActive
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
                        if (rearFogActive) 2 else 1,
                    )
                },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_homelight),
                vehicleControls.homelightDelay?.toString() ?: dash,
                onClick = { sendCycleHomelightDelay(context, vehicleControls.homelightDelay) },
            )
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_metric_cabin),
            subtitle = stringResource(R.string.launcher_vs_section_cabin_sub),
            icon = VehicleSettingsSectionIcons.Cabin,
            expanded = expandedSection == VehicleSettingsSection.Cabin,
            onToggle = { onSectionToggle(VehicleSettingsSection.Cabin) },
        ) {
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_inside_temp),
                insideTemp?.let { valueToString(it, 1, default = dash) + "°" } ?: dash,
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_outside_temp),
                outsideTemp?.let { valueToString(it, 1, default = dash) + "°" } ?: dash,
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_climate_set_driver),
                climateSet?.let { "${it.roundToInt()}°" } ?: dash,
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_climate_set_passenger),
                climateSetPassenger?.let { "${it.roundToInt()}°" } ?: dash,
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_climate_ac),
                active = hvacAc is MbCanBinaryState.On,
                onClick = { sendToggleHvacAc(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_climate_auto),
                active = hvacAuto is MbCanBinaryState.On,
                onClick = { sendToggleHvacAuto(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_climate_recirc),
                active = hvacRecirc is MbCanBinaryState.On,
                onClick = { sendToggleHvacAirRecirculation(context) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.launcher_vs_climate_temp_driver_minus),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            sendAdjustHvacTemperature(
                                context, climateSet, -1.0f, HvacTempZone.Driver,
                            )
                        }
                        .padding(vertical = 6.dp),
                )
                Text(
                    text = stringResource(R.string.launcher_vs_climate_temp_driver_plus),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            sendAdjustHvacTemperature(
                                context, climateSet, 1.0f, HvacTempZone.Driver,
                            )
                        }
                        .padding(vertical = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.launcher_vs_climate_temp_passenger_minus),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            sendAdjustHvacTemperature(
                                context, climateSetPassenger, -1.0f, HvacTempZone.Passenger,
                            )
                        }
                        .padding(vertical = 6.dp),
                )
                Text(
                    text = stringResource(R.string.launcher_vs_climate_temp_passenger_plus),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            sendAdjustHvacTemperature(
                                context, climateSetPassenger, 1.0f, HvacTempZone.Passenger,
                            )
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_adas_title),
            subtitle = stringResource(R.string.launcher_vs_section_adas_sub),
            icon = VehicleSettingsSectionIcons.Adas,
            expanded = expandedSection == VehicleSettingsSection.Adas,
            onToggle = { onSectionToggle(VehicleSettingsSection.Adas) },
        ) {
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_adas_pas_on),
                active = pasOn,
                onClick = { sendToggleParkingRadar(context) },
            )
            val bsdAlertOverlayRevision by LauncherAppConfigStore.adasBsdAlertOverlayRevisionFlow.collectAsStateWithLifecycle()
            val bsdAlertOverlayEnabled = remember(context, bsdAlertOverlayRevision) {
                LauncherAppConfigStore.adasBsdAlertOverlayEnabled(context)
            }
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_bsd_alert_overlay),
                active = bsdAlertOverlayEnabled,
                onClick = {
                    LauncherAppConfigStore.setAdasBsdAlertOverlayEnabled(context, !bsdAlertOverlayEnabled)
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_fcw),
                active = vehicleControls.fcw == true,
                onClick = { sendToggleFcw(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_acc_autobrake),
                active = vehicleControls.accAutobrake == true,
                onClick = { sendToggleAccAutobrake(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_hdc),
                active = vehicleControls.hdc == true,
                onClick = { sendToggleHdc(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_esc_off),
                active = vehicleControls.escOff == true,
                onClick = { sendToggleEscOff(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_avh),
                active = vehicleControls.avh == true,
                onClick = { sendToggleAvh(context) },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_lka_sens),
                vehicleControls.lasSensitivity?.toString() ?: dash,
                onClick = { sendCycleLasSensitivity(context, vehicleControls.lasSensitivity) },
            )
            cruiseSpeed?.takeIf { it > 0u }?.let {
                LauncherSettingsValueRow(
                    stringResource(R.string.launcher_adas_cruise, it.toString()),
                    "${it} km/h",
                )
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_comfort),
            subtitle = stringResource(R.string.launcher_vs_section_comfort_sub),
            icon = VehicleSettingsSectionIcons.Comfort,
            expanded = expandedSection == VehicleSettingsSection.Comfort,
            onToggle = { onSectionToggle(VehicleSettingsSection.Comfort) },
        ) {
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_steering_heat),
                active = steeringHeat is MbCanBinaryState.On,
                onClick = { sendToggleSteeringWheelHeat(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_windscreen_heat),
                active = windscreenHeat is MbCanBinaryState.On,
                onClick = { sendToggleFrontWindscreenHeat(context) },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_seat_heat_left),
                seatHeatLabel(leftSeatMode, dash),
                onClick = {
                    sendCycleFrontSeatHeat(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
                        seatModeToRaw(leftSeatMode),
                    )
                },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_seat_vent_left),
                seatVentLabel(leftSeatMode, dash),
                onClick = {
                    sendCycleFrontSeatVent(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
                        seatModeToRaw(leftSeatMode),
                    )
                },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_seat_heat_right),
                seatHeatLabel(rightSeatMode, dash),
                onClick = {
                    sendCycleFrontSeatHeat(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
                        seatModeToRaw(rightSeatMode),
                    )
                },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_seat_vent_right),
                seatVentLabel(rightSeatMode, dash),
                onClick = {
                    sendCycleFrontSeatVent(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
                        seatModeToRaw(rightSeatMode),
                    )
                },
            )
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_cruise),
            subtitle = stringResource(R.string.launcher_vs_section_cruise_sub),
            icon = VehicleSettingsSectionIcons.Cruise,
            expanded = expandedSection == VehicleSettingsSection.Cruise,
            onToggle = { onSectionToggle(VehicleSettingsSection.Cruise) },
        ) {
            CruiseControlSettingsContent()
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_launcher),
            subtitle = stringResource(R.string.launcher_vs_section_launcher_sub),
            icon = VehicleSettingsSectionIcons.Launcher,
            expanded = expandedSection == VehicleSettingsSection.Launcher,
            onToggle = { onSectionToggle(VehicleSettingsSection.Launcher) },
        ) {
            LauncherHomeSettingsContent()
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_system),
            subtitle = stringResource(R.string.launcher_vs_section_system_sub),
            icon = VehicleSettingsSectionIcons.System,
            expanded = expandedSection == VehicleSettingsSection.System,
            onToggle = { onSectionToggle(VehicleSettingsSection.System) },
        ) {
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_wifi) +
                    (systemSettings.wifiSsid?.let { " · $it" } ?: ""),
                active = systemSettings.wifiEnabled,
                onClick = { systemSettings.applyWifiEnabled(!systemSettings.wifiEnabled) },
            )
            LauncherWifiApBlock()
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_bluetooth) +
                    (systemSettings.bluetoothName?.let { " · $it" } ?: ""),
                active = systemSettings.bluetoothEnabled,
                onClick = { systemSettings.applyBluetoothEnabled(!systemSettings.bluetoothEnabled) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.launcher_vs_volume),
                    color = LauncherColors.TextPrimary,
                    fontSize = 14.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "−",
                        color = LauncherColors.AccentCyan,
                        fontSize = 20.sp,
                        modifier = Modifier.clickable { systemSettings.adjustMediaVolume(-1) },
                    )
                    Text(
                        "${systemSettings.mediaVolume}/${systemSettings.mediaVolumeMax}",
                        color = LauncherColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Text(
                        "+",
                        color = LauncherColors.AccentCyan,
                        fontSize = 20.sp,
                        modifier = Modifier.clickable { systemSettings.adjustMediaVolume(1) },
                    )
                }
            }
            Text(
                stringResource(R.string.launcher_vs_brightness),
                color = LauncherColors.TextPrimary,
                fontSize = 14.sp,
            )
            if (systemSettings.brightnessWritable) {
                Slider(
                    value = systemSettings.brightnessNorm,
                    onValueChange = { systemSettings.applyBrightnessNorm(it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                    ),
                )
            } else {
                Text(
                    stringResource(R.string.launcher_vs_brightness_locked),
                    color = LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_experimental),
            subtitle = stringResource(R.string.launcher_vs_section_experimental_sub),
            icon = VehicleSettingsSectionIcons.Experimental,
            expanded = expandedSection == VehicleSettingsSection.Experimental,
            onToggle = { onSectionToggle(VehicleSettingsSection.Experimental) },
        ) {
            Text(
                text = stringResource(R.string.launcher_vs_exp_note),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_welcome_lamp),
                active = vehicleControls.welcomeLamp == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WELCOME_LAMP) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_breathing_unlock),
                active = vehicleControls.breathingUnlock == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.BREATHING_UNLOCK) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_breathing_lock),
                active = vehicleControls.breathingLock == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.BREATHING_LOCK) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_unlock_animation),
                active = vehicleControls.unlockAnimation == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.UNLOCK_ANIMATION) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_lock_animation),
                active = vehicleControls.lockAnimation == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.LOCK_ANIMATION) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_musical_rhythm),
                active = vehicleControls.musicalRhythm == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.MUSICAL_RHYTHM) },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_exp_static_effect),
                when (vehicleControls.staticEffect) {
                    0 -> stringResource(R.string.launcher_vs_exp_static_off)
                    1 -> stringResource(R.string.launcher_vs_exp_static_mono)
                    3 -> stringResource(R.string.launcher_vs_exp_static_full)
                    else -> dash
                },
                onClick = { sendCycleStaticEffect(context, vehicleControls.staticEffect) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_window_autoclose),
                active = vehicleControls.windowAutoclose == true,
                onClick = {
                    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WINDOW_AUTOCLOSE_SWITCH)
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_dome_door),
                active = vehicleControls.domeDoorCtrl == true,
                onClick = {
                    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.LIGHT_DOME_DOORCTRL_SWITCH)
                },
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_exp_driver_unlock),
                when (vehicleControls.driverUnlockMode) {
                    1 -> stringResource(R.string.launcher_vs_exp_driver_unlock_driver)
                    2 -> stringResource(R.string.launcher_vs_exp_driver_unlock_all)
                    else -> dash
                },
                onClick = { sendCycleDriverUnlockMode(context, vehicleControls.driverUnlockMode) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_doorknob),
                active = vehicleControls.doorknob == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.DOORKNOB_SWITCH) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_welcome_seat),
                active = vehicleControls.welcomeSeat == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.WELCOME_SEAT) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_wireless),
                active = vehicleControls.wirelessCharging == true,
                onClick = {
                    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH)
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_iss),
                active = vehicleControls.iss == true,
                onClick = { sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.ISS_SWITCH) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_fragrance),
                active = vehicleControls.fragrance == true,
                onClick = {
                    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH)
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_veh_wash),
                active = vehicleControls.vehWashMode == true,
                onClick = {
                    sendToggleMbCanProperty(context, MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET)
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_exp_wiper_maint),
                active = wiperMaintenance is MbCanBinaryState.On,
                onClick = { sendToggleWiperMaintenance(context) },
            )
        }

        if (simulationUnlocked) {
            SimulationSectionCard(
                expanded = expandedSection == VehicleSettingsSection.Simulation,
                onToggle = { onSectionToggle(VehicleSettingsSection.Simulation) },
            )
        }
    }
}

private fun seatModeToRaw(mode: MbCanSeatModeState): Int? = when (mode) {
    is MbCanSeatModeState.Off -> 1
    is MbCanSeatModeState.Heat -> when (mode.level) {
        1 -> 2
        2 -> 3
        3 -> 4
        else -> null
    }
    is MbCanSeatModeState.Vent -> when (mode.level) {
        1 -> 5
        2 -> 6
        3 -> 7
        else -> null
    }
    else -> null
}

@Composable
private fun seatHeatLabel(mode: MbCanSeatModeState, @Suppress("UNUSED_PARAMETER") dash: String): String =
    when (mode) {
        is MbCanSeatModeState.Heat -> stringResource(R.string.launcher_vs_seat_heat_level, mode.level)
        else -> stringResource(R.string.launcher_vs_off)
    }

@Composable
private fun seatVentLabel(mode: MbCanSeatModeState, @Suppress("UNUSED_PARAMETER") dash: String): String =
    when (mode) {
        is MbCanSeatModeState.Vent -> stringResource(R.string.launcher_vs_seat_vent_level, mode.level)
        else -> stringResource(R.string.launcher_vs_off)
    }

@Composable
private fun LauncherWheelRow(
    label: String,
    pressure: Float?,
    temperature: Float?,
) {
    val dash = stringResource(R.string.launcher_vs_value_unknown)
    val pressureText = pressure?.let { valueToString(it, 1) + " bar" } ?: dash
    val tempText = temperature?.let { valueToString(it, 0) + "°" } ?: dash
    LauncherSettingsValueRow(label, "$pressureText · $tempText")
}

@Composable
private fun LauncherHeadlightsModeSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.launcher_vs_headlights),
        color = LauncherColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
    val modes = listOf(
        LIGHT_CONTROL_OFF to R.string.launcher_vs_headlights_off,
        LIGHT_CONTROL_POSITION to R.string.launcher_vs_headlights_position,
        LIGHT_CONTROL_LOW_BEAM to R.string.launcher_vs_headlights_low,
        LIGHT_CONTROL_AUTO to R.string.launcher_vs_headlights_auto,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { (mode, labelRes) ->
            val active = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) LauncherColors.AccentCyan
                        else LauncherColors.SurfaceDark,
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = if (active) LauncherColors.CanvasDark else LauncherColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
