package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.refreshHvacTemperaturesFromMbCan
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendSetMbCanProperty
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleHvacDefrosterFront
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString
import kotlin.math.abs

private val HvacOnColor = Color(0xFF4FC3F7)
private val SeatHeatOnColor = Color(0xFFF59E0B)
private val HvacOffColor = LauncherColors.TextSecondary
private const val LAUNCHER_BOTTOM_BAR_CAN_SOURCE = "launcher-bottom-bar"
private const val SEAT_COMMAND_CONFIRM_TIMEOUT_MS = 2_500L
private const val LIGHT_COMMAND_CONFIRM_TIMEOUT_MS = 1_200L

private val LocalDockIconScale = compositionLocalOf { 1f }

@Composable
private fun dockDp(base: Float) = (base * LocalDockIconScale.current).dp

internal data class LauncherPendingSeatCommand(
    val raw: Int,
    val sequence: Long,
)

private fun formatHvacSetTemp(celsius: Float?): String {
    if (celsius == null) return "—"
    val rounded = (celsius * 2f).toInt() / 2f
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        rounded.toInt().toString()
    } else {
        valueToString(rounded, 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherBottomBar(
    @Suppress("UNUSED_PARAMETER") canViewModel: CanDataViewModel,
    onCloseVehicleSettings: () -> Unit = {},
    onOpenVehicleSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") configRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bottomBarRevision by LauncherAppConfigStore.bottomBarHeightRevisionFlow.collectAsStateWithLifecycle()
    val bottomBarHeightDp = remember(context, bottomBarRevision) {
        LauncherAppConfigStore.bottomBarHeightDp(context)
    }
    val dockScaleRevision by LauncherAppConfigStore.dockIconScaleRevisionFlow
        .collectAsStateWithLifecycle()
    val dockScale = remember(context, dockScaleRevision) {
        LauncherAppConfigStore.dockIconScale(context)
    }
    val navButtonsRevision by LauncherAppConfigStore.navButtonsRevisionFlow.collectAsStateWithLifecycle()
    val navButtonsVisible = remember(context, navButtonsRevision) {
        LauncherAppConfigStore.navButtonsVisible(context)
    }
    val climateRevision by LauncherAppConfigStore.climateControlsRevisionFlow.collectAsStateWithLifecycle()
    val climateVisible = remember(context, climateRevision) {
        LauncherAppConfigStore.climateControlsVisible(context)
    }
    val climateScale = remember(context, climateRevision) {
        LauncherAppConfigStore.climateControlsScale(context)
    }
    val bottomSlotsRevision by LauncherAppConfigStore.bottomSlotsRevisionFlow.collectAsStateWithLifecycle()
    val slotsUnified = remember(context, bottomSlotsRevision, navButtonsVisible) {
        val raw = LauncherAppConfigStore.bottomSlotsUnified(context)
        if (navButtonsVisible) {
            if ("home_nav" !in raw && "back_nav" !in raw) {
                listOf("home_nav", "back_nav") + raw.take(raw.size - 2)
            } else {
                raw
            }
        } else {
            raw.filter { it != "home_nav" && it != "back_nav" }
        }
    }

    val localView = LocalView.current

    CompositionLocalProvider(LocalDockIconScale provides dockScale) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeightDp.dp)
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.bottomBarTopPx = rect.top.toInt()
                val location = IntArray(2)
                localView.getLocationOnScreen(location)
                LauncherEmbeddedBoundsState.bottomBarTopOnScreenPx = location[1]
                LauncherEmbeddedBoundsState.bottomBarHeightOnScreenPx = localView.height
            }
            .background(LauncherColors.BottomBarBg)
            .padding(horizontal = 12.dp),
    ) {
        if (climateVisible) {
            CompositionLocalProvider(LocalDockIconScale provides (dockScale * climateScale)) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(dockDp(4f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slotsUnified.forEachIndexed { index, itemId ->
                        if (itemId != null) {
                            LauncherDraggableUnifiedSlot(
                                itemId = itemId,
                                context = context,
                                canViewModel = canViewModel,
                                index = index,
                                onCloseVehicleSettings = onCloseVehicleSettings,
                                onOpenVehicleSettings = onOpenVehicleSettings,
                            )
                        } else {
                            Box(modifier = Modifier.size(dockDp(44f)))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun LauncherDraggableUnifiedSlot(
    itemId: String,
    context: Context,
    canViewModel: CanDataViewModel,
    index: Int,
    onCloseVehicleSettings: () -> Unit,
    onOpenVehicleSettings: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val slotWidthDp = when (itemId) {
        "temp_driver", "temp_pass" -> 88.dp
        else -> 44.dp
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.15f
                    scaleY = 1.15f
                    shadowElevation = 10f
                }
            }
            .pointerInput(itemId, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        offsetX = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        val slotWidthPx = slotWidthDp.toPx()
                        val slotsShift = (offsetX / slotWidthPx).roundToInt()
                        if (slotsShift != 0) {
                            LauncherAppConfigStore.reorderUnifiedSlot(
                                context = context,
                                itemId = itemId,
                                currentIndex = index,
                                slotsShift = slotsShift,
                            )
                        }
                        offsetX = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LauncherBottomDockButtonContent(
            buttonId = itemId,
            context = context,
            canViewModel = canViewModel,
            onCloseVehicleSettings = onCloseVehicleSettings,
            onOpenVehicleSettings = onOpenVehicleSettings,
        )
    }
}

@Composable
private fun LauncherBottomDockButtonContent(
    buttonId: String,
    context: Context,
    canViewModel: CanDataViewModel,
    onCloseVehicleSettings: () -> Unit,
    onOpenVehicleSettings: () -> Unit,
) {
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacDefrost by UniversalCanRepository.hvacDefrosterFrontState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val windscreenHeat by UniversalCanRepository.frontWindscreenHeatState.collectAsStateWithLifecycle()
    val rearDefrost by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()
    val leftSeatMode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val rightSeatMode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val driverTemp by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val passTemp by canViewModel.climateSetTemperature2.collectAsStateWithLifecycle()
    val leftRaw = launcherSeatModeToRaw(leftSeatMode) ?: 1
    val rightRaw = launcherSeatModeToRaw(rightSeatMode) ?: 1

    when (buttonId) {
        "home_nav" -> LauncherDockIcon(
            onClick = {
                goLauncherHome(
                    context = context,
                    onCloseOverlays = { onCloseVehicleSettings() },
                )
            },
            onLongClick = onOpenVehicleSettings,
        ) {
            Icon(Icons.Filled.Home, stringResource(R.string.launcher_home_cd), tint = LauncherColors.AccentCyan)
        }
        "back_nav" -> LauncherDockIcon(
            onClick = {
                goLauncherBack(
                    context = context,
                    vehicleSettingsOpen = LauncherVehicleSettingsUiState.open,
                    onCloseVehicleSettings = onCloseVehicleSettings,
                )
            },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.launcher_back_cd), tint = LauncherColors.TextSecondary)
        }
        "temp_driver" -> LauncherTempStepper(
            tempText = formatHvacSetTemp(driverTemp),
            onDown = { sendAdjustHvacTemperature(context, driverTemp, -0.5f, HvacTempZone.Driver) },
            onUp = { sendAdjustHvacTemperature(context, driverTemp, 0.5f, HvacTempZone.Driver) },
        )
        "temp_pass" -> LauncherTempStepper(
            tempText = formatHvacSetTemp(passTemp),
            onDown = { sendAdjustHvacTemperature(context, passTemp, -0.5f, HvacTempZone.Passenger) },
            onUp = { sendAdjustHvacTemperature(context, passTemp, 0.5f, HvacTempZone.Passenger) },
        )
        "hvac_auto" -> LauncherDockIcon(onClick = { sendToggleHvacAuto(context) }) {
            LauncherHvacIcon(R.drawable.ic_widget_hvac_auto, hvacAuto)
        }
        "seat_heat_left" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, leftRaw)
        }) {
            LauncherSeatHeatIcon(raw = leftRaw)
        }
        "seat_vent_left" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, leftRaw)
        }) {
            LauncherSeatVentIcon(raw = leftRaw)
        }
        "recirc" -> LauncherDockIcon(onClick = { sendToggleHvacAirRecirculation(context) }) {
            LauncherHvacIcon(R.drawable.ic_widget_hvac_air_recirculation, hvacRecirc)
        }
        "seat_heat_right" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, rightRaw)
        }) {
            LauncherSeatHeatIcon(raw = rightRaw, mirrored = true)
        }
        "seat_vent_right" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, rightRaw)
        }) {
            LauncherSeatVentIcon(raw = rightRaw, mirrored = true)
        }
        "steering_heat" -> LauncherDockIcon(onClick = { sendToggleSteeringWheelHeat(context) }) {
            LauncherBinaryTintIcon(R.drawable.ic_widget_steering_wheel_heat, steeringHeat is MbCanBinaryState.On)
        }
        "windscreen_heat" -> LauncherDockIcon(onClick = { sendToggleFrontWindscreenHeat(context) }) {
            LauncherBinaryTintIcon(R.drawable.ic_widget_front_windscreen_heat, windscreenHeat is MbCanBinaryState.On)
        }
        "front_defrost" -> LauncherDockIcon(onClick = { sendToggleHvacDefrosterFront(context) }) {
            LauncherHvacIcon(R.drawable.ic_widget_hvac_defroster_front, hvacDefrost)
        }
        "rear_defrost" -> LauncherDockIcon(onClick = { sendToggleRearWindowMirrorsDefrost(context) }) {
            LauncherBinaryTintIcon(R.drawable.ic_widget_rear_window_mirrors_defrost, rearDefrost is MbCanBinaryState.On)
        }
        else -> if (buttonId.startsWith("pkg:")) {
            val pkg = buttonId.removePrefix("pkg:")
            LauncherDockIcon(onClick = { launchLauncherApp(context, pkg) }) {
                Image(
                    painter = painterResource(R.drawable.ic_widget_seat),
                    contentDescription = null,
                    modifier = Modifier.size(dockDp(26f)),
                )
            }
        }
    }
}

@Composable
private fun LauncherTempStepper(
    tempText: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        LauncherDockIcon(onClick = onDown) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = LauncherColors.TextSecondary,
                modifier = Modifier.size(dockDp(22f)),
            )
        }
        Text(
            text = tempText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 1.dp),
        )
        LauncherDockIcon(onClick = onUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                null,
                tint = LauncherColors.TextSecondary,
                modifier = Modifier.size(dockDp(22f)),
            )
        }
    }
}

@Composable
private fun LauncherHvacIcon(drawableRes: Int, state: MbCanBinaryState) {
    val tint = when (state) {
        is MbCanBinaryState.On -> HvacOnColor
        is MbCanBinaryState.Off -> HvacOffColor
        else -> HvacOffColor.copy(alpha = 0.55f)
    }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(dockDp(26f)),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun LauncherSeatModeIcon(
    drawableRes: Int,
    active: Boolean,
    level: Int,
    onColor: Color,
) {
    Box(modifier = Modifier.size(dockDp(30f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier.size(dockDp(26f)),
            colorFilter = ColorFilter.tint(if (active) onColor else HvacOffColor),
        )
        if (level > 0) {
            Text(
                text = level.toString(),
                color = LauncherColors.CanvasDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(5.dp))
                    .background(onColor)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * Seat heat: base seat + OEM heat waves (ic_widget_seat_heat_1..3), amber.
 * Level lights progressively more waves, like ventilation blades.
 */
@Composable
private fun LauncherSeatHeatIcon(raw: Int, mirrored: Boolean = false) {
    val level = heatLevel(raw)
    Box(modifier = Modifier.size(dockDp(30f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .size(dockDp(26f))
                .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            colorFilter = ColorFilter.tint(if (level > 0) SeatHeatOnColor else HvacOffColor),
        )
        listOf(
            R.drawable.ic_widget_seat_heat_1 to (level >= 1),
            R.drawable.ic_widget_seat_heat_2 to (level >= 2),
            R.drawable.ic_widget_seat_heat_3 to (level >= 3),
        ).forEach { (drawable, enabled) ->
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier
                    .size(dockDp(26f))
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(if (enabled) SeatHeatOnColor else HvacOffColor),
            )
        }
    }
}

@Composable
private fun LauncherSeatVentIcon(raw: Int, mirrored: Boolean = false) {
    val level = ventLevel(raw)
    Box(modifier = Modifier.size(dockDp(30f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .size(dockDp(26f))
                .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            colorFilter = ColorFilter.tint(if (level > 0) HvacOnColor else HvacOffColor),
        )
        listOf(
            R.drawable.ic_widget_seat_vent_0 to (level >= 1),
            R.drawable.ic_widget_seat_vent_1 to (level >= 1),
            R.drawable.ic_widget_seat_vent_2 to (level >= 2),
            R.drawable.ic_widget_seat_vent_3 to (level >= 3),
        ).forEach { (drawable, enabled) ->
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier
                    .size(dockDp(26f))
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(if (enabled) HvacOnColor else HvacOffColor),
            )
        }
    }
}

@Composable
private fun LauncherBinaryTintIcon(drawableRes: Int, active: Boolean) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(dockDp(26f)),
        colorFilter = ColorFilter.tint(if (active) HvacOnColor else HvacOffColor),
    )
}

@Composable
private fun LauncherHeadlightsModeIcon(raw: Int) {
    val active = raw != LIGHT_CONTROL_OFF
    val mode = when (raw) {
        LIGHT_CONTROL_POSITION -> "Г"
        LIGHT_CONTROL_LOW_BEAM -> "Б"
        LIGHT_CONTROL_AUTO -> "A"
        else -> "0"
    }
    Box(modifier = Modifier.size(dockDp(30f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_headlights),
            contentDescription = stringResource(R.string.launcher_vs_headlights),
            modifier = Modifier.size(dockDp(27f)),
            colorFilter = ColorFilter.tint(if (active) HvacOnColor else HvacOffColor),
        )
        Text(
            text = mode,
            color = if (active) LauncherColors.CanvasDark else LauncherColors.TextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(5.dp))
                .background(if (active) HvacOnColor else LauncherColors.CardDarkElevated)
                .padding(horizontal = 3.dp),
        )
    }
}

internal fun launcherSeatModeToRaw(mode: MbCanSeatModeState): Int? = when (mode) {
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

internal fun nextSeatHeatRaw(current: Int): Int = when (current) {
    4 -> 3
    3 -> 2
    2 -> 1
    else -> 4
}

internal fun nextSeatVentRaw(current: Int): Int = when (current) {
    7 -> 6
    6 -> 5
    5 -> 1
    else -> 7
}

private fun nextLightControlRaw(current: Int): Int = when (current) {
    LIGHT_CONTROL_OFF -> LIGHT_CONTROL_POSITION
    LIGHT_CONTROL_POSITION -> LIGHT_CONTROL_LOW_BEAM
    LIGHT_CONTROL_LOW_BEAM -> LIGHT_CONTROL_AUTO
    else -> LIGHT_CONTROL_OFF
}

internal fun heatLevel(raw: Int): Int = when (raw) {
    4 -> 3
    3 -> 2
    2 -> 1
    else -> 0
}

internal fun ventLevel(raw: Int): Int = when (raw) {
    7 -> 3
    6 -> 2
    5 -> 1
    else -> 0
}

internal fun seatVentDrawable(raw: Int): Int = when (ventLevel(raw)) {
    3 -> R.drawable.ic_widget_seat_vent_3
    2 -> R.drawable.ic_widget_seat_vent_2
    1 -> R.drawable.ic_widget_seat_vent_1
    else -> R.drawable.ic_widget_seat_vent_0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherDockIcon(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(dockDp(44f))
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
