package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 60.dp

@Composable
fun LauncherCruisePresetControl(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val presetsRevision by LauncherAppConfigStore.cruisePresetsRevisionFlow
        .collectAsStateWithLifecycle()
    val presets = remember(context, presetsRevision) {
        LauncherAppConfigStore.cruisePresetsKmh(context).take(4)
    }
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val lastSpeed = remember(context, presetsRevision, tboxCruise) {
        LauncherAppConfigStore.lastCruiseSpeedKmh(context)
    }
    val adjustingTargetSpeed by LauncherCruisePresetController.adjustingTargetSpeed.collectAsStateWithLifecycle()

    val activeSpeed = if (LauncherDevVehicleState.simulateEnabled) {
        lastSpeed
    } else {
        adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 } ?: lastSpeed
    }

    var pendingTargetSpeed by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(activeSpeed) {
        if (activeSpeed != null && pendingTargetSpeed == activeSpeed) {
            pendingTargetSpeed = null
        }
    }

    LaunchedEffect(pendingTargetSpeed) {
        if (pendingTargetSpeed != null) {
            delay(3500L)
            pendingTargetSpeed = null
        }
    }

    val currentTargetSpeed = pendingTargetSpeed ?: activeSpeed ?: lastSpeed

    val chipState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasCruiseActive) CruiseChipState.Active else CruiseChipState.Off
        }
        adas.accActive || pendingTargetSpeed != null -> CruiseChipState.Active
        adas.accStandby -> CruiseChipState.Standby
        else -> CruiseChipState.Off
    }

    LauncherCruiseSpeedPresetsPanel(
        presets = presets,
        chipState = chipState,
        currentTargetSpeed = currentTargetSpeed,
        adjustingTargetSpeed = adjustingTargetSpeed,
        onPresetSelected = { kmh ->
            pendingTargetSpeed = kmh
            LauncherCruisePresetController.launchEngageToTarget(context, kmh)
        },
        onPresetLongClick = {
            LauncherCruisePresetController.launchFullOff(context)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Dedicated Speed Presets Panel with 4 round speed preset buttons styled in ADAS panel style.
 * Calculates exact equal spacing between buttons and outer panel/screen edges for any width.
 */
@Composable
internal fun LauncherCruiseSpeedPresetsPanel(
    presets: List<Int>,
    chipState: CruiseChipState,
    currentTargetSpeed: Int?,
    adjustingTargetSpeed: Int?,
    onPresetSelected: (Int) -> Unit,
    onPresetLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        val innerWidthDp = maxWidth
        val fullPanelWidthDp = innerWidthDp + 16.dp // accounts for 8.dp outer container padding on left & right
        val totalButtonsWidthDp = ButtonHeight * presets.size

        val equalGapDp = (fullPanelWidthDp - totalButtonsWidthDp) / (presets.size + 1).toFloat()
        val innerSpacingDp = equalGapDp.coerceAtLeast(2.dp)
        val sidePaddingDp = (equalGapDp - 8.dp).coerceAtLeast(0.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .padding(horizontal = sidePaddingDp),
            horizontalArrangement = Arrangement.spacedBy(innerSpacingDp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { kmh ->
                val isAdjusting = adjustingTargetSpeed == kmh
                val isSelected = (chipState != CruiseChipState.Off && currentTargetSpeed == kmh) || isAdjusting
                val activeChipState = if (isAdjusting) CruiseChipState.Active else chipState
                CruisePresetChip(
                    label = kmh.toString(),
                    chipState = activeChipState,
                    isSelected = isSelected,
                    onClick = { onPresetSelected(kmh) },
                    onLongClick = onPresetLongClick,
                    modifier = Modifier.size(ButtonHeight),
                )
            }
        }
    }
}

internal enum class CruiseChipState {
    Off,
    Standby,
    Active,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CruisePresetChip(
    label: String,
    chipState: CruiseChipState,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeGreen = Color(0xFF10B981)
    val (tint, bg, border) = when {
        isSelected && chipState == CruiseChipState.Active -> Triple(
            activeGreen,
            LauncherColors.LeftPanelCard,
            activeGreen,
        )
        isSelected && chipState == CruiseChipState.Standby -> Triple(
            activeGreen,
            Color(0xFF4B5563).copy(alpha = 0.45f),
            activeGreen,
        )
        else -> Triple(
            LauncherColors.LeftTextPrimary,
            LauncherColors.LeftPanelCard,
            Color.Transparent,
        )
    }

    Box(
        modifier = modifier
            .size(ButtonHeight)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, border, CircleShape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
