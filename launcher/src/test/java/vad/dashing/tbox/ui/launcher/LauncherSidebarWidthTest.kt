package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSidebarWidthTest {

    @Test
    fun sidebarWidthLimitsAre240To400() {
        assertEquals(240, SIDEBAR_WIDTH_MIN)
        assertEquals(400, SIDEBAR_WIDTH_MAX)
        assertEquals(300, SIDEBAR_WIDTH_DEFAULT)
    }

    @Test
    fun coerceWidthValueStaysInRange() {
        fun coerce(width: Int): Int = width.coerceIn(SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX)

        assertEquals(240, coerce(150))
        assertEquals(240, coerce(240))
        assertEquals(300, coerce(300))
        assertEquals(400, coerce(400))
        assertEquals(400, coerce(760))
    }
}
