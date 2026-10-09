package vad.dashing.tbox.ui.launcher

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTwilightCalculatorTest {

    @Test
    fun testMoscowSunriseAndSunsetJune() {
        // Moscow: ~55.7558 N, 37.6173 E
        val lat = 55.7558
        val lon = 37.6173

        // June 21, 2025 (Summer Solstice) at 12:00 UTC
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2025, Calendar.JUNE, 21, 12, 0, 0)
        }

        val sunTimes = SolarTwilightCalculator.calculateSunTimes(lat, lon, cal.timeInMillis)
        assertNotNull(sunTimes.sunriseMillis)
        assertNotNull(sunTimes.sunsetMillis)

        val riseCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            timeInMillis = sunTimes.sunriseMillis!!
        }
        val setCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            timeInMillis = sunTimes.sunsetMillis!!
        }

        // In Moscow MSK (UTC+3) on June 21:
        // Sunrise is around 03:44 MSK, Sunset is around 21:18 MSK
        assertEquals(3, riseCal.get(Calendar.HOUR_OF_DAY))
        assertTrue(riseCal.get(Calendar.MINUTE) in 35..55)

        assertEquals(21, setCal.get(Calendar.HOUR_OF_DAY))
        assertTrue(setCal.get(Calendar.MINUTE) in 10..25)

        // Test isDaytime at noon MSK (09:00 UTC) -> should be true
        val noonMsk = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            set(2025, Calendar.JUNE, 21, 12, 0, 0)
        }
        assertTrue(SolarTwilightCalculator.isDaytime(lat, lon, noonMsk.timeInMillis))

        // Test isDaytime at 01:00 MSK (night) -> should be false
        val nightMsk = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            set(2025, Calendar.JUNE, 21, 1, 0, 0)
        }
        assertFalse(SolarTwilightCalculator.isDaytime(lat, lon, nightMsk.timeInMillis))
    }

    @Test
    fun testMoscowSunriseAndSunsetDecember() {
        // Moscow: ~55.7558 N, 37.6173 E
        val lat = 55.7558
        val lon = 37.6173

        // Dec 21, 2025 (Winter Solstice)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            set(2025, Calendar.DECEMBER, 21, 12, 0, 0)
        }

        val sunTimes = SolarTwilightCalculator.calculateSunTimes(lat, lon, cal.timeInMillis)
        assertNotNull(sunTimes.sunriseMillis)
        assertNotNull(sunTimes.sunsetMillis)

        val riseCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            timeInMillis = sunTimes.sunriseMillis!!
        }
        val setCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            timeInMillis = sunTimes.sunsetMillis!!
        }

        // On Dec 21 in Moscow:
        // Sunrise ~08:58 MSK, Sunset ~15:58 MSK
        assertEquals(8, riseCal.get(Calendar.HOUR_OF_DAY))
        assertTrue(setCal.get(Calendar.HOUR_OF_DAY) in 15..16)
    }
}
