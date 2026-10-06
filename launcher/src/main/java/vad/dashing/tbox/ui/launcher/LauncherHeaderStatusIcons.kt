package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

private val StatusIconTint = LauncherColors.TextSecondary

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
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.topHeaderBottomPx = rect.bottom.toInt()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = outsideTemp?.let { "${valueToString(it, 0)}°" } ?: "—°",
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextPrimary),
                )
                Text(
                    text = fuelText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (voltageLow) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_battery),
                        contentDescription = stringResource(R.string.data_title_voltage),
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(LauncherColors.WarningRed),
                    )
                }
                Text(
                    text = voltageText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = voltageColor,
                    fontSize = 15.sp,
                    fontWeight = if (voltageLow) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherHeaderStatusIcons(tboxViewModel = tboxViewModel)
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
internal fun LauncherHeaderStatusIcons(
    tboxViewModel: TboxViewModel,
    modifier: Modifier = Modifier,
) {
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val netState by tboxViewModel.netState.collectAsStateWithLifecycle()
    val locValues by tboxViewModel.locValues.collectAsStateWithLifecycle()
    val wifiConnected = rememberWifiConnected()
    val wifiLevel = rememberWifiLevel(wifiConnected)
    val bluetoothConnected = rememberBluetoothConnected()
    val cpuUsage = rememberCpuUsagePercentage()
    val ramUsage = rememberRamUsagePercentage()
    val networkType = mobileNetworkTypeLabel(netState.netStatus)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    if (tboxConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                ),
        )
        if (wifiConnected) {
            WifiArcsIcon(level = wifiLevel.coerceIn(1, 4), tint = StatusIconTint)
        }
        if (bluetoothConnected) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_bluetooth),
                contentDescription = stringResource(R.string.launcher_vs_bluetooth),
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(StatusIconTint),
            )
        }
        Text(
            text = "CPU $cpuUsage%",
            color = StatusIconTint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "RAM $ramUsage%",
            color = StatusIconTint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
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
            if (locValues.updateTime != null && !locValues.hasGpsFix) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_gps_off),
                    contentDescription = stringResource(R.string.launcher_gps_no_fix),
                    modifier = Modifier.size(17.dp),
                    colorFilter = ColorFilter.tint(Color(0xFFEF4444)),
                )
            }
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
                network: android.net.Network,
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
