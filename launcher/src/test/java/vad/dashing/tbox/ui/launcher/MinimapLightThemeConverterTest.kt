package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimapLightThemeConverterTest {

    private fun packRgb(r: Int, g: Int, b: Int, a: Int = 0xFF): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun unpackR(argb: Int): Int = (argb ushr 16) and 0xFF
    private fun unpackG(argb: Int): Int = (argb ushr 8) and 0xFF
    private fun unpackB(argb: Int): Int = argb and 0xFF
    private fun luma(argb: Int): Int =
        ((54 * unpackR(argb) + 183 * unpackG(argb) + 19 * unpackB(argb) + 128) ushr 8)

    @Test
    fun darkMapBackgroundAndRouteShadow_mapCleanlyToLightPanelBgWithoutSpeckle() {
        val expected = packRgb(
            MinimapLightThemeConverter.LIGHT_BG_R,
            MinimapLightThemeConverter.LIGHT_BG_G,
            MinimapLightThemeConverter.LIGHT_BG_B,
        )

        // Typical Yandex dark minimap land pixels, JPEG fluctuations, and dark route shadow outline
        val samples = listOf(
            packRgb(30, 38, 52),
            packRgb(27, 35, 48),
            packRgb(33, 41, 56),
            packRgb(14, 18, 24), // Dark route outer shadow (must not turn into dark pepper dots)
            packRgb(25, 33, 46), // Dark road direction arrow interior
        )

        for (sample in samples) {
            assertEquals(expected, MinimapLightThemeConverter.remapPixel(sample))
        }
    }

    @Test
    fun baseMapHierarchy_preservesSharpMonotonicContrastInLightTheme() {
        val land = MinimapLightThemeConverter.remapPixel(packRgb(30, 38, 52))
        val building = MinimapLightThemeConverter.remapPixel(packRgb(44, 54, 72))
        val minorRoad = MinimapLightThemeConverter.remapPixel(packRgb(62, 75, 98))
        val mainRoad = MinimapLightThemeConverter.remapPixel(packRgb(98, 116, 146))

        // In light mode: land (#F4F5F7) > buildings > minor streets > main road with strong contrast
        assertTrue(luma(land) - luma(building) >= 15)
        assertTrue(luma(building) - luma(minorRoad) >= 20)
        assertTrue(luma(minorRoad) - luma(mainRoad) >= 35)
        assertTrue("Main road should have crisp medium-slate contrast", luma(mainRoad) in 135..165)
    }

    @Test
    fun greenRouteYellowCursorAndRedJam_preserveDistinctHues() {
        val greenRoute = MinimapLightThemeConverter.remapPixel(packRgb(130, 235, 30))
        assertTrue("Green route stays green-dominant", unpackG(greenRoute) > unpackR(greenRoute) + 70)
        assertTrue("Green route keeps low blue", unpackB(greenRoute) < 40)

        val redJam = MinimapLightThemeConverter.remapPixel(packRgb(175, 42, 18))
        assertTrue("Red jam stays vivid red", unpackR(redJam) > 180 && unpackG(redJam) < 55 && unpackB(redJam) < 35)

        val yellowCursor = MinimapLightThemeConverter.remapPixel(packRgb(250, 208, 16))
        assertTrue("Yellow cursor stays warm yellow", unpackR(yellowCursor) > 220 && unpackG(yellowCursor) > 170 && unpackB(yellowCursor) < 30)
    }

    @Test
    fun darkBlueWater_remapsToPastelDaySkyBlue() {
        val river = MinimapLightThemeConverter.remapPixel(packRgb(4, 28, 136))
        assertTrue("Water becomes bright sky blue", unpackB(river) > 230 && unpackG(river) > 185 && unpackR(river) in 140..175)
    }

    @Test
    fun goldenCursorDarkEdge_remainsDarkCharcoal_whileGreenRouteDarkEdge_blendsSmoothly() {
        // Golden cursor anti-aliased against its black stroke (R > G > B, low B)
        val cursorEdge = MinimapLightThemeConverter.remapPixel(packRgb(48, 38, 6))
        assertTrue("Cursor border stays dark for crisp arrow outline", luma(cursorEdge) < 115)

        // Green route anti-aliased against dark shadow (G > R, low B) must NOT become dark speckle
        val greenDarkEdge = MinimapLightThemeConverter.remapPixel(packRgb(25, 48, 8))
        val pureGreen = MinimapLightThemeConverter.remapPixel(packRgb(130, 235, 30))
        assertTrue("Green route edge should blend cleanly toward light bg without dark dip", luma(greenDarkEdge) >= luma(pureGreen))
    }

    @Test
    fun antiAliasedRouteEdgeOverDarkLand_hasNoDarkHalo() {
        // 50% dark land (30, 38, 52) + 50% green route (130, 235, 30) = (80, 136, 41)
        val edge = MinimapLightThemeConverter.remapPixel(packRgb(80, 136, 41))
        val pureGreen = MinimapLightThemeConverter.remapPixel(packRgb(130, 235, 30))
        val lightLand = MinimapLightThemeConverter.remapPixel(packRgb(30, 38, 52))

        val minEndpointLuma = minOf(luma(pureGreen), luma(lightLand))
        assertTrue(
            "Anti-aliased edge luma (${luma(edge)}) should not dip below endpoints ($minEndpointLuma)",
            luma(edge) >= minEndpointLuma - 2,
        )
    }
}
