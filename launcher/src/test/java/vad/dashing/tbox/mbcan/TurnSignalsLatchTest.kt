package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnSignalsLatchTest {

    @Test
    fun turnSignalsLatch_retriggerAndHold() {
        var currentTime = 10_000L
        val sideHistory = mutableListOf<TurnSignalSide?>()

        val latch = TurnSignalsLatch(
            timeProvider = { currentTime },
            holdMs = 2_500L,
            onSideChanged = { sideHistory.add(it) }
        )

        // 1. First left flash (2, 0)
        latch.onRawTurnLights(2, 0)
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))
        assertEquals(listOf(TurnSignalSide.Left), sideHistory)

        // 2. Pause between blinks (1, 0) at +500ms
        currentTime += 500L
        latch.onRawTurnLights(1, 0)
        // Still Left!
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))

        // 3. Second left flash (2, 0) at +500ms (1000ms from start)
        currentTime += 500L
        latch.onRawTurnLights(2, 0)
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))

        // 4. Advance time by 2000ms (total 2000ms since 2nd flash -> timer was 2500ms, not expired yet)
        currentTime += 2_000L
        latch.onRawTurnLights(0, 0)
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))

        // 5. Advance time by 600ms (total 2600ms since 2nd flash -> expired!)
        currentTime += 600L
        assertNull(latch.currentSide(currentTime))
    }

    @Test
    fun turnSignalsLatch_oppositeSideInstantlyResets() {
        var currentTime = 10_000L
        val sideHistory = mutableListOf<TurnSignalSide?>()

        val latch = TurnSignalsLatch(
            timeProvider = { currentTime },
            holdMs = 2_500L,
            onSideChanged = { sideHistory.add(it) }
        )

        // Left flash (2, 0)
        latch.onRawTurnLights(2, 0)
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))

        // Driver switches to Right flash (0, 2) at +400ms
        currentTime += 400L
        latch.onRawTurnLights(0, 2)
        assertEquals(TurnSignalSide.Right, latch.currentSide(currentTime))
        assertEquals(listOf(TurnSignalSide.Left, TurnSignalSide.Right), sideHistory)
    }

    @Test
    fun turnSignalsLatch_hazardInstantlyResets() {
        var currentTime = 10_000L
        val sideHistory = mutableListOf<TurnSignalSide?>()

        val latch = TurnSignalsLatch(
            timeProvider = { currentTime },
            holdMs = 2_500L,
            onSideChanged = { sideHistory.add(it) }
        )

        // Left flash (2, 0)
        latch.onRawTurnLights(2, 0)
        assertEquals(TurnSignalSide.Left, latch.currentSide(currentTime))

        // Hazard (2, 2)
        currentTime += 300L
        latch.onRawTurnLights(2, 2)
        assertNull(latch.currentSide(currentTime))
        assertEquals(listOf(TurnSignalSide.Left, null), sideHistory)
    }
}
