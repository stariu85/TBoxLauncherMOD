package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 60.dp

internal enum class AssistChipState {
    Off,
    Standby,
    Active,
    Unavailable,
}

/**
 * Main Central Page: ACC and NGP (2 large buttons)
 */
@Composable
fun LauncherAssistControlBar(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val tjaIcaState by UniversalCanRepository.tjaIcaState.collectAsStateWithLifecycle()

    // 1. ACC (Cruise Control) State
    val accDisplaySpeed = adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 }
    val accState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasCruiseActive) AssistChipState.Active else AssistChipState.Off
        }
        adas.accActive -> AssistChipState.Active
        adas.accStandby -> AssistChipState.Standby
        (tboxCruise ?: 0u) > 0u -> AssistChipState.Active
        else -> AssistChipState.Off
    }

    // 2. NGP State
    val ngpState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasLanesActive) AssistChipState.Active else AssistChipState.Off
        }
        tjaIcaState is MbCanBinaryState.Unavailable -> AssistChipState.Unavailable
        adas.tja == LauncherAdasAssistIcon.Active -> AssistChipState.Active
        tjaIcaState is MbCanBinaryState.On || adas.tja == LauncherAdasAssistIcon.Dark -> AssistChipState.Standby
        else -> AssistChipState.Off
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: ACC / Cruise Button
        val accLabel = if (accState != AssistChipState.Off && accDisplaySpeed != null && accDisplaySpeed > 0) {
            accDisplaySpeed.toString()
        } else {
            "ACC"
        }

        AssistButtonChip(
            label = accLabel,
            iconRes = R.drawable.ic_launcher_cruise,
            state = accState,
            onClick = {
                LauncherCruisePresetController.launchStatusSingleTap(context)
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: NGP Button
        AssistButtonChip(
            label = stringResource(R.string.launcher_cruise_ngp_title),
            iconRes = R.drawable.ic_adas_steering_wheel,
            state = ngpState,
            onClick = {
                if (ngpState == AssistChipState.Unavailable) return@AssistButtonChip
                LauncherCruisePresetController.launchNgpWithSetMinus(context)
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

/**
 * Left Page (Swipe Right from Main): LDW and LKA (2 large buttons)
 */
@Composable
fun LauncherLanesControlBar(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val isLasUnavailable = adas.lasMode == -1 && !LauncherDevVehicleState.simulateEnabled

    // 1. LDW State (Lane Departure Warning)
    val ldwState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasLdwWarning || LauncherDevVehicleState.adasLanesActive) AssistChipState.Active else AssistChipState.Off
        }
        isLasUnavailable -> AssistChipState.Unavailable
        adas.lasMode == 1 || adas.lkaStatusCode == 1 -> {
            if (adas.leftLane != LauncherAdasLaneVisualization.Hidden || adas.rightLane != LauncherAdasLaneVisualization.Hidden) AssistChipState.Active else AssistChipState.Standby
        }
        else -> AssistChipState.Off
    }

    // 2. LKA State (Lane Keep Assist)
    val lkaState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasLkaActive) AssistChipState.Active else AssistChipState.Off
        }
        isLasUnavailable -> AssistChipState.Unavailable
        adas.lasMode == 2 || adas.lkaStatusCode == 2 -> {
            if (adas.leftLane != LauncherAdasLaneVisualization.Hidden || adas.rightLane != LauncherAdasLaneVisualization.Hidden) AssistChipState.Active else AssistChipState.Standby
        }
        else -> AssistChipState.Off
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: LDW Button
        AssistButtonChip(
            label = "LDW",
            iconRes = R.drawable.ic_adas_lka,
            state = ldwState,
            onClick = {
                if (ldwState == AssistChipState.Unavailable) return@AssistButtonChip
                if (LauncherDevVehicleState.simulateEnabled) {
                    LauncherDevVehicleState.toggleAdasLdwWarning()
                } else {
                    val isLdwOn = adas.lasMode == 1 || ldwState == AssistChipState.Active || ldwState == AssistChipState.Standby
                    val nextMode = if (isLdwOn) 0 else 1
                    scope.launch {
                        UniversalCanRepository.execute(
                            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, nextMode)
                        )
                    }
                }
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: LKA Button
        AssistButtonChip(
            label = "LKA",
            iconRes = R.drawable.ic_adas_lka,
            state = lkaState,
            onClick = {
                if (lkaState == AssistChipState.Unavailable) return@AssistButtonChip
                if (LauncherDevVehicleState.simulateEnabled) {
                    LauncherDevVehicleState.toggleAdasLka()
                } else {
                    val isLkaOn = adas.lasMode == 2 || lkaState == AssistChipState.Active || lkaState == AssistChipState.Standby
                    val nextMode = if (isLkaOn) 0 else 2
                    scope.launch {
                        UniversalCanRepository.execute(
                            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, nextMode)
                        )
                    }
                }
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

@Composable
private fun AssistButtonChip(
    label: String,
    state: AssistChipState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    iconSize: Dp = 22.dp,
    fontSize: TextUnit = 19.sp,
) {
    val activeGreen = Color(0xFF10B981)
    val unavailableRed = Color(0xFFEF4444)
    val standbyTextColor = if (LauncherThemeState.darkTheme) Color.White else Color(0xFF374151)

    val (textColor, bg, border) = when (state) {
        AssistChipState.Active -> Triple(
            activeGreen,
            LauncherColors.LeftPanelCard, // Standard card background
            activeGreen,                  // Green border!
        )
        AssistChipState.Standby -> Triple(
            standbyTextColor,             // Graphite in light theme, White in dark theme!
            LauncherColors.LeftPanelCard, // Same card background as Active!
            activeGreen,                  // Same green border as Active!
        )
        AssistChipState.Unavailable -> Triple(
            unavailableRed,
            LauncherColors.LeftPanelCard,
            unavailableRed,                         // Red border when system is unavailable!
        )
        AssistChipState.Off -> Triple(
            LauncherColors.LeftTextPrimary,
            LauncherColors.LeftPanelCard,
            Color.Transparent,
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (state != AssistChipState.Off) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = ColorFilter.tint(textColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.tboxCaption,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
