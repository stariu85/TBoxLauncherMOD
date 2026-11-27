package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherWindowState
import vad.dashing.tbox.R
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "WallpaperAmbilight"
internal const val AMBILIGHT_WORKING_WIDTH = 48
internal const val AMBILIGHT_WORKING_HEIGHT = 32
internal const val AMBILIGHT_EDGE_SEGMENTS = 5
private const val EDGE_SEGMENTS = AMBILIGHT_EDGE_SEGMENTS
private const val BLUR_STRIP_DEPTH = 14
private const val BLUR_RADIUS = 4
private const val BLUR_PASSES = 3
private const val MAX_DECODE_DIM = 160
private const val COLOR_ANIMATION_MS = 320
private const val ALPHA_ANIMATION_MS = 360

internal enum class AmbilightEdge {
    TOP,
    BOTTOM,
    LEFT,
}

internal data class WallpaperCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal data class WallpaperAmbilightData(
    val topEdgeColors: List<Color>,
    val bottomEdgeColors: List<Color>,
    val leftEdgeColors: List<Color>,
    val leftAverageColor: Color,
    val leftHorizonColor: Color,
    val topBlurBitmap: ImageBitmap? = null,
    val bottomBlurBitmap: ImageBitmap? = null,
    val leftBlurBitmap: ImageBitmap? = null,
)

/**
 * Draw-phase-friendly Ambilight state holder. Created once via `remember { LauncherAmbilightUiState() }`
 * in `TeslaLauncherScreen` and mutated in place by [LauncherAmbilightController] + [AmbilightSyncedAnimator]
 * so neither live-wallpaper ticks nor 30 FPS color interpolation ever recompose the launcher tree.
 */
@Stable
internal class LauncherAmbilightUiState {
    internal val modeState: MutableState<WallpaperAmbilightMode> = mutableStateOf(WallpaperAmbilightMode.OFF)
    internal val isDarkThemeState: MutableState<Boolean> = mutableStateOf(true)
    internal val leftPanelFractionState: MutableFloatState = mutableFloatStateOf(0.30f)
    internal val alphaState: MutableFloatState = mutableFloatStateOf(0f)
    internal val topEdgeColorStates: List<MutableState<Color>> = List(EDGE_SEGMENTS) { mutableStateOf(Color.Transparent) }
    internal val bottomEdgeColorStates: List<MutableState<Color>> = List(EDGE_SEGMENTS) { mutableStateOf(Color.Transparent) }
    internal val leftEdgeColorStates: List<MutableState<Color>> = List(EDGE_SEGMENTS) { mutableStateOf(Color.Transparent) }
    internal val leftAverageColorState: MutableState<Color> = mutableStateOf(Color.Transparent)
    internal val leftHorizonColorState: MutableState<Color> = mutableStateOf(Color.Transparent)
    internal val topBlurBitmapState: MutableState<ImageBitmap?> = mutableStateOf(null)
    internal val bottomBlurBitmapState: MutableState<ImageBitmap?> = mutableStateOf(null)
    internal val leftBlurBitmapState: MutableState<ImageBitmap?> = mutableStateOf(null)
    internal val hasDataState: MutableState<Boolean> = mutableStateOf(false)

    val mode: WallpaperAmbilightMode get() = modeState.value
    val isDarkTheme: Boolean get() = isDarkThemeState.value
    val leftPanelFraction: Float get() = leftPanelFractionState.floatValue
    val alpha: Float get() = alphaState.floatValue

    val isVisuallyActive: Boolean
        get() = (modeState.value != WallpaperAmbilightMode.OFF) &&
            (alphaState.floatValue > 0.005f) &&
            hasDataState.value

    companion object {
        val Disabled = LauncherAmbilightUiState()
    }
}

internal val LocalLauncherAmbilightState = compositionLocalOf { LauncherAmbilightUiState.Disabled }

internal const val AMBILIGHT_MIN_PUBLISH_CHANNEL_DELTA = 4f / 255f

/**
 * Returns `true` if [next] differs from [previous] in any sampled edge/horizon color by at least
 * [minChannelDelta] (default ΔRGB >= 4/255), or if segment counts / blur strips changed.
 */
internal fun hasSignificantAmbilightColorChange(
    previous: WallpaperAmbilightData?,
    next: WallpaperAmbilightData,
    minChannelDelta: Float = AMBILIGHT_MIN_PUBLISH_CHANNEL_DELTA,
): Boolean {
    if (previous == null) return true
    if (previous.topBlurBitmap !== next.topBlurBitmap ||
        previous.bottomBlurBitmap !== next.bottomBlurBitmap ||
        previous.leftBlurBitmap !== next.leftBlurBitmap
    ) {
        return true
    }
    fun colorChanged(a: Color, b: Color): Boolean =
        max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) >= minChannelDelta

    fun listChanged(a: List<Color>, b: List<Color>): Boolean {
        if (a.size != b.size) return true
        for (i in a.indices) {
            if (colorChanged(a[i], b[i])) return true
        }
        return false
    }

    return listChanged(previous.topEdgeColors, next.topEdgeColors) ||
        listChanged(previous.bottomEdgeColors, next.bottomEdgeColors) ||
        listChanged(previous.leftEdgeColors, next.leftEdgeColors) ||
        colorChanged(previous.leftAverageColor, next.leftAverageColor) ||
        colorChanged(previous.leftHorizonColor, next.leftHorizonColor)
}

/**
 * Reactive bridge allowing live wallpapers (MP4/WebM video and animated GIF/WebP)
 * to push real-time sampled Ambilight frames into [LauncherAmbilightController].
 */
internal object LiveWallpaperAmbilightBridge {
    internal data class Entry(
        val sourceKey: String,
        val data: WallpaperAmbilightData,
    )

    private val _liveEntryFlow = MutableStateFlow<Entry?>(null)
    val liveEntryFlow: StateFlow<Entry?> = _liveEntryFlow

    fun buildSourceKey(
        uri: String,
        wallpaperRevision: Int,
        isDarkTheme: Boolean,
        mode: WallpaperAmbilightMode,
    ): String = "$uri|$wallpaperRevision|$isDarkTheme|${mode.code}"

    fun publish(sourceKey: String, data: WallpaperAmbilightData): Boolean {
        val current = _liveEntryFlow.value
        if (current != null &&
            current.sourceKey == sourceKey &&
            !hasSignificantAmbilightColorChange(current.data, data)
        ) {
            return false
        }
        _liveEntryFlow.value = Entry(sourceKey = sourceKey, data = data)
        return true
    }

    fun clear(expectedSourceKey: String? = null) {
        val current = _liveEntryFlow.value ?: return
        if (expectedSourceKey == null || current.sourceKey == expectedSourceKey) {
            _liveEntryFlow.value = null
        }
    }
}

/**
 * Pure predicate deciding whether wallpaper Ambilight should be illuminated.
 *
 * Ambilight is always suppressed whenever an external app is open in any mode
 * (embedded workspace, split-screen, full-width, or fullscreen), matching the active-app
 * recovery semantics of [shouldAnimateRightPanel].
 */
internal fun shouldShowWallpaperAmbilight(
    mode: WallpaperAmbilightMode,
    isActivityStarted: Boolean,
    hiddenForExternalApp: Boolean,
    activeAppPackage: String?,
    isActiveAppFullscreen: Boolean,
    isWindowFocused: Boolean,
): Boolean {
    if (mode == WallpaperAmbilightMode.OFF) return false
    if (!isActivityStarted) return false
    if (hiddenForExternalApp) return false
    if (!activeAppPackage.isNullOrBlank()) {
        val recoveredFromFullscreen = isActiveAppFullscreen && isWindowFocused
        if (!recoveredFromFullscreen) return false
    }
    return true
}

/**
 * Computes the pixel region of a wallpaper bitmap that is actually visible inside the right
 * panel viewport for the selected [WallpaperScaleMode].
 */
internal fun computeVisibleWallpaperCropRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    viewportAspect: Float,
    scaleMode: WallpaperScaleMode,
): WallpaperCropRect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0) {
        return WallpaperCropRect(0, 0, 1, 1)
    }
    val safeAspect = viewportAspect.takeIf { it.isFinite() && it > 0.1f } ?: (16f / 9f)
    val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()

    return when (scaleMode) {
        WallpaperScaleMode.FILL_BOUNDS -> WallpaperCropRect(0, 0, bitmapWidth, bitmapHeight)
        WallpaperScaleMode.FILL_HEIGHT -> {
            if (bitmapAspect > safeAspect) {
                val visibleW = (bitmapHeight * safeAspect).roundToInt().coerceIn(1, bitmapWidth)
                val left = ((bitmapWidth - visibleW) / 2).coerceIn(0, bitmapWidth - visibleW)
                WallpaperCropRect(left, 0, visibleW, bitmapHeight)
            } else {
                WallpaperCropRect(0, 0, bitmapWidth, bitmapHeight)
            }
        }
        WallpaperScaleMode.FILL_WIDTH -> {
            if (bitmapAspect < safeAspect) {
                val visibleH = (bitmapWidth / safeAspect).roundToInt().coerceIn(1, bitmapHeight)
                val top = ((bitmapHeight - visibleH) / 2).coerceIn(0, bitmapHeight - visibleH)
                WallpaperCropRect(0, top, bitmapWidth, visibleH)
            } else {
                WallpaperCropRect(0, 0, bitmapWidth, bitmapHeight)
            }
        }
    }
}

/**
 * Crops and resamples a source ARGB pixel grid to [dstWidth]x[dstHeight] according to [crop].
 */
internal fun cropAndResizePixels(
    srcPixels: IntArray,
    srcWidth: Int,
    srcHeight: Int,
    crop: WallpaperCropRect,
    dstWidth: Int = AMBILIGHT_WORKING_WIDTH,
    dstHeight: Int = AMBILIGHT_WORKING_HEIGHT,
): IntArray {
    if (srcPixels.isEmpty() || srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
        return IntArray(max(1, dstWidth * dstHeight))
    }
    val cLeft = crop.left.coerceIn(0, srcWidth - 1)
    val cTop = crop.top.coerceIn(0, srcHeight - 1)
    val cWidth = crop.width.coerceIn(1, srcWidth - cLeft)
    val cHeight = crop.height.coerceIn(1, srcHeight - cTop)
    val out = IntArray(dstWidth * dstHeight)
    for (y in 0 until dstHeight) {
        val sy = (cTop + ((y + 0.5f) * cHeight / dstHeight).toInt()).coerceIn(0, srcHeight - 1)
        val srcRow = sy * srcWidth
        val dstRow = y * dstWidth
        for (x in 0 until dstWidth) {
            val sx = (cLeft + ((x + 0.5f) * cWidth / dstWidth).toInt()).coerceIn(0, srcWidth - 1)
            out[dstRow + x] = srcPixels[srcRow + sx]
        }
    }
    return out
}

/**
 * Exponential moving average blend between previous and incoming pixel buffers for smooth
 * live-wallpaper Ambilight transitions without sudden flickers.
 */
internal fun blendPixelBuffers(
    previous: IntArray?,
    current: IntArray,
    blendFactor: Float = 0.45f,
): IntArray {
    if (previous == null || previous.size != current.size) {
        return current.copyOf()
    }
    val f = blendFactor.coerceIn(0.05f, 1f)
    val inv = 1f - f
    val out = IntArray(current.size)
    for (i in current.indices) {
        val p = previous[i]
        val c = current[i]
        val r = (((p ushr 16) and 0xFF) * inv + ((c ushr 16) and 0xFF) * f).roundToInt().coerceIn(0, 255)
        val g = (((p ushr 8) and 0xFF) * inv + ((c ushr 8) and 0xFF) * f).roundToInt().coerceIn(0, 255)
        val b = ((p and 0xFF) * inv + (c and 0xFF) * f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return out
}

/**
 * Returns true if the pixel grid contains non-black visible content (guards against
 * black intro frames or drivers where TextureView.getBitmap returns empty/zero buffers).
 */
internal fun hasVisibleFrameContent(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return false
    var litCount = 0
    val step = max(1, pixels.size / 128)
    var i = 0
    while (i < pixels.size) {
        val c = pixels[i]
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        if (max(r, max(g, b)) > 14) {
            litCount++
            if (litCount >= 3) return true
        }
        i += step
    }
    return false
}

internal fun computeAverageLuma(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0f
    var sum = 0.0
    for (c in pixels) {
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }
    return (sum / pixels.size.toDouble()).toFloat()
}

private fun rgbToHsl(argb: Int, outHsl: FloatArray) {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val l = (maxC + minC) * 0.5f

    val s = if (delta < 1e-5f) {
        0f
    } else {
        delta / (1f - abs(2f * l - 1f))
    }

    val h = when {
        delta < 1e-5f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    outHsl[0] = h
    outHsl[1] = s.coerceIn(0f, 1f)
    outHsl[2] = l.coerceIn(0f, 1f)
}

/**
 * Pure RGB -> HSL -> theme-safe RGB tone mapping:
 * - In dark theme: preserves true darkness for dark/shadow edge areas (so dark lower zones
 *   of a wallpaper naturally extend as dark regions onto the left and bottom panels) while
 *   keeping colorful lit wallpaper regions vivid and glowing (L up to 0.48).
 * - In light theme: maps genuinely colorful regions to vivid, clearly visible tints
 *   (L = 0.58..0.74) while mapping dark/achromatic pixels to clean light neutral (L = 0.89..0.94)
 *   so dark wallpaper regions never cause muddy darkening on light panels.
 */
internal fun tuneAmbilightColorForTheme(argb: Int, isDarkTheme: Boolean): Color {
    return Color(tuneAmbilightArgbForTheme(argb, isDarkTheme, isBlurStrip = false))
}

private fun tuneAmbilightArgbForTheme(
    argb: Int,
    isDarkTheme: Boolean,
    isBlurStrip: Boolean,
): Int {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val chroma = maxC - minC

    val hsl = FloatArray(3)
    rgbToHsl(argb, hsl)
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]

    val tunedS: Float
    val tunedL: Float
    if (isDarkTheme) {
        // Use perceptual chroma (maxC - minC) instead of raw HSL saturation so near-black
        // shadows with slight tint (e.g. #060C14) are NEVER inflated into bright colors.
        val chromaFactor = ((chroma - 0.03f) / 0.15f).coerceIn(0f, 1f)
        val baseL = if (l <= 0.14f) {
            // Keep black & deep shadow areas genuinely dark (0.035..0.125)
            0.035f + (l / 0.14f) * 0.09f
        } else {
            val t = ((l - 0.14f) / 0.52f).coerceIn(0f, 1f)
            0.125f + t * (if (isBlurStrip) 0.355f else 0.335f)
        }
        val neutralL = (0.035f + l * 0.205f).coerceIn(0.035f, 0.24f)
        val chromaLift = ((chroma - 0.16f).coerceAtLeast(0f) * 0.16f).coerceAtMost(0.07f)
        val colorfulL = (baseL + chromaLift).coerceIn(0.035f, if (isBlurStrip) 0.50f else 0.48f)

        tunedL = neutralL + (colorfulL - neutralL) * chromaFactor
        val colorfulS = (s * 1.28f).coerceIn(0.28f, 0.94f)
        val darkSatScale = ((l - 0.02f) / 0.14f).coerceIn(0.25f, 1f)
        tunedS = (colorfulS * chromaFactor * darkSatScale).coerceIn(0f, 0.94f)
    } else {
        val chromaFactor = ((chroma - 0.04f) / 0.14f).coerceIn(0f, 1f)
        val neutralL = (0.89f + l * 0.05f).coerceIn(0.89f, 0.94f)
        val colorfulL = if (isBlurStrip) {
            (0.58f + l * 0.18f).coerceIn(0.58f, 0.76f)
        } else {
            (0.58f + l * 0.16f).coerceIn(0.58f, 0.74f)
        }
        val colorfulS = if (isBlurStrip) {
            (s * 1.32f).coerceIn(0.42f, 0.92f)
        } else {
            (s * 1.32f).coerceIn(0.48f, 0.92f)
        }
        tunedL = neutralL + (colorfulL - neutralL) * chromaFactor
        tunedS = (colorfulS * chromaFactor).coerceIn(0f, 0.92f)
    }

    return hslToArgb(h, tunedS, tunedL)
}

/**
 * Tone-maps a blurred strip pixel buffer for the active theme so:
 * - Dark theme preserves dark/shadow areas accurately while keeping lit colorful shapes vivid.
 * - Light theme renders vibrant, clearly visible colored tints while NEVER darkening panels
 *   when the wallpaper contains dark or black regions.
 */
internal fun toneMapBlurredStripForThemeInPlace(
    pixels: IntArray,
    isDarkTheme: Boolean,
) {
    for (i in pixels.indices) {
        pixels[i] = tuneAmbilightArgbForTheme(pixels[i], isDarkTheme, isBlurStrip = true)
    }
}

private fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - c * 0.5f
    val r1: Float
    val g1: Float
    val b1: Float
    when {
        h < 60f -> {
            r1 = c; g1 = x; b1 = 0f
        }
        h < 120f -> {
            r1 = x; g1 = c; b1 = 0f
        }
        h < 180f -> {
            r1 = 0f; g1 = c; b1 = x
        }
        h < 240f -> {
            r1 = 0f; g1 = x; b1 = c
        }
        h < 300f -> {
            r1 = x; g1 = 0f; b1 = c
        }
        else -> {
            r1 = c; g1 = 0f; b1 = x
        }
    }
    val r = ((r1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
    val g = ((g1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
    val b = ((b1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Samples [segmentCount] localized edge colors from a normalized [width]x[height] ARGB grid.
 *
 * Strictly samples pixels right at each panel border so every segment reflects the true local
 * color (including dark/black border areas) adjacent to that panel without pulling colors from
 * deeper inside the frame.
 */
internal fun sampleEdgeSegmentColors(
    pixels: IntArray,
    width: Int,
    height: Int,
    edge: AmbilightEdge,
    segmentCount: Int = EDGE_SEGMENTS,
    isDarkTheme: Boolean,
): List<Color> {
    if (pixels.isEmpty() || width <= 0 || height <= 0 || segmentCount <= 0) {
        val fallback = if (isDarkTheme) Color(0xFF161D28) else Color(0xFFE5E7EB)
        return List(max(1, segmentCount)) { fallback }
    }

    val depthH = max(2, (height * 0.11f).roundToInt()).coerceAtMost(height)
    val depthW = max(2, (width * 0.12f).roundToInt()).coerceAtMost(width)

    return List(segmentCount) { seg ->
        val xStart: Int
        val xEnd: Int
        val yStart: Int
        val yEnd: Int
        when (edge) {
            AmbilightEdge.TOP -> {
                xStart = (seg * width) / segmentCount
                xEnd = max(xStart + 1, ((seg + 1) * width) / segmentCount)
                yStart = 0
                yEnd = depthH
            }
            AmbilightEdge.BOTTOM -> {
                xStart = (seg * width) / segmentCount
                xEnd = max(xStart + 1, ((seg + 1) * width) / segmentCount)
                yStart = max(0, height - depthH)
                yEnd = height
            }
            AmbilightEdge.LEFT -> {
                xStart = 0
                xEnd = depthW
                yStart = (seg * height) / segmentCount
                yEnd = max(yStart + 1, ((seg + 1) * height) / segmentCount)
            }
        }

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var totalWeight = 0.0
        val ySpan = max(1, yEnd - yStart).toDouble()
        val xSpan = max(1, xEnd - xStart).toDouble()
        for (y in yStart until yEnd) {
            val rowOffset = y * width
            for (x in xStart until xEnd) {
                val argb = pixels[rowOffset + x]
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))
                val chroma = (maxCh - minCh) / 255.0

                val distNorm = when (edge) {
                    AmbilightEdge.TOP -> (y - yStart).toDouble() / ySpan
                    AmbilightEdge.BOTTOM -> (yEnd - 1 - y).toDouble() / ySpan
                    AmbilightEdge.LEFT -> (x - xStart).toDouble() / xSpan
                }.coerceIn(0.0, 1.0)
                val invDist = 1.0 - distNorm
                val proximity = 0.10 + 0.90 * invDist * invDist
                val colorWeight = 0.80 + chroma * 0.50
                val weight = proximity * colorWeight
                sumR += r * weight
                sumG += g * weight
                sumB += b * weight
                totalWeight += weight
            }
        }

        val edgeArgb = if (totalWeight > 0.0) {
            val r = (sumR / totalWeight).roundToInt().coerceIn(0, 255)
            val g = (sumG / totalWeight).roundToInt().coerceIn(0, 255)
            val b = (sumB / totalWeight).roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else {
            0xFF000000.toInt()
        }
        tuneAmbilightColorForTheme(edgeArgb, isDarkTheme)
    }
}

/**
 * Fast separable sliding-window box blur in pure Kotlin (3 passes approximate Gaussian blur).
 * Safe and blazingly fast on Android 9 without requiring API 31 RenderEffect.
 */
internal fun boxBlurArgbInPlace(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int = BLUR_RADIUS,
    passes: Int = BLUR_PASSES,
) {
    if (pixels.size < width * height || width <= 1 || height <= 1 || radius <= 0 || passes <= 0) return
    val tmp = IntArray(width * height)
    val kernelSize = radius * 2 + 1

    repeat(passes) {
        for (y in 0 until height) {
            val row = y * width
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -radius..radius) {
                val sx = k.coerceIn(0, width - 1)
                val c = pixels[row + sx]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (x in 0 until width) {
                tmp[row + x] = (0xFF shl 24) or
                    ((rSum / kernelSize) shl 16) or
                    ((gSum / kernelSize) shl 8) or
                    (bSum / kernelSize)
                val removeIdx = (x - radius).coerceIn(0, width - 1)
                val addIdx = (x + radius + 1).coerceIn(0, width - 1)
                val oldC = pixels[row + removeIdx]
                val newC = pixels[row + addIdx]
                rSum += ((newC ushr 16) and 0xFF) - ((oldC ushr 16) and 0xFF)
                gSum += ((newC ushr 8) and 0xFF) - ((oldC ushr 8) and 0xFF)
                bSum += (newC and 0xFF) - (oldC and 0xFF)
            }
        }

        for (x in 0 until width) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -radius..radius) {
                val sy = k.coerceIn(0, height - 1)
                val c = tmp[sy * width + x]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (y in 0 until height) {
                pixels[y * width + x] = (0xFF shl 24) or
                    ((rSum / kernelSize) shl 16) or
                    ((gSum / kernelSize) shl 8) or
                    (bSum / kernelSize)
                val removeIdx = (y - radius).coerceIn(0, height - 1)
                val addIdx = (y + radius + 1).coerceIn(0, height - 1)
                val oldC = tmp[removeIdx * width + x]
                val newC = tmp[addIdx * width + x]
                rSum += ((newC ushr 16) and 0xFF) - ((oldC ushr 16) and 0xFF)
                gSum += ((newC ushr 8) and 0xFF) - ((oldC ushr 8) and 0xFF)
                bSum += (newC and 0xFF) - (oldC and 0xFF)
            }
        }
    }
}

/**
 * Creates a mirrored blurred pixel strip for a panel so the seam touching the wallpaper
 * matches pixel-for-pixel and radiates outward naturally.
 */
internal fun createMirroredEdgeStripArgb(
    srcPixels: IntArray,
    srcWidth: Int,
    srcHeight: Int,
    edge: AmbilightEdge,
    stripDepth: Int = BLUR_STRIP_DEPTH,
    leftPanelFraction: Float = 0.30f,
    isDarkTheme: Boolean? = null,
): Triple<IntArray, Int, Int> {
    if (srcPixels.isEmpty() || srcWidth <= 0 || srcHeight <= 0) {
        return Triple(IntArray(1) { 0xFF161D28.toInt() }, 1, 1)
    }
    val safeLeftFrac = leftPanelFraction.coerceIn(0f, 0.55f)
    val clampedDepth = stripDepth.coerceAtLeast(2)

    val result = when (edge) {
        AmbilightEdge.TOP, AmbilightEdge.BOTTOM -> {
            val leftExtraCols = if (safeLeftFrac > 0.01f) {
                ((srcWidth * safeLeftFrac) / (1f - safeLeftFrac)).roundToInt().coerceIn(1, srcWidth)
            } else {
                0
            }
            val outW = leftExtraCols + srcWidth
            val outH = min(clampedDepth, srcHeight)
            val out = IntArray(outW * outH)

            for (y in 0 until outH) {
                val srcY = if (edge == AmbilightEdge.TOP) {
                    (outH - 1 - y).coerceIn(0, srcHeight - 1)
                } else {
                    (srcHeight - 1 - y).coerceIn(0, srcHeight - 1)
                }
                val dstRow = y * outW
                val srcRow = srcY * srcWidth
                for (x in 0 until outW) {
                    val srcX = if (x < leftExtraCols) {
                        (leftExtraCols - 1 - x).coerceIn(0, srcWidth - 1)
                    } else {
                        (x - leftExtraCols).coerceIn(0, srcWidth - 1)
                    }
                    out[dstRow + x] = srcPixels[srcRow + srcX]
                }
            }
            boxBlurArgbInPlace(out, outW, outH, radius = BLUR_RADIUS, passes = BLUR_PASSES)
            Triple(out, outW, outH)
        }
        AmbilightEdge.LEFT -> {
            val outW = min(clampedDepth, srcWidth)
            val out = IntArray(outW * srcHeight)
            for (y in 0 until srcHeight) {
                val srcRow = y * srcWidth
                val dstRow = y * outW
                for (x in 0 until outW) {
                    val srcX = (outW - 1 - x).coerceIn(0, srcWidth - 1)
                    out[dstRow + x] = srcPixels[srcRow + srcX]
                }
            }
            boxBlurArgbInPlace(out, outW, srcHeight, radius = BLUR_RADIUS, passes = BLUR_PASSES)
            Triple(out, outW, srcHeight)
        }
    }
    if (isDarkTheme != null) {
        toneMapBlurredStripForThemeInPlace(result.first, isDarkTheme)
    }
    return result
}

private fun averageColors(colors: List<Color>, fallback: Color): Color {
    if (colors.isEmpty()) return fallback
    var r = 0f
    var g = 0f
    var b = 0f
    for (c in colors) {
        r += c.red
        g += c.green
        b += c.blue
    }
    val inv = 1f / colors.size.toFloat()
    return Color(r * inv, g * inv, b * inv, 1f)
}

internal fun computeWallpaperAmbilightDataFromPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    isDarkTheme: Boolean,
    leftPanelFraction: Float,
    mode: WallpaperAmbilightMode,
): WallpaperAmbilightData {
    val topColors = sampleEdgeSegmentColors(pixels, width, height, AmbilightEdge.TOP, EDGE_SEGMENTS, isDarkTheme)
    val bottomColors = sampleEdgeSegmentColors(pixels, width, height, AmbilightEdge.BOTTOM, EDGE_SEGMENTS, isDarkTheme)
    val leftColors = sampleEdgeSegmentColors(pixels, width, height, AmbilightEdge.LEFT, EDGE_SEGMENTS, isDarkTheme)
    val fallbackColor = if (isDarkTheme) Color(0xFF161D28) else Color(0xFFE5E7EB)
    val leftAvg = averageColors(leftColors, fallbackColor)
    val leftHorizon = if (leftColors.size >= 2) {
        lerp(leftColors[0], leftColors[1], 0.45f)
    } else {
        leftAvg
    }

    val topBlur: ImageBitmap?
    val bottomBlur: ImageBitmap?
    val leftBlur: ImageBitmap?
    if (mode == WallpaperAmbilightMode.BLURRED_WALLPAPER) {
        val (topPx, topW, topH) = createMirroredEdgeStripArgb(
            srcPixels = pixels,
            srcWidth = width,
            srcHeight = height,
            edge = AmbilightEdge.TOP,
            stripDepth = BLUR_STRIP_DEPTH,
            leftPanelFraction = leftPanelFraction,
            isDarkTheme = isDarkTheme,
        )
        val (botPx, botW, botH) = createMirroredEdgeStripArgb(
            srcPixels = pixels,
            srcWidth = width,
            srcHeight = height,
            edge = AmbilightEdge.BOTTOM,
            stripDepth = BLUR_STRIP_DEPTH,
            leftPanelFraction = leftPanelFraction,
            isDarkTheme = isDarkTheme,
        )
        val (leftPx, leftW, leftH) = createMirroredEdgeStripArgb(
            srcPixels = pixels,
            srcWidth = width,
            srcHeight = height,
            edge = AmbilightEdge.LEFT,
            stripDepth = BLUR_STRIP_DEPTH,
            leftPanelFraction = leftPanelFraction,
            isDarkTheme = isDarkTheme,
        )
        topBlur = Bitmap.createBitmap(topPx, topW, topH, Bitmap.Config.ARGB_8888).asImageBitmap()
        bottomBlur = Bitmap.createBitmap(botPx, botW, botH, Bitmap.Config.ARGB_8888).asImageBitmap()
        leftBlur = Bitmap.createBitmap(leftPx, leftW, leftH, Bitmap.Config.ARGB_8888).asImageBitmap()
    } else {
        topBlur = null
        bottomBlur = null
        leftBlur = null
    }

    return WallpaperAmbilightData(
        topEdgeColors = topColors,
        bottomEdgeColors = bottomColors,
        leftEdgeColors = leftColors,
        leftAverageColor = leftAvg,
        leftHorizonColor = leftHorizon,
        topBlurBitmap = topBlur,
        bottomBlurBitmap = bottomBlur,
        leftBlurBitmap = leftBlur,
    )
}

internal object WallpaperAmbilightCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<String, WallpaperAmbilightData>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, WallpaperAmbilightData>?,
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): WallpaperAmbilightData? = cache[key]

    @Synchronized
    fun put(key: String, data: WallpaperAmbilightData) {
        cache[key] = data
    }

    @Synchronized
    fun invalidatePath(path: String) {
        if (path.isBlank()) return
        val prefix = "$path|"
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().key.startsWith(prefix)) {
                it.remove()
            }
        }
    }
}

private fun bitmapAverageLuma(bitmap: Bitmap): Float {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return 0f
    val small = Bitmap.createScaledBitmap(bitmap, 16, 12, true)
    val pixels = IntArray(16 * 12)
    small.getPixels(pixels, 0, 16, 0, 0, 16, 12)
    if (small !== bitmap) small.recycle()
    return computeAverageLuma(pixels)
}

private fun decodeSmallWallpaperSourceBitmap(
    context: Context,
    activeWallpaperUri: String,
    isDarkTheme: Boolean,
): Bitmap? {
    if (activeWallpaperUri.isBlank()) {
        val defaultRes = if (isDarkTheme) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return decodeResourceSmall(context, defaultRes)
    }

    val file = File(activeWallpaperUri)
    if (!file.isFile || file.length() <= 0L) {
        val defaultRes = if (isDarkTheme) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return decodeResourceSmall(context, defaultRes)
    }

    val mediaType = detectWallpaperMediaTypeFromPath(activeWallpaperUri)
    if (mediaType != WallpaperMediaType.STATIC_IMAGE) {
        // Live wallpapers (video and animated GIF/WebP) capture their ~5th frame directly from
        // their active player/drawable and cache it in WallpaperAmbilightCache. Skipping eager
        // MediaMetadataRetriever/frame-0 decoding avoids 4K codec failures, startup contention,
        // and black intro-frame overwrites.
        return null
    }

    val decoded = decodeFileSmall(file) ?: decodeWithImageDecoderSmall(file)
    return decoded ?: decodeResourceSmall(
        context,
        if (isDarkTheme) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light,
    )
}

private fun decodeResourceSmall(context: Context, resId: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeResource(context.resources, resId, opts)
    }.getOrNull()
}

private fun decodeFileSmall(file: File): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()
}

private fun decodeWithImageDecoderSmall(file: File): Bitmap? {
    return runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            if (w > MAX_DECODE_DIM || h > MAX_DECODE_DIM) {
                val scale = min(MAX_DECODE_DIM.toFloat() / w, MAX_DECODE_DIM.toFloat() / h)
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()
}

/**
 * Extracts a single lightweight representative frame for video wallpapers — targeting the ~5th frame
 * (~180 ms at 25–30 FPS) first for instant, zero-overhead static Ambilight, with automatic fallback
 * deeper into the clip only if the intro is near-black.
 *
 * Uses [MediaMetadataRetriever.getScaledFrameAtTime] (API 27+) to avoid allocating full 4K frames
 * and aborts immediately if the first probe cannot be decoded by any Stagefright codec.
 */
internal fun extractSmallVideoFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
        val fifthFrameUs = if (durationMs > 220L) 180_000L else (durationMs * 500L).coerceAtLeast(0L)
        val probeTimesUs = buildList {
            add(fifthFrameUs)
            if (durationMs > 1200L) {
                add((durationMs / 4L) * 1000L)
                add(min(1500L, durationMs - 200L) * 1000L)
                add((durationMs / 2L) * 1000L)
            } else if (durationMs > 200L) {
                add((durationMs / 2L) * 1000L)
            }
            add(0L)
        }.distinct()

        fun decodeFrame(timeUs: Long, option: Int): Bitmap? {
            return runCatching {
                retriever.getScaledFrameAtTime(timeUs, option, MAX_DECODE_DIM, MAX_DECODE_DIM)
            }.getOrNull() ?: retriever.getFrameAtTime(timeUs, option)
        }

        var bestBitmap: Bitmap? = null
        var bestLuma = -1f
        for ((index, timeUs) in probeTimesUs.withIndex()) {
            val raw = if (index == 0) {
                decodeFrame(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: decodeFrame(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                decodeFrame(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (raw == null) {
                // If the very first probe fails both CLOSEST and CLOSEST_SYNC, the codec cannot
                // decode this stream via MediaMetadataRetriever — fail fast instead of burning 5+ s.
                if (index == 0) return null
                continue
            }
            val scaled = scaleBitmapIfNeeded(raw, MAX_DECODE_DIM)
            val luma = bitmapAverageLuma(scaled)
            if (luma > bestLuma) {
                bestBitmap?.recycle()
                bestBitmap = scaled
                bestLuma = luma
            } else {
                scaled.recycle()
            }
            if (bestLuma >= 0.06f) break
        }
        bestBitmap
    } catch (e: Exception) {
        Log.w(TAG, "Failed to extract video frame for ambilight: ${file.name}", e)
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Fallback timeline extractor for Android 9 video drivers where [android.view.TextureView.getBitmap]
 * returns blank/black frames for hardware-decoded video surfaces.
 */
internal fun extractVideoAmbilightTimeline(
    file: File,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    viewportAspect: Float,
    leftPanelFraction: Float,
    mode: WallpaperAmbilightMode,
    sampleCount: Int = 10,
): List<Pair<Long, WallpaperAmbilightData>> {
    if (!file.isFile || file.length() <= 0L) return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
        if (durationMs <= 0L) return emptyList()

        val count = sampleCount.coerceIn(2, 16)
        val results = mutableListOf<Pair<Long, WallpaperAmbilightData>>()
        var prevPixels: IntArray? = null
        for (i in 0 until count) {
            val posMs = (durationMs * i) / count
            val raw = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: continue
            val scaled = scaleBitmapIfNeeded(raw, MAX_DECODE_DIM)
            try {
                val (cLeft, cTop, cWidth, cHeight) = computeVisibleWallpaperCropRect(
                    bitmapWidth = scaled.width,
                    bitmapHeight = scaled.height,
                    viewportAspect = viewportAspect,
                    scaleMode = scaleMode,
                )
                val cropped = Bitmap.createBitmap(scaled, cLeft, cTop, cWidth, cHeight)
                val working = Bitmap.createScaledBitmap(cropped, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT, true)
                if (cropped !== scaled && cropped !== working) cropped.recycle()

                val rawPixels = IntArray(AMBILIGHT_WORKING_WIDTH * AMBILIGHT_WORKING_HEIGHT)
                working.getPixels(rawPixels, 0, AMBILIGHT_WORKING_WIDTH, 0, 0, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT)
                if (working !== scaled) working.recycle()

                if (!hasVisibleFrameContent(rawPixels)) continue
                val framePixels = if (mode == WallpaperAmbilightMode.BLURRED_WALLPAPER) {
                    blendPixelBuffers(prevPixels, rawPixels, blendFactor = 0.65f).also { prevPixels = it }
                } else {
                    rawPixels
                }
                val data = computeWallpaperAmbilightDataFromPixels(
                    pixels = framePixels,
                    width = AMBILIGHT_WORKING_WIDTH,
                    height = AMBILIGHT_WORKING_HEIGHT,
                    isDarkTheme = isDarkTheme,
                    leftPanelFraction = leftPanelFraction,
                    mode = mode,
                )
                results.add(posMs to data)
            } finally {
                if (!scaled.isRecycled) scaled.recycle()
            }
        }
        results
    } catch (e: Exception) {
        Log.w(TAG, "Failed to extract video ambilight timeline for ${file.name}", e)
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

private fun computeInSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= MAX_DECODE_DIM && height / (sample * 2) >= MAX_DECODE_DIM) {
        sample *= 2
    }
    return sample
}

private fun scaleBitmapIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
    if (src.width <= maxDim && src.height <= maxDim && src.config == Bitmap.Config.ARGB_8888) {
        return src
    }
    val scale = min(1f, min(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height))
    val targetW = (src.width * scale).roundToInt().coerceAtLeast(1)
    val targetH = (src.height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
    if (scaled !== src) {
        src.recycle()
    }
    return if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        val converted = scaled.copy(Bitmap.Config.ARGB_8888, false)
        if (converted != null) {
            if (converted !== scaled) scaled.recycle()
            converted
        } else {
            scaled
        }
    }
}

internal fun buildWallpaperAmbilightCacheKey(
    activeWallpaperUri: String,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    viewportAspect: Float,
    leftPanelFraction: Float,
    mode: WallpaperAmbilightMode,
): String {
    val fileStamp = if (activeWallpaperUri.isBlank()) {
        "default"
    } else {
        val f = File(activeWallpaperUri)
        if (f.isFile) "${f.lastModified()}_${f.length()}" else "missing"
    }
    val aspectBucket = (viewportAspect.coerceIn(0.5f, 4.0f) * 10f).roundToInt()
    val leftBucket = if (mode == WallpaperAmbilightMode.BLURRED_WALLPAPER) {
        (leftPanelFraction.coerceIn(0f, 0.55f) * 20f).roundToInt()
    } else {
        0
    }
    return "$activeWallpaperUri|$fileStamp|$isDarkTheme|${scaleMode.code}|$aspectBucket|$leftBucket|${mode.code}"
}

private fun computeWallpaperAmbilightData(
    context: Context,
    activeWallpaperUri: String,
    isDarkTheme: Boolean,
    scaleMode: WallpaperScaleMode,
    viewportAspect: Float,
    leftPanelFraction: Float,
    mode: WallpaperAmbilightMode,
): WallpaperAmbilightData? {
    val cacheKey = buildWallpaperAmbilightCacheKey(
        activeWallpaperUri = activeWallpaperUri,
        isDarkTheme = isDarkTheme,
        scaleMode = scaleMode,
        viewportAspect = viewportAspect,
        leftPanelFraction = leftPanelFraction,
        mode = mode,
    )
    WallpaperAmbilightCache.get(cacheKey)?.let { return it }

    val sourceBitmap = decodeSmallWallpaperSourceBitmap(context, activeWallpaperUri, isDarkTheme) ?: return null
    return try {
        val (cLeft, cTop, cWidth, cHeight) = computeVisibleWallpaperCropRect(
            bitmapWidth = sourceBitmap.width,
            bitmapHeight = sourceBitmap.height,
            viewportAspect = viewportAspect,
            scaleMode = scaleMode,
        )
        val cropped = Bitmap.createBitmap(sourceBitmap, cLeft, cTop, cWidth, cHeight)
        val working = Bitmap.createScaledBitmap(cropped, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT, true)
        if (cropped !== sourceBitmap && cropped !== working) cropped.recycle()

        val pixels = IntArray(AMBILIGHT_WORKING_WIDTH * AMBILIGHT_WORKING_HEIGHT)
        working.getPixels(pixels, 0, AMBILIGHT_WORKING_WIDTH, 0, 0, AMBILIGHT_WORKING_WIDTH, AMBILIGHT_WORKING_HEIGHT)
        if (working !== sourceBitmap) working.recycle()

        computeWallpaperAmbilightDataFromPixels(
            pixels = pixels,
            width = AMBILIGHT_WORKING_WIDTH,
            height = AMBILIGHT_WORKING_HEIGHT,
            isDarkTheme = isDarkTheme,
            leftPanelFraction = leftPanelFraction,
            mode = mode,
        ).also {
            WallpaperAmbilightCache.put(cacheKey, it)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to compute wallpaper ambilight", e)
        null
    } finally {
        if (!sourceBitmap.isRecycled) {
            sourceBitmap.recycle()
        }
    }
}

/**
 * Single-listener 30 FPS tween driver for all Ambilight edge colors and visibility alpha,
 * backed by the shared [LauncherSynced30FpsClock].
 *
 * Replaces 18 independent 60 FPS Compose `animateColorAsState` / `animateFloatAsState`
 * instances with one allocation-free tick handler that automatically unregisters when settled
 * (0 FPS idle overhead).
 */
internal class AmbilightSyncedAnimator(
    private val uiState: LauncherAmbilightUiState,
    private val easing: Easing = FastOutSlowInEasing,
) {
    private val colorTargets: List<MutableState<Color>> = buildList(TOTAL_COLOR_SLOTS) {
        addAll(uiState.topEdgeColorStates)
        addAll(uiState.bottomEdgeColorStates)
        addAll(uiState.leftEdgeColorStates)
        add(uiState.leftAverageColorState)
        add(uiState.leftHorizonColorState)
    }

    private val startR = FloatArray(TOTAL_COLOR_SLOTS)
    private val startG = FloatArray(TOTAL_COLOR_SLOTS)
    private val startB = FloatArray(TOTAL_COLOR_SLOTS)
    private val endR = FloatArray(TOTAL_COLOR_SLOTS)
    private val endG = FloatArray(TOTAL_COLOR_SLOTS)
    private val endB = FloatArray(TOTAL_COLOR_SLOTS)
    private val curR = FloatArray(TOTAL_COLOR_SLOTS)
    private val curG = FloatArray(TOTAL_COLOR_SLOTS)
    private val curB = FloatArray(TOTAL_COLOR_SLOTS)
    private val elapsedNs = LongArray(TOTAL_COLOR_SLOTS)
    private val durationNs = LongArray(TOTAL_COLOR_SLOTS)
    private val finished = BooleanArray(TOTAL_COLOR_SLOTS) { true }

    private val alphaDriver = SyncedFloatTweenDriver(0f)
    private var hasInitializedColors = false
    private var shouldShow = false
    private var attachedClock: LauncherSynced30FpsClock? = null
    private var unregisterClock: (() -> Unit)? = null

    val isAnimating: Boolean
        get() = !alphaDriver.isFinished || finished.any { !it }

    fun attach(clock: LauncherSynced30FpsClock) {
        if (attachedClock === clock) {
            ensureClockListening()
            return
        }
        detach()
        attachedClock = clock
        ensureClockListening()
    }

    fun detach() {
        stopClockListening()
        attachedClock = null
    }

    fun setShouldShow(show: Boolean, durationMillis: Int = ALPHA_ANIMATION_MS) {
        shouldShow = show
        recomputeAlphaTarget(durationMillis)
    }

    fun applyTargetData(
        data: WallpaperAmbilightData?,
        fallbackColor: Color,
        durationMillis: Int = COLOR_ANIMATION_MS,
    ) {
        uiState.topBlurBitmapState.value = data?.topBlurBitmap
        uiState.bottomBlurBitmapState.value = data?.bottomBlurBitmap
        uiState.leftBlurBitmapState.value = data?.leftBlurBitmap
        uiState.hasDataState.value = data != null

        if (data != null) {
            val snap = !hasInitializedColors || durationMillis <= 0
            val durNs = durationMillis.toLong() * 1_000_000L
            for (i in 0 until EDGE_SEGMENTS) {
                val topColor = data.topEdgeColors.getOrElse(i) { data.topEdgeColors.lastOrNull() ?: fallbackColor }
                retargetSlot(i, topColor, durNs, snap)

                val botColor = data.bottomEdgeColors.getOrElse(i) { data.bottomEdgeColors.lastOrNull() ?: fallbackColor }
                retargetSlot(EDGE_SEGMENTS + i, botColor, durNs, snap)

                val leftColor = data.leftEdgeColors.getOrElse(i) { data.leftEdgeColors.lastOrNull() ?: fallbackColor }
                retargetSlot(EDGE_SEGMENTS * 2 + i, leftColor, durNs, snap)
            }
            retargetSlot(EDGE_SEGMENTS * 3, data.leftAverageColor, durNs, snap)
            retargetSlot(EDGE_SEGMENTS * 3 + 1, data.leftHorizonColor, durNs, snap)
            hasInitializedColors = true
        }

        recomputeAlphaTarget(ALPHA_ANIMATION_MS)
        ensureClockListening()
    }

    fun resetForOff() {
        stopClockListening()
        shouldShow = false
        hasInitializedColors = false
        alphaDriver.retarget(0f, durationMillis = 0, newEasing = easing)
        uiState.alphaState.floatValue = 0f
        uiState.hasDataState.value = false
        uiState.topBlurBitmapState.value = null
        uiState.bottomBlurBitmapState.value = null
        uiState.leftBlurBitmapState.value = null
        for (i in 0 until TOTAL_COLOR_SLOTS) {
            finished[i] = true
        }
    }

    internal fun step(deltaNs: Long): Boolean {
        val clampedDeltaNs = deltaNs.coerceIn(0L, MAX_STEP_NS)
        var anyActive = false

        if (!alphaDriver.isFinished) {
            val nextAlpha = alphaDriver.step(clampedDeltaNs)
            uiState.alphaState.floatValue = nextAlpha
            if (!alphaDriver.isFinished) {
                anyActive = true
            }
        }

        for (i in 0 until TOTAL_COLOR_SLOTS) {
            if (finished[i]) continue
            val dur = durationNs[i]
            if (dur <= 0L) {
                curR[i] = endR[i]
                curG[i] = endG[i]
                curB[i] = endB[i]
                finished[i] = true
            } else {
                val nextElapsed = elapsedNs[i] + clampedDeltaNs
                elapsedNs[i] = nextElapsed
                val fraction = (nextElapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                if (fraction >= 1f) {
                    curR[i] = endR[i]
                    curG[i] = endG[i]
                    curB[i] = endB[i]
                    finished[i] = true
                } else {
                    val eased = easing.transform(fraction)
                    curR[i] = startR[i] + (endR[i] - startR[i]) * eased
                    curG[i] = startG[i] + (endG[i] - startG[i]) * eased
                    curB[i] = startB[i] + (endB[i] - startB[i]) * eased
                    anyActive = true
                }
            }
            colorTargets[i].value = Color(
                red = curR[i].coerceIn(0f, 1f),
                green = curG[i].coerceIn(0f, 1f),
                blue = curB[i].coerceIn(0f, 1f),
                alpha = 1f,
            )
        }

        return anyActive
    }

    private fun retargetSlot(slot: Int, target: Color, durNs: Long, snap: Boolean) {
        val tr = target.red
        val tg = target.green
        val tb = target.blue
        if (snap) {
            startR[slot] = tr; startG[slot] = tg; startB[slot] = tb
            endR[slot] = tr; endG[slot] = tg; endB[slot] = tb
            curR[slot] = tr; curG[slot] = tg; curB[slot] = tb
            elapsedNs[slot] = 0L
            durationNs[slot] = 0L
            finished[slot] = true
            colorTargets[slot].value = Color(tr, tg, tb, 1f)
            return
        }
        val diffToEnd = max(abs(tr - endR[slot]), max(abs(tg - endG[slot]), abs(tb - endB[slot])))
        if (diffToEnd < COLOR_EPSILON && (!finished[slot] || max(abs(tr - curR[slot]), max(abs(tg - curG[slot]), abs(tb - curB[slot]))) < COLOR_EPSILON)) {
            return
        }
        val diffToCur = max(abs(tr - curR[slot]), max(abs(tg - curG[slot]), abs(tb - curB[slot])))
        if (diffToCur < COLOR_EPSILON) {
            endR[slot] = tr; endG[slot] = tg; endB[slot] = tb
            curR[slot] = tr; curG[slot] = tg; curB[slot] = tb
            finished[slot] = true
            colorTargets[slot].value = Color(tr, tg, tb, 1f)
            return
        }
        startR[slot] = curR[slot]; startG[slot] = curG[slot]; startB[slot] = curB[slot]
        endR[slot] = tr; endG[slot] = tg; endB[slot] = tb
        elapsedNs[slot] = 0L
        durationNs[slot] = durNs
        finished[slot] = false
    }

    private fun recomputeAlphaTarget(durationMillis: Int) {
        val targetAlpha = if (shouldShow && uiState.hasDataState.value) 1f else 0f
        val active = alphaDriver.retarget(targetAlpha, durationMillis, easing)
        if (!active && uiState.alphaState.floatValue != alphaDriver.currentValue) {
            uiState.alphaState.floatValue = alphaDriver.currentValue
        }
        ensureClockListening()
    }

    private fun ensureClockListening() {
        val clock = attachedClock ?: return
        if (!isAnimating) {
            stopClockListening()
            return
        }
        if (unregisterClock != null) return
        unregisterClock = clock.registerListener(tickDivisor = 1) { _, deltaNs ->
            val stillAnimating = step(deltaNs)
            if (!stillAnimating) {
                stopClockListening()
            }
        }
    }

    private fun stopClockListening() {
        unregisterClock?.invoke()
        unregisterClock = null
    }

    companion object {
        private const val TOTAL_COLOR_SLOTS = EDGE_SEGMENTS * 3 + 2
        private const val COLOR_EPSILON = 0.006f
        private const val MAX_STEP_NS = 100_000_000L
    }
}

/**
 * Isolated child Composable hosting all Ambilight lifecycle/config flow collections and
 * live-wallpaper bridge updates. Keeping this in a separate leaf Composable guarantees that
 * `TeslaLauncherScreen` and its panels NEVER recompose on live wallpaper frames or app-focus
 * transitions, while color/alpha interpolation runs cleanly on the shared [syncedClock].
 */
@Composable
internal fun LauncherAmbilightController(
    state: LauncherAmbilightUiState,
    syncedClock: LauncherSynced30FpsClock,
    adasPanelVisible: Boolean,
    sidebarWidthPx: Float,
    screenWidthPx: Float,
    rightPanelAspect: Float,
) {
    val context = LocalContext.current
    val ambilightRevision by LauncherAppConfigStore.wallpaperAmbilightRevisionFlow
        .collectAsStateWithLifecycle()
    val mode = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightMode(context)
    }
    val animateLive = remember(context, ambilightRevision) {
        LauncherAppConfigStore.wallpaperAmbilightAnimateLive(context)
    }

    val isDarkTheme = LauncherThemeState.darkTheme
    val leftPanelFraction = if (adasPanelVisible && screenWidthPx > 1f && sidebarWidthPx > 1f) {
        (sidebarWidthPx / screenWidthPx).coerceIn(0f, 0.55f)
    } else {
        0f
    }

    val animator = remember(state) { AmbilightSyncedAnimator(state) }

    DisposableEffect(animator, syncedClock) {
        animator.attach(syncedClock)
        onDispose { animator.detach() }
    }

    SideEffect {
        state.modeState.value = mode
        state.isDarkThemeState.value = isDarkTheme
        state.leftPanelFractionState.floatValue = leftPanelFraction
        if (mode == WallpaperAmbilightMode.OFF) {
            animator.resetForOff()
        }
    }

    if (mode == WallpaperAmbilightMode.OFF) {
        return
    }

    val wallpaperRevision by LauncherAppConfigStore.wallpaperRevisionFlow.collectAsStateWithLifecycle()
    val wallpaperDarkRevision by LauncherAppConfigStore.wallpaperDarkRevisionFlow.collectAsStateWithLifecycle()
    val wallpaperLightRevision by LauncherAppConfigStore.wallpaperLightRevisionFlow.collectAsStateWithLifecycle()
    val activeWallpaperRevision = if (isDarkTheme) wallpaperDarkRevision else wallpaperLightRevision
    val activeWallpaperUri = remember(context, isDarkTheme, activeWallpaperRevision) {
        if (isDarkTheme) {
            LauncherAppConfigStore.wallpaperDarkUri(context)
        } else {
            LauncherAppConfigStore.wallpaperLightUri(context)
        }
    }
    val scaleMode = remember(context, wallpaperRevision) {
        LauncherAppConfigStore.wallpaperScaleMode(context)
    }

    val isActivityStarted by LauncherHomeActivityHolder.isStartedFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val hiddenForExternalApp by LauncherWindowState.hiddenForExternalAppFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val activeAppPackage by LauncherActiveAppTracker.activeAppPackageFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isActiveAppFullscreen by LauncherActiveAppTracker.isFullscreenFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val isWindowFocused by LauncherHomeActivityHolder.windowFocusedFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

    val shouldShow = shouldShowWallpaperAmbilight(
        mode = mode,
        isActivityStarted = isActivityStarted,
        hiddenForExternalApp = hiddenForExternalApp,
        activeAppPackage = activeAppPackage,
        isActiveAppFullscreen = isActiveAppFullscreen,
        isWindowFocused = isWindowFocused,
    )

    SideEffect {
        animator.setShouldShow(shouldShow)
    }

    val aspectBucket = (rightPanelAspect.coerceIn(0.5f, 4.0f) * 10f).roundToInt()
    val leftBucket = (leftPanelFraction * 20f).roundToInt()
    val expectedLiveKey = LiveWallpaperAmbilightBridge.buildSourceKey(
        uri = activeWallpaperUri,
        wallpaperRevision = activeWallpaperRevision,
        isDarkTheme = isDarkTheme,
        mode = mode,
    )
    val fallbackColor = if (isDarkTheme) Color(0xFF161D28) else Color(0xFFE5E7EB)
    val activeLiveKeyHolder = remember { arrayOfNulls<String>(1) }

    // Always collect live-wallpaper bridge updates in a coroutine -> ZERO recompositions!
    // When animateLive is OFF, LiveVideoWallpaperLayer / AnimatedImageWallpaperLayer publishes
    // only a single ~5th-frame snapshot (and caches it) so 4K/high-profile codecs work reliably.
    LaunchedEffect(animator, expectedLiveKey, fallbackColor) {
        activeLiveKeyHolder[0] = null
        LiveWallpaperAmbilightBridge.liveEntryFlow.collect { entry ->
            if (entry != null && entry.sourceKey == expectedLiveKey) {
                activeLiveKeyHolder[0] = expectedLiveKey
                animator.applyTargetData(
                    data = entry.data,
                    fallbackColor = fallbackColor,
                    durationMillis = COLOR_ANIMATION_MS,
                )
            }
        }
    }

    // Compute static / cached ~5th-frame wallpaper Ambilight data off the main thread without recomposing UI
    LaunchedEffect(
        animator,
        context,
        activeWallpaperUri,
        activeWallpaperRevision,
        isDarkTheme,
        scaleMode,
        aspectBucket,
        leftBucket,
        mode,
        animateLive,
    ) {
        val computed = withContext(LauncherCpuAffinity.lightPollersDispatcher) {
            computeWallpaperAmbilightData(
                context = context,
                activeWallpaperUri = activeWallpaperUri,
                isDarkTheme = isDarkTheme,
                scaleMode = scaleMode,
                viewportAspect = rightPanelAspect,
                leftPanelFraction = leftPanelFraction,
                mode = mode,
            )
        }
        if (computed != null && activeLiveKeyHolder[0] != expectedLiveKey) {
            animator.applyTargetData(
                data = computed,
                fallbackColor = fallbackColor,
                durationMillis = COLOR_ANIMATION_MS,
            )
        }
    }
}

private fun buildHorizontalBarStops(
    edgeColors: List<Color>,
    cornerColor: Color,
    leftPanelFraction: Float,
    tintStrength: Float,
    baseColor: Color,
): Array<Pair<Float, Color>> {
    if (edgeColors.isEmpty()) {
        return arrayOf(0f to baseColor, 1f to baseColor)
    }
    val leftFrac = leftPanelFraction.coerceIn(0f, 0.55f)
    val stops = mutableListOf<Pair<Float, Color>>()
    if (leftFrac > 0.01f) {
        stops.add(0f to lerp(baseColor, cornerColor, tintStrength * 0.78f))
        stops.add((leftFrac * 0.65f) to lerp(baseColor, edgeColors.first(), tintStrength * 0.92f))
    }
    val span = (1f - leftFrac).coerceAtLeast(0.05f)
    val lastIdx = (edgeColors.size - 1).coerceAtLeast(1)
    edgeColors.forEachIndexed { idx, color ->
        val frac = (leftFrac + span * (idx.toFloat() / lastIdx.toFloat())).coerceIn(0f, 1f)
        stops.add(frac to lerp(baseColor, color, tintStrength))
    }
    return stops.toTypedArray()
}

internal fun Modifier.launcherTopBarAmbilight(
    state: LauncherAmbilightUiState,
    baseColor: Color = LauncherColors.TopBarBg,
): Modifier = drawBehind {
    drawRect(baseColor)
    val alpha = state.alphaState.floatValue
    if (!state.isVisuallyActive || size.width <= 0f || size.height <= 0f) return@drawBehind

    when (state.mode) {
        WallpaperAmbilightMode.OFF -> Unit
        WallpaperAmbilightMode.EDGE_GRADIENT -> {
            val topColors = state.topEdgeColorStates.map { it.value }
            if (topColors.isEmpty()) return@drawBehind
            val strength = if (state.isDarkTheme) 0.86f else 0.90f
            val corner = lerp(
                state.leftEdgeColorStates.firstOrNull()?.value ?: topColors.first(),
                topColors.first(),
                0.45f,
            )
            val stops = buildHorizontalBarStops(
                edgeColors = topColors,
                cornerColor = corner,
                leftPanelFraction = state.leftPanelFraction,
                tintStrength = strength * alpha,
                baseColor = baseColor,
            )
            drawRect(brush = Brush.horizontalGradient(colorStops = stops))
            val topVeilAlpha = (if (state.isDarkTheme) 0.22f else 0.12f) * alpha
            drawRect(
                brush = Brush.verticalGradient(
                    0f to baseColor.copy(alpha = topVeilAlpha),
                    0.75f to baseColor.copy(alpha = topVeilAlpha * 0.20f),
                    1f to Color.Transparent,
                ),
            )
        }
        WallpaperAmbilightMode.BLURRED_WALLPAPER -> {
            val bmp = state.topBlurBitmapState.value ?: return@drawBehind
            drawImage(
                image = bmp,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha,
                filterQuality = FilterQuality.High,
            )
            val topVeil = (if (state.isDarkTheme) 0.44f else 0.26f) * alpha
            val seamVeil = (if (state.isDarkTheme) 0.18f else 0.08f) * alpha
            drawRect(
                brush = Brush.verticalGradient(
                    0f to baseColor.copy(alpha = topVeil),
                    1f to baseColor.copy(alpha = seamVeil),
                ),
            )
        }
    }
}

internal fun Modifier.launcherBottomBarAmbilight(
    state: LauncherAmbilightUiState,
    baseColor: Color = LauncherColors.BottomBarBg,
): Modifier = drawBehind {
    drawRect(baseColor)
    val alpha = state.alphaState.floatValue
    if (!state.isVisuallyActive || size.width <= 0f || size.height <= 0f) return@drawBehind

    when (state.mode) {
        WallpaperAmbilightMode.OFF -> Unit
        WallpaperAmbilightMode.EDGE_GRADIENT -> {
            val bottomColors = state.bottomEdgeColorStates.map { it.value }
            if (bottomColors.isEmpty()) return@drawBehind
            val strength = if (state.isDarkTheme) 0.86f else 0.90f
            val corner = lerp(
                state.leftEdgeColorStates.lastOrNull()?.value ?: bottomColors.first(),
                bottomColors.first(),
                0.45f,
            )
            val stops = buildHorizontalBarStops(
                edgeColors = bottomColors,
                cornerColor = corner,
                leftPanelFraction = state.leftPanelFraction,
                tintStrength = strength * alpha,
                baseColor = baseColor,
            )
            drawRect(brush = Brush.horizontalGradient(colorStops = stops))
            val bottomVeilAlpha = (if (state.isDarkTheme) 0.22f else 0.12f) * alpha
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.25f to baseColor.copy(alpha = bottomVeilAlpha * 0.20f),
                    1f to baseColor.copy(alpha = bottomVeilAlpha),
                ),
            )
        }
        WallpaperAmbilightMode.BLURRED_WALLPAPER -> {
            val bmp = state.bottomBlurBitmapState.value ?: return@drawBehind
            drawImage(
                image = bmp,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha,
                filterQuality = FilterQuality.High,
            )
            val seamVeil = (if (state.isDarkTheme) 0.18f else 0.08f) * alpha
            val bottomVeil = (if (state.isDarkTheme) 0.44f else 0.26f) * alpha
            drawRect(
                brush = Brush.verticalGradient(
                    0f to baseColor.copy(alpha = seamVeil),
                    1f to baseColor.copy(alpha = bottomVeil),
                ),
            )
        }
    }
}

internal fun Modifier.launcherLeftPanelAmbilight(
    state: LauncherAmbilightUiState,
    baseColor: Color = LauncherColors.LeftPanelBg,
): Modifier = drawBehind {
    drawRect(baseColor)
    val alpha = state.alphaState.floatValue
    if (!state.isVisuallyActive || size.width <= 0f || size.height <= 0f) return@drawBehind

    when (state.mode) {
        WallpaperAmbilightMode.OFF -> Unit
        WallpaperAmbilightMode.EDGE_GRADIENT -> {
            val leftColors = state.leftEdgeColorStates.map { it.value }
            if (leftColors.isEmpty()) return@drawBehind
            val baseStrength = (if (state.isDarkTheme) 0.80f else 0.86f) * alpha
            val lastIdx = (leftColors.size - 1).coerceAtLeast(1)
            val verticalStops = Array(leftColors.size) { idx ->
                val frac = idx.toFloat() / lastIdx.toFloat()
                frac to lerp(baseColor, leftColors[idx], baseStrength)
            }
            drawRect(brush = Brush.verticalGradient(colorStops = verticalStops))
            val leftEdgeVeil = (if (state.isDarkTheme) 0.37f else 0.22f) * alpha
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to baseColor.copy(alpha = leftEdgeVeil),
                    0.55f to baseColor.copy(alpha = leftEdgeVeil * 0.30f),
                    1f to Color.Transparent,
                ),
            )
        }
        WallpaperAmbilightMode.BLURRED_WALLPAPER -> {
            val bmp = state.leftBlurBitmapState.value ?: return@drawBehind
            drawImage(
                image = bmp,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha,
                filterQuality = FilterQuality.High,
            )
            val leftBezelVeil = (if (state.isDarkTheme) 0.54f else 0.30f) * alpha
            val seamVeil = (if (state.isDarkTheme) 0.22f else 0.10f) * alpha
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to baseColor.copy(alpha = leftBezelVeil),
                    0.65f to baseColor.copy(alpha = (leftBezelVeil + seamVeil) * 0.5f),
                    1f to baseColor.copy(alpha = seamVeil),
                ),
            )
        }
    }
}

internal fun resolveLeftPanelHorizonHaze(
    state: LauncherAmbilightUiState,
    baseLeftPanelBg: Color = LauncherColors.LeftPanelBg,
): Pair<Color, Float> {
    if (!state.isVisuallyActive) {
        return baseLeftPanelBg to 1f
    }
    val alpha = state.alphaState.floatValue
    val horizonColor = state.leftHorizonColorState.value
    return when (state.mode) {
        WallpaperAmbilightMode.OFF -> baseLeftPanelBg to 1f
        WallpaperAmbilightMode.EDGE_GRADIENT -> {
            val blend = (if (state.isDarkTheme) 0.58f else 0.68f) * alpha
            lerp(baseLeftPanelBg, horizonColor, blend) to (1f - 0.35f * alpha)
        }
        WallpaperAmbilightMode.BLURRED_WALLPAPER -> {
            val blend = (if (state.isDarkTheme) 0.50f else 0.56f) * alpha
            lerp(baseLeftPanelBg, horizonColor, blend) to (1f - 0.60f * alpha)
        }
    }
}
