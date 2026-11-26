package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherGearboxTypeTest {

    @Test
    fun testGearboxTypeFromCode() {
        assertEquals(GearboxType.DCT_7, GearboxType.fromCode("7dct"))
        assertEquals(GearboxType.DCT_7, GearboxType.fromCode("7DCT"))
        assertEquals(GearboxType.DCT_6, GearboxType.fromCode("6dct"))
        assertEquals(GearboxType.DCT_6, GearboxType.fromCode("6DCT"))
        assertEquals(GearboxType.MANUAL, GearboxType.fromCode("manual"))
        assertEquals(GearboxType.MANUAL, GearboxType.fromCode("MANUAL"))
        assertEquals(GearboxType.MANUAL, GearboxType.fromCode("mt"))
        assertEquals(GearboxType.MANUAL, GearboxType.fromCode("MT"))
        assertEquals(GearboxType.DCT_7, GearboxType.fromCode(null))
        assertEquals(GearboxType.DCT_7, GearboxType.fromCode("unknown"))
    }

    @Test
    fun testCanDataRepositoryGearboxTypeState() {
        CanDataRepository.setGearboxType(GearboxType.MANUAL)
        assertEquals(GearboxType.MANUAL, CanDataRepository.gearboxType.value)
        assertTrue(CanDataRepository.isManualGearbox.value)
        assertFalse(CanDataRepository.is6DctGearbox.value)

        CanDataRepository.setGearboxType(GearboxType.DCT_6)
        assertEquals(GearboxType.DCT_6, CanDataRepository.gearboxType.value)
        assertFalse(CanDataRepository.isManualGearbox.value)
        assertTrue(CanDataRepository.is6DctGearbox.value)

        CanDataRepository.setGearboxType(GearboxType.DCT_7)
        assertEquals(GearboxType.DCT_7, CanDataRepository.gearboxType.value)
        assertFalse(CanDataRepository.isManualGearbox.value)
        assertFalse(CanDataRepository.is6DctGearbox.value)
    }

    @Test
    fun testParseGearboxTypeFromSystemConfigStringsIgnoresRawBitSubstrings() {
        // Hex/bitmask strings containing "000", "001", or "110" must not trigger false positives.
        assertEquals(
            GearboxType.DCT_7,
            CanDataRepository.parseGearboxTypeFromSystemConfigStrings(
                "",
                "000110001A2B3C",
                "FFFFFFFF000110001",
            ),
        )
        assertEquals(
            GearboxType.DCT_6,
            CanDataRepository.parseGearboxTypeFromSystemConfigStrings("1.5_6DCT", "000001"),
        )
        assertEquals(
            GearboxType.MANUAL,
            CanDataRepository.parseGearboxTypeFromSystemConfigStrings("1.5_6MT", "110"),
        )
        assertEquals(
            GearboxType.DCT_7,
            CanDataRepository.parseGearboxTypeFromSystemConfigStrings("1.6_7DCT", "110000001"),
        )
    }
}
