package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption
import kotlin.math.roundToInt

private val SimulationRed = Color(0xFFDC2626)
private val SimulationRedDark = Color(0xFF7F1D1D)
private val SimulationRedBg = Color(0xFF2A1515)

/**
 * Red-tinted simulation card shown only after the hidden 8-tap unlock.
 * All controls write into [LauncherDevVehicleState] which overrides the live
 * CAN readings on the 3D model, tire badges and motion preview.
 */
@Composable
fun SimulationSectionCard(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (expanded) SimulationRedBg else LauncherColors.CardDark),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    VehicleSettingsSectionIcons.Simulation,
                    null,
                    tint = SimulationRed,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.launcher_vs_section_simulation),
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.launcher_vs_section_simulation_sub),
                        color = LauncherColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = SimulationRed,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.launcher_vs_sim_hint),
                    color = SimulationRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_all_indicators),
                    active = LauncherDevVehicleState.showAllIndicators,
                    onClick = { LauncherDevVehicleState.toggleShowAllIndicators() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_door_fl),
                    active = LauncherDevVehicleState.doorFlOpen,
                    onClick = { LauncherDevVehicleState.toggleDoorFl() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_door_fr),
                    active = LauncherDevVehicleState.doorFrOpen,
                    onClick = { LauncherDevVehicleState.toggleDoorFr() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_door_rl),
                    active = LauncherDevVehicleState.doorRlOpen,
                    onClick = { LauncherDevVehicleState.toggleDoorRl() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_door_rr),
                    active = LauncherDevVehicleState.doorRrOpen,
                    onClick = { LauncherDevVehicleState.toggleDoorRr() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_tailgate),
                    active = LauncherDevVehicleState.tailgateOpen,
                    onClick = { LauncherDevVehicleState.toggleTailgate() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_seatbelt_driver),
                    active = LauncherDevVehicleState.seatBeltDriver,
                    onClick = { LauncherDevVehicleState.toggleSeatBeltDriver() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_seatbelt_passenger),
                    active = LauncherDevVehicleState.seatBeltPassenger,
                    onClick = { LauncherDevVehicleState.toggleSeatBeltPassenger() },
                )

                Spacer(Modifier.height(4.dp))
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_speed),
                    value = LauncherDevVehicleState.speedKmh,
                    range = 0f..220f,
                    unit = "км/ч",
                    onValueChange = { LauncherDevVehicleState.setSpeed(it) },
                )
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_steer),
                    value = LauncherDevVehicleState.steerAngleDeg,
                    range = -540f..540f,
                    unit = "°",
                    onValueChange = { LauncherDevVehicleState.setSteer(it) },
                )
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_battery),
                    value = LauncherDevVehicleState.batteryVoltageOverride ?: 12.6f,
                    range = 9f..16f,
                    unit = "В",
                    onValueChange = { LauncherDevVehicleState.setBatteryVoltage(it) },
                )
                SimulationGearRow(
                    label = stringResource(R.string.launcher_vs_sim_gear),
                    active = LauncherDevVehicleState.gearSlotOverride,
                    onSelect = { LauncherDevVehicleState.setGearSlot(it) },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.launcher_vs_sim_adas_header),
                    color = SimulationRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_acc),
                    active = LauncherDevVehicleState.adasCruiseActive,
                    onClick = { LauncherDevVehicleState.toggleAdasCruise() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_lanes),
                    active = LauncherDevVehicleState.adasLanesActive,
                    onClick = { LauncherDevVehicleState.toggleAdasLanes() },
                )
                SimulationThreatRow(
                    label = stringResource(R.string.launcher_vs_sim_bsd_left),
                    level = LauncherDevVehicleState.adasBsdLeft,
                    onClick = { LauncherDevVehicleState.cycleBsdLeft() },
                )
                SimulationThreatRow(
                    label = stringResource(R.string.launcher_vs_sim_bsd_right),
                    level = LauncherDevVehicleState.adasBsdRight,
                    onClick = { LauncherDevVehicleState.cycleBsdRight() },
                )
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_front_object),
                    value = LauncherDevVehicleState.adasFrontObjectM,
                    range = 0f..120f,
                    unit = "м",
                    onValueChange = { LauncherDevVehicleState.setAdasFrontObject(it) },
                )
                SimulationFrontObjectTypeRow()
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_pdc_front),
                    value = LauncherDevVehicleState.pdcFrontGroupValue(),
                    range = 0f..150f,
                    unit = "см",
                    onValueChange = { LauncherDevVehicleState.setPdcFrontGroup(it) },
                )
                SimulationSliderRow(
                    label = stringResource(R.string.launcher_vs_sim_pdc_rear),
                    value = LauncherDevVehicleState.pdcRearGroupValue(),
                    range = 0f..150f,
                    unit = "см",
                    onValueChange = { LauncherDevVehicleState.setPdcRearGroup(it) },
                )
                SimulationPdcChannelsBlock()

                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.launcher_vs_sim_lights_header),
                    color = SimulationRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_low_beam),
                    active = LauncherDevVehicleState.lowBeam,
                    onClick = { LauncherDevVehicleState.toggleLowBeam() },
                )
                SimulationToggleRow(
                    label = stringResource(R.string.launcher_vs_sim_high_beam),
                    active = LauncherDevVehicleState.highBeam,
                    onClick = { LauncherDevVehicleState.toggleHighBeam() },
                )

                Spacer(Modifier.height(4.dp))
                SimulationTireRow(
                    label = stringResource(R.string.launcher_vs_sim_tire_fl),
                    corner = LauncherWheelCorner.FL,
                )
                SimulationTireRow(
                    label = stringResource(R.string.launcher_vs_sim_tire_fr),
                    corner = LauncherWheelCorner.FR,
                )
                SimulationTireRow(
                    label = stringResource(R.string.launcher_vs_sim_tire_rl),
                    corner = LauncherWheelCorner.RL,
                )
                SimulationTireRow(
                    label = stringResource(R.string.launcher_vs_sim_tire_rr),
                    corner = LauncherWheelCorner.RR,
                )

                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SimulationRedDark)
                        .clickable { LauncherDevVehicleState.resetSimulation() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.launcher_vs_sim_reset),
                        color = LauncherColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulationToggleRow(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (active) SimulationRed else LauncherColors.TextMuted),
        )
    }
}

@Composable
private fun SimulationGearRow(
    label: String,
    active: Char?,
    onSelect: (Char?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SIM_GEAR_SLOTS.forEach { slot ->
                val selected = active == slot
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SimulationRed else LauncherColors.CardDark)
                        // Re-tap the active slot to hand control back to the real gearbox.
                        .clickable { onSelect(if (selected) null else slot) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = slot.toString(),
                        color = if (selected) {
                            LauncherColors.TextPrimary
                        } else {
                            LauncherColors.TextSecondary
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulationFrontObjectTypeRow() {
    val selected = if (LauncherDevVehicleState.adasFrontObjectM >= 1f) {
        LauncherDevVehicleState.adasFrontObjectType
    } else {
        LauncherAdasFrontObjectType.None
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.launcher_vs_sim_front_object_type),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SIM_FRONT_OBJECT_TYPES.forEach { type ->
                val active = selected == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) SimulationRed else LauncherColors.CardDark)
                        .clickable {
                            LauncherDevVehicleState.selectAdasFrontObjectType(
                                if (active) LauncherAdasFrontObjectType.None else type,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(type.labelRes),
                        color = if (active) {
                            LauncherColors.TextPrimary
                        } else {
                            LauncherColors.TextSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulationSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = LauncherColors.TextPrimary, fontSize = 14.sp)
            Text(
                text = "${sliderValue.roundToInt()} $unit",
                color = LauncherColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onValueChange(it)
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = SimulationRed,
                activeTrackColor = SimulationRed,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

/** Collapsible per-channel PDC sliders (12 ultrasonic channels). */
@Composable
private fun SimulationPdcChannelsBlock() {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.launcher_vs_sim_pdc_channels),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            null,
            tint = SimulationRed,
        )
    }
    if (!expanded) return

    val channels = listOf(
        LauncherPdcChannel.FrontSideLeft to R.string.launcher_pdc_fsl,
        LauncherPdcChannel.FrontLeft to R.string.launcher_pdc_fl,
        LauncherPdcChannel.FrontMidLeft to R.string.launcher_pdc_fml,
        LauncherPdcChannel.FrontMidRight to R.string.launcher_pdc_fmr,
        LauncherPdcChannel.FrontRight to R.string.launcher_pdc_fr,
        LauncherPdcChannel.FrontSideRight to R.string.launcher_pdc_fsr,
        LauncherPdcChannel.RearSideLeft to R.string.launcher_pdc_rsl,
        LauncherPdcChannel.RearLeft to R.string.launcher_pdc_rl,
        LauncherPdcChannel.RearMidLeft to R.string.launcher_pdc_rml,
        LauncherPdcChannel.RearMidRight to R.string.launcher_pdc_rmr,
        LauncherPdcChannel.RearRight to R.string.launcher_pdc_rr,
        LauncherPdcChannel.RearSideRight to R.string.launcher_pdc_rsr,
    )
    channels.forEach { (channel, labelRes) ->
        SimulationSliderRow(
            label = stringResource(labelRes),
            value = LauncherDevVehicleState.pdcChannelValue(channel),
            range = 0f..150f,
            unit = "см",
            onValueChange = { LauncherDevVehicleState.setPdcChannel(channel, it) },
        )
    }
}

/** Tri-state BSD row: Off → Caution (amber) → Alert (red), tap cycles. */
@Composable
private fun SimulationThreatRow(
    label: String,
    level: LauncherRearThreatLevel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        val (text, color) = when (level) {
            LauncherRearThreatLevel.Off ->
                stringResource(R.string.launcher_vs_sim_threat_off) to LauncherColors.TextMuted
            LauncherRearThreatLevel.Caution ->
                stringResource(R.string.launcher_vs_sim_threat_caution) to Color(0xFFF59E0B)
            LauncherRearThreatLevel.Alert ->
                stringResource(R.string.launcher_vs_sim_threat_alert) to SimulationRed
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = if (level == LauncherRearThreatLevel.Off) 0.25f else 0.85f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SimulationTireRow(    label: String,
    corner: LauncherWheelCorner,
) {
    val current = LauncherDevVehicleState.tirePressureOverride[corner] ?: 2.4f
    var sliderValue by remember { mutableFloatStateOf(current) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.SurfaceDark.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = LauncherColors.TextPrimary, fontSize = 14.sp)
            Text(
                text = "${(sliderValue * 10).roundToInt() / 10f} bar",
                color = LauncherColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                LauncherDevVehicleState.setTirePressure(corner, it)
            },
            valueRange = 0f..4.5f,
            colors = SliderDefaults.colors(
                thumbColor = SimulationRed,
                activeTrackColor = SimulationRed,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}
