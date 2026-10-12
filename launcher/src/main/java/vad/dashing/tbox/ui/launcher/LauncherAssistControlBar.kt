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
import vad.dashing.tbox.ui.sendToggleTjaIca
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 50.dp

/**
 * Driver Assist Systems Bar (ACC, NGP, LDW, LKA)
 * Styled proportionally to the Drive Mode Bar.
 */
@Composable
fun LauncherAssistControlBar(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val tjaIcaState by UniversalCanRepository.tjaIcaState.collectAsStateWithLifecycle()

    // 1. ACC (Cruise Control) State
    val accActive = if (LauncherDevVehicleState.simulateEnabled) {
        LauncherDevVehicleState.adasCruiseActive
    } else {
        adas.accActive || adas.accStandby || (tboxCruise ?: 0u) > 0u
    }
    val accDisplaySpeed = adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 }

    // 2. NGP State
    val ngpOn = when {
        LauncherDevVehicleState.simulateEnabled -> LauncherDevVehicleState.adasLanesActive
        tjaIcaState is MbCanBinaryState.On -> true
        else -> adas.tja == LauncherAdasAssistIcon.Active
    }

    // 3. LDW State (Lane Departure Warning)
    val ldwOn = when {
        LauncherDevVehicleState.simulateEnabled -> LauncherDevVehicleState.adasLdwWarning
        else -> adas.lkaStatusCode == 1 || adas.laneDepartureLeft || adas.laneDepartureRight
    }

    // 4. LKA State (Lane Keep Assist)
    val lkaOn = when {
        LauncherDevVehicleState.simulateEnabled -> LauncherDevVehicleState.adasLkaActive
        else -> adas.lkaStatusCode == 2 || adas.laneAssistEngaged
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: ACC / Cruise Button
        val accLabel = if (accActive && accDisplaySpeed != null && accDisplaySpeed > 0) {
            accDisplaySpeed.toString()
        } else {
            "ACC"
        }

        AssistButtonChip(
            label = accLabel,
            iconRes = R.drawable.ic_launcher_cruise,
            selected = accActive,
            activeColor = LauncherColors.AccentCyan,
            onClick = {
                LauncherCruisePresetController.launchStatusSingleTap(context)
            },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: NGP Button
        AssistButtonChip(
            label = stringResource(R.string.launcher_cruise_ngp_title),
            iconRes = R.drawable.ic_adas_steering_wheel,
            selected = ngpOn,
            activeColor = Color(0xFF10B981), // Green
            onClick = {
                if (LauncherDevVehicleState.simulateEnabled) {
                    LauncherDevVehicleState.toggleAdasLanes()
                } else {
                    sendToggleTjaIca(context)
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: LDW Button
        AssistButtonChip(
            label = "LDW",
            iconRes = R.drawable.ic_adas_lka,
            iconTint = Color(0xFF22C55E), // Green road icon
            selected = ldwOn,
            activeColor = Color(0xFFF59E0B), // Yellow/Amber
            onClick = {
                if (LauncherDevVehicleState.simulateEnabled) {
                    LauncherDevVehicleState.toggleAdasLdwWarning()
                } else {
                    val nextMode = if (ldwOn) 0 else 1
                    scope.launch {
                        UniversalCanRepository.execute(
                            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, nextMode)
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 4: LKA Button
        AssistButtonChip(
            label = "LKA",
            iconRes = R.drawable.ic_adas_lka,
            iconTint = Color(0xFF3B82F6), // Blue road icon
            selected = lkaOn,
            activeColor = Color(0xFF3B82F6), // Blue
            onClick = {
                if (LauncherDevVehicleState.simulateEnabled) {
                    LauncherDevVehicleState.toggleAdasLka()
                } else {
                    val nextMode = if (lkaOn) 0 else 2
                    scope.launch {
                        UniversalCanRepository.execute(
                            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, nextMode)
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

@Composable
private fun AssistButtonChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    iconTint: Color? = null,
) {
    val textColor = if (selected) activeColor else LauncherColors.LeftTextPrimary
    val resolvedIconTint = iconTint ?: textColor

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(resolvedIconTint),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.tboxCaption,
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
