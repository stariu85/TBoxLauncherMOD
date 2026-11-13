package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAdasDecodeTest {

    @Test
    fun decodeAccMode_matchesVolgunovTBoxStockMapping() {
        assertEquals(LauncherAdasAccMode.Off, decodeAccMode(0.toByte()))
        assertEquals(LauncherAdasAccMode.Standby, decodeAccMode(1.toByte()))
        assertEquals(LauncherAdasAccMode.Standby, decodeAccMode(2.toByte()))
        assertEquals(LauncherAdasAccMode.ActiveBlue, decodeAccMode(3.toByte()))
        assertEquals(LauncherAdasAccMode.ActiveBlue, decodeAccMode(4.toByte()))
        assertEquals(LauncherAdasAccMode.ActiveBlue, decodeAccMode(5.toByte()))
        assertEquals(LauncherAdasAccMode.Standby, decodeAccMode(6.toByte()))
        assertEquals(LauncherAdasAccMode.Override, decodeAccMode(7.toByte()))
        assertEquals(LauncherAdasAccMode.Fault, decodeAccMode(9.toByte()))
        assertEquals(LauncherAdasAccMode.Unknown, decodeAccMode(8.toByte()))
        assertEquals(LauncherAdasAccMode.Unknown, decodeAccMode(10.toByte()))
    }

    @Test
    fun buildLauncherAdasState_standbyModes() {
        // Mode 2: Standby with set speed
        val state2 = buildLauncherAdasState(
            accModeRaw = 2.toByte(),
            vSetDisRaw = 80.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.Standby, state2.accMode)
        assertFalse(state2.accActive)
        assertTrue(state2.accStandby)
        assertFalse(state2.accOverride)
        assertEquals(80, state2.accSetSpeedKmh)

        // Mode 6: Standby with set speed
        val state6 = buildLauncherAdasState(
            accModeRaw = 6.toByte(),
            vSetDisRaw = 90.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.Standby, state6.accMode)
        assertFalse(state6.accActive)
        assertTrue(state6.accStandby)
        assertFalse(state6.accOverride)
        assertEquals(90, state6.accSetSpeedKmh)
    }

    @Test
    fun buildLauncherAdasState_activeModes() {
        val state3 = buildLauncherAdasState(
            accModeRaw = 3.toByte(),
            vSetDisRaw = 80.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.ActiveBlue, state3.accMode)
        assertTrue(state3.accActive)
        assertFalse(state3.accStandby)
        assertFalse(state3.accOverride)
        assertEquals(80, state3.accSetSpeedKmh)
    }

    @Test
    fun buildLauncherAdasState_overrideMode() {
        val state7 = buildLauncherAdasState(
            accModeRaw = 7.toByte(),
            vSetDisRaw = 100.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.Override, state7.accMode)
        assertFalse(state7.accActive)
        assertFalse(state7.accStandby)
        assertTrue(state7.accOverride)
        assertEquals(100, state7.accSetSpeedKmh)
    }

    @Test
    fun buildLauncherAdasState_faultAndOffModes() {
        val state9 = buildLauncherAdasState(
            accModeRaw = 9.toByte(),
            vSetDisRaw = 80.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.Fault, state9.accMode)
        assertFalse(state9.accActive)
        assertFalse(state9.accStandby)
        assertFalse(state9.accOverride)
        assertNull(state9.accSetSpeedKmh)

        val state0 = buildLauncherAdasState(
            accModeRaw = 0.toByte(),
            vSetDisRaw = 80.toByte(),
            objValidRaw = 0,
            frontObjectTypeRaw = 0,
            dxTarObjRaw = 0,
            objectDxRaw = 0,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 0,
            rightLaneRaw = 0,
            lkaStatusRaw = 0,
            adasTakeOverRaw = 0,
            slaSpdLimitRaw = 0,
            slaSpdLimitWarningRaw = 0,
        )
        assertEquals(LauncherAdasAccMode.Off, state0.accMode)
        assertFalse(state0.accActive)
        assertFalse(state0.accStandby)
        assertFalse(state0.accOverride)
        assertNull(state0.accSetSpeedKmh)
    }
}
