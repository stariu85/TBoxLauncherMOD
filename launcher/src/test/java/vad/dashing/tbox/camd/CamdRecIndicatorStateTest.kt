package vad.dashing.tbox.camd

import org.junit.Assert.assertEquals
import org.junit.Test

class CamdRecIndicatorStateTest {

    private fun connected(recEnabled: Boolean, recActive: Boolean) = CamdConnectionStatus.Connected(
        openWindows = emptyList(),
        avmState = "stopped",
        keepAvm = false,
        rawStatus = "OK",
        recEnabled = recEnabled,
        recActive = recActive,
    )

    @Test
    fun notInstalled_and_noConnection_areOff() {
        assertEquals(CamdRecIndicatorState.OFF, CamdConnectionStatus.NotInstalled.resolveRecIndicatorState())
        assertEquals(CamdRecIndicatorState.OFF, CamdConnectionStatus.NoConnection("err").resolveRecIndicatorState())
    }

    @Test
    fun recDisabledAndInactive_isOff() {
        assertEquals(
            CamdRecIndicatorState.OFF,
            connected(recEnabled = false, recActive = false).resolveRecIndicatorState(),
        )
    }

    @Test
    fun recEnabledAndInactive_isStandbyBlue() {
        assertEquals(
            CamdRecIndicatorState.STANDBY,
            connected(recEnabled = true, recActive = false).resolveRecIndicatorState(),
        )
    }

    @Test
    fun recActive_isActiveRed() {
        assertEquals(
            CamdRecIndicatorState.ACTIVE,
            connected(recEnabled = true, recActive = true).resolveRecIndicatorState(),
        )
        assertEquals(
            CamdRecIndicatorState.ACTIVE,
            connected(recEnabled = false, recActive = true).resolveRecIndicatorState(),
        )
    }
}
