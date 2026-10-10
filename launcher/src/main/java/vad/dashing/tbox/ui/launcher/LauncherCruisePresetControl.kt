package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 50.dp
private const val SUBMODE_DISMISS_TIMEOUT_MS = 4000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCruisePresetControl(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val presetsRevision by LauncherAppConfigStore.cruisePresetsRevisionFlow
        .collectAsStateWithLifecycle()
    val ngpRevision by LauncherAppConfigStore.ngpRevisionFlow
        .collectAsStateWithLifecycle()
    val presets = remember(context, presetsRevision) {
        LauncherAppConfigStore.cruisePresetsKmh(context).take(3)
    }
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val lastSpeed = remember(context, presetsRevision, tboxCruise) {
        LauncherAppConfigStore.lastCruiseSpeedKmh(context)
    }
    val ngpEnabled = remember(context, ngpRevision) {
        LauncherAppConfigStore.ngpEnabled(context)
    }
    val timeGapLevel = remember(context, presetsRevision, adas.timeGapLevel) {
        adas.timeGapLevel?.takeIf { it in 1..3 } ?: LauncherAppConfigStore.timeGapLevel(context)
    }
    val carSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val carSpeedAccurate by canViewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val currentVehicleSpeedKmh = remember(carSpeed, carSpeedAccurate) {
        val raw = carSpeedAccurate ?: carSpeed
        raw?.roundToIntOrNull()?.coerceIn(0, 260) ?: 0
    }

    val activeSpeed = if (LauncherDevVehicleState.simulateEnabled) {
        lastSpeed
    } else {
        adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 } ?: lastSpeed
    }
    val engaged = if (LauncherDevVehicleState.simulateEnabled) {
        LauncherDevVehicleState.adasCruiseActive
    } else {
        adas.accActive || adas.accStandby
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
    val effectiveEngaged = engaged || pendingTargetSpeed != null
    val displaySpeed = if (effectiveEngaged) {
        currentTargetSpeed
    } else {
        if (currentVehicleSpeedKmh > 0) currentVehicleSpeedKmh else lastSpeed
    }

    val chipState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasCruiseActive) CruiseChipState.Active else CruiseChipState.Off
        }
        adas.accActive || pendingTargetSpeed != null -> CruiseChipState.Active
        adas.accStandby -> CruiseChipState.Standby
        else -> CruiseChipState.Off
    }

    var subModeExpanded by remember { mutableStateOf(false) }
    var popupDismissTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(subModeExpanded, popupDismissTimeMs) {
        if (!subModeExpanded) return@LaunchedEffect
        while (SystemClock.uptimeMillis() < popupDismissTimeMs) {
            delay(100)
        }
        subModeExpanded = false
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: Main Cruise ON/OFF Button (long press toggles NGP & DIST sub-mode)
        CruiseToggleChip(
            chipState = chipState,
            setSpeed = displaySpeed,
            onClick = {
                if (chipState != CruiseChipState.Off) {
                    pendingTargetSpeed = null
                    LauncherCruisePresetController.toggleCruise(context)
                } else {
                    val target = if (currentVehicleSpeedKmh >= 30) currentVehicleSpeedKmh else lastSpeed
                    pendingTargetSpeed = target
                    LauncherCruisePresetController.toggleCruise(context, currentVehicleSpeedKmh)
                }
                if (subModeExpanded) {
                    popupDismissTimeMs = SystemClock.uptimeMillis() + SUBMODE_DISMISS_TIMEOUT_MS
                }
            },
            onLongClick = {
                subModeExpanded = true
                popupDismissTimeMs = SystemClock.uptimeMillis() + SUBMODE_DISMISS_TIMEOUT_MS
            },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: Smooth crossfade between Speed Presets and NGP + DIST sub-menu
        Crossfade(
            targetState = subModeExpanded,
            animationSpec = tween(300),
            modifier = Modifier
                .weight(3f)
                .height(ButtonHeight),
            label = "cruiseSubModeCrossfade",
        ) { isSubMode ->
            if (isSubMode) {
                // NGP and DIST buttons sharing remaining space equally
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CruiseNgpPopupButton(
                        enabled = ngpEnabled,
                        onClick = {
                            val nextNgp = !ngpEnabled
                            LauncherCruisePresetController.setNgpEnabled(context, nextNgp)
                            popupDismissTimeMs = SystemClock.uptimeMillis() + SUBMODE_DISMISS_TIMEOUT_MS
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonHeight),
                    )

                    CruiseDistancePopupButton(
                        onClick = {
                            val nextLevel = if (timeGapLevel >= 3) 1 else timeGapLevel + 1
                            LauncherCruisePresetController.setTimeGap(context, nextLevel)
                            popupDismissTimeMs = SystemClock.uptimeMillis() + SUBMODE_DISMISS_TIMEOUT_MS
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonHeight),
                    )
                }
            } else {
                // Default 3 Speed Preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    presets.forEach { kmh ->
                        val isSelected = chipState != CruiseChipState.Off && currentTargetSpeed == kmh
                        CruisePresetChip(
                            label = kmh.toString(),
                            chipState = chipState,
                            isSelected = isSelected,
                            onClick = {
                                pendingTargetSpeed = kmh
                                LauncherCruisePresetController.applyPreset(context, kmh)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(ButtonHeight),
                        )
                    }
                }
            }
        }
    }
}

private enum class CruiseChipState {
    Off,
    Standby,
    Active,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CruiseToggleChip(
    chipState: CruiseChipState,
    setSpeed: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tint, bg, border) = when (chipState) {
        CruiseChipState.Active -> Triple(
            LauncherColors.AccentCyan,
            LauncherColors.AccentCyan.copy(alpha = 0.25f),
            LauncherColors.AccentCyan.copy(alpha = 0.85f),
        )
        CruiseChipState.Standby -> Triple(
            Color(0xFFD1D5DB),
            Color(0xFF4B5563).copy(alpha = 0.45f),
            Color(0xFF9CA3AF).copy(alpha = 0.75f),
        )
        CruiseChipState.Off -> Triple(
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
                if (chipState != CruiseChipState.Off) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_cruise),
                contentDescription = stringResource(R.string.launcher_cruise_presets),
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(tint),
            )
            if (setSpeed != null && setSpeed > 0) {
                Text(
                    text = setSpeed.toString(),
                    style = MaterialTheme.typography.tboxCaption,
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CruiseNgpPopupButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = LauncherColors.AccentCyan
    val inactiveTint = LauncherColors.LeftTextPrimary
    val tint = if (enabled) activeTint else inactiveTint

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) activeTint.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard,
            )
            .then(
                if (enabled) {
                    Modifier.border(1.5.dp, activeTint.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.launcher_cruise_ngp_title),
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CruiseDistancePopupButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = LauncherColors.AccentCyan

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(activeTint.copy(alpha = 0.25f))
            .border(1.5.dp, activeTint.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DIST",
            style = MaterialTheme.typography.tboxCaption,
            color = activeTint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CruisePresetChip(
    label: String,
    chipState: CruiseChipState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tint, bg, border) = when {
        isSelected && chipState == CruiseChipState.Active -> Triple(
            LauncherColors.AccentCyan,
            LauncherColors.AccentCyan.copy(alpha = 0.25f),
            LauncherColors.AccentCyan.copy(alpha = 0.85f),
        )
        isSelected && chipState == CruiseChipState.Standby -> Triple(
            Color(0xFFD1D5DB),
            Color(0xFF4B5563).copy(alpha = 0.45f),
            Color(0xFF9CA3AF).copy(alpha = 0.75f),
        )
        else -> Triple(
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
                if (isSelected) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
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
            color = tint,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Float.roundToIntOrNull(): Int? =
    if (this.isFinite()) kotlin.math.round(this).toInt() else null
