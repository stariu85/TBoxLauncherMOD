package vad.dashing.tbox.ui.launcher

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/**
 * Clean & Large Seat Memory Panel for desktop popup window.
 * Features 3 large preset buttons (M1, M2, M3) with custom names and 2-stage long-press:
 * 1st Long Press -> Enter Mirror Adjustment Mode.
 * 2nd Long Press -> Save seat & mirror position to CAN (`eVEHICLE_SEAT_POSITION_STOREREQ`).
 * Short Tap -> Restore seat & mirror position from CAN (`eVEHICLE_SEAT_POSITION_CALLOUTREQ`).
 */
@Composable
internal fun SeatMemoryAdasPanel(
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val revision by LauncherAppConfigStore.seatMemoryAdasPanelRevisionFlow.collectAsStateWithLifecycle()

    var adjustingSlot by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(adjustingSlot) {
        if (adjustingSlot != null) {
            MbCanEngineFacade.setHardKeyCallback { keyCode, _, extra ->
                handleMirrorAdjustmentScrollKey(keyCode, extra)
            }
        } else {
            MbCanEngineFacade.setHardKeyCallback(null)
        }
        onDispose {
            MbCanEngineFacade.setHardKeyCallback(null)
        }
    }

    val presetNames = remember(context, revision) {
        listOf(
            LauncherAppConfigStore.seatMemoryPresetName(context, 1),
            LauncherAppConfigStore.seatMemoryPresetName(context, 2),
            LauncherAppConfigStore.seatMemoryPresetName(context, 3),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mirror_adjust_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_seat_memory_btn),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                    )
                    Text(
                        text = "Память водительского сиденья",
                        color = LauncherColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (adjustingSlot != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(LauncherColors.WarningAmber.copy(alpha = pulseAlpha * 0.25f))
                            .border(1.dp, LauncherColors.WarningAmber.copy(alpha = pulseAlpha), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.launcher_seat_memory_adjust_mode),
                            color = LauncherColors.WarningAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 3 Large Preset Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (1..3).forEach { slot ->
                val presetName = presetNames.getOrElse(slot - 1) { "M$slot" }
                val isAdjusting = adjustingSlot == slot
                val seatMemorySavedText = stringResource(R.string.launcher_seat_memory_saved, presetName)

                val buttonBg = when {
                    isAdjusting -> LauncherColors.WarningAmber.copy(alpha = 0.25f)
                    else -> LauncherColors.CardDarkElevated
                }

                val borderColor = when {
                    isAdjusting -> LauncherColors.WarningAmber.copy(alpha = pulseAlpha)
                    else -> Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(buttonBg)
                        .then(
                            if (isAdjusting) Modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .pointerInput(slot, isAdjusting) {
                            detectTapGestures(
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (adjustingSlot != null && !isAdjusting) {
                                        adjustingSlot = null
                                    } else if (!isAdjusting) {
                                        MbCanEngineFacade.canSetVehicleParam(
                                            MbCanKnownVehiclePropertyId.SEAT_POSITION_CALLOUTREQ,
                                            slot,
                                        )
                                    }
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isAdjusting) {
                                        MbCanEngineFacade.canSetVehicleParam(
                                            MbCanKnownVehiclePropertyId.SEAT_POSITION_STOREREQ,
                                            slot,
                                        )
                                        adjustingSlot = null
                                        Toast.makeText(
                                            context,
                                            seatMemorySavedText,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        adjustingSlot = slot
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = presetName,
                        color = if (isAdjusting) LauncherColors.WarningAmber else LauncherColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Adjustment Mode Banner
        AnimatedVisibility(
            visible = adjustingSlot != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.WarningAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.launcher_seat_memory_adjust_hint),
                    color = LauncherColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { adjustingSlot = null },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel",
                        tint = LauncherColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun handleMirrorAdjustmentScrollKey(keyCode: Int, extra: Byte) {
    // Key codes from MBHardKeyCode:
    // eMBHARDKEY_UP = 103, eMBHARDKEY_DOWN = 108, eMBHARDKEY_LEFT = 105, eMBHARDKEY_RIGHT = 106,
    // eMBHARDKEY_ANTICLOCKWISE = 114, eMBHARDKEY_CLOCKWISE = 115
    val isRightWheel = extra.toInt() == 1
    when (keyCode) {
        103, 115 -> { // UP / CLOCKWISE
            val propertyId = if (isRightWheel) {
                MbCanKnownVehiclePropertyId.MIRROR_SET_RMIRROR_UD_LOCATION
            } else {
                MbCanKnownVehiclePropertyId.MIRROR_SET_LMIRROR_UD_LOCATION
            }
            MbCanEngineFacade.canSetVehicleParam(propertyId, 1) // 1 = Up
        }
        108, 114 -> { // DOWN / ANTICLOCKWISE
            val propertyId = if (isRightWheel) {
                MbCanKnownVehiclePropertyId.MIRROR_SET_RMIRROR_UD_LOCATION
            } else {
                MbCanKnownVehiclePropertyId.MIRROR_SET_LMIRROR_UD_LOCATION
            }
            MbCanEngineFacade.canSetVehicleParam(propertyId, 2) // 2 = Down
        }
        105 -> { // LEFT
            val propertyId = if (isRightWheel) {
                MbCanKnownVehiclePropertyId.MIRROR_SET_RMIRROR_LR_LOCATION
            } else {
                MbCanKnownVehiclePropertyId.MIRROR_SET_LMIRROR_LR_LOCATION
            }
            MbCanEngineFacade.canSetVehicleParam(propertyId, 1) // 1 = Left
        }
        106 -> { // RIGHT
            val propertyId = if (isRightWheel) {
                MbCanKnownVehiclePropertyId.MIRROR_SET_RMIRROR_LR_LOCATION
            } else {
                MbCanKnownVehiclePropertyId.MIRROR_SET_LMIRROR_LR_LOCATION
            }
            MbCanEngineFacade.canSetVehicleParam(propertyId, 2) // 2 = Right
        }
    }
}

@Composable
internal fun SeatMemoryDialog(
    onDismissRequest: () -> Unit,
) {
    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_seat_memory_btn),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
                Text(
                    text = "Память водительского сиденья",
                    color = LauncherColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.ok), color = LauncherColors.AccentCyan, fontSize = 16.sp)
            }
        },
        text = {
            SeatMemoryAdasPanel(modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
internal fun PassengerSeatAdjustDialog(
    onDismissRequest: () -> Unit,
) {
    var sliderBackrest by remember { mutableFloatStateOf(0f) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val backrestDirection = when {
        sliderBackrest < -0.15f -> 1
        sliderBackrest > 0.15f -> 2
        else -> 0
    }

    LaunchedEffect(backrestDirection) {
        if (backrestDirection != 0) {
            while (true) {
                MbCanEngineFacade.canSetVehicleParam(
                    MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR,
                    backrestDirection
                )
                delay(150L)
            }
        } else {
            MbCanEngineFacade.canSetVehicleParam(
                MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR,
                0
            )
        }
    }

    val positionDirection = when {
        sliderPosition < -0.15f -> 1
        sliderPosition > 0.15f -> 2
        else -> 0
    }

    LaunchedEffect(positionDirection) {
        if (positionDirection != 0) {
            while (true) {
                MbCanEngineFacade.canSetVehicleParam(
                    MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR,
                    positionDirection
                )
                delay(150L)
            }
        } else {
            MbCanEngineFacade.canSetVehicleParam(
                MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR,
                0
            )
        }
    }

    LauncherDarkAlertDialog(
        onDismissRequest = {
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR, 0)
            MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR, 0)
            onDismissRequest()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_seat_memory_btn),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = -1f },
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
                Text(
                    text = "Регулировка пассажирского сиденья",
                    color = LauncherColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_BACK_FR, 0)
                    MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.PASSENGER_SEAT_FR, 0)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(android.R.string.ok), color = LauncherColors.AccentCyan, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Slider 1: Backrest Incline
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "◄ Вперед",
                            color = if (sliderBackrest < -0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Наклон спинки",
                            color = LauncherColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Назад ►",
                            color = if (sliderBackrest > 0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Slider(
                        value = sliderBackrest,
                        onValueChange = { newVal ->
                            sliderBackrest = newVal
                        },
                        onValueChangeFinished = {
                            sliderBackrest = 0f
                        },
                        valueRange = -1f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = LauncherColors.AccentCyan,
                            activeTrackColor = LauncherColors.AccentCyan,
                            inactiveTrackColor = LauncherColors.CardDarkElevated,
                        ),
                    )
                }

                // Slider 2: Seat Position
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "◄ Вперед",
                            color = if (sliderPosition < -0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Положение кресла",
                            color = LauncherColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Назад ►",
                            color = if (sliderPosition > 0.15f) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Slider(
                        value = sliderPosition,
                        onValueChange = { newVal ->
                            sliderPosition = newVal
                        },
                        onValueChangeFinished = {
                            sliderPosition = 0f
                        },
                        valueRange = -1f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = LauncherColors.AccentCyan,
                            activeTrackColor = LauncherColors.AccentCyan,
                            inactiveTrackColor = LauncherColors.CardDarkElevated,
                        ),
                    )
                }
            }
        },
    )
}
