package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 50.dp

@Composable
fun LauncherDriveModeBar(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
    onModeSelected: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    // Gear states
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()

    val simGearOverride = LauncherDevVehicleState.gearSlotOverride
    val parsedGear = simGearOverride ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    var latchedGear by remember { mutableStateOf<Char?>(null) }
    SideEffect {
        if (parsedGear != null) latchedGear = parsedGear
    }
    val activeGear = parsedGear ?: latchedGear

    // Drive mode states
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()

    val resolvedLabel = remember(driveModeRaw, driveModeWetRaw, gearBoxDriveMode) {
        resolveDriveModeDisplayLabel(
            driveModeRaw = driveModeRaw,
            driveModeWetRaw = driveModeWetRaw,
            gearBoxDriveMode = gearBoxDriveMode,
        ).uppercase()
    }

    var pendingMode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resolvedLabel) {
        pendingMode = null
    }
    val activeMode = pendingMode ?: resolvedLabel

    val isEco = activeMode == "ECO"
    val isNor = activeMode == "NOR" || activeMode == "NORMAL" || activeMode == "COMFORT"
    val isSpt = activeMode == "SPT" || activeMode == "SPORT"

    val onSelectMode: (String) -> Unit = { mode ->
        pendingMode = mode
        onModeSelected?.invoke(mode)
        scope.launch {
            val isWet6Dct = UniversalCanRepository.carSettingsDriveMode6dctWet.value != null
            val (propertyId, value) = when (mode) {
                "ECO" -> if (isWet6Dct) {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 1
                } else {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 2
                }
                "NOR" -> if (isWet6Dct) {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 2
                } else {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 0
                }
                "SPT" -> if (isWet6Dct) {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 0
                } else {
                    MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 1
                }
                else -> MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 0
            }
            UniversalCanRepository.execute(MbCanCommand.SetProperty(propertyId, value))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: Active Gear Indicator
        GearIndicatorChip(
            activeGear = activeGear,
            currentGearNum = gearBoxCurrentGear,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: ECO Button
        DriveModeChip(
            label = "ECO",
            selected = isEco,
            activeColor = Color(0xFF16A34A), // Green
            onClick = { onSelectMode("ECO") },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: NOR Button
        DriveModeChip(
            label = "NOR",
            selected = isNor,
            activeColor = Color(0xFF3B82F6), // Blue
            onClick = { onSelectMode("NOR") },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 4: SPT Button
        DriveModeChip(
            label = "SPT",
            selected = isSpt,
            activeColor = Color(0xFFEF4444), // Red
            onClick = { onSelectMode("SPT") },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

@Composable
private fun GearIndicatorChip(
    activeGear: Char?,
    currentGearNum: Int?,
    modifier: Modifier = Modifier,
) {
    val gearText = remember(activeGear, currentGearNum) {
        if (activeGear == 'D') {
            if (currentGearNum != null && currentGearNum > 0) {
                "D $currentGearNum"
            } else {
                "D"
            }
        } else {
            activeGear?.toString() ?: "-"
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.LeftPanelCard),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = gearText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.LeftTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DriveModeChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (selected) activeColor else LauncherColors.LeftTextPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) activeColor.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, activeColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
