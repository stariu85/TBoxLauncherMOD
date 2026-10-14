package vad.dashing.tbox.ui.launcher

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import vad.dashing.tbox.TboxRepository
import kotlin.math.abs

private const val PREFS = "tbox_launcher_theme"
private const val KEY_LAST_LAT = "last_known_lat"
private const val KEY_LAST_LON = "last_known_lon"
private const val STARTUP_FAST_POLL_DURATION_MS = 60_000L // 1 minute
private const val STARTUP_FAST_POLL_INTERVAL_MS = 5_000L  // 5 seconds
private const val REGULAR_POLL_INTERVAL_MS = 60_000L       // 60 seconds

/**
 * Resolves current GPS coordinates (from TBox UDP link or Android LocationManager)
 * and determines if it is currently daytime according to solar calculations.
 */
@Composable
fun rememberSolarDaytimeState(): Boolean? {
    val context = LocalContext.current
    var isDaytime by remember(context) {
        val initialCoords = resolveBestGpsCoordinates(context)
        mutableStateOf(
            initialCoords?.let { (lat, lon) ->
                SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
            },
        )
    }

    LaunchedEffect(Unit) {
        val startUptimeMs = SystemClock.uptimeMillis()
        var liveCoords = resolveLiveGpsCoordinates(context)

        // Fast startup polling: check every 5 seconds for 1 minute or until live GPS fix arrives
        while (liveCoords == null && (SystemClock.uptimeMillis() - startUptimeMs) < STARTUP_FAST_POLL_DURATION_MS) {
            delay(STARTUP_FAST_POLL_INTERVAL_MS)
            liveCoords = resolveLiveGpsCoordinates(context)
            if (liveCoords != null) {
                val (lat, lon) = liveCoords
                val day = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                isDaytime = day
                if (LauncherThemeState.autoThemeSolarEnabled) {
                    LauncherThemeState.setDarkTheme(context, !day)
                }
                break
            }
        }

        // Regular periodic polling (every 60 seconds)
        while (true) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val day = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                isDaytime = day
                if (LauncherThemeState.autoThemeSolarEnabled) {
                    LauncherThemeState.setDarkTheme(context, !day)
                }
            }
            delay(REGULAR_POLL_INTERVAL_MS)
        }
    }

    return isDaytime
}

internal fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@Suppress("MissingPermission")
internal fun resolveLiveGpsCoordinates(context: Context): Pair<Double, Double>? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 1. Try TBox UDP GPS fix (direct hardware CAN/socket data from vehicle, no Android permission needed)
    val loc = TboxRepository.locValues.value
    if (loc.hasGpsFix && (abs(loc.latitude) > 0.001 || abs(loc.longitude) > 0.001)) {
        saveLastKnownCoordinates(prefs, loc.latitude, loc.longitude)
        return Pair(loc.latitude, loc.longitude)
    }

    // 2. Try Android system LocationManager (if permission is granted)
    if (hasLocationPermission(context)) {
        val systemLoc = runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            providers.firstNotNullOfOrNull { provider ->
                lm?.getLastKnownLocation(provider)?.takeIf {
                    abs(it.latitude) > 0.001 || abs(it.longitude) > 0.001
                }
            }
        }.getOrNull()

        if (systemLoc != null) {
            saveLastKnownCoordinates(prefs, systemLoc.latitude, systemLoc.longitude)
            return Pair(systemLoc.latitude, systemLoc.longitude)
        }
    }

    return null
}

internal fun resolveBestGpsCoordinates(context: Context): Pair<Double, Double>? {
    val live = resolveLiveGpsCoordinates(context)
    if (live != null) return live

    // Fallback to last saved coordinates
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val savedLat = prefs.getFloat(KEY_LAST_LAT, Float.MIN_VALUE).toDouble()
    val savedLon = prefs.getFloat(KEY_LAST_LON, Float.MIN_VALUE).toDouble()
    if (abs(savedLat) > 0.001 || abs(savedLon) > 0.001) {
        return Pair(savedLat, savedLon)
    }

    return null
}

private fun saveLastKnownCoordinates(
    prefs: SharedPreferences,
    lat: Double,
    lon: Double,
) {
    prefs.edit()
        .putFloat(KEY_LAST_LAT, lat.toFloat())
        .putFloat(KEY_LAST_LON, lon.toFloat())
        .apply()
}
