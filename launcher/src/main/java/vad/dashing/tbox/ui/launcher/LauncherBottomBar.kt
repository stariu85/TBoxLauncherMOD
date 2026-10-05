package vad.dashing.tbox.ui.launcher

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.rememberLaunchableAppEntries
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.refreshHvacTemperaturesFromMbCan
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendCycleHvacFanDirection
import vad.dashing.tbox.ui.sendCycleHvacFanSpeed
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleHvacAc
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleHvacDefrosterFront
import vad.dashing.tbox.ui.sendToggleHvacPm25
import vad.dashing.tbox.ui.sendToggleHvacPower
import vad.dashing.tbox.ui.sendToggleHvacSync
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.theme.tboxCaption
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

@Composable
private fun LauncherFanSpeedIcon(
    speedLevel: Int,
    activeColor: Color = Color(0xFF4FC3F7),
    inactiveColor: Color = Color.White.copy(alpha = 0.2f),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.82f
        val dotRadius = size.minDimension * 0.065f

        for (i in 0 until 7) {
            val angleDeg = -210f + i * 40f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dotX = center.x + radius * cos(angleRad).toFloat()
            val dotY = center.y + radius * sin(angleRad).toFloat()

            val isLit = (i < speedLevel.coerceIn(0, 7))
            drawCircle(
                color = if (isLit) activeColor else inactiveColor,
                radius = if (isLit) dotRadius * 1.2f else dotRadius,
                center = Offset(dotX, dotY),
            )
        }
    }
}

internal val ALL_CLIMATE_DOCK_BUTTONS = listOf(
    LauncherDockButtonDescriptor("hvac_power", "Питание климата", R.drawable.ic_widget_hvac_power),
    LauncherDockButtonDescriptor("hvac_ac", "Кондиционер AC", R.drawable.ic_widget_hvac_ac),
    LauncherDockButtonDescriptor("hvac_auto", "Климат AUTO", R.drawable.ic_widget_hvac_auto),
    LauncherDockButtonDescriptor("hvac_sync", "Синхронизация SYNC", null),
    LauncherDockButtonDescriptor("hvac_pm25", "Очистка PM 2.5", R.drawable.ic_widget_pm25_leaf),
    LauncherDockButtonDescriptor("hvac_fan_speed", "Скорость вентилятора", R.drawable.ic_widget_fan),
    LauncherDockButtonDescriptor("hvac_fan_direction", "Направление обдува", R.drawable.ic_widget_fan_face_feet),
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
    pickerApps: List<LaunchableAppEntry> = emptyList(),
    splitPresets: List<LauncherSplitPreset> = emptyList(),
    onSelectButton: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxPopupWidthDp = (screenWidthDp - 32).coerceAtLeast(320).dp
    val appsByPackage = remember(pickerApps) { pickerApps.associateBy { it.packageName } }

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
                .widthIn(min = 280.dp, max = maxPopupWidthDp)
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .wrapContentWidth(),
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

                if (missingButtons.isNotEmpty()) {
                    Text(
                        text = "Иконки климата",
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        missingButtons.forEach { btn ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton(btn.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (btn.id == "hvac_sync") {
                                        Text(
                                            text = "SYNC",
                                            color = LauncherColors.AccentCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (btn.id == "hvac_pm25") {
                                        Image(
                                            painter = painterResource(btn.iconRes!!),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(Color(0xFF22C55E)),
                                            modifier = Modifier.size(26.dp),
                                        )
                                    } else if (btn.iconRes != null) {
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

                if (pickerApps.isNotEmpty()) {
                    Text(
                        text = "Приложения",
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        pickerApps.forEach { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton("pkg:${app.packageName}") }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(28.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            color = LauncherColors.AccentCyan,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(
                                    text = app.label,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 55.dp),
                                )
                            }
                        }
                    }
                }

                if (splitPresets.isNotEmpty()) {
                    Text(
                        text = "Сплит-экраны",
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        splitPresets.forEach { preset ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton("split:${preset.id}") }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.CenterStart)
                                            .offset(x = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LauncherMiniIcon(app = appsByPackage[preset.leftPackage], fallback = preset.leftPackage.substringAfterLast('.'))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.CenterEnd)
                                            .offset(x = (-2).dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LauncherMiniIcon(app = appsByPackage[preset.rightPackage], fallback = preset.rightPackage.substringAfterLast('.'))
                                    }
                                    Text(
                                        text = "‖",
                                        modifier = Modifier.align(Alignment.Center),
                                        color = LauncherColors.AccentCyan.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 55.dp),
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
    val floatingHomeVisibleRevision by LauncherAppConfigStore.floatingHomeVisibleRevisionFlow.collectAsStateWithLifecycle()
    val floatingHomeVisible = remember(context, floatingHomeVisibleRevision) {
        LauncherAppConfigStore.floatingHomeVisible(context)
    }
    val bottomSlotsRevision by LauncherAppConfigStore.bottomSlotsRevisionFlow.collectAsStateWithLifecycle()
    val slotsUnified = remember(context, bottomSlotsRevision) {
        LauncherAppConfigStore.bottomSlotsUnified(context)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            refreshHvacTemperaturesFromMbCan()
            delay(1_000L)
        }
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
            val slotUnitWidthDp = ((screenWidthDp - 24f) / GRID_SLOTS_TOTAL_COUNT.toFloat()).dp
            var isDraggingAnySlot by remember { mutableStateOf(false) }
            var isEditingBottomSlots by remember { mutableStateOf(false) }
            var pickerSlotIndex by remember { mutableStateOf<Int?>(null) }
            val coroutineScope = rememberCoroutineScope()
            var editingTimerJob by remember { mutableStateOf<Job?>(null) }

            val triggerEditingMode: () -> Unit = {
                isEditingBottomSlots = true
                editingTimerJob?.cancel()
                editingTimerJob = coroutineScope.launch {
                    delay(5_000L)
                    isEditingBottomSlots = false
                }
            }

            val slotBorderAlpha by animateFloatAsState(
                targetValue = if (isEditingBottomSlots || isDraggingAnySlot) 0.22f else 0.00f,
                animationSpec = tween(400),
                label = "slotBorderAlpha",
            )
            val plusIconAlpha by animateFloatAsState(
                targetValue = if (isEditingBottomSlots || isDraggingAnySlot) 0.35f else 0.00f,
                animationSpec = tween(400),
                label = "plusIconAlpha",
            )

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
                    var skipCount = 0
                    for (index in 0 until GRID_SLOTS_TOTAL_COUNT) {
                        if (skipCount > 0) {
                            skipCount--
                            continue
                        }
                        val itemId = slotsUnified.getOrNull(index)
                        val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass") 2 else 1
                        if (itemSpan > 1) {
                            skipCount = itemSpan - 1
                        }
                        val itemWidthDp = slotUnitWidthDp * itemSpan

                        Box(
                            modifier = Modifier
                                .weight(itemSpan.toFloat())
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
                                    onDragStateChange = { dragging ->
                                        isDraggingAnySlot = dragging
                                        if (dragging) {
                                            isEditingBottomSlots = true
                                            editingTimerJob?.cancel()
                                        } else {
                                            triggerEditingMode()
                                        }
                                    },
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
                                                val minSlot = if (floatingHomeVisible) 1 else 0
                                                if (index >= minSlot) {
                                                    triggerEditingMode()
                                                    pickerSlotIndex = index
                                                }
                                            },
                                            onClick = {},
                                        )
                                        .then(
                                            if (slotBorderAlpha > 0.001f) {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = slotBorderAlpha),
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (plusIconAlpha > 0.001f) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Добавить иконку",
                                            tint = Color.White.copy(alpha = plusIconAlpha),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            pickerSlotIndex?.let { slotIdx ->
                val presentIds = slotsUnified.filterNotNull().toSet()
                val missingButtons = ALL_CLIMATE_DOCK_BUTTONS.filter { it.id !in presentIds }
                val rawApps = rememberLaunchableAppEntries(null, 0)
                val hidden = remember(context) { LauncherAppConfigStore.hiddenPackages(context) }
                val pickerApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
                val homeItems = remember(context, configRevision) { LauncherHomeStore.loadItems(context) }
                val activeSplitIds = remember(homeItems) {
                    homeItems.filterIsInstance<LauncherHomeItem.Split>().map { it.presetId }.toSet()
                }
                val splitPresets = remember(context, activeSplitIds) {
                    LauncherSplitPresetStore.loadPresets(context).filter { it.id in activeSplitIds }
                }
                val missingPresets = remember(splitPresets, presentIds, hidden) {
                    splitPresets.filter { "split:${it.id}" !in presentIds && it.leftPackage !in hidden && it.rightPackage !in hidden }
                }

                LauncherDockSlotPickerPopup(
                    slotIndex = slotIdx,
                    missingButtons = missingButtons,
                    pickerApps = pickerApps,
                    splitPresets = missingPresets,
                    onSelectButton = { buttonId ->
                        LauncherAppConfigStore.setButtonInUnifiedSlot(context, slotIdx, buttonId)
                        triggerEditingMode()
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
                        val minSlot = if (LauncherAppConfigStore.floatingHomeVisible(context)) 1 else 0
                        if (offsetY < trashThresholdPx && index >= minSlot) {
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
    @Suppress("UNUSED_PARAMETER") onOpenVehicleSettings: () -> Unit,
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
        "hvac_sync" -> {
            val syncState by UniversalCanRepository.hvacSyncState.collectAsStateWithLifecycle()
            val isSyncOn = syncState is MbCanBinaryState.On || (driverTemp != null && passTemp != null && abs(driverTemp!! - passTemp!!) < 0.1f)
            val tint = if (isSyncOn) HvacOnColor else HvacOffColor
            LauncherDockIcon(onClick = { sendToggleHvacSync(context) }) {
                Text(
                    text = "SYNC",
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        "hvac_pm25" -> {
            val pm25State by UniversalCanRepository.hvacPm25State.collectAsStateWithLifecycle()
            LauncherDockIcon(onClick = { sendToggleHvacPm25(context) }) {
                val isOn = pm25State is MbCanBinaryState.On
                val tint = if (isOn) Color(0xFF22C55E) else HvacOffColor
                Image(
                    painter = painterResource(R.drawable.ic_widget_pm25_leaf),
                    contentDescription = "Очистка воздуха PM 2.5",
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(dockDp(24f)),
                )
            }
        }
        "hvac_power" -> {
            val hvacPower by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
            val fanSpeed by UniversalCanRepository.hvacFanSpeedRawState.collectAsStateWithLifecycle()
            val isOn = hvacPower is MbCanBinaryState.On || fanSpeed > 0
            LauncherDockIcon(onClick = { sendToggleHvacPower(context) }) {
                val tint = if (isOn) HvacOnColor else HvacOffColor
                Image(
                    painter = painterResource(R.drawable.ic_widget_hvac_power),
                    contentDescription = "Питание климата",
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(dockDp(24f)),
                )
            }
        }
        "hvac_fan_speed" -> {
            val speedRaw by UniversalCanRepository.hvacFanSpeedRawState.collectAsStateWithLifecycle()
            val isOn = speedRaw > 0
            val iconTint = if (isOn) HvacOnColor else HvacOffColor
            LauncherDockIcon(onClick = { sendCycleHvacFanSpeed(context) }) {
                Box(
                    modifier = Modifier.size(dockDp(38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherFanSpeedIcon(
                        speedLevel = speedRaw.coerceIn(0, 7),
                        activeColor = HvacOnColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_widget_fan),
                        contentDescription = "Скорость вентилятора",
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(dockDp(18f)),
                    )
                }
            }
        }
        "hvac_ac" -> {
            val hvacAc by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
            LauncherDockIcon(onClick = { sendToggleHvacAc(context) }) {
                val isOn = hvacAc is MbCanBinaryState.On
                val tint = if (isOn) HvacOnColor else HvacOffColor
                Image(
                    painter = painterResource(R.drawable.ic_widget_hvac_ac),
                    contentDescription = "Кондиционер AC",
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(dockDp(24f)),
                )
            }
        }
        "hvac_fan_direction" -> {
            val rawDirection by UniversalCanRepository.hvacFanDirectionRawState.collectAsStateWithLifecycle()
            val drawableRes = when (rawDirection) {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT -> R.drawable.ic_widget_fan_feet
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> R.drawable.ic_widget_fan_face_feet
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE -> R.drawable.ic_widget_fan_face
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> R.drawable.ic_widget_fan_defrost_feet
                else -> R.drawable.ic_widget_fan_feet
            }
            LauncherDockIcon(onClick = { sendCycleHvacFanDirection(context) }) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = "Направление обдува",
                    colorFilter = ColorFilter.tint(HvacOnColor),
                    modifier = Modifier.size(dockDp(24f)),
                )
            }
        }
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
        else -> when {
            buttonId.startsWith("split:") -> {
                val presetId = buttonId.removePrefix("split:")
                val rawApps = rememberLaunchableAppEntries(null, 0)
                val hidden = remember(context) { LauncherAppConfigStore.hiddenPackages(context) }
                val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
                val appsByPackage = remember(visibleApps) { visibleApps.associateBy { it.packageName } }
                val splitPresets = remember(context) { LauncherSplitPresetStore.loadPresets(context) }
                val preset = remember(presetId, splitPresets) { splitPresets.firstOrNull { it.id == presetId } }

                if (preset != null) {
                    LauncherDockIcon(onClick = { launchSplitPreset(context, preset, visibleApps) }) {
                        Box(
                            modifier = Modifier.size(dockDp(38f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dockDp(22f))
                                    .align(Alignment.CenterStart)
                                    .offset(x = dockDp(2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherMiniIcon(app = appsByPackage[preset.leftPackage], fallback = preset.leftPackage.substringAfterLast('.'))
                            }
                            Box(
                                modifier = Modifier
                                    .size(dockDp(22f))
                                    .align(Alignment.CenterEnd)
                                    .offset(x = (-dockDp(2f))),
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherMiniIcon(app = appsByPackage[preset.rightPackage], fallback = preset.rightPackage.substringAfterLast('.'))
                            }
                            Text(
                                text = "‖",
                                modifier = Modifier.align(Alignment.Center),
                                color = LauncherColors.AccentCyan.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            else -> {
                val pkg = buttonId.removePrefix("pkg:").removePrefix("app:")
                val rawApps = rememberLaunchableAppEntries(null, 0)
                val hidden = remember(context) { LauncherAppConfigStore.hiddenPackages(context) }
                val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
                val app = remember(pkg, visibleApps) { visibleApps.firstOrNull { it.packageName == pkg } }

                LauncherDockIcon(onClick = { launchLauncherApp(context, pkg) }) {
                    if (app?.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(dockDp(28f)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = (app?.label ?: pkg.substringAfterLast('.')).take(1).uppercase(),
                            color = LauncherColors.AccentCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
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
