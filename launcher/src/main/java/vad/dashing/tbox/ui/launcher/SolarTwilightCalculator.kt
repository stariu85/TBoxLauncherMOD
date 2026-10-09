package vad.dashing.tbox.ui.launcher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomical Sunrise & Sunset calculator using standard NOAA / Official Almanac algorithms.
 * Calculates exact local sunrise and sunset timestamps for any latitude, longitude, and date.
 */
object SolarTwilightCalculator {

    private const val OFFICIAL_ZENITH = 90.8333 // 90°50' accounting for atmospheric refraction

    data class SunTimes(
        val sunriseMillis: Long?,
        val sunsetMillis: Long?,
    )

    /**
     * Returns true if [timestampMillis] falls between sunrise and sunset on that day
     * at [latitude] and [longitude].
     */
    fun isDaytime(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val sunTimes = calculateSunTimes(latitude, longitude, timestampMillis)
        val sunrise = sunTimes.sunriseMillis
        val sunset = sunTimes.sunsetMillis

        if (sunrise == null || sunset == null) {
            // Polar day / polar night edge cases:
            // If latitude > 0 and summer in Northern Hemisphere, or latitude < 0 in Southern,
            // day lasts 24h.
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = timestampMillis
            }
            val month = cal.get(Calendar.MONTH) // 0-indexed (0..11)
            val isNorthernSummer = month in 3..8
            val isDay = if (latitude >= 0) isNorthernSummer else !isNorthernSummer
            return isDay
        }

        return timestampMillis in sunrise until sunset
    }

    /**
     * Computes exact sunrise and sunset epoch milliseconds for [latitude], [longitude],
     * and [timestampMillis]. Returns null for a field if polar day/night occurs on that date.
     */
    fun calculateSunTimes(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long = System.currentTimeMillis(),
    ): SunTimes {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMillis
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        val sunriseUtc = computeSunTimeUtc(
            latitude = latitude,
            longitude = longitude,
            dayOfYear = dayOfYear,
            isSunrise = true,
        )

        val sunsetUtc = computeSunTimeUtc(
            latitude = latitude,
            longitude = longitude,
            dayOfYear = dayOfYear,
            isSunrise = false,
        )

        val sunriseMillis = sunriseUtc?.let { utcHoursToEpochMillis(year, month, day, it) }
        val sunsetMillis = sunsetUtc?.let { utcHoursToEpochMillis(year, month, day, it) }

        return SunTimes(
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
        )
    }

    private fun computeSunTimeUtc(
        latitude: Double,
        longitude: Double,
        dayOfYear: Int,
        isSunrise: Boolean,
    ): Double? {
        val lngHour = longitude / 15.0
        val t = if (isSunrise) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        // Sun's mean anomaly
        val m = (0.9856 * t) - 3.289

        // Sun's true longitude
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = normalizeDegrees(l)

        // Sun's right ascension
        var ra = Math.toDegrees(atanDegree(0.91764 * tan(Math.toRadians(l))))
        ra = normalizeDegrees(ra)

        // Right ascension value needs to be in the same quadrant as L
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0 // Convert to hours

        // Sun's declination
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        // Sun's local hour angle
        val cosH = (cos(Math.toRadians(OFFICIAL_ZENITH)) - (sinDec * sin(Math.toRadians(latitude)))) /
            (cosDec * cos(Math.toRadians(latitude)))

        if (cosH > 1.0) {
            // Sun never rises on this date (polar night)
            return null
        }
        if (cosH < -1.0) {
            // Sun never sets on this date (midnight sun / polar day)
            return null
        }

        val hDeg = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val h = hDeg / 15.0

        // Local mean time of rising/setting
        val tLocal = h + ra - (0.06571 * t) - 6.622

        // UTC time
        val ut = tLocal - lngHour
        return normalizeHours(ut)
    }

    private fun utcHoursToEpochMillis(year: Int, month: Int, day: Int, utcHours: Double): Long {
        val hours = floor(utcHours).toInt()
        val minutesDecimal = (utcHours - hours) * 60.0
        val minutes = floor(minutesDecimal).toInt()
        val seconds = floor((minutesDecimal - minutes) * 60.0).toInt()

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, seconds)
        }
        return cal.timeInMillis
    }

    private fun atanDegree(value: Double): Double = kotlin.math.atan(value)

    private fun normalizeDegrees(deg: Double): Double {
        var result = deg % 360.0
        if (result < 0) result += 360.0
        return result
    }

    private fun normalizeHours(hours: Double): Double {
        var result = hours % 24.0
        if (result < 0) result += 24.0
        return result
    }
}
