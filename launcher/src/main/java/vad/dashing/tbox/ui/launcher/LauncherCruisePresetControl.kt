package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 50.dp
private const val POPUP_DISMISS_TIMEOUT_MS = 4000L

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
    val activeSpeed = tboxCruise?.toInt()?.takeIf { it > 0 } ?: adas.accSetSpeedKmh
    val engaged = adas.accActive || adas.accStandby || (tboxCruise != null && tboxCruise!! > 0u)
    val displaySpeed = if (engaged) (activeSpeed ?: lastSpeed) else lastSpeed

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Button 1: Cruise ON/OFF toggle with long-press popup for NGP & Time Gap
        CruiseToggleChip(
            engaged = engaged,
            setSpeed = displaySpeed,
            ngpEnabled = ngpEnabled,
            timeGapLevel = timeGapLevel,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CruiseToggleChip(
    engaged: Boolean,
    setSpeed: Int?,
    ngpEnabled: Boolean,
    timeGapLevel: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activeTint = LauncherColors.AccentCyan
    val inactiveTint = LauncherColors.LeftTextPrimary
    val tint = if (engaged) activeTint else inactiveTint

    var popupExpanded by remember { mutableStateOf(false) }
    var popupDismissTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(popupExpanded, popupDismissTimeMs) {
        if (!popupExpanded) return@LaunchedEffect
        while (SystemClock.uptimeMillis() < popupDismissTimeMs) {
            delay(100)
        }
        popupExpanded = false
    }

    BoxWithConstraints(modifier = modifier) {
        val chipWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
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
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        popupDismissTimeMs = SystemClock.uptimeMillis() + POPUP_DISMISS_TIMEOUT_MS
                        popupExpanded = true
                    },
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

        if (popupExpanded) {
            val density = LocalDensity.current.density
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = -(132.dp.value * density).toInt()),
                onDismissRequest = { popupExpanded = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false),
            ) {
                Column(
                    modifier = Modifier
                        .width(chipWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                popupDismissTimeMs = SystemClock.uptimeMillis() + POPUP_DISMISS_TIMEOUT_MS
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Top dropup button: Distance setting
                    CruiseDistancePopupButton(
                        onClick = {
                            val nextLevel = if (timeGapLevel >= 3) 1 else timeGapLevel + 1
                            LauncherCruisePresetController.setTimeGap(context, nextLevel)
                            popupDismissTimeMs = SystemClock.uptimeMillis() + POPUP_DISMISS_TIMEOUT_MS
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight),
                    )

                    // Middle dropup button: NGP ON/OFF toggle
                    CruiseNgpPopupButton(
                        enabled = ngpEnabled,
                        onClick = {
                            val nextNgp = !ngpEnabled
                            LauncherAppConfigStore.setNgpEnabled(context, nextNgp)
                            popupDismissTimeMs = SystemClock.uptimeMillis() + POPUP_DISMISS_TIMEOUT_MS
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight),
                    )
                }
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
