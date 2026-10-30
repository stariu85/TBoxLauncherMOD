package vad.dashing.tbox.ui.launcher

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.tbm.TbmConnectionState
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MirrorFoldLastCommandStore
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendToggleAvh
import vad.dashing.tbox.ui.sendToggleMirrorFold
import vad.dashing.tbox.ui.sendToggleParkingRadar
import vad.dashing.tbox.ui.sendToggleRearFogLight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusIconTint: Color
    get() = if (LauncherThemeState.darkTheme) Color.White else Color(0xFF111827)
private val IndicatorIconTint: Color
    get() = if (LauncherThemeState.darkTheme) Color(0xFFB0B7C0) else Color(0xFF5C6675)

private fun isSpacer(itemId: String): Boolean = itemId == "spacer" || itemId.startsWith("spacer")

private fun isIndicator(itemId: String): Boolean = itemId in setOf(
    "temp", "inside_temp", "combined_temp", "engine_temp", "fuel", "avg_fuel", "trip_cost", "trip_duration", "air_quality", "voltage", "cpu", "ram", "gps", "maintenance"
)

private data class TopBarGroup(
    val isIndicatorGroup: Boolean,
    val items: List<Pair<Int, String>>,
)

private fun groupTopBarItems(items: List<String>): List<TopBarGroup> {
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<TopBarGroup>()
    var currentGroup = mutableListOf<Pair<Int, String>>()
    var currentIsIndicator = isIndicator(items[0])

    items.forEachIndexed { index, itemId ->
        val itemIsIndicator = isIndicator(itemId)
        if (itemIsIndicator == currentIsIndicator) {
            currentGroup.add(index to itemId)
        } else {
            result.add(TopBarGroup(currentIsIndicator, currentGroup))
            currentGroup = mutableListOf(index to itemId)
            currentIsIndicator = itemIsIndicator
        }
    }
    if (currentGroup.isNotEmpty()) {
        result.add(TopBarGroup(currentIsIndicator, currentGroup))
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherTopHeaderBar(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isAnyItemDragging by remember { mutableStateOf(false) }
    val isPickerOverlayShowing = LauncherTopBarPickerOverlayWindow.isShowingState.value
    val isSpacerHighlighted = isAnyItemDragging || isPickerOverlayShowing

    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val insideTemp by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val engineTemp by canViewModel.engineTemperature.collectAsStateWithLifecycle()
    val insideAirQuality by canViewModel.insideAirQuality.collectAsStateWithLifecycle()
    val outsideAirQuality by canViewModel.outsideAirQuality.collectAsStateWithLifecycle()
    val avgFuel by canViewModel.averageFuelConsumption.collectAsStateWithLifecycle()
    val tbmRepo = remember(context) { TbmRepositoryProvider.get(context) }
    val tbmAvgFuel by tbmRepo.activeTripAvgFuelL100km.collectAsStateWithLifecycle()
    val displayedAvgFuel = tbmAvgFuel ?: avgFuel
    val activeTripDistanceKm by tbmRepo.activeTripDistanceKm.collectAsStateWithLifecycle()
    val localTripDistanceKm by canViewModel.localTripDistanceKm.collectAsStateWithLifecycle()
    val displayTripDistanceKm = activeTripDistanceKm ?: localTripDistanceKm

    val activeTripDurationSeconds by tbmRepo.activeTripDurationSeconds.collectAsStateWithLifecycle()
    val localTripDurationSeconds by canViewModel.localTripDurationSeconds.collectAsStateWithLifecycle()
    val displayTripDurationSec = activeTripDurationSeconds ?: localTripDurationSeconds

    var durationTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            CanDataRepository.updateTripDuration()
            durationTick++
        }
    }
    val cruisePresetsRevision by LauncherAppConfigStore.cruisePresetsRevisionFlow
        .collectAsStateWithLifecycle()
    val fuelPrice = remember(context, cruisePresetsRevision) {
        LauncherAppConfigStore.fuelPriceRub(context)
    }
    val tripCost = remember(displayedAvgFuel, displayTripDistanceKm, fuelPrice) {
        val avg = displayedAvgFuel ?: 0f
        val dist = displayTripDistanceKm ?: 0f
        (avg / 100f) * dist * fuelPrice
    }
    val fuelPctFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val fuelPctRaw by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelPct = fuelPctFiltered ?: fuelPctRaw
    val rangeKm by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()
    val maintenanceDistance by canViewModel.distanceToNextMaintenance.collectAsStateWithLifecycle()

    val cpuUsage = rememberCpuUsagePercentage()
    val ramUsage = rememberRamUsagePercentage()

    val topBarLeftIndicatorsRevision by LauncherAppConfigStore.topBarLeftIndicatorsRevisionFlow
        .collectAsStateWithLifecycle()
    val topBarLeftIndicatorsVisible = remember(context, topBarLeftIndicatorsRevision) {
        LauncherAppConfigStore.topBarLeftIndicatorsVisible(context)
    }

    val topBarHeightRevision by LauncherAppConfigStore.topBarHeightRevisionFlow
        .collectAsStateWithLifecycle()
    val topBarHeightDp = remember(context, topBarHeightRevision) {
        LauncherAppConfigStore.topBarHeightDp(context)
    }

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val locValues by tboxViewModel.locValues.collectAsStateWithLifecycle()
    val satInfo = rememberSatelliteInfo(
        tboxConnected = tboxConnected,
        locValues = locValues,
    )

    val topBarItemsRevision by LauncherAppConfigStore.topBarItemsRevisionFlow
        .collectAsStateWithLifecycle()
    val topBarItems = remember(context, topBarItemsRevision) {
        LauncherAppConfigStore.topBarItems(context)
    }

    val borderRevision by LauncherAppConfigStore.topBarIndicatorsBorderRevisionFlow
        .collectAsStateWithLifecycle()
    val indicatorsBorderEnabled = remember(context, borderRevision) {
        LauncherAppConfigStore.topBarIndicatorsBorderEnabled(context)
    }

    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)
    val headlightsRaw = vehicleControls.lightControlRaw ?: LIGHT_CONTROL_OFF

    val buttonDiameterDp = (topBarHeightDp - 10).coerceAtLeast(16)
    val indicatorFontSizeSp = 18.sp
    val indicatorIconSizeDp = 18.dp

    var fuelShowsRange by remember {
        mutableStateOf(LauncherAppConfigStore.fuelShowsRange(context))
    }
    val unitKm = stringResource(R.string.unit_km)
    val fuelText = if (fuelShowsRange) {
        rangeKm?.let { "${valueToString(it, 0)} $unitKm" } ?: "—"
    } else {
        fuelPct?.toInt()?.let { "$it%" } ?: "—"
    }

    val voltageValue = voltage?.takeIf { !it.isNaN() && !it.isInfinite() }
    val voltageLow = voltageValue != null && voltageValue < 12f
    val voltageText = voltageValue?.let {
        "${valueToString(it, 1)} ${stringResource(R.string.unit_volt)}"
    } ?: "— ${stringResource(R.string.unit_volt)}"

    var clockNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            clockNowMs = now
            delay(1_000L - (now % 1_000L))
        }
    }
    val timeText = remember(clockNowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clockNowMs))
    }
    val dateText = remember(clockNowMs) {
        SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(clockNowMs))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.topHeaderBottomPx = rect.bottom.toInt()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left side: ADAS assist zone + Custom Info/Button items
        Row(
            modifier = Modifier
                .padding(start = 10.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            if (topBarLeftIndicatorsVisible) {
                val groups = remember(topBarItems) { groupTopBarItems(topBarItems) }
                val isDark = LauncherThemeState.darkTheme
                val borderColor = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)

                groups.forEach { group ->
                    if (group.isIndicatorGroup) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 5.dp)
                                .then(
                                    if (indicatorsBorderEnabled) {
                                        Modifier
                                            .clip(CircleShape)
                                            .border(1.dp, borderColor, CircleShape)
                                            .padding(horizontal = 12.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                group.items.forEachIndexed { itemIndex, (originalIndex, itemId) ->
                                    if (itemIndex > 0) {
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.tboxCaption,
                                            color = LauncherColors.TextPrimary.copy(alpha = 0.5f),
                                            fontSize = indicatorFontSizeSp,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                        )
                                    }

                                    LauncherDraggableTopBarItem(
                                        itemId = itemId,
                                        index = originalIndex,
                                        totalCount = topBarItems.size,
                                        onDragStateChange = { dragging ->
                                            isAnyItemDragging = dragging
                                        },
                                        onReorder = { fromIdx, toIdx ->
                                            LauncherAppConfigStore.reorderTopBarItem(context, fromIdx, toIdx)
                                        },
                                        onRemove = {
                                            LauncherAppConfigStore.removeTopBarItem(context, itemId)
                                        },
                                    ) {
                                        RenderTopBarItemContent(
                                            itemId = itemId,
                                            outsideTemp = outsideTemp,
                                            insideTemp = insideTemp,
                                            engineTemp = engineTemp,
                                            insideAirQuality = insideAirQuality,
                                            outsideAirQuality = outsideAirQuality,
                                            displayedAvgFuel = displayedAvgFuel,
                                            tripCost = tripCost,
                                            displayTripDurationSec = displayTripDurationSec,
                                            fuelText = fuelText,
                                            fuelShowsRange = fuelShowsRange,
                                            onFuelShowsRangeToggle = {
                                                val next = !fuelShowsRange
                                                fuelShowsRange = next
                                                LauncherAppConfigStore.setFuelShowsRange(context, next)
                                            },
                                            voltageText = voltageText,
                                            voltageLow = voltageLow,
                                            cpuUsage = cpuUsage,
                                            ramUsage = ramUsage,
                                            satInfo = satInfo,
                                            maintenanceDistance = maintenanceDistance,
                                            headlightsRaw = headlightsRaw,
                                            vehicleControls = vehicleControls,
                                            buttonDiameterDp = buttonDiameterDp,
                                            indicatorFontSizeSp = indicatorFontSizeSp,
                                            indicatorIconSizeDp = indicatorIconSizeDp,
                                            isSpacerHighlighted = isSpacerHighlighted,
                                            context = context,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        group.items.forEach { (originalIndex, itemId) ->
                            LauncherDraggableTopBarItem(
                                itemId = itemId,
                                index = originalIndex,
                                totalCount = topBarItems.size,
                                onDragStateChange = { dragging ->
                                    isAnyItemDragging = dragging
                                },
                                onReorder = { fromIdx, toIdx ->
                                    LauncherAppConfigStore.reorderTopBarItem(context, fromIdx, toIdx)
                                },
                                onRemove = {
                                    LauncherAppConfigStore.removeTopBarItem(context, itemId)
                                },
                            ) {
                                RenderTopBarItemContent(
                                    itemId = itemId,
                                    outsideTemp = outsideTemp,
                                    insideTemp = insideTemp,
                                    engineTemp = engineTemp,
                                    insideAirQuality = insideAirQuality,
                                    outsideAirQuality = outsideAirQuality,
                                    displayedAvgFuel = displayedAvgFuel,
                                    tripCost = tripCost,
                                    displayTripDurationSec = displayTripDurationSec,
                                    fuelText = fuelText,
                                    fuelShowsRange = fuelShowsRange,
                                    onFuelShowsRangeToggle = {
                                        val next = !fuelShowsRange
                                        fuelShowsRange = next
                                        LauncherAppConfigStore.setFuelShowsRange(context, next)
                                    },
                                    voltageText = voltageText,
                                    voltageLow = voltageLow,
                                    cpuUsage = cpuUsage,
                                    ramUsage = ramUsage,
                                    satInfo = satInfo,
                                    headlightsRaw = headlightsRaw,
                                    vehicleControls = vehicleControls,
                                    buttonDiameterDp = buttonDiameterDp,
                                    indicatorFontSizeSp = indicatorFontSizeSp,
                                    indicatorIconSizeDp = indicatorIconSizeDp,
                                    isSpacerHighlighted = isSpacerHighlighted,
                                    context = context,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Middle: EMPTY SPACE (long press here triggers the addition menu)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(topBarHeightDp) {
                    detectTapGestures(
                        onLongPress = {
                            LauncherTopBarPickerOverlayWindow.show(
                                context = context,
                                topBarHeightDp = topBarHeightDp,
                                onSelectItem = { itemId ->
                                    LauncherAppConfigStore.addTopBarItem(context, itemId)
                                },
                                onDismiss = {},
                            )
                        },
                    )
                },
        )

        // 3. Right side: Status icons + Time/Date
        Row(
            modifier = Modifier
                .padding(end = 10.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherHeaderStatusIcons(
                tboxViewModel = tboxViewModel,
                showVehicleAlerts = false,
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.tboxCaption,
                color = LauncherColors.TextPrimary.copy(alpha = 0.5f),
                fontSize = indicatorFontSizeSp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = timeText,
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.tboxCaption,
                color = LauncherColors.TextPrimary.copy(alpha = 0.5f),
                fontSize = indicatorFontSizeSp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = dateText,
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun RenderTopBarItemContent(
    itemId: String,
    outsideTemp: Float?,
    insideTemp: Float?,
    engineTemp: Float? = null,
    insideAirQuality: UInt? = null,
    outsideAirQuality: UInt? = null,
    displayedAvgFuel: Float?,
    tripCost: Float? = null,
    displayTripDurationSec: Long? = null,
    fuelText: String,
    fuelShowsRange: Boolean,
    onFuelShowsRangeToggle: () -> Unit,
    voltageText: String,
    voltageLow: Boolean,
    cpuUsage: Int,
    ramUsage: Int,
    satInfo: SatelliteInfo,
    maintenanceDistance: UInt? = null,
    headlightsRaw: Int,
    vehicleControls: LauncherVehicleControlSnapshot,
    buttonDiameterDp: Int,
    indicatorFontSizeSp: TextUnit,
    indicatorIconSizeDp: Dp,
    isSpacerHighlighted: Boolean,
    context: Context,
) {
    val isDark = LauncherThemeState.darkTheme
    when {
        isSpacer(itemId) -> {
            val bgColor = if (isSpacerHighlighted) {
                if (isDark) Color(0x509CA3AF) else Color(0x60BAE6FD)
            } else {
                Color.Transparent
            }
            val borderColor = if (isSpacerHighlighted) {
                if (isDark) Color(0x80D1D5DB) else Color(0x8038BDF8)
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .width((buttonDiameterDp + 32).dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            )
        }
        itemId == "temp" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_temp_up),
                    contentDescription = "Температура снаружи",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = outsideTemp?.let { "${valueToString(it, 0)}°" } ?: "—°",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "inside_temp" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_temp_down),
                    contentDescription = "Температура в салоне",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = insideTemp?.let { "${valueToString(it, 0)}°" } ?: "—°",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "combined_temp" -> {
            val insideStr = insideTemp?.let { valueToString(it, 0) } ?: "—"
            val outsideStr = outsideTemp?.let { valueToString(it, 0) } ?: "—"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_temp_combined),
                    contentDescription = "Температура снаружи и в салоне",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = "$outsideStr/$insideStr°",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "air_quality" -> {
            val insideStr = insideAirQuality?.toString() ?: "—"
            val outsideStr = outsideAirQuality?.toString() ?: "—"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_leaf),
                    contentDescription = "Качество воздуха PM 2.5",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = "$outsideStr/$insideStr",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "avg_fuel" -> {
            val avgNum = displayedAvgFuel?.let { valueToString(it, 1) } ?: "—"
            val unitStr = stringResource(R.string.unit_liter)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            CanDataRepository.resetLocalTripDistance()
                        }
                    )
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fuel_drop),
                    contentDescription = "Средний расход",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = avgNum,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = unitStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary.copy(alpha = 0.8f),
                    fontSize = (indicatorFontSizeSp / 2f),
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "trip_cost" -> {
            val costStr = valueToString(tripCost, 1)
            val unitStr = stringResource(R.string.unit_ruble_short)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            CanDataRepository.resetLocalTripDistance()
                        }
                    )
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_trip_cost),
                    contentDescription = "Стоимость поездки",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = costStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = unitStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary.copy(alpha = 0.8f),
                    fontSize = (indicatorFontSizeSp / 2f),
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "engine_temp" -> {
            val engineTempStr = engineTemp?.let { "${valueToString(it, 0)}°" } ?: "—°"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_engine_temp),
                    contentDescription = "Температура двигателя",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = engineTempStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "trip_duration" -> {
            val totalSec = displayTripDurationSec ?: 0L
            val hours = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            val unitHours = stringResource(R.string.unit_hours)
            val unitMins = stringResource(R.string.unit_minutes)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            CanDataRepository.resetLocalTripDistance()
                        }
                    )
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_trip_duration),
                    contentDescription = "Время поездки",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                if (hours > 0L) {
                    Text(
                        text = "$hours",
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary,
                        fontSize = indicatorFontSizeSp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = unitHours,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary.copy(alpha = 0.8f),
                        fontSize = (indicatorFontSizeSp / 2f),
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = "$mins",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = unitMins,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary.copy(alpha = 0.8f),
                    fontSize = (indicatorFontSizeSp / 2f),
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "fuel" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFuelShowsRangeToggle,
                ),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fuel),
                    contentDescription = stringResource(R.string.launcher_vs_fuel),
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = fuelText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "voltage" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_battery),
                    contentDescription = stringResource(R.string.data_title_voltage),
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(if (voltageLow) LauncherColors.WarningRed else IndicatorIconTint),
                )
                Text(
                    text = voltageText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "cpu" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_cpu),
                    contentDescription = "ЦП",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = "$cpuUsage%",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "ram" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_ram),
                    contentDescription = "ОЗУ",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = "$ramUsage%",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "gps" -> {
            val satColor = when {
                satInfo.isInertial -> Color(0xFF3B82F6)
                satInfo.hasFix -> Color(0xFF22C55E)
                else -> Color(0xFFF97316)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_satellite),
                    contentDescription = stringResource(R.string.location_visible_satellites),
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(satColor),
                )
                Text(
                    text = "${satInfo.visibleSatellites}/${satInfo.usingSatellites}",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "maintenance" -> {
            val distStr = maintenanceDistance?.let { valueToString(it, 0) } ?: "—"
            val unitStr = stringResource(R.string.unit_km)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_maintenance),
                    contentDescription = "Пробег до ТО",
                    modifier = Modifier.size(indicatorIconSizeDp),
                    colorFilter = ColorFilter.tint(IndicatorIconTint),
                )
                Text(
                    text = distStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = indicatorFontSizeSp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = unitStr,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary.copy(alpha = 0.8f),
                    fontSize = (indicatorFontSizeSp / 2f),
                    fontWeight = FontWeight.Light,
                )
            }
        }
        itemId == "headlights" -> {
            val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable { sendCycleHeadlightsSwitch(context, headlightsRaw) },
                contentAlignment = Alignment.Center,
            ) {
                LauncherTopBarHeadlightsIcon(
                    raw = headlightsRaw,
                    iconSizeDp = (buttonDiameterDp * 0.605f).dp,
                )
            }
        }
        itemId == "trunk" -> {
            val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { sendOpenCloseTrunk(context) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_trunk),
                    contentDescription = "Багажник",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
            }
        }
        itemId == "theme" -> {
            val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        LauncherThemeState.setDarkTheme(context, !isDark)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(if (isDark) R.drawable.ic_theme_moon else R.drawable.ic_theme_sun),
                    contentDescription = "Тема",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
            }
        }
        itemId == "mirrors" -> {
            val mirrorsFolded = MirrorFoldLastCommandStore.readLastValue() == MIRROR_FOLD_SWITCH_VALUE_FOLD
            val buttonCircleBg = if (mirrorsFolded) {
                if (isDark) Color(0xFF0369A1) else Color(0xFF38BDF8)
            } else {
                if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            }
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        sendToggleMirrorFold(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_mirror),
                    contentDescription = "Складывание зеркал",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(
                        if (mirrorsFolded) Color.White else LauncherColors.TextPrimary
                    ),
                )
            }
        }
        itemId == "autohold" -> {
            val avhActive = vehicleControls.avh == true
            val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            val holdTextColor = if (avhActive) {
                Color(0xFF22C55E)
            } else {
                Color(0xFF9CA3AF)
            }
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        sendToggleAvh(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "HOLD",
                    color = holdTextColor,
                    fontSize = (buttonDiameterDp * 0.26f).sp,
                    fontWeight = if (avhActive) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
        }
        itemId == "pdc" -> {
            val pdcActive = vehicleControls.parkingRadar == true
            val buttonCircleBg = if (pdcActive) {
                if (isDark) Color(0xFF0369A1) else Color(0xFF38BDF8)
            } else {
                if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            }
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        sendToggleParkingRadar(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_widget_parking_radar),
                    contentDescription = "Парктроники",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(
                        if (pdcActive) Color.White else LauncherColors.TextPrimary
                    ),
                )
            }
        }
        itemId == "rear_fog" -> {
            val fogActive = vehicleControls.rearFogLight == true
            val buttonCircleBg = if (fogActive) {
                if (isDark) Color(0xFF0369A1) else Color(0xFF38BDF8)
            } else {
                if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            }
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        sendToggleRearFogLight(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_rear_fog),
                    contentDescription = "Задняя ПТФ",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(
                        if (fogActive) Color.White else LauncherColors.TextPrimary
                    ),
                )
            }
        }
        itemId == "avm" -> {
            val buttonCircleBg = if (isDark) Color(0xFF2D3B4E) else Color(0xFFCBD5E1)
            val buttonCircleBorder = if (isDark) Color(0x6038BDF8) else Color(0x4094A3B8)
            Box(
                modifier = Modifier
                    .size(buttonDiameterDp.dp)
                    .clip(CircleShape)
                    .background(buttonCircleBg)
                    .border(1.dp, buttonCircleBorder, CircleShape)
                    .clickable {
                        launchLauncherAppEmbedded(context, "com.mengbo.avm")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_avm),
                    contentDescription = "Камеры 360",
                    modifier = Modifier.size((buttonDiameterDp * 0.55f).dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
            }
        }
    }
}

@Composable
private fun LauncherDraggableTopBarItem(
    itemId: String,
    index: Int,
    totalCount: Int,
    onDragStateChange: (Boolean) -> Unit = {},
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var itemWidthPx by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val removeThresholdPx = remember(density) { with(density) { 20.dp.toPx() } }
    val isRemoving = isDragging && offsetY > removeThresholdPx

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 100f else 0f)
            .onGloballyPositioned { coordinates ->
                itemWidthPx = coordinates.size.width.toFloat()
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                alpha = if (isRemoving) 0.30f else if (isDragging) 0.80f else 1.0f
                scaleX = if (isDragging) 1.10f else 1.0f
                scaleY = if (isDragging) 1.10f else 1.0f
            }
            .pointerInput(itemId, index, totalCount) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        onDragStateChange(true)
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragEnd = {
                        val shouldRemove = offsetY > removeThresholdPx
                        if (shouldRemove) {
                            onRemove()
                        } else if (abs(offsetX) > itemWidthPx * 0.3f) {
                            val spacingPx = with(density) { 14.dp.toPx() }
                            val stepWidthPx = if (itemWidthPx > 0f) itemWidthPx + spacingPx else with(density) { 50.dp.toPx() }
                            val offsetSlots = (offsetX / stepWidthPx).roundToInt()
                            val targetIndex = (index + offsetSlots).coerceIn(0, totalCount - 1)
                            if (targetIndex != index) {
                                onReorder(index, targetIndex)
                            }
                        }
                        isDragging = false
                        onDragStateChange(false)
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
    ) {
        content()
    }
}

@Composable
private fun LauncherTopBarHeadlightsIcon(
    raw: Int,
    iconSizeDp: Dp,
) {
    val (iconRes, tintColor) = when (raw) {
        LIGHT_CONTROL_AUTO -> R.drawable.ic_headlights_auto to Color(0xFF22C55E)
        LIGHT_CONTROL_LOW_BEAM -> R.drawable.ic_headlights_low_beam to Color(0xFF38BDF8)
        LIGHT_CONTROL_POSITION -> R.drawable.ic_headlights_position to Color(0xFF38BDF8)
        else -> R.drawable.ic_headlights_off to Color(0xFF6B7280)
    }
    Box(modifier = Modifier.size(iconSizeDp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.launcher_vs_headlights),
            modifier = Modifier.size(iconSizeDp),
            colorFilter = ColorFilter.tint(tintColor),
        )
    }
}

@Composable
internal fun LauncherHeaderStatusIcons(
    tboxViewModel: TboxViewModel,
    modifier: Modifier = Modifier,
    showVehicleAlerts: Boolean = true,
) {
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val netState by tboxViewModel.netState.collectAsStateWithLifecycle()
    val wifiConnected = rememberWifiConnected()
    val wifiLevel = rememberWifiLevel(wifiConnected)
    val bluetoothConnected = rememberBluetoothConnected()
    val networkType = mobileNetworkTypeLabel(netState.netStatus)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showVehicleAlerts) {
            LauncherVehicleAlertsStrip()
        }
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    if (tboxConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                ),
        )
        val bluetoothTint = if (bluetoothConnected) {
            StatusIconTint
        } else {
            LauncherColors.TextMuted
        }
        Image(
            painter = painterResource(R.drawable.ic_launcher_bluetooth),
            contentDescription = stringResource(R.string.launcher_vs_bluetooth),
            modifier = Modifier.size((18 * 1.15f).dp),
            colorFilter = ColorFilter.tint(bluetoothTint),
        )
        if (wifiConnected) {
            WifiArcsIcon(level = wifiLevel.coerceIn(1, 4), tint = StatusIconTint, modifier = Modifier.size(18.dp))
        }
        if (tboxConnected) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (networkType != null) {
                    Text(
                        text = networkType,
                        color = StatusIconTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val signalRes = when (netState.signalLevel.coerceIn(0, 4)) {
                    4 -> R.drawable.signal_4
                    3 -> R.drawable.signal_3
                    2 -> R.drawable.signal_2
                    1 -> R.drawable.signal_1
                    else -> R.drawable.signal_0
                }
                Image(
                    painter = painterResource(signalRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(StatusIconTint),
                )
            }
        }
    }
}

private data class SatelliteInfo(
    val visibleSatellites: Int = 0,
    val usingSatellites: Int = 0,
    val hasFix: Boolean = false,
    val isInertial: Boolean = false,
)

@Composable
private fun rememberSatelliteInfo(
    tboxConnected: Boolean,
    locValues: LocValues,
): SatelliteInfo {
    val context = LocalContext.current
    val tbmRepo = remember(context) { TbmRepositoryProvider.get(context) }
    val tbmConnState by tbmRepo.connectionState.collectAsStateWithLifecycle()
    val tbmSatVisible by tbmRepo.geoSatellitesVisible.collectAsStateWithLifecycle()
    val tbmSatUsed by tbmRepo.geoSatellitesUsed.collectAsStateWithLifecycle()
    val tbmFixValid by tbmRepo.geoFixValid.collectAsStateWithLifecycle()
    val tbmLocateStatus by tbmRepo.locateStatusSignal.collectAsStateWithLifecycle()
    val tbmSpeedSource by tbmRepo.geoSpeedSource.collectAsStateWithLifecycle()

    val isTbmConnected = tbmConnState is TbmConnectionState.Connected
    val isTbmGeoAvailable = isTbmConnected && (tbmSatVisible != null || tbmSatUsed != null || tbmFixValid != null || tbmLocateStatus != null)

    val now = System.currentTimeMillis()
    val isTboxLocFresh = tboxConnected &&
            locValues.updateTime != null &&
            (now - locValues.updateTime.time <= 15_000L)

    var systemSatInfo by remember { mutableStateOf(SatelliteInfo()) }

    val useSystemFallback = !isTbmGeoAvailable && !isTboxLocFresh

    DisposableEffect(useSystemFallback, context) {
        if (!useSystemFallback) {
            onDispose { }
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                systemSatInfo = SatelliteInfo()
                onDispose { }
            } else {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val mainHandler = Handler(Looper.getMainLooper())

                var hardwareGnssActive = false
                var lastLocationFix = false

                fun extractSatellitesFromLocation(location: Location): Pair<Int, Int> {
                    val extras = location.extras ?: return 0 to 0
                    val visible = extras.getInt("satellites_in_view", 0).takeIf { it > 0 }
                        ?: extras.getInt("satellites_visible", 0).takeIf { it > 0 }
                        ?: extras.getInt("visible_satellites", 0).takeIf { it > 0 }
                        ?: extras.getInt("satellites", 0)

                    val used = extras.getInt("satellites_used", 0).takeIf { it > 0 }
                        ?: extras.getInt("satellites_used_in_fix", 0).takeIf { it > 0 }
                        ?: extras.getInt("used_satellites", 0).takeIf { it > 0 }
                        ?: extras.getInt("satellites", 0)

                    val finalVisible = maxOf(visible, used)
                    return finalVisible to used
                }

                fun processLocation(location: Location) {
                    lastLocationFix = true
                    val (visible, used) = extractSatellitesFromLocation(location)
                    @Suppress("DEPRECATION")
                    val isMock = location.isFromMockProvider
                    if (!hardwareGnssActive || isMock) {
                        systemSatInfo = SatelliteInfo(
                            visibleSatellites = visible,
                            usingSatellites = used,
                            hasFix = true,
                        )
                    }
                }

                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        processLocation(location)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        if (provider == LocationManager.GPS_PROVIDER) {
                            lastLocationFix = false
                        }
                    }
                }

                val gnssCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        hardwareGnssActive = true
                        val total = status.satelliteCount
                        var used = 0
                        for (i in 0 until total) {
                            if (status.usedInFix(i)) {
                                used++
                            }
                        }
                        val fix = used > 0 || lastLocationFix
                        systemSatInfo = SatelliteInfo(
                            visibleSatellites = total,
                            usingSatellites = used,
                            hasFix = fix
                        )
                    }
                }

                try {
                    lm?.registerGnssStatusCallback(gnssCallback, mainHandler)

                    val providers = listOfNotNull(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER,
                        LocationManager.NETWORK_PROVIDER.takeIf { lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }
                    )

                    for (provider in providers) {
                        try {
                            lm?.requestLocationUpdates(
                                provider,
                                2000L,
                                0f,
                                locationListener,
                                Looper.getMainLooper()
                            )
                        } catch (_: Exception) {}
                    }

                    val initialLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                    if (initialLoc != null) {
                        processLocation(initialLoc)
                    }
                } catch (_: SecurityException) {
                    systemSatInfo = SatelliteInfo()
                } catch (_: Exception) {
                    // Ignored
                }

                onDispose {
                    try {
                        lm?.unregisterGnssStatusCallback(gnssCallback)
                        lm?.removeUpdates(locationListener)
                    } catch (_: Exception) {
                        // Ignored
                    }
                }
            }
        }
    }

    return when {
        isTbmGeoAvailable -> {
            val visible = tbmSatVisible ?: tbmSatUsed ?: 0
            val used = tbmSatUsed ?: 0
            val fix = tbmFixValid == true || tbmLocateStatus == true
            val inertial = (tbmSpeedSource == "retention" || tbmSpeedSource == "can") && !fix
            SatelliteInfo(
                visibleSatellites = maxOf(visible, used),
                usingSatellites = used,
                hasFix = fix,
                isInertial = inertial,
            )
        }
        isTboxLocFresh -> {
            SatelliteInfo(
                visibleSatellites = locValues.visibleSatellites,
                usingSatellites = locValues.usingSatellites,
                hasFix = locValues.hasGpsFix,
                isInertial = false,
            )
        }
        else -> {
            systemSatInfo
        }
    }
}

@Composable
private fun WifiArcsIcon(
    level: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.86f
        val stroke = size.minDimension * 0.13f
        drawCircle(
            color = tint,
            radius = stroke * 0.55f,
            center = Offset(cx, cy),
        )
        listOf(
            0.30f to 2,
            0.52f to 3,
            0.74f to 4,
        ).forEach { (frac, need) ->
            val radius = size.minDimension * frac
            val active = level >= need
            drawArc(
                color = tint.copy(alpha = if (active) 1f else 0.22f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private fun mobileNetworkTypeLabel(netStatus: String): String? = when (netStatus.uppercase()) {
    "2G", "3G", "4G", "5G", "LTE" -> netStatus.uppercase()
    else -> null
}

@Composable
private fun rememberWifiConnected(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        runCatching { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }.getOrNull()
    }
    var wifiConnected by remember { mutableStateOf(readWifiConnected(connectivityManager)) }
    DisposableEffect(connectivityManager) {
        val cm = connectivityManager ?: return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiConnected = readWifiConnected(cm)
            }

            override fun onLost(network: Network) {
                wifiConnected = readWifiConnected(cm)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                wifiConnected = readWifiConnected(cm)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return wifiConnected
}

@Composable
private fun rememberWifiLevel(wifiConnected: Boolean): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    DisposableEffect(wifiConnected) {
        if (!wifiConnected) {
            level = 0
            onDispose { }
        } else {
            level = readWifiSignalLevel(context)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    level = readWifiSignalLevel(context)
                    handler.postDelayed(this, 3000L)
                }
            }
            handler.postDelayed(runnable, 3000L)
            onDispose { handler.removeCallbacks(runnable) }
        }
    }
    return if (wifiConnected) level else 0
}

private fun readWifiConnected(connectivityManager: ConnectivityManager?): Boolean {
    val cm = connectivityManager ?: return false
    return runCatching {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun readWifiSignalLevel(context: Context): Int {
    return runCatching {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return 0
        val info = wifiManager.connectionInfo ?: return 0
        if (info.networkId == -1) return 0
        WifiManager.calculateSignalLevel(info.rssi, 5).coerceIn(0, 4)
    }.getOrDefault(0)
}

@Composable
private fun rememberBluetoothConnected(): Boolean {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(readBluetoothConnected(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                    if (state == BluetoothAdapter.STATE_CONNECTED) {
                        isConnected = true
                        return
                    } else if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                        isConnected = false
                        return
                    }
                } else if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    isConnected = true
                    return
                } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    isConnected = readBluetoothConnected(context)
                    return
                }
                isConnected = readBluetoothConnected(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        runCatching { context.registerReceiver(receiver, filter) }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000L)
            isConnected = readBluetoothConnected(context)
        }
    }

    return isConnected
}

@Suppress("MissingPermission")
private fun readBluetoothConnected(context: Context): Boolean {
    return runCatching {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) return false

        val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
        val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
        val gatt = adapter.getProfileConnectionState(BluetoothProfile.GATT)

        a2dp == BluetoothProfile.STATE_CONNECTED ||
                headset == BluetoothProfile.STATE_CONNECTED ||
                gatt == BluetoothProfile.STATE_CONNECTED
    }.getOrDefault(false)
}

@Composable
private fun rememberCpuUsagePercentage(): Int {
    var cpuUsage by remember { mutableIntStateOf(0) }
    val tracker = remember { CpuUsageTracker() }
    LaunchedEffect(Unit) {
        while (isActive) {
            cpuUsage = tracker.readCpuUsage()
            delay(2500L)
        }
    }
    return cpuUsage
}

@Composable
private fun rememberRamUsagePercentage(): Int {
    val context = LocalContext.current
    var ramUsage by remember { mutableIntStateOf(readRamUsagePercentage(context)) }
    LaunchedEffect(context) {
        while (isActive) {
            ramUsage = readRamUsagePercentage(context)
            delay(3000L)
        }
    }
    return ramUsage
}

private class CpuUsageTracker {
    private var lastTotalTime = 0L
    private var lastIdleTime = 0L

    fun readCpuUsage(): Int {
        return runCatching {
            val statFile = File("/proc/stat")
            if (statFile.canRead()) {
                val line = statFile.useLines { lines ->
                    lines.firstOrNull { it.startsWith("cpu ") }
                }
                if (line != null) {
                    val toks = line.trim().split("\\s+".toRegex())
                    if (toks.size >= 5) {
                        val user = toks[1].toLongOrNull() ?: 0L
                        val nice = toks[2].toLongOrNull() ?: 0L
                        val system = toks[3].toLongOrNull() ?: 0L
                        val idle = toks[4].toLongOrNull() ?: 0L
                        val iowait = toks.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = toks.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = toks.getOrNull(7)?.toLongOrNull() ?: 0L
                        val steal = toks.getOrNull(8)?.toLongOrNull() ?: 0L

                        val total = user + nice + system + idle + iowait + irq + softirq + steal
                        val idleTime = idle + iowait

                        val totalDiff = total - lastTotalTime
                        val idleDiff = idleTime - lastIdleTime

                        lastTotalTime = total
                        lastIdleTime = idleTime

                        if (totalDiff > 0) {
                            val usage = ((totalDiff - idleDiff).toDouble() / totalDiff.toDouble() * 100.0).toInt()
                            return usage.coerceIn(0, 100)
                        }
                    }
                }
            }
            readLoadAvgCpuUsage()
        }.getOrDefault(0)
    }

    private fun readLoadAvgCpuUsage(): Int {
        return runCatching {
            val loadFile = File("/proc/loadavg")
            if (loadFile.canRead()) {
                val text = loadFile.readText().trim()
                val toks = text.split("\\s+".toRegex())
                val load = toks.firstOrNull()?.toFloatOrNull() ?: return 0
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                ((load / cores) * 100f).toInt().coerceIn(0, 100)
            } else 0
        }.getOrDefault(0)
    }
}

private fun readRamUsagePercentage(context: Context): Int {
    return runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem <= 0) return 0
        val used = mi.totalMem - mi.availMem
        ((used.toDouble() / mi.totalMem.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }.getOrDefault(0)
}
