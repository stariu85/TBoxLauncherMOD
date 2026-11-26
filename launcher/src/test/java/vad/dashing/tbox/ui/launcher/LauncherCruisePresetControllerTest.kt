package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCruisePresetControllerTest {

    @Test
    fun shouldRetriggerConventionalPreset_requiresAtLeast3KmhDifference() {
        assertFalse(shouldRetriggerConventionalPreset(90, 90))
        assertFalse(shouldRetriggerConventionalPreset(90, 88))
        assertFalse(shouldRetriggerConventionalPreset(90, 92))
        assertTrue(shouldRetriggerConventionalPreset(90, 87))
        assertTrue(shouldRetriggerConventionalPreset(90, 78))
        assertTrue(shouldRetriggerConventionalPreset(90, 93))
    }

    @Test
    fun evaluateConventionalCatchUp_withinOneKmhIsReached() {
        assertEquals(
            CcsCatchUpDecision.Reached,
            evaluateConventionalCatchUp(
                targetKmh = 90,
                plateauSpeedKmh = 89.2f,
                previousPlateauSpeedKmh = null,
                previousErrorSign = 0,
            ),
        )
        assertEquals(
            CcsCatchUpDecision.Reached,
            evaluateConventionalCatchUp(
                targetKmh = 90,
                plateauSpeedKmh = 90.8f,
                previousPlateauSpeedKmh = 82.0f,
                previousErrorSign = 1,
            ),
        )
    }

    @Test
    fun evaluateConventionalCatchUp_firstRoundShortfallTriggersCatchUp() {
        val actual = evaluateConventionalCatchUp(
            targetKmh = 90,
            plateauSpeedKmh = 78.3f,
            previousPlateauSpeedKmh = null,
            previousErrorSign = 0,
        )
        assertEquals(CcsCatchUpDecision.CatchUp(actualKmh = 78, errorKmh = 12), actual)
    }

    @Test
    fun evaluateConventionalCatchUp_secondRoundWithProgressContinuesCatchUp() {
        val actual = evaluateConventionalCatchUp(
            targetKmh = 90,
            plateauSpeedKmh = 86.1f,
            previousPlateauSpeedKmh = 78.3f,
            previousErrorSign = 1,
        )
        assertEquals(CcsCatchUpDecision.CatchUp(actualKmh = 86, errorKmh = 4), actual)
    }

    @Test
    fun evaluateConventionalCatchUp_secondRoundWithoutProgressStopsForUphillSafety() {
        val actual = evaluateConventionalCatchUp(
            targetKmh = 90,
            plateauSpeedKmh = 78.8f,
            previousPlateauSpeedKmh = 78.3f,
            previousErrorSign = 1,
        )
        assertEquals(CcsCatchUpDecision.NoProgress, actual)
    }

    @Test
    fun evaluateConventionalCatchUp_overshootTriggersNegativeCatchUpAndChecksDownwardProgress() {
        val round1 = evaluateConventionalCatchUp(
            targetKmh = 80,
            plateauSpeedKmh = 88.2f,
            previousPlateauSpeedKmh = null,
            previousErrorSign = 0,
        )
        assertEquals(CcsCatchUpDecision.CatchUp(actualKmh = 88, errorKmh = -8), round1)

        // Failed to slow down (e.g. steep downhill) -> stop
        val round2NoProgress = evaluateConventionalCatchUp(
            targetKmh = 80,
            plateauSpeedKmh = 88.0f,
            previousPlateauSpeedKmh = 88.2f,
            previousErrorSign = -1,
        )
        assertEquals(CcsCatchUpDecision.NoProgress, round2NoProgress)

        // Slowed down meaningfully -> continue
        val round2Progress = evaluateConventionalCatchUp(
            targetKmh = 80,
            plateauSpeedKmh = 83.0f,
            previousPlateauSpeedKmh = 88.2f,
            previousErrorSign = -1,
        )
        assertEquals(CcsCatchUpDecision.CatchUp(actualKmh = 83, errorKmh = -3), round2Progress)
    }
}
