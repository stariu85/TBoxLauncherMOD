package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Popup
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

internal data class LauncherDockButtonDescriptor(
    val id: String,
    val title: String,
    val iconRes: Int? = null,
)

internal val ALL_CLIMATE_DOCK_BUTTONS = listOf(
    LauncherDockButtonDescriptor("seat_heat_left", "Подогрев водителя", R.drawable.ic_widget_seat_heat_left),
    LauncherDockButtonDescriptor("seat_vent_left", "Вентиляция водителя", R.drawable.ic_widget_seat_vent_3),
    LauncherDockButtonDescriptor("temp_driver", "Температура водителя", R.drawable.ic_widget_hvac_auto),
    LauncherDockButtonDescriptor("hvac_auto", "Климат AUTO", R.drawable.ic_widget_hvac_auto),
    LauncherDockButtonDescriptor("temp_pass", "Температура пассажира", R.drawable.ic_widget_hvac_auto),
    LauncherDockButtonDescriptor("seat_heat_right", "Подогрев пассажира", R.drawable.ic_widget_seat_heat_right),
    LauncherDockButtonDescriptor("seat_vent_right", "Вентиляция пассажира", R.drawable.ic_widget_seat_vent_3),
    LauncherDockButtonDescriptor("recirc", "Рециркуляция", R.drawable.ic_widget_hvac_air_recirculation),
    LauncherDockButtonDescriptor("steering_heat", "Подогрев руля", R.drawable.ic_widget_steering_wheel_heat),
    LauncherDockButtonDescriptor("windscreen_heat", "Подогрев лобового", R.drawable.ic_widget_front_windscreen_heat),
    LauncherDockButtonDescriptor("front_defrost", "Обдув лобового", R.drawable.ic_widget_hvac_defroster_front),
    LauncherDockButtonDescriptor("rear_defrost", "Обогрев заднего стекла", R.drawable.ic_widget_rear_window_mirrors_defrost),
    LauncherDockButtonDescriptor("home_nav", "Кнопка Домой"),
    LauncherDockButtonDescriptor("back_nav", "Кнопка Назад"),
)

@Composable
private fun LauncherDockSlotPickerPopup(
    slotIndex: Int,
    missingButtons: List<LauncherDockButtonDescriptor>,
    onSelectButton: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -110),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF218202A),
            border = BorderStroke(1.dp, Color(0x5038BDF8)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Добавить иконку в слот №${slotIndex + 1}",
                        color = LauncherColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, null, tint = LauncherColors.TextMuted)
                    }
                }

                if (missingButtons.isEmpty()) {
                    Text(
                        text = "Все доступные иконки климата уже добавлены на панель",
                        color = LauncherColors.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        items(missingButtons) { btn ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton(btn.id) }
                                    .padding(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(38.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (btn.iconRes != null) {
                                        Image(
                                            painter = painterResource(btn.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (btn.id == "home_nav") Icons.Filled.Home else Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            tint = LauncherColors.AccentCyan,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = btn.title,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatHvacSetTemp(celsius: Float?): String {
    if (celsius == null || celsius <= 0f) return "—"
    return "${celsius.roundToInt()}°"
}

private val LocalClimateCardBgVisible = compositionLocalOf { true }

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
    val climateCardBgRevision by LauncherAppConfigStore.climateCardBgRevisionFlow.collectAsStateWithLifecycle()
    val climateCardBgVisible = remember(context, climateCardBgRevision) {
        LauncherAppConfigStore.climateCardBgVisible(context)
    }
    val bottomSlotsRevision by LauncherAppConfigStore.bottomSlotsRevisionFlow.collectAsStateWithLifecycle()
    val slotsUnified = remember(context, bottomSlotsRevision) {
        LauncherAppConfigStore.bottomSlotsUnified(context)
    }

    val localView = LocalView.current

    CompositionLocalProvider(LocalDockIconScale provides dockScale) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeightDp.dp)
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
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val slotUnitWidthDp = ((screenWidthDp - 24f) / GRID_SLOTS_TOTAL_COUNT.toFloat()).coerceAtLeast(28f).dp
            var isDraggingAnySlot by remember { mutableStateOf(false) }
            var pickerSlotIndex by remember { mutableStateOf<Int?>(null) }

            CompositionLocalProvider(
                LocalDockIconScale provides (dockScale * climateScale),
                LocalClimateCardBgVisible provides climateCardBgVisible,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slotsUnified.forEachIndexed { index, itemId ->
                        val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass") 2 else 1
                        val itemWidthDp = slotUnitWidthDp * itemSpan

                        Box(
                            modifier = Modifier
                                .width(itemWidthDp)
                                .height(bottomBarHeightDp.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (itemId != null) {
                                LauncherDraggableUnifiedSlot(
                                    itemId = itemId,
                                    context = context,
                                    canViewModel = canViewModel,
                                    index = index,
                                    slotWidthDp = itemWidthDp,
                                    onDragStateChange = { dragging -> isDraggingAnySlot = dragging },
                                    onCloseVehicleSettings = onCloseVehicleSettings,
                                    onOpenVehicleSettings = onOpenVehicleSettings,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp, 36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onLongClick = {
                                                if (index >= 1) {
                                                    pickerSlotIndex = index
                                                }
                                            },
                                            onClick = {},
                                        )
                                        .then(
                                            if (isDraggingAnySlot) {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            } else {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.04f),
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Добавить иконку",
                                        tint = Color.White.copy(alpha = if (isDraggingAnySlot) 0.35f else 0.12f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            pickerSlotIndex?.let { slotIdx ->
                val presentIds = slotsUnified.filterNotNull().toSet()
                val missingButtons = ALL_CLIMATE_DOCK_BUTTONS.filter { it.id !in presentIds }
                LauncherDockSlotPickerPopup(
                    slotIndex = slotIdx,
                    missingButtons = missingButtons,
                    onSelectButton = { buttonId ->
                        LauncherAppConfigStore.setButtonInUnifiedSlot(context, slotIdx, buttonId)
                        pickerSlotIndex = null
                    },
                    onDismiss = { pickerSlotIndex = null },
                )
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
    slotWidthDp: Dp,
    onDragStateChange: (Boolean) -> Unit,
    onCloseVehicleSettings: () -> Unit,
    onOpenVehicleSettings: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val trashThresholdPx = remember(density) { with(density) { -36.dp.toPx() } }
    val isTrashThreshold = isDragging && offsetY < trashThresholdPx

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 200f else 0f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                if (isDragging) {
                    scaleX = if (isTrashThreshold) 1.05f else 1.15f
                    scaleY = if (isTrashThreshold) 1.05f else 1.15f
                    shadowElevation = 16f
                }
            }
            .pointerInput(itemId, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        onDragStateChange(true)
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragStateChange(false)
                        if (offsetY < trashThresholdPx && index >= 1) {
                            // Dragged UP out of bottom panel -> Remove icon from slot!
                            LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, null)
                        } else {
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
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragStateChange(false)
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            LauncherBottomDockButtonContent(
                buttonId = itemId,
                context = context,
                canViewModel = canViewModel,
                onCloseVehicleSettings = onCloseVehicleSettings,
                onOpenVehicleSettings = onOpenVehicleSettings,
            )
            if (isTrashThreshold) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
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
            onDown = { sendAdjustHvacTemperature(context, driverTemp, -1.0f, HvacTempZone.Driver) },
            onUp = { sendAdjustHvacTemperature(context, driverTemp, 1.0f, HvacTempZone.Driver) },
        )
        "temp_pass" -> LauncherTempStepper(
            tempText = formatHvacSetTemp(passTemp),
            onDown = { sendAdjustHvacTemperature(context, passTemp, -1.0f, HvacTempZone.Passenger) },
            onUp = { sendAdjustHvacTemperature(context, passTemp, 1.0f, HvacTempZone.Passenger) },
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
    val cardBgVisible = LocalClimateCardBgVisible.current
    Box(
        modifier = Modifier
            .size(dockDp(44f))
            .clip(RoundedCornerShape(12.dp))
            .background(if (cardBgVisible) LauncherColors.CardDark else Color.Transparent)
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
