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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusIconTint = LauncherColors.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherTopHeaderBar(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val fuelPctFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val fuelPctRaw by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelPct = fuelPctFiltered ?: fuelPctRaw
    val rangeKm by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()

    val cpuUsage = rememberCpuUsagePercentage()
    val ramUsage = rememberRamUsagePercentage()

    val adasAlertsRevision by LauncherAppConfigStore.adasAlertsPlacementRevisionFlow
        .collectAsStateWithLifecycle()
    val adasAlertsInLeftPanel = remember(context, adasAlertsRevision) {
        LauncherAppConfigStore.adasAlertsInLeftPanel(context)
    }

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

    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)
    val headlightsRaw = vehicleControls.lightControlRaw ?: LIGHT_CONTROL_OFF

    val buttonDiameterDp = (topBarHeightDp - 16).coerceAtLeast(16)
    val indicatorFontSizeSp = (buttonDiameterDp / 2f).sp
    val indicatorIconSizeDp = (buttonDiameterDp * 0.65f).dp

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
    val voltageColor = when {
        voltageValue == null -> LauncherColors.TextSecondary
        voltageLow -> LauncherColors.WarningRed
        else -> LauncherColors.TextPrimary
    }

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
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!adasAlertsInLeftPanel) {
                LauncherAdasStrip(canViewModel = canViewModel)
                if (topBarLeftIndicatorsVisible && topBarItems.isNotEmpty()) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.tboxCaption,
                        color = StatusIconTint.copy(alpha = 0.5f),
                        fontSize = indicatorFontSizeSp,
                        modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                    )
                }
            }

            if (topBarLeftIndicatorsVisible) {
                topBarItems.forEachIndexed { index, itemId ->
                    if (index > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.tboxCaption,
                            color = StatusIconTint.copy(alpha = 0.5f),
                            fontSize = indicatorFontSizeSp,
                            modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                        )
                    }

                    LauncherDraggableTopBarItem(
                        itemId = itemId,
                        index = index,
                        totalCount = topBarItems.size,
                        onReorder = { fromIdx, toIdx ->
                            LauncherAppConfigStore.reorderTopBarItem(context, fromIdx, toIdx)
                        },
                        onRemove = {
                            LauncherAppConfigStore.removeTopBarItem(context, itemId)
                        },
                    ) {
                        when (itemId) {
                            "temp" -> {
                                Text(
                                    text = outsideTemp?.let { "${valueToString(it, 0)}°" } ?: "—°",
                                    style = MaterialTheme.typography.tboxCaption,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = indicatorFontSizeSp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            "fuel" -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            val next = !fuelShowsRange
                                            fuelShowsRange = next
                                            LauncherAppConfigStore.setFuelShowsRange(context, next)
                                        },
                                    ),
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_launcher_fuel),
                                        contentDescription = stringResource(R.string.launcher_vs_fuel),
                                        modifier = Modifier.size(indicatorIconSizeDp),
                                        colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                                    )
                                    Text(
                                        text = fuelText,
                                        style = MaterialTheme.typography.tboxCaption,
                                        color = LauncherColors.TextPrimary,
                                        fontSize = indicatorFontSizeSp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            "voltage" -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (voltageLow) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_launcher_battery),
                                            contentDescription = stringResource(R.string.data_title_voltage),
                                            modifier = Modifier.size(indicatorIconSizeDp),
                                            colorFilter = ColorFilter.tint(LauncherColors.WarningRed),
                                        )
                                    }
                                    Text(
                                        text = voltageText,
                                        style = MaterialTheme.typography.tboxCaption,
                                        color = voltageColor,
                                        fontSize = indicatorFontSizeSp,
                                        fontWeight = if (voltageLow) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                            "cpu" -> {
                                Text(
                                    text = "CPU $cpuUsage%",
                                    style = MaterialTheme.typography.tboxCaption,
                                    color = StatusIconTint,
                                    fontSize = indicatorFontSizeSp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            "ram" -> {
                                Text(
                                    text = "RAM $ramUsage%",
                                    style = MaterialTheme.typography.tboxCaption,
                                    color = StatusIconTint,
                                    fontSize = indicatorFontSizeSp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            "gps" -> {
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
                            "headlights" -> {
                                val isDark = LauncherThemeState.darkTheme
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
                                        iconSizeDp = (buttonDiameterDp * 0.55f).dp,
                                    )
                                }
                            }
                            "trunk" -> {
                                val isDark = LauncherThemeState.darkTheme
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherHeaderStatusIcons(
                tboxViewModel = tboxViewModel,
                showVehicleAlerts = !adasAlertsInLeftPanel,
            )
            Text(
                text = "$timeText  ·  $dateText",
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun LauncherDraggableTopBarItem(
    itemId: String,
    index: Int,
    totalCount: Int,
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
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragEnd = {
                        val shouldRemove = offsetY > removeThresholdPx
                        if (shouldRemove) {
                            onRemove()
                        } else if (abs(offsetX) > itemWidthPx * 0.4f && itemWidthPx > 0f) {
                            val targetIndex = if (offsetX > 0f) {
                                (index + 1).coerceAtMost(totalCount - 1)
                            } else {
                                (index - 1).coerceAtLeast(0)
                            }
                            if (targetIndex != index) {
                                onReorder(index, targetIndex)
                            }
                        }
                        isDragging = false
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        isDragging = false
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
    val active = raw != LIGHT_CONTROL_OFF
    val (iconRes, mode) = when (raw) {
        LIGHT_CONTROL_AUTO -> R.drawable.ic_headlights_auto to "A"
        LIGHT_CONTROL_LOW_BEAM -> R.drawable.ic_headlights_low_beam to "Б"
        LIGHT_CONTROL_POSITION -> R.drawable.ic_headlights_low_beam to "Г"
        else -> R.drawable.ic_headlights_low_beam to "0"
    }
    val hvacOnColor = Color(0xFF38BDF8)
    val hvacOffColor = LauncherColors.TextMuted
    Box(modifier = Modifier.size(iconSizeDp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.launcher_vs_headlights),
            modifier = Modifier.size(iconSizeDp),
            colorFilter = ColorFilter.tint(if (active) hvacOnColor else hvacOffColor),
        )
        Text(
            text = mode,
            color = if (active) LauncherColors.CanvasDark else LauncherColors.TextPrimary,
            fontSize = (iconSizeDp.value * 0.3f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(3.dp))
                .background(if (active) hvacOnColor else LauncherColors.CardDarkElevated),
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
            if (LauncherThemeState.darkTheme) Color.White else Color(0xFF4B5563)
        } else {
            LauncherColors.TextMuted
        }
        Image(
            painter = painterResource(R.drawable.ic_launcher_bluetooth),
            contentDescription = stringResource(R.string.launcher_vs_bluetooth),
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(bluetoothTint),
        )
        if (wifiConnected) {
            WifiArcsIcon(level = wifiLevel.coerceIn(1, 4), tint = StatusIconTint)
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
                        fontSize = 9.sp,
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
