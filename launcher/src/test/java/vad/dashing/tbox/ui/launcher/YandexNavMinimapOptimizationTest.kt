package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexNavMinimapOptimizationTest {

    @Test
    fun resolveMinimapDecodeIntervalMs_throttlesStationaryAndMovingSpeeds() {
        assertEquals(
            YandexNavRepository.MINIMAP_STATIONARY_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(0f),
        )
        assertEquals(
            YandexNavRepository.MINIMAP_STATIONARY_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(2.9f),
        )
        assertEquals(
            YandexNavRepository.MINIMAP_STATIONARY_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(-5f),
        )
        assertEquals(
            YandexNavRepository.MINIMAP_STATIONARY_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(Float.NaN),
        )
        assertEquals(
            YandexNavRepository.MINIMAP_MOVING_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(3.0f),
        )
        assertEquals(
            YandexNavRepository.MINIMAP_MOVING_DECODE_INTERVAL_MS,
            YandexNavRepository.resolveMinimapDecodeIntervalMs(60f),
        )
    }

    @Test
    fun scaleMinimapDimensionPx_scalesClampsAndAlignsToEight() {
        assertEquals(640, YandexNavRepository.scaleMinimapDimensionPx(640, 100))
        assertEquals(320, YandexNavRepository.scaleMinimapDimensionPx(640, 50))
        assertEquals(480, YandexNavRepository.scaleMinimapDimensionPx(640, 75))
        // Clamps percent to 50..100
        assertEquals(320, YandexNavRepository.scaleMinimapDimensionPx(640, 10))
        assertEquals(640, YandexNavRepository.scaleMinimapDimensionPx(640, 150))
        // Clamps resulting pixels to 96..1920 and aligns to multiple of 8
        assertEquals(96, YandexNavRepository.scaleMinimapDimensionPx(100, 50))
        assertEquals(1920, YandexNavRepository.scaleMinimapDimensionPx(2400, 100))
        assertEquals(0, YandexNavRepository.scaleMinimapDimensionPx(647, 75) % 8)
    }

    @Test
    fun virtualRoadFrameInterval_capsAtRoughly30Fps() {
        assertTrue(VIRTUAL_ROAD_FRAME_INTERVAL_NS in 30_000_000L..34_000_000L)
    }

    @Test
    fun shouldSuppressNavMinimapForAcc_hidesOnlyWhenEnabledAndAccEngaged() {
        assertTrue(shouldSuppressNavMinimapForAcc(hideOnAccEnabled = true, accActive = true, accOverride = false))
        assertTrue(shouldSuppressNavMinimapForAcc(hideOnAccEnabled = true, accActive = false, accOverride = true))
        assertEquals(false, shouldSuppressNavMinimapForAcc(hideOnAccEnabled = true, accActive = false, accOverride = false))
        assertEquals(false, shouldSuppressNavMinimapForAcc(hideOnAccEnabled = false, accActive = true, accOverride = false))
        assertEquals(false, shouldSuppressNavMinimapForAcc(hideOnAccEnabled = false, accActive = false, accOverride = true))
    }

    @Test
    fun resolveLanesOrTurnTapAction_togglesMinimapOnlyWhenSettingEnabled() {
        assertEquals(
            LanesOrTurnTapAction.TOGGLE_MINIMAP,
            resolveLanesOrTurnTapAction(minimapSettingEnabled = true),
        )
        assertEquals(
            LanesOrTurnTapAction.OPEN_YANDEX_NAV,
            resolveLanesOrTurnTapAction(minimapSettingEnabled = false),
        )
    }

    @Test
    fun isNavMinimapEffectivelyVisible_combinesSettingManualToggleAndAccSuppression() {
        assertEquals(
            false,
            isNavMinimapEffectivelyVisible(
                minimapSettingEnabled = false,
                hideOnAccEnabled = false,
                accActive = false,
                accOverride = false,
                manuallyHidden = false,
            ),
        )
        assertEquals(
            true,
            isNavMinimapEffectivelyVisible(
                minimapSettingEnabled = true,
                hideOnAccEnabled = false,
                accActive = false,
                accOverride = false,
                manuallyHidden = false,
            ),
        )
        assertEquals(
            false,
            isNavMinimapEffectivelyVisible(
                minimapSettingEnabled = true,
                hideOnAccEnabled = false,
                accActive = false,
                accOverride = false,
                manuallyHidden = true,
            ),
        )
        assertEquals(
            false,
            isNavMinimapEffectivelyVisible(
                minimapSettingEnabled = true,
                hideOnAccEnabled = true,
                accActive = true,
                accOverride = false,
                manuallyHidden = false,
                accOverrideVisible = false,
            ),
        )
        assertEquals(
            true,
            isNavMinimapEffectivelyVisible(
                minimapSettingEnabled = true,
                hideOnAccEnabled = true,
                accActive = true,
                accOverride = false,
                manuallyHidden = false,
                accOverrideVisible = true,
            ),
        )
    }
}
