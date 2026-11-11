package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherGearSelectorTest {

    @Test
    fun unknownModeIsNotNeutral() {
        assertNull(resolveActiveGearSlot("N/A", 0))
        assertNull(resolveActiveGearSlot("", 0))
    }

    @Test
    fun driveModeWinsOverZeroCurrentGear() {
        assertEquals('D', resolveActiveGearSlot("D", 0))
        assertEquals('M', resolveActiveGearSlot("M", 1))
        assertEquals('M', resolveActiveGearSlot("MANUAL", 2))
        assertEquals('P', resolveActiveGearSlot("P", 0))
        assertEquals('N', resolveActiveGearSlot("N", 0))
        assertEquals('R', resolveActiveGearSlot("R", 0))
    }
}
