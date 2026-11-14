package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherNavTrafficLightTest {

    @Test
    fun timerFontSize_decreasesForThreeDigitValuesToFitInCircle() {
        // Standard scale (1.0f)
        assertEquals(7f, resolveTrafficLightTimerFontSizeSp("100", 1.0f), 0.001f)
        assertEquals(7f, resolveTrafficLightTimerFontSizeSp("120", 1.0f), 0.001f)
        assertEquals(7f, resolveTrafficLightTimerFontSizeSp("999", 1.0f), 0.001f)

        // 1 and 2 digits use 10f
        assertEquals(10f, resolveTrafficLightTimerFontSizeSp("99", 1.0f), 0.001f)
        assertEquals(10f, resolveTrafficLightTimerFontSizeSp("45", 1.0f), 0.001f)
        assertEquals(10f, resolveTrafficLightTimerFontSizeSp("5", 1.0f), 0.001f)
        assertEquals(10f, resolveTrafficLightTimerFontSizeSp("0", 1.0f), 0.001f)
    }

    @Test
    fun timerFontSize_scalesWithWidgetScale() {
        val scale = 1.5f
        assertEquals(10.5f, resolveTrafficLightTimerFontSizeSp("120", scale), 0.001f)
        assertEquals(15.0f, resolveTrafficLightTimerFontSizeSp("30", scale), 0.001f)
    }

    @Test
    fun effectiveTimer_showsWhenFreshWithinThreeSeconds() {
        val lastUpdate = 10_000L

        // Just updated
        assertEquals(120, resolveEffectiveTrafficLightTimer(120, lastUpdate, currentTimeMs = 10_000L))

        // 1 second later
        assertEquals(119, resolveEffectiveTrafficLightTimer(119, lastUpdate, currentTimeMs = 11_000L))

        // 2.9 seconds later
        assertEquals(25, resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 12_900L))

        // Exactly 3.0 seconds later
        assertEquals(25, resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 13_000L))
    }

    @Test
    fun effectiveTimer_hidesWhenStaleForMoreThanThreeSeconds() {
        val lastUpdate = 10_000L

        // 3.001 seconds later -> hidden
        assertNull(resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 13_001L))

        // 3.5 seconds later -> hidden
        assertNull(resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 13_500L))

        // 5 seconds later -> hidden
        assertNull(resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 15_000L))

        // 60 seconds later -> hidden
        assertNull(resolveEffectiveTrafficLightTimer(25, lastUpdate, currentTimeMs = 70_000L))
    }

    @Test
    fun effectiveTimer_hidesWhenNullOrUninitializedOrNegative() {
        // null countdown
        assertNull(resolveEffectiveTrafficLightTimer(null, 10_000L, currentTimeMs = 10_500L))

        // lastUpdateMs <= 0
        assertNull(resolveEffectiveTrafficLightTimer(30, 0L, currentTimeMs = 10_500L))

        // negative countdown (e.g. -1 indicating no timer)
        assertNull(resolveEffectiveTrafficLightTimer(-1, 10_000L, currentTimeMs = 10_500L))
    }

    @Test
    fun yandexNavState_trafficLightCountdownDefaults() {
        val defaultState = YandexNavState()
        assertNull(defaultState.trafficLightCountdownSeconds)
        assertEquals(0L, defaultState.trafficLightCountdownLastUpdateMs)
        assertNull(defaultState.trafficLightColor)
    }
}
