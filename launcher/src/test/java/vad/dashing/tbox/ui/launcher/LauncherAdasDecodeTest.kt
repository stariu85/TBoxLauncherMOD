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
        assertTrue(state9.hasAnyAlert)

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

    @Test
    fun pdcLevelFor_150cmIsFreeSpaceNone() {
        assertEquals(LauncherPdcLevel.None, LauncherPdcZones.levelFor(0))
        assertEquals(LauncherPdcLevel.None, LauncherPdcZones.levelFor(9))
        assertEquals(LauncherPdcLevel.Near, LauncherPdcZones.levelFor(10))
        assertEquals(LauncherPdcLevel.Near, LauncherPdcZones.levelFor(35))
        assertEquals(LauncherPdcLevel.Mid, LauncherPdcZones.levelFor(75))
        assertEquals(LauncherPdcLevel.Far, LauncherPdcZones.levelFor(149))
        assertEquals(LauncherPdcLevel.None, LauncherPdcZones.levelFor(150))
        assertFalse(LauncherPdcZones(rearLeftCm = 150).hasAny)
    }

    @Test
    fun decodeTsrSign_prefersSlaCodesAndDropsTinyPositiveConfidence() {
        // SLA codes 2..33 decode as (code - 1) * 5 km/h
        val sign60 = decodeTsrSign(valueRaw = 13.toByte(), unitRaw = 0, confidence = 0f, signClass = 1)
        assertTrue(sign60.valid)
        assertEquals(60, sign60.speedLimitKmh)

        val sign110 = decodeTsrSign(valueRaw = 23.toByte(), unitRaw = 0, confidence = 0.8f, signClass = 1)
        assertTrue(sign110.valid)
        assertEquals(110, sign110.speedLimitKmh)

        // Values > 33 fall back to literal km/h
        val literal80 = decodeTsrSign(valueRaw = 80.toByte(), unitRaw = 0, confidence = 0.9f, signClass = 1)
        assertTrue(literal80.valid)
        assertEquals(80, literal80.speedLimitKmh)

        // Confidence in (0f, 0.15f] is filtered out, including < 0.01f
        val tinyConf = decodeTsrSign(valueRaw = 13.toByte(), unitRaw = 0, confidence = 0.005f, signClass = 1)
        assertFalse(tinyConf.valid)
    }
}
