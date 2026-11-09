package vad.dashing.tbox.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Live navigation & traffic light state captured from Yandex Navigator / Monjaro Plus broadcast intents.
 */
data class YandexNavState(
    val isNavigating: Boolean = false,
    val maneuverBitmap: Bitmap? = null,
    val maneuverDistance: String? = null,
    val speedLimit: String? = null,
    val nextStreet: String? = null,
    val nextText: String? = null,
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

            when (action) {
                ACTION_MANEUVER -> {
                    val bmp = getParcelableBitmap(intent, "maneuver_bitmap")
                        ?: getParcelableBitmap(intent, "maneuver_image")
                    val mDist = extractStringOrInt(intent, "maneuver_distance")
                        ?: extractStringOrInt(intent, "maneuver_dist")
                        ?: extractStringOrInt(intent, "distance_to_turn")
                    val tDist = extractStringOrInt(intent, "remaining_distance")
                        ?: extractStringOrInt(intent, "total_distance")
                    val genericDist = extractStringOrInt(intent, "distance")

                    val maneuverDist = mDist ?: genericDist
                    val finishDist = tDist ?: if (mDist != null) genericDist else null

                    val time = extractStringOrInt(intent, "Time_text")
                        ?: extractStringOrInt(intent, "time_text")
                        ?: extractStringOrInt(intent, "time")
                    val arrival = extractStringOrInt(intent, "arrival")
                        ?: extractStringOrInt(intent, "arrival_time")

                    _state.update {
                        it.copy(
                            isNavigating = true,
                            maneuverBitmap = bmp ?: it.maneuverBitmap,
                            maneuverDistance = maneuverDist ?: it.maneuverDistance,
                            remainingDistance = finishDist ?: it.remainingDistance,
                            remainingTime = time ?: it.remainingTime,
                            arrivalTime = arrival ?: it.arrivalTime,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_SPEEDLIMIT -> {
                    val limit = extractStringOrInt(intent, "speedlimit_text")
                        ?: extractStringOrInt(intent, "speed_limit")
                    val cleanLimit = limit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: limit
                    _state.update {
                        it.copy(
                            speedLimit = cleanLimit ?: it.speedLimit,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_NEXTSTREET -> {
                    val street = intent.getStringExtra("next_street")
                    val text = intent.getStringExtra("next_text")
                    _state.update {
                        it.copy(
                            nextStreet = street ?: it.nextStreet,
                            nextText = text ?: it.nextText,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_DISTANCE -> {
                    val dist = extractStringOrInt(intent, "distance")
                        ?: extractStringOrInt(intent, "remaining_distance")
                        ?: extractStringOrInt(intent, "total_distance")
                    _state.update {
                        it.copy(
                            remainingDistance = dist ?: it.remainingDistance,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_TIME -> {
                    val time = extractStringOrInt(intent, "Time_text")
                        ?: extractStringOrInt(intent, "time_text")
                        ?: extractStringOrInt(intent, "time")
                    _state.update {
                        it.copy(
                            remainingTime = time ?: it.remainingTime,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_ARRIVAL -> {
                    val arrival = extractStringOrInt(intent, "Time_text")
                        ?: extractStringOrInt(intent, "arrival")
                        ?: extractStringOrInt(intent, "arrival_time")
                    _state.update {
                        it.copy(
                            arrivalTime = arrival ?: it.arrivalTime,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_LANES, ACTION_LANES_BITMAP -> {
                    val lanesBmp = getParcelableBitmap(intent, "lanes_bitmap")
                    val lanesDist = extractStringOrInt(intent, "LANE_DIST") ?: extractStringOrInt(intent, "lanes_distance")
                    _state.update {
                        it.copy(
                            lanesBitmap = lanesBmp ?: it.lanesBitmap,
                            lanesDistance = lanesDist ?: it.lanesDistance,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_LANES_BITMAP_CLEAR -> {
                    _state.update {
                        it.copy(
                            lanesBitmap = null,
                            lanesDistance = null,
                            lastUpdateMs = now,
                        )
                    }
                }
                ACTION_TRAFFIC_LIGHT_UPDATE -> {
                    val color = intent.getStringExtra("tl_color")
                    val secs = intent.getIntExtra("tl_countdown", -1).takeIf { it >= 0 }
                        ?: intent.getIntExtra("tl_sec", -1).takeIf { it >= 0 }
                        ?: intent.getIntExtra("tl_secs", -1).takeIf { it >= 0 }
                    val dist = intent.getIntExtra("tl_dist_m", -1).takeIf { it >= 0 }
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
                ACTION_NAVIGATION_ENDED -> {
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
            addAction(ACTION_MANEUVER)
            addAction(ACTION_SPEEDLIMIT)
            addAction(ACTION_NEXTSTREET)
            addAction(ACTION_DISTANCE)
            addAction(ACTION_TIME)
            addAction(ACTION_ARRIVAL)
            addAction(ACTION_LANES)
            addAction(ACTION_LANES_BITMAP)
            addAction(ACTION_LANES_BITMAP_CLEAR)
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

    private fun getParcelableBitmap(intent: Intent, key: String): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(key, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(key) as? Bitmap
            }
        }.getOrNull()
    }

    // Actions broadcasted by Yandex Navigator mod / Monjaro Plus
    private const val ACTION_MANEUVER = "com.yandex.MANEUVER"
    private const val ACTION_SPEEDLIMIT = "com.yandex.SPEEDLIMIT"
    private const val ACTION_NEXTSTREET = "com.yandex.NEXTSTREET"
    private const val ACTION_DISTANCE = "com.yandex.DISTANCE"
    private const val ACTION_TIME = "com.yandex.TIME"
    private const val ACTION_ARRIVAL = "com.yandex.ARRIVAL"
    private const val ACTION_LANES = "com.yandex.LANES"
    private const val ACTION_LANES_BITMAP = "com.yandex.LANES_BITMAP"
    private const val ACTION_LANES_BITMAP_CLEAR = "com.yandex.LANES_BITMAP_CLEAR"
    private const val ACTION_TRAFFIC_LIGHT_UPDATE = "plus.monjaro.TRAFFIC_LIGHT_UPDATE"
    private const val ACTION_NAVIGATION_ENDED = "plus.monjaro.NAVIGATION_ENDED"
}
