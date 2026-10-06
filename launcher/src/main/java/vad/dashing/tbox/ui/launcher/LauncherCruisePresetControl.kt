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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 50.dp

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
        LauncherAppConfigStore.cruisePresetsKmh(context).take(3)
    }
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val lastSpeed = remember(context, presetsRevision, tboxCruise) {
        LauncherAppConfigStore.lastCruiseSpeedKmh(context)
    }
    val activeSpeed = tboxCruise?.toInt()?.takeIf { it > 0 } ?: adas.accSetSpeedKmh
    val engaged = adas.accActive || adas.accStandby || (tboxCruise != null && tboxCruise!! > 0u)
    val displaySpeed = if (engaged) (activeSpeed ?: lastSpeed) else lastSpeed

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Button 1: Cruise ON/OFF toggle with target speed display
        CruiseToggleChip(
            engaged = engaged,
            setSpeed = displaySpeed,
            onClick = { LauncherCruisePresetController.toggleCruise(context) },
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Buttons 2, 3, 4: Speed preset buttons from settings
        presets.forEach { kmh ->
            val isSelected = engaged && displaySpeed == kmh
            CruisePresetChip(
                label = kmh.toString(),
                selected = isSelected,
                onClick = { LauncherCruisePresetController.applyPreset(context, kmh) },
                modifier = Modifier
                    .weight(1f)
                    .height(ButtonHeight),
            )
        }
    }
}

@Composable
private fun CruiseToggleChip(
    engaged: Boolean,
    setSpeed: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = LauncherColors.AccentCyan
    val inactiveTint = LauncherColors.LeftTextPrimary
    val tint = if (engaged) activeTint else inactiveTint

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (engaged) activeTint.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard,
            )
            .then(
                if (engaged) {
                    Modifier.border(1.5.dp, activeTint.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
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
private fun CruisePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = LauncherColors.AccentCyan
    val inactiveTint = LauncherColors.LeftTextPrimary
    val tint = if (selected) activeTint else inactiveTint

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) activeTint.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, activeTint.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
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
