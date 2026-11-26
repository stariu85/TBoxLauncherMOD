package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherMediaMiniPlayerAnimationTest {

    @Test
    fun rotationThrottle_skipsFramesBelow20FpsInterval() {
        assertEquals(50_000_000L, ROTATING_PLAYER_NAME_FRAME_INTERVAL_NS)
        assertNull(advanceRotatingPlayerNameAngle(currentAngleDeg = 10f, deltaNs = 0L))
        assertNull(advanceRotatingPlayerNameAngle(currentAngleDeg = 10f, deltaNs = 16_666_666L))
        assertNull(advanceRotatingPlayerNameAngle(currentAngleDeg = 10f, deltaNs = 49_999_999L))
    }

    @Test
    fun rotationThrottle_advancesAtConfiguredAngularSpeedAndWrapsAt360() {
        val next = advanceRotatingPlayerNameAngle(
            currentAngleDeg = 0f,
            deltaNs = 50_000_000L, // 0.05 s
        )
        assertNotNull(next)
        val expectedStep = 0.05f * (360f / 23.4f)
        assertEquals(expectedStep, next!!, 1e-4f)

        val wrapped = advanceRotatingPlayerNameAngle(
            currentAngleDeg = 359.5f,
            deltaNs = 50_000_000L,
        )
        assertNotNull(wrapped)
        assertEquals((359.5f + expectedStep) % 360f, wrapped!!, 1e-4f)
    }

    @Test
    fun rotationThrottle_clampsLargeDeltaToAvoidJumpAfterPause() {
        val clamped = advanceRotatingPlayerNameAngle(
            currentAngleDeg = 42f,
            deltaNs = 5_000_000_000L, // 5 s pause spike
        )
        assertNotNull(clamped)
        val expected = 42f + ROTATING_PLAYER_NAME_MAX_DT_SEC * ROTATING_PLAYER_NAME_DEGREES_PER_SEC
        assertEquals(expected, clamped!!, 1e-4f)
    }

    @Test
    fun shouldRunMediaMarquee_requiresBothAnimationAllowedAndPlaying() {
        assertTrue(shouldRunMediaMarquee(animate = true, isPlaying = true))
        assertFalse(shouldRunMediaMarquee(animate = false, isPlaying = true))
        assertFalse(shouldRunMediaMarquee(animate = true, isPlaying = false))
        assertFalse(shouldRunMediaMarquee(animate = false, isPlaying = false))
    }
}
