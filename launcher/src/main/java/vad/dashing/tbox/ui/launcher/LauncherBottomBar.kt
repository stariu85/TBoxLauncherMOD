package vad.dashing.tbox.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import vad.dashing.tbox.tbm.TbmAutomationItem
import vad.dashing.tbox.tbm.TbmConnectionState
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.refreshHvacTemperaturesFromMbCan
import vad.dashing.tbox.ui.rememberLaunchableAppEntries
import vad.dashing.tbox.ui.rememberPlaySystemClickSound
import vad.dashing.tbox.ui.sendAdjustAudioVolume
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleFrontSeatHeat
import vad.dashing.tbox.ui.sendCycleFrontSeatVent
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendCycleHvacFanDirection
import vad.dashing.tbox.ui.sendCycleHvacFanSpeed
import vad.dashing.tbox.ui.sendDoubleTapHvacFanSpeed
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HvacOnColor = Color(0xFF4FC3F7)
private val SeatHeatOnColor = Color(0xFFF97316)
private val HvacOffColor: Color get() = LauncherColors.TextSecondary
private const val LAUNCHER_BOTTOM_BAR_CAN_SOURCE = "launcher-bottom-bar"
private const val SEAT_COMMAND_CONFIRM_TIMEOUT_MS = 2_500L
private const val LIGHT_COMMAND_CONFIRM_TIMEOUT_MS = 1_200L

private val LocalDockIconScale = compositionLocalOf { 1f }
private val LocalDockSecondRowEnabled = compositionLocalOf { false }

internal data class ParsedTbmAutoButton(
    val automationId: String,
    val iconName: String,
    val customTitle: String?,
)

internal fun parseTbmAutoButtonId(buttonId: String): ParsedTbmAutoButton {
    val raw = buttonId.removePrefix("tbm_auto:")
    val parts = raw.split(":", limit = 3)
    val autoId = parts.getOrNull(0).orEmpty()
    val iconName = parts.getOrNull(1)?.ifBlank { null } ?: "play"
    val customTitle = parts.getOrNull(2)
    return ParsedTbmAutoButton(automationId = autoId, iconName = iconName, customTitle = customTitle)
}

@Composable
private fun dockDp(base: Float) = (base * LocalDockIconScale.current).dp

@Composable
private fun dockSp(base: Float) = (base * LocalDockIconScale.current).sp

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
    LauncherDockButtonDescriptor("hvac_auto", "Климат AUTO", null),
    LauncherDockButtonDescriptor("hvac_sync", "Синхронизация SYNC", null),
    LauncherDockButtonDescriptor("temp_driver", "Температура водителя", null),
    LauncherDockButtonDescriptor("temp_pass", "Температура пассажира", null),
    LauncherDockButtonDescriptor("hvac_heater_level", "Уровень печки", null),
    LauncherDockButtonDescriptor("audio_volume", "Регулятор громкости", null),
    LauncherDockButtonDescriptor("screen_brightness", "Яркость экрана", null),
    LauncherDockButtonDescriptor("hvac_pm25", "Очистка PM 2.5", R.drawable.ic_widget_pm25_on),
    LauncherDockButtonDescriptor("hvac_fan_speed", "Скорость вентилятора", R.drawable.ic_widget_fan),
    LauncherDockButtonDescriptor("hvac_fan_direction", "Направление обдува", R.drawable.ic_widget_fan_face_feet),
    LauncherDockButtonDescriptor("seat_heat_left", "Подогрев водителя", R.drawable.ic_widget_seat_heat_left),
    LauncherDockButtonDescriptor("seat_vent_left", "Вентиляция водителя", R.drawable.ic_widget_seat_vent_left),
    LauncherDockButtonDescriptor("seat_heat_right", "Подогрев пассажира", R.drawable.ic_widget_seat_heat_right),
    LauncherDockButtonDescriptor("seat_vent_right", "Вентиляция пассажира", R.drawable.ic_widget_seat_vent_right),
    LauncherDockButtonDescriptor("seat_heat_rear_left", "Подогрев зад. лев.", R.drawable.ic_widget_seat_heat_left),
    LauncherDockButtonDescriptor("seat_heat_rear_right", "Подогрев зад. прав.", R.drawable.ic_widget_seat_heat_right),
    LauncherDockButtonDescriptor("recirc", "Рециркуляция", R.drawable.ic_widget_hvac_air_recirculation),
    LauncherDockButtonDescriptor("steering_heat", "Подогрев руля", R.drawable.ic_widget_steering_wheel_heat),
    LauncherDockButtonDescriptor("windscreen_heat", "Подогрев лобового", R.drawable.ic_widget_front_windscreen_heat),
    LauncherDockButtonDescriptor("front_defrost", "Обдув лобового", R.drawable.ic_widget_hvac_defroster_front),
    LauncherDockButtonDescriptor("rear_defrost", "Обогрев заднего стекла", R.drawable.ic_widget_rear_window_mirrors_defrost),
    LauncherDockButtonDescriptor("headlights", "Переключатель фар", R.drawable.ic_headlights_auto),
    LauncherDockButtonDescriptor("home_nav", "Кнопка Домой"),
    LauncherDockButtonDescriptor("back_nav", "Кнопка Назад", R.drawable.undo_24),
    LauncherDockButtonDescriptor("all_apps", "Все приложения", R.drawable.ic_all_apps_dock),
    LauncherDockButtonDescriptor("undo_nav", "Предыдущее приложение", R.drawable.ic_undo_app),
    LauncherDockButtonDescriptor("close_apps", "Закрыть приложения", R.drawable.close_apps_24),
    LauncherDockButtonDescriptor("dcl_log", "Запись лога (5 мин)", R.drawable.menu_icon_log),
    LauncherDockButtonDescriptor("car_dialer", "Телефон", R.drawable.ic_widget_phone),
    LauncherDockButtonDescriptor("radio_app", "Радио", R.drawable.radio_vtkb246z55e9),
    LauncherDockButtonDescriptor("avm_camera", "Камеры 360", R.drawable.ic_launcher_avm),
)

@Composable
internal fun LauncherDockSlotPickerContent(
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

    val context = LocalContext.current
    val pickerCoroutineScope = rememberCoroutineScope()
    val tbmRepo = remember(context) { TbmRepositoryProvider.get(context) }
    val tbmConnState by tbmRepo.connectionState.collectAsStateWithLifecycle()
    val tbmAutomations by tbmRepo.automations.collectAsStateWithLifecycle()
    val isTbmConnected = tbmConnState is TbmConnectionState.Connected

    LaunchedEffect(Unit) {
        tbmRepo.refreshAutomations()
    }

    val isDark = LauncherThemeState.darkTheme
    val popupBg = if (isDark) Color(0xF218202A) else Color(0xF2E5E7EB)
    val popupBorder = if (isDark) Color(0x5038BDF8) else Color(0x3094A3B8)
    val pickerIconColor = if (isDark) LauncherColors.AccentCyan else Color(0xFF374151)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = popupBg,
        border = BorderStroke(1.dp, popupBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
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
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(31.dp)) {
                        Icon(Icons.Filled.Close, null, tint = LauncherColors.TextMuted)
                    }
                }

                var flashingPresetKey by remember { mutableStateOf<String?>(null) }
                val presetsRevision by LauncherAppConfigStore.bottomSlotsRevisionFlow.collectAsStateWithLifecycle()

                val presetItems = remember {
                    listOf(
                        "summer" to "Лето",
                        "winter" to "Зима",
                        "general" to "Общее",
                    )
                }

                Text(
                    text = "Пресеты кнопок",
                    color = LauncherColors.TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    presetItems.forEach { (presetKey, presetName) ->
                        val hasPreset = remember(context, presetsRevision) {
                            LauncherAppConfigStore.hasBottomSlotsPreset(context, presetKey)
                        }
                        val isFlashing = flashingPresetKey == presetKey

                        val btnBg = when {
                            isFlashing -> Color(0xFF22C55E)
                            hasPreset -> LauncherColors.CardDark
                            else -> LauncherColors.CardDark.copy(alpha = 0.5f)
                        }
                        val btnBorder = when {
                            isFlashing -> Color(0xFF22C55E)
                            hasPreset -> LauncherColors.AccentCyan
                            else -> Color.White.copy(alpha = 0.12f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(btnBg)
                                .border(1.dp, btnBorder, RoundedCornerShape(12.dp))
                                .pointerInput(presetKey) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        val startMs = SystemClock.uptimeMillis()
                                        var isSaved = false

                                        val timerJob = pickerCoroutineScope.launch {
                                            delay(3000L)
                                            isSaved = true
                                            LauncherAppConfigStore.saveBottomSlotsPreset(context, presetKey)
                                            Toast.makeText(context, "Пресет «$presetName» сохранен", Toast.LENGTH_SHORT).show()
                                            flashingPresetKey = presetKey
                                            delay(600L)
                                            flashingPresetKey = null
                                        }

                                        val up = waitForUpOrCancellation()
                                        timerJob.cancel()

                                        if (!isSaved && up != null) {
                                            val elapsed = SystemClock.uptimeMillis() - startMs
                                            if (elapsed < 3000L) {
                                                val applied = LauncherAppConfigStore.applyBottomSlotsPreset(context, presetKey)
                                                if (applied) {
                                                    Toast.makeText(context, "Применен пресет «$presetName»", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, "Пресет «$presetName» не сохранен (зажмите на 3 сек)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = presetName,
                                    color = if (isFlashing) Color.White else if (hasPreset) LauncherColors.TextPrimary else LauncherColors.TextMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isFlashing) "Сохранено!" else if (hasPreset) "Сохранен" else "Пусто (3с)",
                                    color = if (isFlashing) Color.White else if (hasPreset) LauncherColors.AccentCyan else LauncherColors.TextMuted.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }

                if (missingButtons.isNotEmpty()) {
                    Text(
                        text = "Иконки климата",
                        color = LauncherColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        missingButtons.forEach { btn ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton(btn.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(47.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (btn.id == "hvac_sync" || btn.id == "hvac_auto") {
                                        Text(
                                            text = if (btn.id == "hvac_sync") "SYNC" else "AUTO",
                                            color = pickerIconColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (btn.id == "temp_driver" || btn.id == "temp_pass") {
                                        Text(
                                            text = if (btn.id == "temp_driver") "t° В" else "t° П",
                                            color = pickerIconColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (btn.id == "hvac_heater_level") {
                                        Text(
                                            text = "Печка",
                                            color = pickerIconColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (btn.id == "audio_volume") {
                                        Image(
                                            painter = painterResource(R.drawable.ic_speaker_plus),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(pickerIconColor),
                                            modifier = Modifier.size(31.8.dp),
                                        )
                                    } else if (btn.id == "screen_brightness") {
                                        Image(
                                            painter = painterResource(R.drawable.ic_brightness_plus),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(pickerIconColor),
                                            modifier = Modifier.size(28.9.dp),
                                        )
                                    } else if (btn.id == "hvac_pm25") {
                                        Image(
                                            painter = painterResource(btn.iconRes!!),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(pickerIconColor),
                                            modifier = Modifier.size(34.dp),
                                        )
                                    } else if (btn.iconRes != null) {
                                        Image(
                                            painter = painterResource(btn.iconRes),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(pickerIconColor),
                                            modifier = Modifier.size(34.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (btn.id == "home_nav") Icons.Filled.Home else Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            tint = pickerIconColor,
                                            modifier = Modifier.size(31.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = btn.title,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                val homeItemsForTbm = remember(context) { LauncherHomeStore.loadItems(context) }
                val configuredTbmAutomations = remember(homeItemsForTbm) {
                    homeItemsForTbm.filterIsInstance<LauncherHomeItem.TbmAutomation>()
                }

                if (configuredTbmAutomations.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.launcher_home_add_tbm_automation),
                        color = LauncherColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        configuredTbmAutomations.forEach { auto ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable {
                                        onSelectButton("tbm_auto:${auto.automationId}:${auto.iconName}:${auto.automationName}")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(47.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = resolveTbmIconVector(auto.iconName),
                                        contentDescription = auto.automationName,
                                        tint = pickerIconColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Text(
                                    text = auto.automationName,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 85.dp),
                                )
                            }
                        }
                    }
                }

                if (pickerApps.isNotEmpty()) {
                    Text(
                        text = "Приложения",
                        color = LauncherColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        pickerApps.forEach { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton("pkg:${app.packageName}") }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(47.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(36.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            color = LauncherColors.AccentCyan,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(
                                    text = app.label,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 72.dp),
                                )
                            }
                        }
                    }
                }

                if (splitPresets.isNotEmpty()) {
                    Text(
                        text = "Сплит-экраны",
                        color = LauncherColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        splitPresets.forEach { preset ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LauncherColors.CardDark)
                                    .clickable { onSelectButton("split:${preset.id}") }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(47.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(29.dp)
                                            .align(Alignment.CenterStart)
                                            .offset(x = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LauncherMiniIcon(app = appsByPackage[preset.leftPackage], fallback = preset.leftPackage.substringAfterLast('.'), iconScale = 1.3f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(29.dp)
                                            .align(Alignment.CenterEnd)
                                            .offset(x = (-2).dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LauncherMiniIcon(app = appsByPackage[preset.rightPackage], fallback = preset.rightPackage.substringAfterLast('.'), iconScale = 1.3f)
                                    }
                                    Text(
                                        text = "‖",
                                        modifier = Modifier.align(Alignment.Center),
                                        color = LauncherColors.AccentCyan.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 72.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun LauncherDockSlotPickerPopup(
    slotIndex: Int,
    missingButtons: List<LauncherDockButtonDescriptor>,
    pickerApps: List<LaunchableAppEntry> = emptyList(),
    splitPresets: List<LauncherSplitPreset> = emptyList(),
    onSelectButton: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -110),
        onDismissRequest = onDismiss,
    ) {
        LauncherDockSlotPickerContent(
            slotIndex = slotIndex,
            missingButtons = missingButtons,
            pickerApps = pickerApps,
            splitPresets = splitPresets,
            onSelectButton = onSelectButton,
            onDismiss = onDismiss,
        )
    }
}

private fun formatHvacSetTempAnnotated(celsius: Float?): AnnotatedString {
    if (celsius == null || celsius <= 0f) return AnnotatedString("—")
    val tenths = (celsius * 10f).roundToInt()
    val whole = tenths / 10
    val fraction = abs(tenths % 10)

    return buildAnnotatedString {
        append("$whole.")
        withStyle(style = SpanStyle(fontSize = 0.7.em)) {
            append(fraction.toString())
        }
    }
}

private val LocalClimateCardBgVisible = compositionLocalOf { false }

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
    val tempColorIndicationRevision by LauncherAppConfigStore.climateTempColorIndicationRevisionFlow.collectAsStateWithLifecycle()
    val tempColorIndication = remember(context, tempColorIndicationRevision) {
        LauncherAppConfigStore.climateTempColorIndication(context)
    }
    val floatingHomeVisibleRevision by LauncherAppConfigStore.floatingHomeVisibleRevisionFlow.collectAsStateWithLifecycle()
    val floatingHomeVisible = remember(context, floatingHomeVisibleRevision) {
        LauncherAppConfigStore.floatingHomeVisible(context)
    }
    val bottomSlotsRevision by LauncherAppConfigStore.bottomSlotsRevisionFlow.collectAsStateWithLifecycle()
    val dockSecondRowRevision by LauncherAppConfigStore.dockSecondRowRevisionFlow.collectAsStateWithLifecycle()
    val dockColumnsRevision by LauncherAppConfigStore.dockColumnsRevisionFlow.collectAsStateWithLifecycle()
    val secondRowEnabled = remember(context, dockSecondRowRevision, bottomSlotsRevision) {
        LauncherAppConfigStore.dockSecondRowEnabled(context)
    }
    val dockColumns = remember(context, dockColumnsRevision, bottomSlotsRevision) {
        LauncherAppConfigStore.dockColumns(context)
    }
    val slotsUnified = remember(context, bottomSlotsRevision, dockSecondRowRevision, dockColumnsRevision) {
        LauncherAppConfigStore.bottomSlotsUnified(context)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            refreshHvacTemperaturesFromMbCan()
            delay(30_000L)
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

            var rowLeftX by remember { mutableFloatStateOf(0f) }
            var rowWidthPx by remember { mutableFloatStateOf(1f) }
            var rowHeightPx by remember { mutableFloatStateOf(0f) }

            CompositionLocalProvider(
                LocalDockIconScale provides (dockScale * climateScale),
                LocalClimateCardBgVisible provides climateCardBgVisible,
                LocalDockSecondRowEnabled provides secondRowEnabled,
            ) {
                if (secondRowEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        repeat(2) { rowIndex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        if (rowIndex == 0) {
                                            val bounds = coordinates.boundsInWindow()
                                            rowLeftX = bounds.left
                                            rowWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
                                            rowHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val startIdx = rowIndex * dockColumns
                                val endIdx = startIdx + dockColumns
                                var skipCount = 0
                                val homeSlotIndex = if (secondRowEnabled) dockColumns else 0
                                for (index in startIdx until endIdx) {
                                    if (skipCount > 0) {
                                        skipCount--
                                        continue
                                    }
                                    val itemId = slotsUnified.getOrNull(index)
                                    val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass" || itemId == "hvac_heater_level" || itemId == "audio_volume" || itemId == "screen_brightness") 2 else 1
                                    if (itemSpan > 1) {
                                        skipCount = itemSpan - 1
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(itemSpan.toFloat())
                                            .height((bottomBarHeightDp / 2f).dp)
                                            .onGloballyPositioned { coordinates ->
                                                if (index == homeSlotIndex) {
                                                    val bounds = coordinates.boundsInWindow()
                                                    LauncherEmbeddedBoundsState.slot0Bounds = Rect(
                                                        bounds.left.toInt(),
                                                        bounds.top.toInt(),
                                                        bounds.right.toInt(),
                                                        bounds.bottom.toInt(),
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (itemId != null) {
                                            LauncherDraggableUnifiedSlot(
                                                itemId = itemId,
                                                context = context,
                                                canViewModel = canViewModel,
                                                index = index,
                                                rowLeftX = rowLeftX,
                                                rowWidthPx = rowWidthPx,
                                                rowHeightPx = rowHeightPx,
                                                secondRowEnabled = true,
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
                                                            val isHomeReserved = floatingHomeVisible && index == homeSlotIndex
                                                            if (!isHomeReserved) {
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
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                rowLeftX = bounds.left
                                rowWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
                                rowHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var skipCount = 0
                        val homeSlotIndex = 0
                        for (index in 0 until dockColumns) {
                            if (skipCount > 0) {
                                skipCount--
                                continue
                            }
                            val itemId = slotsUnified.getOrNull(index)
                            val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass" || itemId == "hvac_heater_level" || itemId == "audio_volume" || itemId == "screen_brightness") 2 else 1
                            if (itemSpan > 1) {
                                skipCount = itemSpan - 1
                            }

                            Box(
                                modifier = Modifier
                                    .weight(itemSpan.toFloat())
                                    .height(bottomBarHeightDp.dp)
                                    .onGloballyPositioned { coordinates ->
                                        if (index == homeSlotIndex) {
                                            val bounds = coordinates.boundsInWindow()
                                            LauncherEmbeddedBoundsState.slot0Bounds = Rect(
                                                bounds.left.toInt(),
                                                bounds.top.toInt(),
                                                bounds.right.toInt(),
                                                bounds.bottom.toInt(),
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (itemId != null) {
                                    LauncherDraggableUnifiedSlot(
                                        itemId = itemId,
                                        context = context,
                                        canViewModel = canViewModel,
                                        index = index,
                                        rowLeftX = rowLeftX,
                                        rowWidthPx = rowWidthPx,
                                        rowHeightPx = rowHeightPx,
                                        secondRowEnabled = false,
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
                                                    val isHomeReserved = floatingHomeVisible && index == homeSlotIndex
                                                    if (!isHomeReserved) {
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
            }

            pickerSlotIndex?.let { slotIdx ->
                val presentIds = slotsUnified.filterNotNull().toSet()
                val missingButtons = ALL_CLIMATE_DOCK_BUTTONS.filter { it.id !in presentIds }
                val rawApps = rememberLaunchableAppEntries(null, 0)
                val hidden = remember(context) { LauncherAppConfigStore.hiddenPackages(context) }
                val homeItems = remember(context, configRevision) { LauncherHomeStore.loadItems(context) }
                val desktopAppPackages = remember(homeItems) {
                    homeItems.filterIsInstance<LauncherHomeItem.App>()
                        .map { it.packageName }
                        .toSet()
                }
                val pickerApps = remember(rawApps, hidden, desktopAppPackages) {
                    LauncherAppConfigStore.filterVisible(rawApps, hidden)
                        .filter { it.packageName in desktopAppPackages }
                }
                val activeSplitIds = remember(homeItems) {
                    homeItems.filterIsInstance<LauncherHomeItem.Split>().map { it.presetId }.toSet()
                }
                val splitPresets = remember(context, activeSplitIds) {
                    LauncherSplitPresetStore.loadPresets(context).filter { it.id in activeSplitIds }
                }
                val missingPresets = remember(splitPresets, presentIds, hidden) {
                    splitPresets.filter { "split:${it.id}" !in presentIds && it.leftPackage !in hidden && it.rightPackage !in hidden }
                }
                val bottomBarHeightDp = remember(context) { LauncherAppConfigStore.bottomBarHeightDp(context) }

                DisposableEffect(slotIdx) {
                    LauncherDockSlotPickerOverlayWindow.show(
                        context = context,
                        slotIndex = slotIdx,
                        missingButtons = missingButtons,
                        pickerApps = pickerApps,
                        splitPresets = missingPresets,
                        bottomBarHeightDp = bottomBarHeightDp,
                        onSelectButton = { buttonId ->
                            LauncherAppConfigStore.setButtonInUnifiedSlot(context, slotIdx, buttonId)
                            triggerEditingMode()
                            pickerSlotIndex = null
                        },
                        onDismiss = { pickerSlotIndex = null },
                    )
                    onDispose {
                        LauncherDockSlotPickerOverlayWindow.hide()
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
    rowLeftX: Float,
    rowWidthPx: Float,
    rowHeightPx: Float = 0f,
    secondRowEnabled: Boolean = false,
    onDragStateChange: (Boolean) -> Unit,
    onCloseVehicleSettings: () -> Unit,
    onOpenVehicleSettings: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var itemLeftX by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val trashThresholdPx = remember(density) { with(density) { -36.dp.toPx() } }
    val isTrashThreshold = isDragging && offsetY < trashThresholdPx
    val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass" || itemId == "hvac_heater_level" || itemId == "audio_volume" || itemId == "screen_brightness") 2 else 1

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 200f else 0f)
            .onGloballyPositioned { coordinates ->
                itemLeftX = coordinates.boundsInWindow().left
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
                        val homeSlotIndex = LauncherAppConfigStore.floatingHomeSlotIndex(context)
                        val dockCols = LauncherAppConfigStore.dockColumns(context)
                        val isHomeReserved = LauncherAppConfigStore.floatingHomeVisible(context) && index == homeSlotIndex
                        if (offsetY < trashThresholdPx && !isHomeReserved) {
                            // Dragged UP out of bottom panel -> Remove icon from slot!
                            LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, null)
                        } else {
                            val unitSlotWidthPx = (rowWidthPx / dockCols.toFloat()).coerceAtLeast(1f)
                            val droppedLeftX = itemLeftX + offsetX

                            val targetRow = if (secondRowEnabled) {
                                val currentRow = index / dockCols
                                val rowThreshold = (rowHeightPx * 0.75f).coerceAtLeast(20f)
                                if (currentRow == 0 && offsetY > rowThreshold) {
                                    1
                                } else if (currentRow == 1 && offsetY < -rowThreshold) {
                                    0
                                } else {
                                    currentRow
                                }
                            } else {
                                0
                            }

                            val rowStartSlot = targetRow * dockCols
                            val maxCol = if (itemSpan > 1) dockCols - 2 else dockCols - 1
                            val reservedRow = homeSlotIndex / dockCols
                            val minCol = if (targetRow == reservedRow && LauncherAppConfigStore.floatingHomeVisible(context)) 1 else 0

                            val targetCol = ((droppedLeftX - rowLeftX + unitSlotWidthPx / 2f) / unitSlotWidthPx)
                                .toInt()
                                .coerceIn(minCol, maxCol)

                            val targetSlot = rowStartSlot + targetCol

                            if (targetSlot != index) {
                                LauncherAppConfigStore.reorderUnifiedSlot(
                                    context = context,
                                    itemId = itemId,
                                    currentIndex = index,
                                    targetIndex = targetSlot,
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
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                if (isDragging) {
                    scaleX = if (isTrashThreshold) 1.05f else 1.15f
                    scaleY = if (isTrashThreshold) 1.05f else 1.15f
                    shadowElevation = 16f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            LauncherBottomDockButtonContent(
                buttonId = itemId,
                index = index,
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
    index: Int,
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
    val tempColorIndicationRevision by LauncherAppConfigStore.climateTempColorIndicationRevisionFlow.collectAsStateWithLifecycle()
    val tempColorIndication = remember(context, tempColorIndicationRevision) {
        LauncherAppConfigStore.climateTempColorIndication(context)
    }
    val leftSeatMode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val rightSeatMode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val rearLeftSeatMode by UniversalCanRepository.rearLeftSeatModeState.collectAsStateWithLifecycle()
    val rearRightSeatMode by UniversalCanRepository.rearRightSeatModeState.collectAsStateWithLifecycle()
    val driverTemp by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val passTemp by canViewModel.climateSetTemperature2.collectAsStateWithLifecycle()
    val leftRaw = launcherSeatModeToRaw(leftSeatMode) ?: 1
    val rightRaw = launcherSeatModeToRaw(rightSeatMode) ?: 1
    val rearLeftRaw = launcherSeatModeToRaw(rearLeftSeatMode) ?: 1
    val rearRightRaw = launcherSeatModeToRaw(rearRightSeatMode) ?: 1

    when (buttonId) {
        "hvac_sync" -> {
            val syncState by UniversalCanRepository.hvacSyncState.collectAsStateWithLifecycle()
            val isSyncOn = syncState is MbCanBinaryState.On || (syncState !is MbCanBinaryState.Off && driverTemp != null && passTemp != null && abs(driverTemp!! - passTemp!!) < 0.1f)
            val tint = if (isSyncOn) HvacOnColor else HvacOffColor
            LauncherDockIcon(onClick = { sendToggleHvacSync(context) }) {
                Text(
                    text = "SYNC",
                    color = tint,
                    fontSize = dockSp(10.4f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        "hvac_pm25" -> {
            val pm25State by UniversalCanRepository.hvacPm25State.collectAsStateWithLifecycle()
            LauncherDockIcon(onClick = { sendToggleHvacPm25(context) }) {
                val isOn = pm25State is MbCanBinaryState.On
                val drawableRes = if (isOn) R.drawable.ic_widget_pm25_on else R.drawable.ic_widget_pm25_off
                val tint = if (isOn) Color(0xFF22C55E) else HvacOffColor
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = "Очистка воздуха PM 2.5",
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(dockDp(26.4f)),
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
                    modifier = Modifier.size(dockDp(26.4f)),
                )
            }
        }
        "hvac_fan_speed" -> {
            val speedRaw by UniversalCanRepository.hvacFanSpeedRawState.collectAsStateWithLifecycle()
            val hvacAc by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
            val isFanOn = speedRaw > 0
            val isAcOn = hvacAc is MbCanBinaryState.On
            val activeColor = if (isAcOn) HvacOnColor else Color(0xFFF97316)
            val iconTint = if (isFanOn) activeColor else HvacOffColor
            LauncherDockIcon(
                onClick = { sendCycleHvacFanSpeed(context) },
                onDoubleClick = { sendDoubleTapHvacFanSpeed(context) },
            ) {
                Box(
                    modifier = Modifier.size(dockDp(45.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherFanSpeedIcon(
                        speedLevel = speedRaw.coerceIn(0, 7),
                        activeColor = activeColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_widget_fan),
                        contentDescription = "Скорость вентилятора",
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(dockDp(21.6f)),
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
                    modifier = Modifier.size(dockDp(26.4f)),
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
                    colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                    modifier = Modifier.size(dockDp(31.68f)),
                )
            }
        }
        "home_nav" -> LauncherDockIcon(
            onClick = {
                goLauncherHome(
                    context = context,
                    onCloseOverlays = { onCloseVehicleSettings() },
                    source = "BottomBar:home_nav",
                )
            },
        ) {
            Icon(Icons.Filled.Home, stringResource(R.string.launcher_home_cd), tint = LauncherColors.AccentCyan)
        }
        "back_nav" -> LauncherDockIcon(
            onClick = {
                goLauncherBack(
                    context = context,
                    source = "BottomBar:back_nav",
                )
            },
        ) {
            Image(
                painter = painterResource(R.drawable.undo_24),
                contentDescription = stringResource(R.string.launcher_back_cd),
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(17.35f)),
            )
        }
        "all_apps", "all_apps_nav", "apps_drawer" -> LauncherDockIcon(
            onClick = {
                val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
                if (activity != null) {
                    val settingsViewModel = ViewModelProvider(
                        activity,
                        SettingsViewModelFactory(SettingsManager(activity)),
                    )[SettingsViewModel::class.java]
                    LauncherAppDrawerWindow.toggle(
                        context = activity,
                        settingsViewModel = settingsViewModel,
                        onConfigChanged = {},
                    )
                }
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_all_apps_dock),
                contentDescription = "Все приложения",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(21.42f)),
            )
        }
        "undo_nav", "undo_24", "last_app" -> LauncherDockIcon(
            onClick = {
                performUndoLaunch(context, source = "BottomBar:undo_nav")
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_undo_app),
                contentDescription = "Предыдущее приложение",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(36.29f)),
            )
        }
        "close_apps", "close_apps_nav", "close_apps_24" -> LauncherDockIcon(
            onClick = {
                dismissForeignFreeformTasks(context, source = "BottomBar:close_apps")
            },
        ) {
            Image(
                painter = painterResource(R.drawable.close_apps_24),
                contentDescription = "Закрыть приложения",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(28.98f)),
            )
        }
        "dcl_log" -> {
            var showPermissionDialog by remember { mutableStateOf(false) }

            if (showPermissionDialog) {
                LauncherDarkAlertDialog(
                    onDismissRequest = { showPermissionDialog = false },
                    title = { Text(stringResource(R.string.launcher_dcl_permission_title)) },
                    text = { Text(stringResource(R.string.launcher_dcl_permission_desc)) },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                showPermissionDialog = false
                                vad.dashing.tbox.DclLogRecorder.openStorageSettings(context)
                            },
                        ) {
                            Text(stringResource(R.string.action_settings_open))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.OutlinedButton(onClick = { showPermissionDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }

            val isLoggingActive = vad.dashing.tbox.DclLogRecorder.isLoggingActive()
            val tint = if (isLoggingActive) Color(0xFFEF4444) else (if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151))

            LauncherDockIcon(
                onClick = {
                    if (vad.dashing.tbox.DclLogRecorder.isLoggingActive()) {
                        vad.dashing.tbox.DclLogRecorder.stopLogging("Stopped by user via bottom bar button")
                        Toast.makeText(context, context.getString(R.string.launcher_dcl_logs_stopped_toast), Toast.LENGTH_SHORT).show()
                    } else {
                        if (!vad.dashing.tbox.DclLogRecorder.hasStoragePermission(context)) {
                            showPermissionDialog = true
                        } else {
                            vad.dashing.tbox.DclLogRecorder.startSession(context, durationMs = 300_000L)
                            Toast.makeText(context, context.getString(R.string.launcher_dcl_logs_started_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.menu_icon_log),
                    contentDescription = stringResource(R.string.launcher_dcl_logs_button_title),
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(dockDp(28f)),
                )
            }
        }
        "car_dialer", "car_phone", "dialer" -> LauncherDockIcon(
            onClick = {
                launchLauncherApp(context, "com.autopai.car.dialer")
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_widget_phone),
                contentDescription = "Телефон",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(25.2f)),
            )
        }
        "radio_app", "radio", "com.wt.multimedia.local" -> LauncherDockIcon(
            onClick = {
                launchLauncherApp(context, "com.wt.multimedia.local")
            },
        ) {
            Image(
                painter = painterResource(R.drawable.radio_vtkb246z55e9),
                contentDescription = "Радио",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(28f)),
            )
        }
        "avm_camera", "avm", "camera_360", "cameras", "com.mengbo.avm" -> LauncherDockIcon(
            onClick = {
                launchLauncherAppEmbedded(context, "com.mengbo.avm")
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_avm),
                contentDescription = "Камеры 360",
                colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                modifier = Modifier.size(dockDp(26f)),
            )
        }
        "temp_driver" -> LauncherTempStepper(
            tempText = formatHvacSetTempAnnotated(driverTemp),
            onDown = { sendAdjustHvacTemperature(context, driverTemp, -0.5f, HvacTempZone.Driver) },
            onUp = { sendAdjustHvacTemperature(context, driverTemp, 0.5f, HvacTempZone.Driver) },
            onTextClick = { launchClimateApp(context) },
            useTempColorIndication = tempColorIndication,
            celsius = driverTemp,
        )
        "temp_pass" -> LauncherTempStepper(
            tempText = formatHvacSetTempAnnotated(passTemp),
            onDown = { sendAdjustHvacTemperature(context, passTemp, -0.5f, HvacTempZone.Passenger) },
            onUp = { sendAdjustHvacTemperature(context, passTemp, 0.5f, HvacTempZone.Passenger) },
            onTextClick = { launchClimateApp(context) },
            useTempColorIndication = tempColorIndication,
            celsius = passTemp,
        )
        "hvac_heater_level" -> {
            val currentTemp = driverTemp
            val heaterLevel = if (currentTemp != null) {
                ((currentTemp - 16f).roundToInt() + 1).coerceIn(1, 15)
            } else {
                8
            }
            LauncherHeaterLevelStepper(
                level = heaterLevel,
                onDown = { sendAdjustHvacTemperature(context, driverTemp, -1.0f, HvacTempZone.Driver) },
                onUp = { sendAdjustHvacTemperature(context, driverTemp, 1.0f, HvacTempZone.Driver) },
                onTextClick = { launchClimateApp(context) },
            )
        }
        "audio_volume" -> {
            val systemVolume = rememberSystemMediaVolume(context)

            LauncherVolumeStepper(
                volumeText = systemVolume.toString(),
                onDown = { sendAdjustAudioVolume(context, -1) },
                onUp = { sendAdjustAudioVolume(context, 1) },
            )
        }
        "screen_brightness" -> {
            val systemSettings = rememberLauncherSystemSettings(context)
            val brightnessPercent = (systemSettings.brightnessNorm * 100f).roundToInt()

            LauncherBrightnessStepper(
                brightnessText = brightnessPercent.toString(),
                onDown = {
                    val currentPercent = (systemSettings.brightnessNorm * 100f).roundToInt()
                    val targetPercent = (currentPercent - 10).coerceIn(0, 100)
                    systemSettings.applyBrightnessNorm(targetPercent / 100f)
                },
                onUp = {
                    val currentPercent = (systemSettings.brightnessNorm * 100f).roundToInt()
                    val targetPercent = (currentPercent + 10).coerceIn(0, 100)
                    systemSettings.applyBrightnessNorm(targetPercent / 100f)
                },
            )
        }
        "hvac_auto" -> {
            val isAutoOn = hvacAuto is MbCanBinaryState.On
            val tint = if (isAutoOn) HvacOnColor else HvacOffColor
            LauncherDockIcon(onClick = { sendToggleHvacAuto(context) }) {
                Text(
                    text = "AUTO",
                    color = tint,
                    fontSize = dockSp(10.4f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        "seat_heat_left" -> LauncherDockIcon(
            onClick = {
                sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, leftRaw)
            },
            onDoubleClick = {
                LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, "seat_heat_rear_left")
            },
        ) {
            LauncherSeatHeatIcon(raw = leftRaw)
        }
        "seat_vent_left" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, leftRaw)
        }) {
            LauncherSeatVentIcon(raw = leftRaw)
        }
        "recirc" -> LauncherDockIcon(onClick = { sendToggleHvacAirRecirculation(context) }) {
            val isRecircOn = hvacRecirc is MbCanBinaryState.On
            val recircRes = if (isRecircOn) R.drawable.ic_widget_hvac_recirc_on else R.drawable.ic_widget_hvac_recirc_off
            Image(
                painter = painterResource(recircRes),
                contentDescription = null,
                modifier = Modifier.size(dockDp(34.606f)),
                colorFilter = ColorFilter.tint(HvacOffColor),
            )
        }
        "seat_heat_right" -> LauncherDockIcon(
            onClick = {
                sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, rightRaw)
            },
            onDoubleClick = {
                LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, "seat_heat_rear_right")
            },
        ) {
            LauncherSeatHeatIcon(raw = rightRaw, mirrored = true)
        }
        "seat_vent_right" -> LauncherDockIcon(onClick = {
            sendCycleFrontSeatVent(context, MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, rightRaw)
        }) {
            LauncherSeatVentIcon(raw = rightRaw, mirrored = true)
        }
        "seat_heat_rear_left" -> LauncherDockIcon(
            onClick = {
                sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH, rearLeftRaw)
            },
            onDoubleClick = {
                LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, "seat_heat_left")
            },
        ) {
            LauncherSeatHeatIcon(raw = rearLeftRaw, badgeText = "REAR")
        }
        "seat_heat_rear_right" -> LauncherDockIcon(
            onClick = {
                sendCycleFrontSeatHeat(context, MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH, rearRightRaw)
            },
            onDoubleClick = {
                LauncherAppConfigStore.setButtonInUnifiedSlot(context, index, "seat_heat_right")
            },
        ) {
            LauncherSeatHeatIcon(raw = rearRightRaw, mirrored = true, badgeText = "REAR")
        }
        "steering_heat" -> LauncherDockIcon(onClick = { sendToggleSteeringWheelHeat(context) }) {
            LauncherBinaryTintIcon(
                drawableRes = R.drawable.ic_widget_steering_wheel_heat,
                active = steeringHeat is MbCanBinaryState.On,
                activeColor = Color(0xFFF97316),
                sizeDp = 24.7f,
            )
        }
        "windscreen_heat" -> LauncherDockIcon(onClick = { sendToggleFrontWindscreenHeat(context) }) {
            LauncherBinaryTintIcon(
                drawableRes = R.drawable.ic_widget_front_windscreen_heat,
                active = windscreenHeat is MbCanBinaryState.On,
                activeColor = Color(0xFFF97316),
            )
        }
        "front_defrost" -> LauncherDockIcon(onClick = { sendToggleHvacDefrosterFront(context) }) {
            LauncherHvacIcon(R.drawable.ic_widget_hvac_defroster_front, hvacDefrost)
        }
        "rear_defrost" -> LauncherDockIcon(onClick = { sendToggleRearWindowMirrorsDefrost(context) }) {
            LauncherBinaryTintIcon(
                drawableRes = R.drawable.ic_widget_rear_window_mirrors_defrost,
                active = rearDefrost is MbCanBinaryState.On,
                activeColor = Color(0xFFF97316),
            )
        }
        "headlights" -> {
            val snapshot = rememberLauncherVehicleControlSnapshot(enabled = true)
            val currentRaw = snapshot.lightControlRaw ?: LIGHT_CONTROL_OFF
            LauncherDockIcon(onClick = { sendCycleHeadlightsSwitch(context, currentRaw) }) {
                LauncherHeadlightsModeIcon(raw = currentRaw)
            }
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
            buttonId.startsWith("tbm_auto:") -> {
                val parsed = remember(buttonId) { parseTbmAutoButtonId(buttonId) }
                val autoId = parsed.automationId
                val tbmRepository = remember(context) { TbmRepositoryProvider.get(context) }
                val tbmAutomations by tbmRepository.automations.collectAsStateWithLifecycle()
                val autoItem = remember(autoId, tbmAutomations) { tbmAutomations.firstOrNull { it.id == autoId } }
                val displayTitle = parsed.customTitle ?: (autoItem?.name ?: autoId)
                val iconVector = resolveTbmIconVector(parsed.iconName)

                LauncherDockIcon(onClick = {
                    val execName = displayTitle.ifBlank { autoItem?.name ?: autoId }
                    val execMsg = context.getString(R.string.launcher_tbm_automation_executed, execName)
                    val failMsg = context.getString(R.string.launcher_tbm_automation_failed, execName, "Error")
                    CoroutineScope(Dispatchers.Main).launch {
                        val res = tbmRepository.runAutomation(autoId)
                        if (res.getOrDefault(false)) {
                            Toast.makeText(context, execMsg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(2.dp),
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = displayTitle.ifBlank { autoItem?.name ?: autoId },
                            tint = if (LauncherThemeState.darkTheme) LauncherColors.AccentCyan else Color(0xFF374151),
                            modifier = Modifier.size(dockDp(if (displayTitle.isNotBlank()) 22f else 28f)),
                        )
                        if (displayTitle.isNotBlank()) {
                            Text(
                                text = displayTitle,
                                color = LauncherColors.TextPrimary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            else -> {
                val pkg = buttonId.removePrefix("pkg:").removePrefix("app:")
                if (pkg == "com.wt.multimedia.local") {
                    LauncherDockIcon(onClick = { launchLauncherApp(context, pkg) }) {
                        Image(
                            painter = painterResource(R.drawable.radio_vtkb246z55e9),
                            contentDescription = "Радио",
                            colorFilter = ColorFilter.tint(if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)),
                            modifier = Modifier.size(dockDp(28f)),
                        )
                    }
                } else {
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
}

@Composable
private fun LauncherTempStepper(
    tempText: AnnotatedString,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onTextClick: (() -> Unit)? = null,
    useTempColorIndication: Boolean = false,
    celsius: Float? = null,
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 18.dp.toPx() } }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val blueColor = Color(0xFF3B82F6)
    val redColor = Color(0xFFEF4444)

    val downTint = if (useTempColorIndication) blueColor else LauncherColors.TextSecondary
    val upTint = if (useTempColorIndication) redColor else LauncherColors.TextSecondary

    val textColor = if (useTempColorIndication) {
        val fraction = if (celsius != null) ((celsius - 16f) / 14f).coerceIn(0f, 1f) else 0.5f
        lerp(blueColor, redColor, fraction)
    } else {
        LauncherColors.TextPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = { totalDragY = 0f },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                        if (totalDragY <= -thresholdPx) {
                            onUp()
                            totalDragY = 0f
                        } else if (totalDragY >= thresholdPx) {
                            onDown()
                            totalDragY = 0f
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(dockDp(30f))
                .fillMaxHeight()
                .clickable(onClick = onDown),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = downTint,
                modifier = Modifier.size(dockDp(24f)),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (onTextClick != null) {
                        Modifier.clickable(onClick = onTextClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tempText,
                style = MaterialTheme.typography.tboxCaption,
                color = textColor,
                fontSize = dockSp(21.275f),
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }

        Box(
            modifier = Modifier
                .width(dockDp(30f))
                .fillMaxHeight()
                .clickable(onClick = onUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                null,
                tint = upTint,
                modifier = Modifier.size(dockDp(24f)),
            )
        }
    }
}

@Composable
private fun LauncherHeaterLevelStepper(
    level: Int,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onTextClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 18.dp.toPx() } }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val blueColor = Color(0xFF3B82F6)
    val redColor = Color(0xFFEF4444)
    val fraction = (level.coerceIn(1, 15) - 1) / 14f
    val levelColor = lerp(blueColor, redColor, fraction)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = { totalDragY = 0f },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                        if (totalDragY <= -thresholdPx) {
                            onUp()
                            totalDragY = 0f
                        } else if (totalDragY >= thresholdPx) {
                            onDown()
                            totalDragY = 0f
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(dockDp(30f))
                .fillMaxHeight()
                .clickable(onClick = onDown),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                null,
                tint = blueColor,
                modifier = Modifier.size(dockDp(24f)),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (onTextClick != null) {
                        Modifier.clickable(onClick = onTextClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.tboxCaption,
                color = levelColor,
                fontSize = dockSp(18.5f),
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }

        Box(
            modifier = Modifier
                .width(dockDp(30f))
                .fillMaxHeight()
                .clickable(onClick = onUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = redColor,
                modifier = Modifier.size(dockDp(24f)),
            )
        }
    }
}

@Composable
private fun rememberSystemMediaVolume(context: Context): Int {
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var currentVolume by remember(context) {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }

    DisposableEffect(context) {
        val appCtx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val updated = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                currentVolume = updated
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        runCatching { appCtx.registerReceiver(receiver, filter) }

        onDispose {
            runCatching { appCtx.unregisterReceiver(receiver) }
        }
    }

    return currentVolume
}

@Composable
private fun LauncherVolumeStepper(
    volumeText: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        LauncherDockIcon(onClick = onDown) {
            Image(
                painter = painterResource(R.drawable.ic_speaker_minus),
                contentDescription = "Уменьшить громкость",
                colorFilter = ColorFilter.tint(LauncherColors.TextSecondary),
                modifier = Modifier.size(dockDp(20.57f)),
            )
        }
        Text(
            text = volumeText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextPrimary,
            fontSize = dockSp(18f),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        LauncherDockIcon(onClick = onUp) {
            Image(
                painter = painterResource(R.drawable.ic_speaker_plus),
                contentDescription = "Увеличить громкость",
                colorFilter = ColorFilter.tint(LauncherColors.TextSecondary),
                modifier = Modifier.size(dockDp(20.57f)),
            )
        }
    }
}

@Composable
private fun LauncherBrightnessStepper(
    brightnessText: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        LauncherDockIcon(onClick = onDown) {
            Image(
                painter = painterResource(R.drawable.ic_brightness_minus),
                contentDescription = "Уменьшить яркость",
                colorFilter = ColorFilter.tint(LauncherColors.TextSecondary),
                modifier = Modifier.size(dockDp(18.7f)),
            )
        }
        Text(
            text = brightnessText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextPrimary,
            fontSize = dockSp(18f),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        LauncherDockIcon(onClick = onUp) {
            Image(
                painter = painterResource(R.drawable.ic_brightness_plus),
                contentDescription = "Увеличить яркость",
                colorFilter = ColorFilter.tint(LauncherColors.TextSecondary),
                modifier = Modifier.size(dockDp(18.7f)),
            )
        }
    }
}

@Composable
private fun LauncherHvacIcon(drawableRes: Int, state: MbCanBinaryState, sizeDp: Float = 26f) {
    val tint = when (state) {
        is MbCanBinaryState.On -> HvacOnColor
        else -> HvacOffColor
    }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(dockDp(sizeDp)),
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
private fun LauncherSeatHeatIcon(
    raw: Int,
    mirrored: Boolean = false,
    badgeText: String? = null,
) {
    val level = heatLevel(raw)
    val activeColor = if (level > 0) SeatHeatOnColor else HvacOffColor

    Box(
        modifier = Modifier.size(dockDp(25.5f)),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeText != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(dockDp(17.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_widget_seat),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dockDp(15.5f))
                            .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                        colorFilter = ColorFilter.tint(activeColor),
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
                                .size(dockDp(15.5f))
                                .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                            colorFilter = ColorFilter.tint(if (enabled) SeatHeatOnColor else HvacOffColor),
                        )
                    }
                }
                Text(
                    text = badgeText,
                    color = activeColor,
                    fontSize = dockSp(6.5f),
                    fontWeight = FontWeight.Bold,
                    lineHeight = dockSp(7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.ic_widget_seat),
                contentDescription = null,
                modifier = Modifier
                    .size(dockDp(22.1f))
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(activeColor),
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
                        .size(dockDp(22.1f))
                        .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                    colorFilter = ColorFilter.tint(if (enabled) SeatHeatOnColor else HvacOffColor),
                )
            }
        }
    }
}

@Composable
private fun LauncherSeatVentIcon(raw: Int, mirrored: Boolean = false) {
    val level = ventLevel(raw)
    Box(modifier = Modifier.size(dockDp(25.5f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .size(dockDp(22.1f))
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
                    .size(dockDp(22.1f))
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(if (enabled) HvacOnColor else HvacOffColor),
            )
        }
    }
}

@Composable
private fun LauncherBinaryTintIcon(
    drawableRes: Int,
    active: Boolean,
    activeColor: Color = HvacOnColor,
    sizeDp: Float = 26f,
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(dockDp(sizeDp)),
        colorFilter = ColorFilter.tint(if (active) activeColor else HvacOffColor),
    )
}

@Composable
private fun LauncherHeadlightsModeIcon(raw: Int) {
    val (iconRes, tintColor) = when (raw) {
        LIGHT_CONTROL_AUTO -> R.drawable.ic_headlights_auto to Color(0xFF22C55E)
        LIGHT_CONTROL_LOW_BEAM -> R.drawable.ic_headlights_low_beam to Color(0xFF38BDF8)
        LIGHT_CONTROL_POSITION -> R.drawable.ic_headlights_position to Color(0xFF38BDF8)
        else -> R.drawable.ic_headlights_off to Color(0xFF6B7280)
    }
    Box(modifier = Modifier.size(dockDp(30f)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.launcher_vs_headlights),
            modifier = Modifier.size(dockDp(27f)),
            colorFilter = ColorFilter.tint(tintColor),
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
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardBgVisible = LocalClimateCardBgVisible.current
    val playSound = rememberPlaySystemClickSound()
    val wrappedOnClick = remember(onClick, playSound) {
        {
            playSound()
            vad.dashing.tbox.DclLogRecorder.logUiClick("DockIcon", label = "click")
            onClick()
        }
    }
    val wrappedDoubleClick = remember(onDoubleClick, playSound) {
        onDoubleClick?.let {
            {
                playSound()
                vad.dashing.tbox.DclLogRecorder.logUiClick("DockIcon", label = "doubleClick")
                it()
            }
        }
    }
    val wrappedLongClick = remember(onLongClick, playSound) {
        onLongClick?.let {
            {
                playSound()
                vad.dashing.tbox.DclLogRecorder.logUiClick("DockIcon", label = "longClick")
                it()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(dockDp(44f))
            .clip(RoundedCornerShape(12.dp))
            .background(if (cardBgVisible) LauncherColors.CardDark else Color.Transparent)
            .then(
                if (wrappedDoubleClick != null || wrappedLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = wrappedOnClick,
                        onDoubleClick = wrappedDoubleClick,
                        onLongClick = wrappedLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = wrappedOnClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
