package vad.dashing.tbox.ui.launcher

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val lastUpdateMs: Long = 0L,
)

object YandexNavRepository {
    private const val TAG = "YandexNavRepository"

    private val _state = MutableStateFlow(YandexNavState())
    val state: StateFlow<YandexNavState> = _state.asStateFlow()

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

            when (normAction) {
                "SPEEDLIMIT" -> {
                    val limit = extractStringOrInt(intent, "speedlimit_text")
                        ?: extractStringOrInt(intent, "speed_limit")
                    val cleanLimit = limit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: limit
                    _state.update {
                        it.copy(
                            speedLimit = cleanLimit.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.speedLimit,
                            lastUpdateMs = now,
                        )
                    }
                }
                "TRAFFIC_LIGHT_UPDATE" -> {
                    val pos = extractIntExtra(intent, "tl_position", "position", "pos")
                    if (pos != null && pos != 0) {
                        return
                    }

                    val color = intent.getStringExtra("tl_color")
                    val secs = extractIntExtra(intent, "tl_countdown", "tl_sec", "tl_secs", "tl_time", "countdown", "sec", "secs", "time", "timer")
                    val dist = extractIntExtra(intent, "tl_dist_m", "tl_dist", "tl_distance", "dist_m", "dist")
                    val arrow = intent.getStringExtra("tl_arrow")

                    _state.update {
                        it.copy(
                            trafficLightColor = color ?: it.trafficLightColor,
                            trafficLightCountdownSeconds = secs ?: it.trafficLightCountdownSeconds,
                            trafficLightDistanceMeters = dist ?: it.trafficLightDistanceMeters,
                            trafficLightArrow = arrow ?: it.trafficLightArrow,
                            lastUpdateMs = now,
                        )
                    }
                }
                "NAVIGATION_ENDED" -> {
                    _state.update {
                        YandexNavState(isNavigating = false)
                    }
                }
            }
        }
    }

    fun start(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            listOf(
                "com.yandex.", "com.yandex.navi.", "ru.yandex.yandexnavi.", "ru.yandex.yandexmaps."
            ).forEach { prefix ->
                addAction("${prefix}SPEEDLIMIT")
            }
            addAction(ACTION_TRAFFIC_LIGHT_UPDATE)
            addAction(ACTION_NAVIGATION_ENDED)
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
    }

    fun stop(context: Context) {
        if (!registered) return
        runCatching {
            context.applicationContext.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun extractIntExtra(intent: Intent, vararg keys: String): Int? {
        val extras = intent.extras ?: return null
        for (k in keys) {
            if (!extras.containsKey(k)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(k) ?: continue
            when (value) {
                is Number -> return value.toInt().takeIf { it >= 0 }
                is String -> {
                    val strDigits = value.filter { it.isDigit() }
                    val parsed = strDigits.toIntOrNull()
                    if (parsed != null && parsed >= 0) return parsed
                }
            }
        }
        return null
    }

    private fun extractStringOrInt(intent: Intent, key: String): String? {
        if (!intent.hasExtra(key)) return null
        @Suppress("DEPRECATION")
        val extra = intent.extras?.get(key) ?: return null
        val str = extra.toString().trim()
        if (str.isEmpty() || str == "null") return null

        val num = str.toIntOrNull()
        if (num != null) {
            return if (num >= 1000) {
                String.format(Locale.US, "%.1f км", num / 1000f)
            } else {
                "$num м"
            }
        }
        return str
    }

    private fun isIgnoredStreetName(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        val lower = raw.trim().lowercase()
        if (lower in IGNORED_STREET_NAMES) return true
        if (lower.startsWith("navigator is running") ||
            lower.startsWith("яндекс.навигатор") ||
            lower.startsWith("яндекс навигатор") ||
            lower.startsWith("yandex navigator") ||
            lower.startsWith("yandex.navigator") ||
            lower.contains("is running")
        ) return true
        return false
    }

    private val IGNORED_STREET_NAMES = setOf(
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
        "yandex maps",
        "yandex.maps",
        "null"
    )

    private val DISTANCE_REGEX = Regex("(?i)(\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.))")
    private val TIME_REGEX = Regex("(?i)\\b(\\d+\\s*(?:мин|ч|мин.|ч.)|\\d+\\s*(?:min|h))\\b")
    private val ETA_REGEX = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")
    private val MANEUVER_PREFIX_REGEX = Regex("(?i)^(через\\s+\\d+(?:[.,]\\d+)?\\s*(?:километров|километра|километр|метров|метра|метр|км|м|km|m|км\\.|м\\.)\\s*)?(поверните|держитесь|съезд|развернитесь|перестройтесь|прямо|двигайтесь)?(\\s+(направо|налево|правее|левее))?\\s*(на|в|по)?\\s*")

    private fun isYandexPackage(pkg: String): Boolean {
        return pkg == "ru.yandex.yandexnavi" ||
                pkg == "ru.yandex.yandexmaps" ||
                pkg == "ru.yandex.mobile.navigator" ||
                pkg.startsWith("ru.yandex.yandexnavi") ||
                pkg.startsWith("ru.yandex.yandexmaps")
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!isYandexPackage(pkg)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

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
                    notification.largeIcon
                }
            }
        }.getOrNull()

        var mDistance: String? = null
        var streetText: String? = null
        var remDist: String? = null
        var remTime: String? = null
        var arrTime: String? = null

        val allRaw = listOfNotNull(title, text, bigText, subText).joinToString("\n")
        val parts = allRaw.split("•", "\n", "|").map { it.trim() }.filter { it.isNotBlank() }

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
                val hasDist = DISTANCE_REGEX.containsMatchIn(part)
                val lower = part.lowercase()
                val hasManeuverKw = listOf("через", "поверните", "держитесь", "развернитесь", "съезд", "перестройтесь", "прямо", "двигайтесь").any { lower.contains(it) }

                if (!hasDist && !hasManeuverKw && part.length > 2) {
                    streetText = part
                } else if (hasManeuverKw) {
                    val cleaned = part.replace(MANEUVER_PREFIX_REGEX, "").trim()
                    if (cleaned.length > 2 && !isIgnoredStreetName(cleaned) && !DISTANCE_REGEX.containsMatchIn(cleaned)) {
                        streetText = cleaned
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val validStreet = streetText.takeIf { !isIgnoredStreetName(it) }

        val candidates = listOfNotNull(title, text, bigText).map { it.trim() }.filter {
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
                maneuverBitmap = iconBitmap ?: it.maneuverBitmap,
                maneuverDistance = mDistance.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.maneuverDistance,
                nextStreet = validStreet.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.nextStreet,
                fullNotificationText = fullTextCandidate.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.fullNotificationText,
                remainingDistance = remDist.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.remainingDistance,
                remainingTime = remTime.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.remainingTime,
                arrivalTime = arrTime.takeIf { s -> !s.isNullOrBlank() && s != "null" } ?: it.arrivalTime,
                lastUpdateMs = now,
            )
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (isYandexPackage(pkg)) {
            val now = System.currentTimeMillis()
            if (now - _state.value.lastUpdateMs > 3000L) {
                _state.update { YandexNavState(isNavigating = false) }
            }
        }
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
