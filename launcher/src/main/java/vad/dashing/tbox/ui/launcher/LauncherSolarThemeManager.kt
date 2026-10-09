package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import vad.dashing.tbox.TboxRepository
import kotlin.math.abs

private const val PREFS = "tbox_launcher_theme"
private const val KEY_LAST_LAT = "last_known_lat"
private const val KEY_LAST_LON = "last_known_lon"

/**
 * Resolves current GPS coordinates (from TBox UDP link or Android LocationManager)
 * and determines if it is currently daytime according to solar calculations.
 */
@Composable
fun rememberSolarDaytimeState(): Boolean? {
    val context = LocalContext.current
    var isDaytime by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val day = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                isDaytime = day
            }
            // Re-check every 60 seconds
            delay(60_000L)
        }
    }

    return isDaytime
}

@Suppress("MissingPermission")
internal fun resolveBestGpsCoordinates(context: Context): Pair<Double, Double>? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 1. Try TBox UDP GPS fix
    val loc = TboxRepository.locValues.value
    if (loc.hasGpsFix && (abs(loc.latitude) > 0.001 || abs(loc.longitude) > 0.001)) {
        saveLastKnownCoordinates(prefs, loc.latitude, loc.longitude)
        return Pair(loc.latitude, loc.longitude)
    }

    // 2. Try Android system LocationManager
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

    // 3. Fallback to last saved coordinates
    val savedLat = prefs.getFloat(KEY_LAST_LAT, Float.MIN_VALUE).toDouble()
    val savedLon = prefs.getFloat(KEY_LAST_LON, Float.MIN_VALUE).toDouble()
    if (abs(savedLat) > 0.001 || abs(savedLon) > 0.001) {
        return Pair(savedLat, savedLon)
    }

    return null
}

private fun saveLastKnownCoordinates(
    prefs: android.content.SharedPreferences,
    lat: Double,
    lon: Double,
) {
    prefs.edit()
        .putFloat(KEY_LAST_LAT, lat.toFloat())
        .putFloat(KEY_LAST_LON, lon.toFloat())
        .apply()
}
