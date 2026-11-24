package vad.dashing.tbox.ui.launcher

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import vad.dashing.tbox.MediaControlNotificationListenerService
import java.util.Locale

/**
 * Live navigation & traffic light state:
 * - Traffic light & Speed limits are captured from Broadcast intents.
 * - Turn maneuver icon, turn distance, street name, remaining distance & remaining time are captured ONLY from notifications.
 */
data class YandexNavState(
    val isNavigating: Boolean = false,
    val maneuverBitmap: Bitmap? = null,
    val maneuverDistance: String? = null,
    val speedLimit: String? = null,
    val nextStreet: String? = null,
    val nextText: String? = null,
    val fullNotificationText: String? = null,
    val remainingDistance: String? = null,
    val remainingTime: String? = null,
    val arrivalTime: String? = null,
    val lanesBitmap: Bitmap? = null,
    val lanesDistance: String? = null,
    val trafficLightColor: String? = null,
    val trafficLightCountdownSeconds: Int? = null,
    val trafficLightDistanceMeters: Int? = null,
    val trafficLightArrow: String? = null,
    val trafficLightLastUpdateMs: Long = 0L,
    val trafficLightCountdownLastUpdateMs: Long = 0L,
    val lastUpdateMs: Long = 0L,
    val maneuverTopDescription: String? = null,
    val maneuverPrimaryIcon: Bitmap? = null,
    val maneuverSecondaryIcon: Bitmap? = null,
    val etaProgressBitmap: Bitmap? = null,
    val lanesBitmaps: List<Bitmap> = emptyList(),
    val notificationTrafficLightText: String? = null,
    val notificationTrafficLightBitmap: Bitmap? = null,
    val minimapBitmap: Bitmap? = null,
    val minimapHasRoute: Boolean = false,
    val minimapLastUpdateMs: Long = 0L,
)

data class MinimapFrame(
    val bitmap: Bitmap? = null,
    val hasRoute: Boolean = false,
    val lastUpdateMs: Long = 0L,
)

object YandexNavRepository {
    private const val TAG = "YandexNavRepository"
    private const val TRAFFIC_LIGHT_TIMER_TIMEOUT_MS = 3_000L
    private const val TRAFFIC_LIGHT_STALE_TIMEOUT_MS = 5_000L
    private const val MINIMAP_STALE_TIMEOUT_MS = 3_500L
    private const val LANES_STALE_TIMEOUT_MS = 4_000L

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerStaleJob: Job? = null
    private var trafficLightStaleJob: Job? = null
    private var minimapStaleJob: Job? = null
    private var lanesStaleJob: Job? = null

    private fun cancelTimerStaleJob() {
        timerStaleJob?.cancel()
        timerStaleJob = null
    }

    private fun cancelTrafficLightStaleJob() {
        trafficLightStaleJob?.cancel()
        trafficLightStaleJob = null
    }

    private fun scheduleTrafficLightStaleJob() {
        trafficLightStaleJob?.cancel()
        trafficLightStaleJob = repositoryScope.launch {
            delay(TRAFFIC_LIGHT_STALE_TIMEOUT_MS)
            _state.update {
                it.copy(
                    trafficLightColor = null,
                    trafficLightCountdownSeconds = null,
                    trafficLightDistanceMeters = null,
                    trafficLightArrow = null,
                    trafficLightLastUpdateMs = 0L,
                    trafficLightCountdownLastUpdateMs = 0L,
                )
            }
        }
    }

    private fun scheduleTimerStaleJob(timestampMs: Long) {
        timerStaleJob?.cancel()
        timerStaleJob = repositoryScope.launch {
            delay(TRAFFIC_LIGHT_TIMER_TIMEOUT_MS)
            _state.update { current ->
                if (current.trafficLightCountdownLastUpdateMs == timestampMs) {
                    current.copy(
                        trafficLightCountdownSeconds = null,
                        trafficLightCountdownLastUpdateMs = 0L,
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun cancelMinimapStaleJob() {
        minimapStaleJob?.cancel()
        minimapStaleJob = null
    }

    private fun scheduleMinimapStaleJob() {
        minimapStaleJob?.cancel()
        minimapStaleJob = repositoryScope.launch {
            delay(MINIMAP_STALE_TIMEOUT_MS)
            _minimapFrame.value = MinimapFrame()
        }
    }

    private fun cancelLanesStaleJob() {
        lanesStaleJob?.cancel()
        lanesStaleJob = null
    }

    private fun scheduleLanesStaleJob() {
        lanesStaleJob?.cancel()
        lanesStaleJob = repositoryScope.launch {
            delay(LANES_STALE_TIMEOUT_MS)
            _state.update {
                it.copy(
                    lanesBitmap = null,
                    lanesBitmaps = emptyList(),
                    lanesDistance = null,
                )
            }
        }
    }

    private val _state = MutableStateFlow(YandexNavState())
    val state: StateFlow<YandexNavState> = _state.asStateFlow()

    private val _minimapFrame = MutableStateFlow(MinimapFrame())
    val minimapFrame: StateFlow<MinimapFrame> = _minimapFrame.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.d(TAG, "Received nav broadcast: $action")
            val extras = intent.extras
            if (extras != null) {
                for (k in extras.keySet()) {
                    @Suppress("DEPRECATION")
                    Log.d(TAG, "   [$action] Extra: $k = ${extras.get(k)}")
                }
            }
            val now = System.currentTimeMillis()
            val normAction = action.substringAfterLast('.').uppercase()

            if (extras != null) {
                var bitmap: Bitmap? = null
                var mDistance: String? = null
                var streetText: String? = null
                var remDist: String? = null
                var remTime: String? = null
                var arrTime: String? = null

                for (k in extras.keySet()) {
                    @Suppress("DEPRECATION")
                    val value = extras.get(k) ?: continue
                    val valStr = value.toString().trim()
                    Log.d(TAG, "   [$action] Extra: $k = $valStr (${value.javaClass.simpleName})")

                    if (value is Bitmap) {
                        if (k.contains("maneuver") || k.contains("icon") || bitmap == null) {
                            bitmap = value
                        }
                        continue
                    }

                    val kLower = k.lowercase()
                    if (kLower == "maneuver_distance" || kLower == "maneuver_dist" || kLower == "dist_to_turn" || kLower == "dist_m" || kLower == "distance_text") {
                        mDistance = valStr
                    } else if (kLower == "street_name" || kLower == "next_street" || kLower == "street" || kLower == "road_name" || kLower == "target_street") {
                        if (!isIgnoredStreetName(valStr)) streetText = valStr
                    } else if (kLower == "remaining_distance" || kLower == "rem_dist" || kLower == "route_distance" || kLower == "total_distance") {
                        remDist = valStr
                    } else if (kLower == "remaining_time" || kLower == "rem_time" || kLower == "route_time" || kLower == "time_to_target") {
                        remTime = valStr
                    } else if (kLower == "arrival_time" || kLower == "eta" || kLower == "arrival") {
                        arrTime = valStr
                    } else if (kLower.contains("street") || kLower.contains("road")) {
                        if (!isIgnoredStreetName(valStr)) streetText = valStr
                    } else if (kLower.contains("dist")) {
                        if (mDistance == null) mDistance = valStr else if (remDist == null && valStr != mDistance) remDist = valStr
                    } else if (kLower.contains("time")) {
                        if (remTime == null) remTime = valStr
                    }
                }

                if (bitmap != null || mDistance != null || streetText != null || remDist != null || remTime != null || arrTime != null) {
                    _state.update {
                        it.copy(
                            isNavigating = true,
                            maneuverBitmap = bitmap ?: it.maneuverBitmap,
                            maneuverDistance = mDistance?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.maneuverDistance,
                            nextStreet = streetText?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.nextStreet,
                            remainingDistance = remDist?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.remainingDistance,
                            remainingTime = remTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.remainingTime,
                            arrivalTime = arrTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.arrivalTime,
                            lastUpdateMs = now,
                        )
                    }
                }
            }

            when (normAction) {
                "SPEEDLIMIT", "SPEED_LIMIT", "SPEED_LIMIT_UPDATE", "SPEEDLIMIT_UPDATE" -> {
                    val rawValue = extractRawExtraString(intent, "speedlimit_text", "speed_limit", "speedlimit", "speed_limit_text", "limit", "speed", "value")
                    if (rawValue == "0" || rawValue == "-1" || rawValue == "null") {
                        _state.update {
                            it.copy(
                                speedLimit = null,
                                lastUpdateMs = now,
                            )
                        }
                    } else {
                        val limit = extractSpeedLimit(intent, "speedlimit_text", "speed_limit", "speedlimit", "speed_limit_text", "limit", "speed", "value")
                        if (limit != null) {
                            _state.update {
                                it.copy(
                                    speedLimit = limit,
                                    lastUpdateMs = now,
                                )
                            }
                        }
                    }
                }
                "TRAFFIC_LIGHT_UPDATE" -> {
                    val pos = extractIntExtra(intent, "tl_position", "position", "pos")
                    if (pos != null && pos != 0) {
                        return
                    }

                    val colorRaw = intent.getStringExtra("tl_color")?.trim()
                    val secs = extractIntExtra(intent, "tl_countdown", "tl_sec", "tl_secs", "tl_time", "countdown", "sec", "secs", "time", "timer")
                    val dist = extractIntExtra(intent, "tl_dist_m", "tl_dist", "tl_distance", "dist_m", "dist")
                    val arrow = intent.getStringExtra("tl_arrow")?.trim()

                    val colorClean = colorRaw?.takeIf {
                        it.isNotBlank() && it != "null" && it != "off" && it != "none" && it != "0" && it != "-1"
                    }

                    val isCleared = (colorRaw != null && colorClean == null && secs == null) ||
                            (colorClean == null && secs == null && arrow == null && dist == null)

                    if (isCleared) {
                        cancelTimerStaleJob()
                        cancelTrafficLightStaleJob()
                        _state.update {
                            it.copy(
                                trafficLightColor = null,
                                trafficLightCountdownSeconds = null,
                                trafficLightDistanceMeters = null,
                                trafficLightArrow = null,
                                trafficLightLastUpdateMs = 0L,
                                trafficLightCountdownLastUpdateMs = 0L,
                                lastUpdateMs = now,
                            )
                        }
                    } else if (colorClean != null || secs != null || dist != null || arrow != null) {
                        scheduleTrafficLightStaleJob()
                        val validSecs = secs?.takeIf { it >= 0 }
                        val timerExplicitlyProvided = secs != null
                        if (validSecs != null) {
                            scheduleTimerStaleJob(now)
                        } else if (timerExplicitlyProvided) {
                            cancelTimerStaleJob()
                        }
                        _state.update {
                            val newCountdownSeconds = when {
                                validSecs != null -> validSecs
                                timerExplicitlyProvided -> null
                                it.trafficLightCountdownLastUpdateMs > 0L && (now - it.trafficLightCountdownLastUpdateMs) > TRAFFIC_LIGHT_TIMER_TIMEOUT_MS -> null
                                else -> it.trafficLightCountdownSeconds
                            }
                            val newCountdownLastUpdateMs = when {
                                validSecs != null -> now
                                timerExplicitlyProvided -> 0L
                                it.trafficLightCountdownLastUpdateMs > 0L && (now - it.trafficLightCountdownLastUpdateMs) > TRAFFIC_LIGHT_TIMER_TIMEOUT_MS -> 0L
                                else -> it.trafficLightCountdownLastUpdateMs
                            }
                            it.copy(
                                trafficLightColor = colorClean ?: it.trafficLightColor,
                                trafficLightCountdownSeconds = newCountdownSeconds,
                                trafficLightDistanceMeters = dist?.takeIf { d -> d > 0 } ?: it.trafficLightDistanceMeters,
                                trafficLightArrow = arrow?.takeIf { a -> a.isNotBlank() && a != "null" } ?: it.trafficLightArrow,
                                trafficLightLastUpdateMs = now,
                                trafficLightCountdownLastUpdateMs = newCountdownLastUpdateMs,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "LANES_BITMAP" -> {
                    @Suppress("DEPRECATION")
                    val bitmap = intent.getParcelableExtra<Bitmap>("lanes_bitmap")
                    if (bitmap != null) {
                        scheduleLanesStaleJob()
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                lanesBitmap = bitmap,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "LANES_BITMAP_CLEAR" -> {
                    cancelLanesStaleJob()
                    _state.update {
                        it.copy(
                            lanesBitmap = null,
                            lanesBitmaps = emptyList(),
                            lanesDistance = null,
                            lastUpdateMs = now,
                        )
                    }
                }
                "LANE_DIST" -> {
                    val dist = extractRawExtraString(intent, "dist")
                    val metrics = extractRawExtraString(intent, "metrics") ?: ""
                    val distText = if (dist != null) {
                        "$dist $metrics".replace("\\s+".toRegex(), " ").trim()
                    } else null
                    if (distText != null) {
                        scheduleLanesStaleJob()
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                lanesDistance = distText,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "LANES", "LANE_SIGN" -> {
                    val dist = extractRawExtraString(intent, "dist")
                    val metrics = extractRawExtraString(intent, "metrics") ?: ""
                    val distText = if (dist != null) {
                        "$dist $metrics".replace("\\s+".toRegex(), " ").trim()
                    } else null
                    scheduleLanesStaleJob()
                    _state.update {
                        it.copy(
                            isNavigating = true,
                            lanesDistance = distText ?: it.lanesDistance,
                            lastUpdateMs = now,
                        )
                    }
                }
                "MINIMAP" -> {
                    val jpeg = intent.getByteArrayExtra("minimap_jpeg")
                    val hasRoute = intent.getBooleanExtra("minimap_has_route", false)
                    val bmp = if (jpeg != null && jpeg.isNotEmpty()) {
                        runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }.getOrNull()
                    } else null
                    if (bmp != null) {
                        _minimapFrame.value = MinimapFrame(
                            bitmap = bmp,
                            hasRoute = hasRoute,
                            lastUpdateMs = now,
                        )
                        scheduleMinimapStaleJob()
                    }
                }
                "NIXT" -> {
                    val nextText = extractRawExtraString(intent, "next_text")
                    if (!nextText.isNullOrBlank()) {
                        val cleanDist = nextText.replace("\\s+".toRegex(), " ").trim()
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                nextText = cleanDist,
                                maneuverDistance = cleanDist,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "NEXTSTREET" -> {
                    val street = extractRawExtraString(intent, "next_street")
                    val validStreet = street?.takeIf { !isIgnoredStreetName(it) }
                    if (validStreet != null) {
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                nextStreet = validStreet,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "DISTANCE" -> {
                    val remDist = extractRawExtraString(intent, "Distance_text", "distance_text", "dist")
                    if (remDist != null) {
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                remainingDistance = remDist,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "TIME" -> {
                    val remTime = extractRawExtraString(intent, "Time_text", "time_text", "time")
                    if (remTime != null) {
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                remainingTime = remTime,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "ARRIVAL" -> {
                    val arrTime = extractRawExtraString(intent, "Arrival_text", "arrival_text", "arrival")
                    if (arrTime != null) {
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                arrivalTime = arrTime,
                                lastUpdateMs = now,
                            )
                        }
                    }
                }
                "MANEUVER", "MANEUVER_UPDATE", "HUD_UPDATE", "NAVIGATION_UPDATE", "ROUTE_UPDATE", "STREET_UPDATE", "DISTANCE_UPDATE" -> {
                    val mDistance = extractRawExtraString(intent, "maneuver_distance", "maneuver_dist", "dist_to_turn", "distance_text", "dist_text", "maneuver_text", "dist_m", "dist", "distance")
                    val streetText = extractRawExtraString(intent, "street_name", "next_street", "street", "road_name", "target_street", "maneuver_street", "street_text")
                    val remDist = extractRawExtraString(intent, "remaining_distance", "rem_dist", "route_distance", "total_distance", "dist_to_dest")
                    val remTime = extractRawExtraString(intent, "remaining_time", "rem_time", "route_time", "time_to_target", "time_left")
                    val arrTime = extractRawExtraString(intent, "arrival_time", "eta", "arrival", "eta_text")

                    val validStreet = streetText?.takeIf { !isIgnoredStreetName(it) }

                    _state.update {
                        it.copy(
                            isNavigating = true,
                            maneuverDistance = mDistance?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.maneuverDistance,
                            nextStreet = validStreet?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.nextStreet,
                            remainingDistance = remDist?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.remainingDistance,
                            remainingTime = remTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.remainingTime,
                            arrivalTime = arrTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.arrivalTime,
                            lastUpdateMs = now,
                        )
                    }
                }
                "NAVIGATION_ENDED", "NAVIGATION_STOPPED" -> {
                    cancelTimerStaleJob()
                    cancelMinimapStaleJob()
                    cancelLanesStaleJob()
                    _minimapFrame.value = MinimapFrame()
                    _state.update {
                        YandexNavState(isNavigating = false)
                    }
                }
            }
        }
    }

    fun enableMinimap(context: Context, widthPx: Int = 360, heightPx: Int = 360) {
        val w = (widthPx / 8 * 8).coerceIn(96, 1920)
        val h = (heightPx / 8 * 8).coerceIn(96, 1920)
        val minimapIntent = Intent("com.yandex.MINIMAP_ENABLE").apply {
            putExtra("minimap_width", w)
            putExtra("minimap_height", h)
            putExtra("minimap_zoom", 16f)
            putExtra("minimap_roads", false)
            putExtra("minimap_labels", false)
            putExtra("minimap_view", 0)
            putExtra("minimap_hide_on_route_end", false)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(minimapIntent) }
    }

    fun disableMinimap(context: Context) {
        cancelMinimapStaleJob()
        _minimapFrame.value = MinimapFrame()
        val minimapIntent = Intent("com.yandex.MINIMAP_DISABLE").apply {
            putExtra("minimap_hide_on_route_end", false)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(minimapIntent) }
    }

    fun start(context: Context) {
        ensureNotificationListenerEnabled(context)
        if (registered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            listOf(
                "plus.monjaro.",
                "com.yandex.",
                "com.yandex.navi.",
                "ru.yandex.yandexnavi.",
                "ru.yandex.yandexmaps.",
                "com.yandex.yandexnavi.",
                "yandex.windshield.",
                "ru.yandex.autonavi."
            ).forEach { prefix ->
                addAction("${prefix}SPEEDLIMIT")
                addAction("${prefix}SPEED_LIMIT")
                addAction("${prefix}SPEED_LIMIT_UPDATE")
                addAction("${prefix}SPEEDLIMIT_UPDATE")
                addAction("${prefix}action.SPEED_LIMIT")
                addAction("${prefix}MANEUVER")
                addAction("${prefix}MANEUVER_UPDATE")
                addAction("${prefix}HUD_UPDATE")
                addAction("${prefix}NAVIGATION_UPDATE")
                addAction("${prefix}NAVIGATION_STARTED")
                addAction("${prefix}ROUTE_UPDATE")
                addAction("${prefix}STREET_UPDATE")
                addAction("${prefix}DISTANCE_UPDATE")
                addAction("${prefix}NAVIGATION_ENDED")
                addAction("${prefix}NAVIGATION_STOPPED")
                addAction("${prefix}TRAFFIC_LIGHT_UPDATE")
                addAction("${prefix}LANES")
                addAction("${prefix}LANES_BITMAP")
                addAction("${prefix}LANES_BITMAP_CLEAR")
                addAction("${prefix}LANE_DIST")
                addAction("${prefix}LANE_SIGN")
                addAction("${prefix}MINIMAP")
                addAction("${prefix}NIXT")
                addAction("${prefix}NEXTSTREET")
                addAction("${prefix}DISTANCE")
                addAction("${prefix}TIME")
                addAction("${prefix}ARRIVAL")
            }
            addAction(ACTION_TRAFFIC_LIGHT_UPDATE)
            addAction(ACTION_NAVIGATION_ENDED)
            addAction("com.yandex.LANES")
            addAction("com.yandex.LANES_BITMAP")
            addAction("com.yandex.LANES_BITMAP_CLEAR")
            addAction("com.yandex.LANE_DIST")
            addAction("com.yandex.LANE_SIGN")
            addAction("com.yandex.MINIMAP")
            addAction("com.yandex.NIXT")
            addAction("com.yandex.NEXTSTREET")
            addAction("com.yandex.DISTANCE")
            addAction("com.yandex.TIME")
            addAction("com.yandex.ARRIVAL")
        }

        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
            Log.i(TAG, "YandexNavRepository broadcast receiver registered successfully")
        }.onFailure {
            Log.e(TAG, "Failed to register YandexNavRepository broadcast receiver", it)
        }
        YandexNavWindowTracker.start(appContext)
    }

    fun stop(context: Context) {
        if (!registered) return
        cancelTimerStaleJob()
        YandexNavWindowTracker.stop()
        runCatching {
            context.applicationContext.unregisterReceiver(receiver)
            registered = false
        }
    }

    @VisibleForTesting
    internal fun handleIntentForTest(context: Context, intent: Intent) {
        receiver.onReceive(context, intent)
    }

    @VisibleForTesting
    internal fun resetForTest() {
        cancelTimerStaleJob()
        _state.value = YandexNavState()
    }

    private fun extractIntExtra(intent: Intent, vararg keys: String): Int? {
        val extras = intent.extras ?: return null
        for (k in keys) {
            if (!extras.containsKey(k)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(k) ?: continue
            when (value) {
                is Number -> return value.toInt()
                is String -> {
                    val strDigits = value.filter { it.isDigit() || it == '-' }
                    val parsed = strDigits.toIntOrNull()
                    if (parsed != null) return parsed
                }
            }
        }
        return null
    }

    private fun extractRawExtraString(intent: Intent, vararg keys: String): String? {
        val extras = intent.extras ?: return null
        for (k in keys) {
            if (!extras.containsKey(k)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(k) ?: continue
            val str = value.toString().trim()
            if (str.isNotEmpty()) return str
        }
        return null
    }

    private fun extractSpeedLimit(intent: Intent, vararg keys: String): String? {
        val str = extractRawExtraString(intent, *keys) ?: return null
        if (str == "null" || str == "0" || str == "-1") return null
        val digits = str.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            val num = digits.toIntOrNull()
            if (num != null && num > 0 && num < 300) {
                return num.toString()
            }
        }
        return null
    }

    internal fun isIgnoredStreetName(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        val lower = raw.trim().lowercase()
        if (lower in IGNORED_STREET_NAMES) return true
        if (lower.contains("запущен") ||
            lower.contains("is running") ||
            lower.contains("работает в фоновом") ||
            lower.contains("в фоновом режиме") ||
            lower.contains("поиск спутников") ||
            lower.contains("построение маршрута") ||
            lower.startsWith("яндекс.навигатор") ||
            lower.startsWith("яндекс навигатор") ||
            lower.startsWith("yandex navigator") ||
            lower.startsWith("yandex.navigator") ||
            lower.startsWith("navigator is running") ||
            lower.startsWith("навигатор запущен") ||
            lower == "яндекс.навигатор" ||
            lower == "яндекс навигатор" ||
            lower == "yandex navigator" ||
            lower == "yandex.navigator" ||
            lower == "навигатор" ||
            lower == "null"
        ) return true
        return false
    }

    internal val IGNORED_STREET_NAMES = setOf(
        "navigator is running",
        "yandex navigator",
        "yandex.navigator",
        "яндекс навигатор",
        "яндекс.навигатор",
        "яндекс карты",
        "яндекс.карты",
        "навигатор",
        "навигация",
        "построение маршрута",
        "поиск спутников",
        "яндекс навигатор работает в фоновом режиме",
        "яндекс.навигатор запущен",
        "яндекс навигатор запущен",
        "навигатор запущен",
        "yandex maps",
        "yandex.maps",
        "null"
    )

    private val DISTANCE_REGEX = Regex("(?i)(\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.))")
    private val TIME_REGEX = Regex("(?i)\\b((?:\\d+\\s*(?:ч|ч.|h)\\s*)?\\d+\\s*(?:мин|мин.|min))\\b|\\b(\\d+\\s*(?:ч|ч.|h))\\b")
    private val ETA_REGEX = Regex("\\b([01]?\\d|2[0-3])[:.][0-5]\\d\\b")
    private val MANEUVER_PREFIX_REGEX = Regex("(?i)^(через\\s+\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.)\\s*)?(поверните|держитесь|съезд|развернитесь|перестройтесь|прямо|двигайтесь)?(\\s+(направо|налево|правее|левее))?\\s*(на|в|по)?\\s*")
    private val CLEAN_MANEUVER_REGEX = Regex("(?i)^(через\\s+\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.)\\s*|\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.)\\s*|поверните|держитесь|съезд|развернитесь|перестройтесь|прямо|двигайтесь|направо|налево|правее|левее|через|на|в|по)*\\s*")

    private fun extractStreetName(part: String): String? {
        if (isIgnoredStreetName(part)) return null

        var cleaned = part
            .replace(DISTANCE_REGEX, "")
            .replace(TIME_REGEX, "")
            .replace(ETA_REGEX, "")
            .trim()

        cleaned = cleaned.replace(CLEAN_MANEUVER_REGEX, "").trim()
        cleaned = cleaned.replace(Regex("(?i)^(поверните|держитесь|съезд|развернитесь|перестройтесь|прямо|двигайтесь|направо|налево|правее|левее|через|на|в|по)\\s+"), "").trim()

        if (cleaned.length >= 2 && !isIgnoredStreetName(cleaned) && !DISTANCE_REGEX.containsMatchIn(cleaned)) {
            return cleaned
        }
        return null
    }

    private fun isYandexPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "ru.yandex.yandexnavi" ||
                lower == "ru.yandex.yandexmaps" ||
                lower == "ru.yandex.mobile.navigator" ||
                lower == "com.yandex.navi" ||
                lower == "ru.yandex.navi" ||
                lower.startsWith("ru.yandex.yandexnavi") ||
                lower.startsWith("ru.yandex.yandexmaps") ||
                lower.startsWith("ru.yandex.mobile.navigator") ||
                lower.startsWith("com.yandex.navi") ||
                lower.contains("yandex.navi") ||
                lower.contains("yandexnavi") ||
                lower.contains("yandexmaps")
    }

    private fun extractAllNotificationStrings(notification: Notification, extras: Bundle): List<String> {
        val strings = mutableListOf<String>()

        val ticker = notification.tickerText?.toString()?.trim()
        if (!ticker.isNullOrBlank()) {
            strings.add(ticker)
        }

        for (key in extras.keySet()) {
            @Suppress("DEPRECATION")
            val value = extras.get(key) ?: continue
            when (value) {
                is CharSequence -> {
                    val str = value.toString().trim()
                    if (str.isNotBlank() && str !in strings) {
                        strings.add(str)
                    }
                }
                is Array<*> -> {
                    for (item in value) {
                        if (item is CharSequence) {
                            val str = item.toString().trim()
                            if (str.isNotBlank() && str !in strings) {
                                strings.add(str)
                            }
                        }
                    }
                }
                is Iterable<*> -> {
                    for (item in value) {
                        if (item is CharSequence) {
                            val str = item.toString().trim()
                            if (str.isNotBlank() && str !in strings) {
                                strings.add(str)
                            }
                        }
                    }
                }
            }
        }

        return strings
    }

    fun ensureNotificationListenerEnabled(context: Context) {
        val component = ComponentName(context, MediaControlNotificationListenerService::class.java)
        val flatComponent = component.flattenToString()
        runCatching {
            val cr = context.contentResolver
            val existing = Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
            if (!existing.contains(flatComponent)) {
                val updated = if (existing.isBlank()) flatComponent else "$existing:$flatComponent"
                Settings.Secure.putString(cr, "enabled_notification_listeners", updated)
                Log.i(TAG, "Granted NotificationListener permission to $flatComponent via Settings.Secure")
            }
        }
        runCatching {
            Runtime.getRuntime().exec(arrayOf("cmd", "notification", "allow_listener", flatComponent))
        }
        runCatching {
            val cr = context.contentResolver
            val existing = Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
            val updated = if (existing.isBlank()) flatComponent else "$existing:$flatComponent"
            Runtime.getRuntime().exec(arrayOf("settings", "put", "secure", "enabled_notification_listeners", updated))
        }
        runCatching {
            NotificationListenerService.requestRebind(component)
            Log.i(TAG, "Requested rebind for NotificationListenerService: $component")
        }
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!isYandexPackage(pkg)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val allStrings = extractAllNotificationStrings(notification, extras)
        Log.d(TAG, "onNotificationPosted from Yandex ($pkg). Extracted strings: $allStrings")
        if (allStrings.isEmpty()) return

        val iconBitmap: Bitmap? = runCatching {
            @Suppress("DEPRECATION")
            val largeIconExtra = extras.get(Notification.EXTRA_LARGE_ICON)
                ?: extras.get("android.largeIcon")
                ?: extras.get(Notification.EXTRA_LARGE_ICON_BIG)
            when (largeIconExtra) {
                is Bitmap -> largeIconExtra
                is Icon -> drawableToBitmap(largeIconExtra.loadDrawable(context))
                else -> {
                    @Suppress("DEPRECATION")
                    notification.largeIcon ?: notification.smallIcon?.let { drawableToBitmap(it.loadDrawable(context)) }
                }
            }
        }.getOrNull()

        val rvData = extractFromRemoteViews(context, notification)

        var mDistance: String? = rvData?.distance
        var streetText: String? = rvData?.street
        var remDist: String? = rvData?.remainingDistance
        var remTime: String? = rvData?.remainingTime
        var arrTime: String? = rvData?.arrivalTime

        val parts = allStrings.flatMap { str ->
            str.split("•", "\n", "|", "·").map { it.trim() }
        }.filter { it.isNotBlank() }

        for (part in parts) {
            if (isIgnoredStreetName(part)) continue

            val distMatch = DISTANCE_REGEX.find(part)
            if (distMatch != null) {
                if (mDistance == null) {
                    mDistance = distMatch.value
                } else if (remDist == null && distMatch.value != mDistance) {
                    remDist = distMatch.value
                }
            }

            if (remTime == null) {
                val timeMatch = TIME_REGEX.find(part)
                if (timeMatch != null) remTime = timeMatch.value
            }
            if (arrTime == null) {
                val etaMatch = ETA_REGEX.find(part)
                if (etaMatch != null) arrTime = etaMatch.value
            }

            if (streetText == null) {
                val extracted = extractStreetName(part)
                if (extracted != null) {
                    streetText = extracted
                }
            }
        }

        if (streetText == null) {
            streetText = parts.firstOrNull { p ->
                !isIgnoredStreetName(p) && !DISTANCE_REGEX.containsMatchIn(p) && !TIME_REGEX.containsMatchIn(p)
            }
        }

        val now = System.currentTimeMillis()
        val validStreet = streetText.takeIf { !isIgnoredStreetName(it) }

        val candidates = allStrings.map { it.trim() }.filter {
            it.isNotBlank() && !isIgnoredStreetName(it)
        }
        val fullTextCandidate = when {
            candidates.size >= 2 -> candidates.joinToString(" ")
            candidates.size == 1 -> candidates.first()
            else -> null
        }

        _state.update {
            it.copy(
                isNavigating = true,
                maneuverBitmap = rvData?.primaryIcon ?: iconBitmap ?: it.maneuverBitmap,
                maneuverPrimaryIcon = rvData?.primaryIcon ?: it.maneuverPrimaryIcon,
                maneuverSecondaryIcon = rvData?.secondaryIcon ?: it.maneuverSecondaryIcon,
                maneuverTopDescription = rvData?.topDescription ?: it.maneuverTopDescription,
                maneuverDistance = mDistance.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.maneuverDistance,
                nextStreet = validStreet.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.nextStreet,
                fullNotificationText = fullTextCandidate.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.fullNotificationText,
                remainingDistance = remDist.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.remainingDistance,
                remainingTime = remTime.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.remainingTime,
                arrivalTime = arrTime.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.arrivalTime,
                etaProgressBitmap = rvData?.etaProgressBitmap ?: it.etaProgressBitmap,
                lanesBitmap = rvData?.lanesBitmap ?: it.lanesBitmap,
                lanesBitmaps = if (rvData != null && rvData.lanesBitmaps.isNotEmpty()) rvData.lanesBitmaps else it.lanesBitmaps,
                notificationTrafficLightText = rvData?.trafficLightText ?: it.notificationTrafficLightText,
                notificationTrafficLightBitmap = rvData?.trafficLightBitmap ?: it.notificationTrafficLightBitmap,
                lastUpdateMs = now,
            )
        }
    }

    private data class RemoteViewsData(
        val distance: String? = null,
        val street: String? = null,
        val topDescription: String? = null,
        val primaryIcon: Bitmap? = null,
        val secondaryIcon: Bitmap? = null,
        val remainingDistance: String? = null,
        val remainingTime: String? = null,
        val arrivalTime: String? = null,
        val etaProgressBitmap: Bitmap? = null,
        val lanesBitmap: Bitmap? = null,
        val lanesBitmaps: List<Bitmap> = emptyList(),
        val trafficLightText: String? = null,
        val trafficLightBitmap: Bitmap? = null,
    )

    @Suppress("DEPRECATION")
    private fun extractFromRemoteViews(context: Context, notification: Notification): RemoteViewsData? {
        val remoteViews = notification.bigContentView ?: notification.contentView ?: return null
        return runCatching {
            val targetPackage = remoteViews.`package` ?: "ru.yandex.yandexnavi"
            val packageContext = runCatching {
                context.createPackageContext(targetPackage, Context.CONTEXT_RESTRICTED)
            }.getOrElse { context }

            val inflated = remoteViews.apply(packageContext, null)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST)
            inflated.measure(widthSpec, heightSpec)
            inflated.layout(0, 0, inflated.measuredWidth, inflated.measuredHeight)

            val viewsByName = mutableMapOf<String, View>()
            fun collect(v: View) {
                if (v.id != View.NO_ID) {
                    runCatching {
                        val name = v.resources.getResourceEntryName(v.id)
                        viewsByName[name] = v
                    }
                }
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        collect(v.getChildAt(i))
                    }
                }
            }
            collect(inflated)

            fun getText(name: String): String? {
                val tv = viewsByName[name] as? TextView ?: return null
                if (tv.visibility != View.VISIBLE) return null
                return tv.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }

            fun getBitmap(name: String): Bitmap? {
                val v = viewsByName[name] ?: return null
                if (v.visibility != View.VISIBLE) return null
                if (v is ImageView) {
                    return drawableToBitmap(v.drawable)
                }
                return viewToBitmap(v)
            }

            val distance = getText("titleView")
            val street = getText("descriptionView")
            val topDesc = getText("topDescriptionView")
            val primaryIcon = getBitmap("primaryIcon") ?: getBitmap("primaryIconTinted")
            val secondaryIcon = getBitmap("secondaryIcon") ?: getBitmap("secondaryIconTinted")
            val remainingDist = getText("remainingDistanceView")
            val remainingTime = getText("remainingTimeView")
            val arrivalTime = getText("timeOfArrivalView")
            val etaProgress = getBitmap("etaProgressImage")

            val lanesViewGroup = viewsByName["lanes"] as? ViewGroup
            val lanesBitmap = if (lanesViewGroup != null && lanesViewGroup.visibility == View.VISIBLE) {
                viewToBitmap(lanesViewGroup)
            } else null

            val lanesList = mutableListOf<Bitmap>()
            for (i in 1..8) {
                val laneBmp = getBitmap("lane$i")
                if (laneBmp != null) {
                    lanesList.add(laneBmp)
                }
            }

            val tlText = getText("traffic_light_data") ?: getText("traffic_light_data_expanded")
            val tlBitmap = getBitmap("traffic_light_data_arrow") ?: getBitmap("traffic_light_expanded")

            RemoteViewsData(
                distance = distance,
                street = street,
                topDescription = topDesc,
                primaryIcon = primaryIcon,
                secondaryIcon = secondaryIcon,
                remainingDistance = remainingDist,
                remainingTime = remainingTime,
                arrivalTime = arrivalTime,
                etaProgressBitmap = etaProgress,
                lanesBitmap = lanesBitmap,
                lanesBitmaps = lanesList,
                trafficLightText = tlText,
                trafficLightBitmap = tlBitmap,
            )
        }.onFailure {
            Log.d(TAG, "extractFromRemoteViews: error inflating RemoteViews: ${it.message}")
        }.getOrNull()
    }

    private fun viewToBitmap(view: View): Bitmap? {
        val w = view.measuredWidth.takeIf { it > 0 } ?: view.width
        val h = view.measuredHeight.takeIf { it > 0 } ?: view.height
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (isYandexPackage(pkg)) {
            val now = System.currentTimeMillis()
            if (now - _state.value.lastUpdateMs > 3000L) {
                _state.update {
                    it.copy(
                        isNavigating = false,
                        maneuverBitmap = null,
                        maneuverDistance = null,
                        nextStreet = null,
                        nextText = null,
                        fullNotificationText = null,
                        remainingDistance = null,
                        remainingTime = null,
                        arrivalTime = null,
                        maneuverTopDescription = null,
                        maneuverPrimaryIcon = null,
                        maneuverSecondaryIcon = null,
                        etaProgressBitmap = null,
                        lanesBitmap = null,
                        lanesBitmaps = emptyList(),
                        notificationTrafficLightText = null,
                        notificationTrafficLightBitmap = null,
                    )
                }
            }
        }
    }

    fun clearSpeedLimit() {
        _state.update { it.copy(speedLimit = null) }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // Actions broadcasted by Yandex Navigator mod / Monjaro Plus
    private const val ACTION_TRAFFIC_LIGHT_UPDATE = "plus.monjaro.TRAFFIC_LIGHT_UPDATE"
    private const val ACTION_NAVIGATION_ENDED = "plus.monjaro.NAVIGATION_ENDED"
}
