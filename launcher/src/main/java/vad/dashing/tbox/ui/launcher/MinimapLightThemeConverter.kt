package vad.dashing.tbox.ui.launcher

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Chroma-aware color converter that adapts Yandex Navigator's dark-only offscreen minimap
 * (`com.yandex.MINIMAP`) to seamlessly match the ADAS light theme background (`#F4F5F7`).
 *
 * Separates the desaturated cool-slate map base (land, buildings, minor/major roads, cursor)
 * from saturated semantic layers (green/yellow/red route & traffic jams, yellow cursor, blue water),
 * clamping JPEG background noise around `#1E2634` flat to `#F4F5F7` and cleanly unmixing anti-aliased edges.
 */
internal object MinimapLightThemeConverter {

    // Matches LauncherColors.LightPanelBg (0xFFF4F5F7)
    internal const val LIGHT_BG_R = 244
    internal const val LIGHT_BG_G = 245
    internal const val LIGHT_BG_B = 247

    // Day-mode pastel sky blue for rivers / ponds
    private const val WATER_R = 156
    private const val WATER_G = 200
    private const val WATER_B = 245

    /**
     * Precomputed 256-entry ARGB lookup table for neutral/cool-slate base-map pixels indexed by
     * Rec.709 luminance `0..255`.
     *
     * Key design points:
     * 1. Monotonic downward curve from `#F4F5F7` (no dark dip below luma 22): eliminates the
     *    broken "pepper/speckle" artifact along dark route shadows and JPEG ringing near the cursor.
     * 2. Stronger contrast ramp for buildings (`luma ~53 -> #DFE3EC`), minor streets (`luma ~75 -> #BAC4D4`),
     *    and main roads (`luma ~116 -> #8896AC`) so the map stays sharp even with 30% widget transparency.
     */
    private val BASE_PACKED_LUT = IntArray(256) { luma ->
        val r: Int
        val g: Int
        val b: Int
        when {
            // Dark map land (~luma 37), dark route drop-shadow (~luma 12..30), and dark road direction
            // arrows (~luma 28..36) -> flat #F4F5F7 shelf with zero dark speckle.
            luma <= 42 -> {
                r = LIGHT_BG_R
                g = LIGHT_BG_G
                b = LIGHT_BG_B
            }
            // Building footprints (~luma 53 -> #DFE3EC) up to building edges (luma 62 -> #D2D8E4)
            luma <= 62 -> {
                val t = (luma - 42) / 20f
                r = lerpInt(LIGHT_BG_R, 210, t)
                g = lerpInt(LIGHT_BG_G, 216, t)
                b = lerpInt(LIGHT_BG_B, 228, t)
            }
            // Minor streets (~luma 75 -> #B7C1D1) up to main arterial roads (~luma 116 -> #8896AC)
            luma <= 120 -> {
                val t = (luma - 62) / 58f
                r = lerpInt(210, 134, t)
                g = lerpInt(216, 148, t)
                b = lerpInt(228, 170, t)
            }
            // Bright road highlights / overpasses -> crisp slate without clipping
            else -> {
                val t = (luma - 120) / 135f
                r = lerpInt(134, 96, t)
                g = lerpInt(148, 108, t)
                b = lerpInt(170, 128, t)
            }
        }
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    internal fun remapPixelsInPlace(pixels: IntArray, count: Int = pixels.size) {
        val lut = BASE_PACKED_LUT
        val end = count.coerceAtMost(pixels.size)
        for (i in 0 until end) {
            val px = pixels[i]
            val a = px ushr 24
            if (a == 0) continue

            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF

            val luma = ((54 * r + 183 * g + 19 * b + 128) ushr 8)
            val maxRG = if (r > g) r else g
            val warmExcess = maxRG - b
            val blueExcess = b - g - 36

            // Fast path (~85-90% of pixels): land, buildings, streets, main roads.
            // Ignore tiny warm JPEG 4:2:0 chroma noise (warmExcess <= 10) so background stays clean.
            if (warmExcess <= 10 && blueExcess <= 0) {
                pixels[i] = (a shl 24) or (lut[luma] and 0x00FFFFFF)
                continue
            }

            pixels[i] = remapSemanticPixel(a, r, g, b, luma, warmExcess, blueExcess)
        }
    }

    @VisibleForTesting
    internal fun remapPixel(argb: Int): Int {
        val a = argb ushr 24
        if (a == 0) return argb
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val luma = ((54 * r + 183 * g + 19 * b + 128) ushr 8)
        val maxRG = if (r > g) r else g
        val warmExcess = maxRG - b
        val blueExcess = b - g - 36
        if (warmExcess <= 10 && blueExcess <= 0) {
            return (a shl 24) or (BASE_PACKED_LUT[luma] and 0x00FFFFFF)
        }
        return remapSemanticPixel(a, r, g, b, luma, warmExcess, blueExcess)
    }

    private fun remapSemanticPixel(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
        luma: Int,
        warmExcess: Int,
        blueExcess: Int,
    ): Int {
        // 1. Deep blue water (rivers, canals, ponds) -> soft day sky-blue (#9CC8F5)
        if (warmExcess <= 10) {
            val basePacked = BASE_PACKED_LUT[luma]
            val baseR = (basePacked ushr 16) and 0xFF
            val baseG = (basePacked ushr 8) and 0xFF
            val baseB = basePacked and 0xFF
            val alphaWater = (blueExcess / 68f).coerceIn(0f, 1f)
            val outR = lerpInt(baseR, WATER_R, alphaWater)
            val outG = lerpInt(baseG, WATER_G, alphaWater)
            val outB = lerpInt(baseB, WATER_B, alphaWater)
            return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        // 2. Yellow cursor arrow with pitch-black outline:
        // Keep the cursor outline crisp dark charcoal ONLY when the pixel clearly carries the cursor's
        // golden-yellow hue (R > G > B, low B, moderate-to-low luma), avoiding dark route shadows.
        val isGreenish = g >= r
        val isGoldenCursorBorder = !isGreenish &&
            b <= 26 &&
            luma in 14..135 &&
            warmExcess >= 16 &&
            g * 10 >= r * 6 &&
            r - g >= 6
        if (isGoldenCursorBorder) {
            val cursorT = ((luma - 14) / 121f).coerceIn(0f, 1f)
            val outR = lerpInt(62, 238, cursorT)
            val outG = lerpInt(68, 192, cursorT)
            val outB = lerpInt(78, 22, cursorT)
            return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        // 3. Route (green/yellow/red/maroon) & highway congestion lines:
        // Unmix any anti-aliased dark-map / dark-outline background contribution and composite over light map.
        val isYellowish = !isGreenish && (g * 2 > r + b)
        val warmSpan = when {
            isGreenish -> 180f
            isYellowish -> 195f
            else -> 120f // Red / maroon / orange traffic lines
        }
        val alphaRoute = ((warmExcess - 10) / warmSpan).coerceIn(0f, 1f)
        val mapWeight = 1f - alphaRoute

        val estDarkR: Float
        val estDarkG: Float
        val estDarkB: Float
        val lightMapR: Int
        val lightMapG: Int
        val lightMapB: Int

        if (mapWeight > 0.005f) {
            val denom = mapWeight.coerceAtLeast(0.15f)
            estDarkB = ((b - alphaRoute * 20f) / denom).coerceIn(36f, 175f)
            estDarkR = estDarkB * 0.62f
            estDarkG = estDarkB * 0.76f
            val estMapLuma = estDarkG.roundToInt().coerceIn(37, 160)
            val lightPacked = BASE_PACKED_LUT[estMapLuma]
            lightMapR = (lightPacked ushr 16) and 0xFF
            lightMapG = (lightPacked ushr 8) and 0xFF
            lightMapB = lightPacked and 0xFF
        } else {
            estDarkR = 0f
            estDarkG = 0f
            estDarkB = 0f
            lightMapR = LIGHT_BG_R
            lightMapG = LIGHT_BG_G
            lightMapB = LIGHT_BG_B
        }

        var fR = (r - mapWeight * estDarkR).coerceAtLeast(0f)
        var fG = (g - mapWeight * estDarkG).coerceAtLeast(0f)
        var fB = (b - mapWeight * estDarkB).coerceAtLeast(0f)

        // Contrast & saturation tuning so route and traffic lines look crisp on #F4F5F7
        when {
            isGreenish -> {
                fR *= 0.80f
                fG *= 0.86f
                fB *= 0.72f
            }
            isYellowish -> {
                fR *= 0.96f
                fG *= 0.90f
                fB *= 0.75f
            }
            else -> {
                // Keep red/orange traffic lines rich and saturated instead of washed-out salmon
                fR = (fR * 1.12f).coerceAtMost(235f * alphaRoute)
                fG *= 0.78f
                fB *= 0.72f
            }
        }

        val outR = (fR + mapWeight * lightMapR).roundToInt().coerceIn(0, 255)
        val outG = (fG + mapWeight * lightMapG).roundToInt().coerceIn(0, 255)
        val outB = (fB + mapWeight * lightMapB).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun lerpInt(start: Int, end: Int, fraction: Float): Int =
        (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
}

/**
 * Reusable double-buffered converter so background frame conversion never mutates a [Bitmap]
 * currently being read by Compose's RenderThread and avoids per-frame heap allocations.
 */
internal class MinimapLightThemeBuffer {
    private var pixelBuffer: IntArray = IntArray(0)
    private val slots = arrayOfNulls<Bitmap>(2)
    private var nextSlot = 0

    @Synchronized
    fun convert(source: Bitmap): ImageBitmap? {
        if (source.isRecycled) return null
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            source
        }
        val width = readable.width
        val height = readable.height
        if (width <= 0 || height <= 0) return null

        val pixelCount = width * height
        if (pixelBuffer.size < pixelCount) {
            pixelBuffer = IntArray(pixelCount)
        }
        val pixels = pixelBuffer
        try {
            readable.getPixels(pixels, 0, width, 0, 0, width, height)
        } catch (_: IllegalStateException) {
            return null
        }

        MinimapLightThemeConverter.remapPixelsInPlace(pixels, pixelCount)

        val slotIndex = nextSlot
        nextSlot = slotIndex xor 1
        var target = slots[slotIndex]
        if (target == null || target.isRecycled || target.width != width || target.height != height) {
            target = createBitmap(width, height)
            slots[slotIndex] = target
        }
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        target.prepareToDraw()
        return target.asImageBitmap()
    }
}
