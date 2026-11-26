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
import android.os.SystemClock
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
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.MediaControlNotificationListenerService
import vad.dashing.tbox.ShellExecutor
import vad.dashing.tbox.mbcan.UniversalCanRepository
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

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
    val broadcastManeuverBitmap: Bitmap? = null,
    val broadcastManeuverDistance: String? = null,
    val remainingDistanceMeters: Float? = null,
    val routeInitialDistanceMeters: Float? = null,
    val routeProgressRatio: Float? = null,
)

data class MinimapFrame(
    val bitmap: Bitmap? = null,
    val hasRoute: Boolean = false,
    val lastUpdateMs: Long = 0L,
    val frameSeq: Long = 0L,
)

object YandexNavRepository {
    private const val TAG = "YandexNavRepository"
    private const val TRAFFIC_LIGHT_TIMER_TIMEOUT_MS = 3_000L
    private const val TRAFFIC_LIGHT_STALE_TIMEOUT_MS = 5_000L
    private const val LANES_STALE_TIMEOUT_MS = 4_000L

    internal const val MINIMAP_MOVING_DECODE_INTERVAL_MS = 67L // ~15 FPS while moving
    internal const val MINIMAP_STATIONARY_DECODE_INTERVAL_MS = 333L // ~3 FPS when stationary (< 3 km/h)
    internal const val MINIMAP_STATIONARY_SPEED_THRESHOLD_KMH = 3.0f
    private const val MINIMAP_BITMAP_POOL_SIZE = 3

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val minimapDecodeDispatcher get() = LauncherCpuAffinity.minimapDecodeDispatcher
    private val pendingMinimapJpeg = AtomicReference<Triple<ByteArray, Boolean, Long>?>(null)
    private val minimapDecodeScheduled = AtomicBoolean(false)
    private val minimapFrameSeq = AtomicLong(0L)
    private val minimapPoolLock = Any()
    private val minimapBitmapPool = arrayOfNulls<Bitmap>(MINIMAP_BITMAP_POOL_SIZE)
    private var minimapBitmapPoolIndex = 0
    @Volatile private var lastMinimapDecodeElapsedMs: Long = 0L

    @VisibleForTesting
    internal var elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    @VisibleForTesting
    internal var vehicleSpeedProvider: () -> Float = { resolveCurrentVehicleSpeedKmh() }

    internal fun resolveMinimapDecodeIntervalMs(speedKmh: Float): Long {
        val safeSpeed = if (speedKmh.isFinite() && speedKmh > 0f) speedKmh else 0f
        return if (safeSpeed < MINIMAP_STATIONARY_SPEED_THRESHOLD_KMH) {
            MINIMAP_STATIONARY_DECODE_INTERVAL_MS
        } else {
            MINIMAP_MOVING_DECODE_INTERVAL_MS
        }
    }

    internal fun scaleMinimapDimensionPx(rawPx: Int, resolutionPercent: Int): Int {
        val clampedPercent = resolutionPercent.coerceIn(
            NAV_BG_MINIMAP_RESOLUTION_MIN_PERCENT,
            NAV_BG_MINIMAP_RESOLUTION_MAX_PERCENT,
        )
        val scaled = (rawPx * clampedPercent / 100f).roundToInt()
        return (scaled / 8 * 8).coerceIn(96, 1920)
    }

    private fun resolveCurrentVehicleSpeedKmh(): Float {
        if (LauncherDevVehicleState.simulateEnabled) {
            return sanitizeLauncherVehicleSpeed(LauncherDevVehicleState.speedKmh)
        }
        val candidate = UniversalCanRepository.carSpeedState.value?.takeIf { it >= 0f }
            ?: CanDataRepository.carSpeedAccurate.value?.takeIf { it >= 0f }
            ?: CanDataRepository.carSpeed.value
            ?: 0f
        return sanitizeLauncherVehicleSpeed(candidate)
    }

    private fun clearMinimapDecodeState() {
        pendingMinimapJpeg.set(null)
        lastMinimapDecodeElapsedMs = 0L
        synchronized(minimapPoolLock) {
            for (i in minimapBitmapPool.indices) {
                minimapBitmapPool[i] = null
            }
            minimapBitmapPoolIndex = 0
        }
    }

    private fun decodeMinimapWithPool(bytes: ByteArray): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val boundsOk = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        }.isSuccess
        val outW = boundsOpts.outWidth
        val outH = boundsOpts.outHeight
        if (!boundsOk || outW <= 0 || outH <= 0) {
            return runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        val slotIndex: Int
        val candidate: Bitmap?
        synchronized(minimapPoolLock) {
            slotIndex = minimapBitmapPoolIndex
            minimapBitmapPoolIndex = (minimapBitmapPoolIndex + 1) % MINIMAP_BITMAP_POOL_SIZE
            candidate = minimapBitmapPool[slotIndex]
        }

        val currentDisplayed = _minimapFrame.value.bitmap
        val canReuse = candidate != null &&
            !candidate.isRecycled &&
            candidate.isMutable &&
            candidate.config == Bitmap.Config.ARGB_8888 &&
            candidate.width == outW &&
            candidate.height == outH &&
            candidate !== currentDisplayed

        val decodeOpts = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (canReuse) {
                inBitmap = candidate
            }
        }

        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: IllegalArgumentException) {
            val fallbackOpts = BitmapFactory.Options().apply {
                inMutable = true
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, fallbackOpts)
            }.getOrNull()
        } catch (_: Throwable) {
            null
        }

        if (decoded != null) {
            synchronized(minimapPoolLock) {
                minimapBitmapPool[slotIndex] = decoded
            }
        }
        return decoded
    }

    private val jobLock = Any()
    @Volatile private var timerStaleJob: Job? = null
    @Volatile private var trafficLightStaleJob: Job? = null
    @Volatile private var minimapWindowStateJob: Job? = null
    @Volatile private var lanesStaleJob: Job? = null

    private fun cancelTimerStaleJob() {
        synchronized(jobLock) {
            timerStaleJob?.cancel()
            timerStaleJob = null
        }
    }

    private fun cancelTrafficLightStaleJob() {
        synchronized(jobLock) {
            trafficLightStaleJob?.cancel()
            trafficLightStaleJob = null
        }
    }

    private fun scheduleTrafficLightStaleJob() {
        synchronized(jobLock) {
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
    }

    private fun scheduleTimerStaleJob(timestampMs: Long) {
        synchronized(jobLock) {
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
    }

    private fun cancelLanesStaleJob() {
        synchronized(jobLock) {
            lanesStaleJob?.cancel()
            lanesStaleJob = null
        }
    }

    private fun scheduleLanesStaleJob() {
        synchronized(jobLock) {
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
    }

    private fun enqueueMinimapDecode(jpeg: ByteArray, hasRoute: Boolean, timestampMs: Long) {
        pendingMinimapJpeg.set(Triple(jpeg, hasRoute, timestampMs))
        kickMinimapDecode()
    }

    private fun kickMinimapDecode() {
        if (!minimapDecodeScheduled.compareAndSet(false, true)) return
        repositoryScope.launch(minimapDecodeDispatcher) {
            try {
                while (true) {
                    if (pendingMinimapJpeg.get() == null) break
                    val minIntervalMs = resolveMinimapDecodeIntervalMs(vehicleSpeedProvider())
                    val lastDecodeMs = lastMinimapDecodeElapsedMs
                    if (lastDecodeMs > 0L) {
                        val elapsedMs = elapsedRealtimeProvider() - lastDecodeMs
                        if (elapsedMs in 0L until minIntervalMs) {
                            delay(minIntervalMs - elapsedMs)
                        }
                    }
                    val item = pendingMinimapJpeg.getAndSet(null) ?: break
                    val (bytes, routeFlag, ts) = item
                    val bmp = decodeMinimapWithPool(bytes)
                    lastMinimapDecodeElapsedMs = elapsedRealtimeProvider()
                    if (bmp != null) {
                        _minimapFrame.value = MinimapFrame(
                            bitmap = bmp,
                            hasRoute = routeFlag,
                            lastUpdateMs = ts,
                            frameSeq = minimapFrameSeq.incrementAndGet(),
                        )
                        _hasMinimapBitmap.value = true
                    }
                }
            } finally {
                minimapDecodeScheduled.set(false)
                if (pendingMinimapJpeg.get() != null) {
                    kickMinimapDecode()
                }
            }
        }
    }

    private val _state = MutableStateFlow(YandexNavState())
    val state: StateFlow<YandexNavState> = _state.asStateFlow()

    private val _minimapFrame = MutableStateFlow(MinimapFrame())
    val minimapFrame: StateFlow<MinimapFrame> = _minimapFrame.asStateFlow()

    private val _hasMinimapBitmap = MutableStateFlow(false)
    val hasMinimapBitmap: StateFlow<Boolean> = _hasMinimapBitmap.asStateFlow()

    private fun clearMinimapFrame() {
        clearMinimapDecodeState()
        _minimapFrame.value = MinimapFrame()
        _hasMinimapBitmap.value = false
    }

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val verboseLogs = runCatching { Log.isLoggable(TAG, Log.VERBOSE) }.getOrDefault(false)
            val extras = intent.extras
            if (verboseLogs) {
                Log.v(TAG, "Received nav broadcast: $action")
                if (extras != null) {
                    for (k in extras.keySet()) {
                        @Suppress("DEPRECATION")
                        Log.v(TAG, "   [$action] Extra: $k = ${extras.get(k)}")
                    }
                }
            }
            val now = System.currentTimeMillis()
            val normAction = action.substringAfterLast('.').uppercase()

            // Only run generic heuristic extra scanning on general navigation/maneuver broadcasts.
            // Specific broadcasts (TRAFFIC_LIGHT_UPDATE, LANE_DIST, LANES, DISTANCE, MINIMAP, MANEUVER*, etc.)
            // are handled directly below without double-processing.
            val isGenericNavAction = normAction !in SPECIFIC_NON_MANEUVER_ACTIONS
            if (extras != null && isGenericNavAction) {
                var bitmap: Bitmap? = null
                var mDistance: String? = null
                var mMetrics: String? = null
                var streetText: String? = null
                var remDist: String? = null
                var remTime: String? = null
                var arrTime: String? = null

                for (k in extras.keySet()) {
                    @Suppress("DEPRECATION")
                    val value = extras.get(k) ?: continue
                    val valStr = value.toString().trim()
                    if (verboseLogs) {
                        Log.v(TAG, "   [$action] Extra: $k = $valStr (${value.javaClass.simpleName})")
                    }

                    if (value is Bitmap) {
                        val kLowerBmp = k.lowercase()
                        if (!kLowerBmp.contains("lane") && !kLowerBmp.contains("minimap")) {
                            if (kLowerBmp.contains("maneuver") || kLowerBmp.contains("icon") || kLowerBmp.contains("arrow") || kLowerBmp.contains("turn") || bitmap == null) {
                                bitmap = value
                            }
                        }
                        continue
                    }

                    val kLower = k.lowercase()
                    if (kLower == "metrics" || kLower == "unit" || kLower == "units" || kLower == "maneuver_metrics") {
                        mMetrics = valStr
                    } else if (kLower == "maneuver_distance" || kLower == "maneuver_dist" || kLower == "dist_to_turn" || kLower == "dist_m" || kLower == "distance_text") {
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
                    } else if (kLower.contains("dist") && !kLower.contains("tl_") && !kLower.contains("lane") && !kLower.contains("rem") && !kLower.contains("total") && !kLower.contains("route")) {
                        if (mDistance == null) mDistance = valStr
                    } else if (kLower.contains("time") && !kLower.contains("tl_")) {
                        if (remTime == null) remTime = valStr
                    }
                }

                if (bitmap != null || mDistance != null || streetText != null || remDist != null || remTime != null || arrTime != null) {
                    val cleanDist = formatBroadcastDistance(mDistance, mMetrics)
                    val cleanRemDist = formatBroadcastDistance(remDist, null)
                    val remMeters = parseDistanceToMeters(remDist, null)
                    _state.update {
                        val updatedProgress = updateRouteProgress(it, remMeters)
                        updatedProgress.copy(
                            isNavigating = true,
                            maneuverBitmap = bitmap ?: it.maneuverBitmap,
                            broadcastManeuverBitmap = bitmap ?: it.broadcastManeuverBitmap,
                            maneuverDistance = cleanDist ?: it.maneuverDistance,
                            broadcastManeuverDistance = cleanDist ?: it.broadcastManeuverDistance,
                            nextStreet = streetText?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.nextStreet,
                            remainingDistance = cleanRemDist ?: it.remainingDistance,
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
                    val metrics = extractRawExtraString(intent, "metrics")
                    val distText = formatBroadcastDistance(dist, metrics)
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
                    val metrics = extractRawExtraString(intent, "metrics")
                    val distText = formatBroadcastDistance(dist, metrics)
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
                    if (jpeg != null && jpeg.isNotEmpty()) {
                        enqueueMinimapDecode(jpeg, hasRoute, now)
                    }
                }
                "NIXT", "NEXT" -> {
                    val nextText = extractRawExtraString(intent, "next_text", "maneuver_distance", "dist_text", "dist")
                    val metrics = extractRawExtraString(intent, "metrics", "unit", "units")
                    val cleanDist = formatBroadcastDistance(nextText, metrics)
                    if (!cleanDist.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isNavigating = true,
                                nextText = cleanDist,
                                maneuverDistance = cleanDist,
                                broadcastManeuverDistance = cleanDist,
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
                    val metrics = extractRawExtraString(intent, "metrics", "unit", "units")
                    val cleanRemDist = formatBroadcastDistance(remDist, metrics)
                    val remMeters = parseDistanceToMeters(remDist, metrics)
                    if (cleanRemDist != null) {
                        _state.update {
                            val updatedProgress = updateRouteProgress(it, remMeters)
                            updatedProgress.copy(
                                isNavigating = true,
                                remainingDistance = cleanRemDist,
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
                "MANEUVER", "MANEUVER_UPDATE", "MANEUVER_BITMAP", "HUD_UPDATE", "NAVIGATION_UPDATE", "ROUTE_UPDATE", "STREET_UPDATE", "DISTANCE_UPDATE" -> {
                    @Suppress("DEPRECATION")
                    val maneuverBmp = intent.getParcelableExtra<Bitmap>("maneuver_bitmap")
                        ?: intent.getParcelableExtra<Bitmap>("maneuver_icon")
                        ?: intent.getParcelableExtra<Bitmap>("icon")
                        ?: intent.getParcelableExtra<Bitmap>("bitmap")
                    val mDistance = extractRawExtraString(intent, "maneuver_distance", "maneuver_dist", "dist_to_turn", "distance_text", "dist_text", "maneuver_text", "next_text", "dist_m", "dist", "distance")
                    val mMetrics = extractRawExtraString(intent, "metrics", "unit", "units", "maneuver_metrics")
                    val streetText = extractRawExtraString(intent, "street_name", "next_street", "street", "road_name", "target_street", "maneuver_street", "street_text")
                    val remDist = extractRawExtraString(intent, "remaining_distance", "rem_dist", "route_distance", "total_distance", "dist_to_dest")
                    val remTime = extractRawExtraString(intent, "remaining_time", "rem_time", "route_time", "time_to_target", "time_left")
                    val arrTime = extractRawExtraString(intent, "arrival_time", "eta", "arrival", "eta_text")

                    val validStreet = streetText?.takeIf { !isIgnoredStreetName(it) }
                    val cleanDist = formatBroadcastDistance(mDistance, mMetrics)
                    val cleanRemDist = formatBroadcastDistance(remDist, null)
                    val remMeters = parseDistanceToMeters(remDist, null)

                    _state.update {
                        val updatedProgress = updateRouteProgress(it, remMeters)
                        updatedProgress.copy(
                            isNavigating = true,
                            maneuverBitmap = maneuverBmp ?: it.maneuverBitmap,
                            broadcastManeuverBitmap = maneuverBmp ?: it.broadcastManeuverBitmap,
                            maneuverDistance = cleanDist ?: it.maneuverDistance,
                            broadcastManeuverDistance = cleanDist ?: it.broadcastManeuverDistance,
                            nextStreet = validStreet?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.nextStreet,
                            remainingDistance = cleanRemDist ?: it.remainingDistance,
                            remainingTime = remTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.remainingTime,
                            arrivalTime = arrTime?.takeIf { s -> s.isNotBlank() && s != "null" } ?: it.arrivalTime,
                            lastUpdateMs = now,
                        )
                    }
                }
                "MANEUVER_CLEAR", "MANEUVER_BITMAP_CLEAR" -> {
                    _state.update {
                        it.copy(
                            broadcastManeuverBitmap = null,
                            broadcastManeuverDistance = null,
                            lastUpdateMs = now,
                        )
                    }
                }
                "NAVIGATION_ENDED", "NAVIGATION_STOPPED" -> {
                    cancelTimerStaleJob()
                    cancelLanesStaleJob()
                    _state.update {
                        YandexNavState(isNavigating = false)
                    }
                }
            }
        }
    }

    fun resolveMinimapZoomForSpeed(speedKmh: Float, autoZoomEnabled: Boolean): Float {
        if (!autoZoomEnabled) return 16f
        return when {
            speedKmh < 20f -> 17.0f
            speedKmh < 50f -> 16.5f
            speedKmh < 80f -> 16.0f
            speedKmh < 110f -> 15.2f
            else -> 14.5f
        }
    }

    fun enableMinimap(
        context: Context,
        widthPx: Int = 360,
        heightPx: Int = 360,
        zoom: Float = 16f,
        viewMode: Int = 0,
    ) {
        val w = (widthPx / 8 * 8).coerceIn(96, 1920)
        val h = (heightPx / 8 * 8).coerceIn(96, 1920)
        val z = zoom.coerceIn(10f, 19f)
        val minimapIntent = Intent("com.yandex.MINIMAP_ENABLE").apply {
            putExtra("minimap_width", w)
            putExtra("minimap_height", h)
            putExtra("minimap_zoom", z)
            putExtra("minimap_roads", false)
            putExtra("minimap_labels", false)
            putExtra("minimap_view", viewMode)
            putExtra("minimap_3d", viewMode != 0)
            putExtra("minimap_hide_on_route_end", false)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(minimapIntent) }
    }

    fun disableMinimap(context: Context) {
        clearMinimapFrame()
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
                addAction("${prefix}MANEUVER_BITMAP")
                addAction("${prefix}MANEUVER_CLEAR")
                addAction("${prefix}MANEUVER_BITMAP_CLEAR")
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
                addAction("${prefix}NEXT")
                addAction("${prefix}NEXTSTREET")
                addAction("${prefix}DISTANCE")
                addAction("${prefix}TIME")
                addAction("${prefix}ARRIVAL")
            }
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
        synchronized(jobLock) {
            minimapWindowStateJob?.cancel()
            minimapWindowStateJob = repositoryScope.launch {
                var wasOpen = false
                YandexNavWindowTracker.isWindowOpen.collect { isOpen ->
                    if (!isOpen) {
                        clearMinimapFrame()
                        if (wasOpen) {
                            cancelTimerStaleJob()
                            cancelTrafficLightStaleJob()
                            cancelLanesStaleJob()
                            _state.update { cur ->
                                val hasNotificationData = cur.maneuverPrimaryIcon != null ||
                                    cur.maneuverDistance != null ||
                                    cur.remainingDistance != null ||
                                    cur.remainingTime != null ||
                                    cur.arrivalTime != null ||
                                    cur.notificationTrafficLightText != null ||
                                    cur.notificationTrafficLightBitmap != null
                                cur.copy(
                                    isNavigating = if (hasNotificationData) cur.isNavigating else false,
                                    speedLimit = null,
                                    trafficLightColor = null,
                                    trafficLightCountdownSeconds = null,
                                    trafficLightDistanceMeters = null,
                                    trafficLightArrow = null,
                                    trafficLightLastUpdateMs = 0L,
                                    trafficLightCountdownLastUpdateMs = 0L,
                                    broadcastManeuverBitmap = null,
                                    broadcastManeuverDistance = null,
                                    nextText = null,
                                    lanesBitmap = null,
                                    lanesBitmaps = emptyList(),
                                    lanesDistance = null,
                                )
                            }
                        }
                    }
                    wasOpen = isOpen
                }
            }
        }
    }

    fun stop(context: Context) {
        cancelTimerStaleJob()
        cancelTrafficLightStaleJob()
        cancelLanesStaleJob()
        synchronized(jobLock) {
            minimapWindowStateJob?.cancel()
            minimapWindowStateJob = null
        }
        clearMinimapFrame()
        YandexNavWindowTracker.stop()
        if (!registered) return
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
        cancelTrafficLightStaleJob()
        cancelLanesStaleJob()
        clearMinimapFrame()
        elapsedRealtimeProvider = { SystemClock.elapsedRealtime() }
        vehicleSpeedProvider = { resolveCurrentVehicleSpeedKmh() }
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

    private val SPECIFIC_NON_MANEUVER_ACTIONS = setOf(
        "SPEEDLIMIT",
        "SPEED_LIMIT",
        "SPEED_LIMIT_UPDATE",
        "SPEEDLIMIT_UPDATE",
        "TRAFFIC_LIGHT_UPDATE",
        "LANES_BITMAP",
        "LANES_BITMAP_CLEAR",
        "LANE_DIST",
        "LANES",
        "LANE_SIGN",
        "MINIMAP",
        "NIXT",
        "NEXT",
        "NEXTSTREET",
        "DISTANCE",
        "TIME",
        "ARRIVAL",
        "MANEUVER",
        "MANEUVER_UPDATE",
        "MANEUVER_BITMAP",
        "HUD_UPDATE",
        "NAVIGATION_UPDATE",
        "ROUTE_UPDATE",
        "STREET_UPDATE",
        "DISTANCE_UPDATE",
        "NAVIGATION_ENDED",
        "NAVIGATION_STOPPED",
        "MANEUVER_CLEAR",
        "MANEUVER_BITMAP_CLEAR",
    )

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val LEADING_NUMBER_UNIT_REGEX = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(.*)$")
    private val LEADING_MANEUVER_WORD_REGEX = Regex("(?i)^(поверните|держитесь|съезд|развернитесь|перестройтесь|прямо|двигайтесь|направо|налево|правее|левее|через|на|в|по)\\s+")

    /**
     * Normalizes raw distance strings from Yandex Navigator broadcasts.
     * Handles raw float/double strings (e.g. "249.84375" or "1250.4567" meters) and rounds them
     * cleanly into human-readable compact distance labels ("250 м", "1.3 км").
     */
    @VisibleForTesting
    internal fun formatBroadcastDistance(rawDist: String?, rawMetrics: String? = null): String? {
        val cleaned = rawDist?.replace(WHITESPACE_REGEX, " ")?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && it != "-1" }
            ?: return null
        val unitClean = rawMetrics?.trim()?.takeIf { it.isNotBlank() && it != "null" }

        // Split leading number and optional inline unit (e.g. "249.84375 м" or "249.84375")
        val match = LEADING_NUMBER_UNIT_REGEX.find(cleaned)
        if (match != null) {
            val numStr = match.groupValues[1].replace(',', '.')
            val inlineUnit = match.groupValues[2].trim()
            val effectiveUnit = inlineUnit.ifEmpty { unitClean ?: "" }
            val value = numStr.toDoubleOrNull()
            if (value != null && value >= 0.0) {
                val isKmUnit = effectiveUnit.lowercase().let {
                    it == "км" || it == "км." || it == "km" || it.startsWith("километр")
                }
                return if (isKmUnit) {
                    val roundedTenths = kotlin.math.round(value * 10.0) / 10.0
                    val numFormatted = if (roundedTenths % 1.0 == 0.0) {
                        roundedTenths.toInt().toString()
                    } else {
                        String.format(Locale.US, "%.1f", roundedTenths)
                    }
                    "$numFormatted ${effectiveUnit.ifEmpty { "км" }}"
                } else if (effectiveUnit.isEmpty() && value >= 1000.0) {
                    // Raw meters without unit >= 1000 -> format as km
                    val km = kotlin.math.round((value / 1000.0) * 10.0) / 10.0
                    val numFormatted = if (km % 1.0 == 0.0) {
                        km.toInt().toString()
                    } else {
                        String.format(Locale.US, "%.1f", km)
                    }
                    "$numFormatted км"
                } else {
                    val meters = kotlin.math.round(value).toInt()
                    val unitLabel = effectiveUnit.ifEmpty { "м" }
                    "$meters $unitLabel"
                }
            }
        }

        return if (unitClean != null && !cleaned.endsWith(unitClean, ignoreCase = true)) {
            "$cleaned $unitClean"
        } else {
            cleaned
        }
    }

    /**
     * Parses a distance string (e.g. "8.4 км", "250 м", "12450.5") into meters.
     */
    @VisibleForTesting
    internal fun parseDistanceToMeters(rawDist: String?, rawMetrics: String? = null): Float? {
        val cleaned = rawDist?.replace(WHITESPACE_REGEX, " ")?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && it != "-1" }
            ?: return null
        val unitClean = rawMetrics?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val match = LEADING_NUMBER_UNIT_REGEX.find(cleaned) ?: return null
        val numStr = match.groupValues[1].replace(',', '.')
        val inlineUnit = match.groupValues[2].trim()
        val effectiveUnit = inlineUnit.ifEmpty { unitClean ?: "" }.lowercase()
        val value = numStr.toFloatOrNull() ?: return null
        if (value < 0f) return null
        val isKm = effectiveUnit == "км" || effectiveUnit == "км." || effectiveUnit == "km" || effectiveUnit.startsWith("километр")
        return if (isKm) value * 1000f else value
    }

    /**
     * Scans Yandex's notification traffic progress bar (`etaProgressImage` / `etaProgressContainer`)
     * to detect the current vehicle marker or passed-gray vs upcoming-colored transition X ratio (`0..1`).
     */
    @VisibleForTesting
    internal fun detectProgressRatioFromEtaBitmap(bmp: Bitmap?): Float? {
        if (bmp == null || bmp.isRecycled) return null
        val w = bmp.width
        val h = bmp.height
        if (w < 20 || h < 2) return null

        return runCatching {
            val isHardwareCopy = bmp.config == Bitmap.Config.HARDWARE
            val readable = if (isHardwareCopy) {
                bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
            } else {
                bmp
            }
            try {
                val pixels = IntArray(w * h)
                readable.getPixels(pixels, 0, w, 0, 0, w, h)
                val midY = h / 2
                val midRowOffset = midY * w
                var lastGrayCol = -1
                var firstColoredCol = -1
                var brightestMarkerCol = -1
                var maxMarkerScore = 0

                for (x in 0 until w) {
                    // Check for a cursor/arrow marker protruding above or below the bar centerline, or bright white pin
                    for (y in 0 until h) {
                        val px = pixels[y * w + x]
                        val a = px ushr 24
                        if (a < 128) continue
                        val r = (px ushr 16) and 0xFF
                        val g = (px ushr 8) and 0xFF
                        val b = px and 0xFF
                        val luma = (54 * r + 183 * g + 19 * b) ushr 8
                        val distFromCenter = kotlin.math.abs(y - midY)
                        if (distFromCenter >= h / 3 && luma > 210) {
                            val score = luma + distFromCenter * 15
                            if (score > maxMarkerScore) {
                                maxMarkerScore = score
                                brightestMarkerCol = x
                            }
                        }
                    }

                    // Along the centerline, Yandex draws passed route as desaturated gray and upcoming route as colored (green/yellow/red)
                    val midPx = pixels[midRowOffset + x]
                    val a = midPx ushr 24
                    if (a >= 100) {
                        val r = (midPx ushr 16) and 0xFF
                        val g = (midPx ushr 8) and 0xFF
                        val b = midPx and 0xFF
                        val maxC = maxOf(r, g, b)
                        val minC = minOf(r, g, b)
                        val chroma = maxC - minC
                        if (chroma >= 45) {
                            if (firstColoredCol == -1) firstColoredCol = x
                        } else if (firstColoredCol == -1) {
                            lastGrayCol = x
                        }
                    }
                }

                when {
                    brightestMarkerCol in 1 until (w - 1) -> (brightestMarkerCol.toFloat() / (w - 1).toFloat()).coerceIn(0f, 1f)
                    firstColoredCol > 1 && lastGrayCol >= 0 -> (firstColoredCol.toFloat() / (w - 1).toFloat()).coerceIn(0f, 1f)
                    else -> null
                }
            } finally {
                if (isHardwareCopy && !readable.isRecycled) {
                    readable.recycle()
                }
            }
        }.getOrNull()
    }

    private fun updateRouteProgress(
        current: YandexNavState,
        newRemainingMeters: Float?,
        bitmapDetectedRatio: Float? = null,
    ): YandexNavState {
        if (newRemainingMeters == null && bitmapDetectedRatio == null) return current
        val rem = newRemainingMeters ?: current.remainingDistanceMeters
        val prevInit = current.routeInitialDistanceMeters
        // If no initial distance yet, or user started a new/longer route (remaining grew by > 300m), reset initial distance
        val initMeters = when {
            rem == null -> prevInit
            prevInit == null || prevInit < 50f -> rem.coerceAtLeast(50f)
            rem > prevInit + 300f -> rem
            else -> prevInit
        }
        val distanceRatio = if (rem != null && initMeters != null && initMeters > 0f) {
            (1f - (rem / initMeters)).coerceIn(0f, 1f)
        } else {
            null
        }
        // Prefer distance-based progress when initial > current (smooth real-time movement as remaining distance drops),
        // or fall back to bitmap-detected transition when joining mid-route
        val effectiveRatio = when {
            distanceRatio != null && distanceRatio > 0.005f -> distanceRatio
            bitmapDetectedRatio != null -> bitmapDetectedRatio
            distanceRatio != null -> distanceRatio
            else -> current.routeProgressRatio
        }
        return current.copy(
            remainingDistanceMeters = rem,
            routeInitialDistanceMeters = initMeters,
            routeProgressRatio = effectiveRatio,
        )
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
        cleaned = cleaned.replace(LEADING_MANEUVER_WORD_REGEX, "").trim()

        if (cleaned.length >= 2 && !isIgnoredStreetName(cleaned) && !DISTANCE_REGEX.containsMatchIn(cleaned)) {
            return cleaned
        }
        return null
    }

    private fun isYandexPackage(pkg: String): Boolean = isYandexNavigationPackage(pkg)

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
        val appCtx = context.applicationContext
        val component = ComponentName(appCtx, MediaControlNotificationListenerService::class.java)
        val flatComponent = component.flattenToString()
        val existing = runCatching {
            Settings.Secure.getString(appCtx.contentResolver, "enabled_notification_listeners").orEmpty()
        }.getOrDefault("")
        if (existing.split(':').any { it == flatComponent }) {
            return
        }
        val updated = if (existing.isBlank()) flatComponent else "$existing:$flatComponent"
        val wroteDirectly = runCatching {
            Settings.Secure.putString(appCtx.contentResolver, "enabled_notification_listeners", updated)
            Log.i(TAG, "Granted NotificationListener permission to $flatComponent via Settings.Secure")
            true
        }.getOrDefault(false)
        if (wroteDirectly) {
            runCatching {
                NotificationListenerService.requestRebind(component)
                Log.i(TAG, "Requested rebind for NotificationListenerService: $component")
            }
            return
        }
        repositoryScope.launch(Dispatchers.IO) {
            ShellExecutor.exec(listOf("cmd", "notification", "allow_listener", flatComponent))
            val afterCmd = runCatching {
                Settings.Secure.getString(appCtx.contentResolver, "enabled_notification_listeners").orEmpty()
            }.getOrDefault("")
            if (afterCmd.split(':').none { it == flatComponent }) {
                val nextVal = if (afterCmd.isBlank()) flatComponent else "$afterCmd:$flatComponent"
                ShellExecutor.exec(listOf("settings", "put", "secure", "enabled_notification_listeners", nextVal))
            }
            runCatching {
                NotificationListenerService.requestRebind(component)
                Log.i(TAG, "Requested rebind for NotificationListenerService: $component")
            }
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

        val remMeters = parseDistanceToMeters(remDist, null)
        val etaBmp = rvData?.etaProgressBitmap
        _state.update {
            val updatedProgress = updateRouteProgress(it, remMeters, bitmapDetectedRatio = null)
            updatedProgress.copy(
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
                etaProgressBitmap = etaBmp ?: it.etaProgressBitmap,
                lanesBitmap = rvData?.lanesBitmap ?: it.lanesBitmap,
                lanesBitmaps = if (rvData != null && rvData.lanesBitmaps.isNotEmpty()) rvData.lanesBitmaps else it.lanesBitmaps,
                notificationTrafficLightText = rvData?.trafficLightText ?: it.notificationTrafficLightText,
                notificationTrafficLightBitmap = rvData?.trafficLightBitmap ?: it.notificationTrafficLightBitmap,
                lastUpdateMs = now,
            )
        }
        if (etaBmp != null) {
            repositoryScope.launch(Dispatchers.Default) {
                val bmpRatio = detectProgressRatioFromEtaBitmap(etaBmp) ?: return@launch
                _state.update { cur ->
                    if (cur.etaProgressBitmap === etaBmp) {
                        updateRouteProgress(cur, newRemainingMeters = null, bitmapDetectedRatio = bmpRatio)
                    } else {
                        cur
                    }
                }
            }
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
