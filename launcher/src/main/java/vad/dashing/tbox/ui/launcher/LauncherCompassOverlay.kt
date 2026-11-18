package vad.dashing.tbox.ui.launcher

import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LocValues
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes forward azimuth (bearing) in degrees [0, 360) from (lat1, lon1) to (lat2, lon2).
 */
internal fun computeGpsBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    val bearingRad = atan2(y, x)
    return ((Math.toDegrees(bearingRad).toFloat() + 360f) % 360f)
}

/**
 * Minimum GPS displacement threshold (in metres) to calculate a new bearing.
 * Prevents erratic compass needle spinning when the vehicle is stationary.
 */
private const val MIN_GPS_DISPLACEMENT_METERS = 2.5f

/**
 * Computes approximate distance in meters between two GPS coordinates.
 */
internal fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    return runCatching {
        val distanceArr = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, distanceArr)
        distanceArr[0]
    }.getOrElse {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        (r * c).toFloat()
    }
}

/**
 * Global state holder tracking vehicle heading strictly from GPS coordinate changes.
 * Preserves last known heading across drive view transitions (e.g. traffic lights/stops).
 */
internal object LauncherGpsCompassTracker {
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0
    var currentHeading: Float = 0f
        private set

    private val _headingFlow = MutableStateFlow(0f)
    val headingFlow: StateFlow<Float> = _headingFlow.asStateFlow()

    fun onLocationUpdate(loc: LocValues): Float {
        val lat = loc.latitude
        val lon = loc.longitude

        // Validate coordinates
        if (!loc.hasGpsFix || abs(lat) < 0.001 || abs(lon) < 0.001) {
            return currentHeading
        }

        return onCoordinatesUpdate(lat, lon)
    }

    fun onCoordinatesUpdate(lat: Double, lon: Double): Float {
        if (abs(lat) < 0.001 || abs(lon) < 0.001) {
            return currentHeading
        }

        if (abs(lastLat) < 0.001 || abs(lastLon) < 0.001) {
            lastLat = lat
            lastLon = lon
            return currentHeading
        }

        val distance = computeDistanceMeters(lastLat, lastLon, lat, lon)

        if (distance >= MIN_GPS_DISPLACEMENT_METERS) {
            currentHeading = computeGpsBearing(lastLat, lastLon, lat, lon)
            lastLat = lat
            lastLon = lon
            _headingFlow.value = currentHeading
        }

        return currentHeading
    }

    fun reset() {
        lastLat = 0.0
        lastLon = 0.0
        currentHeading = 0f
        _headingFlow.value = 0f
    }
}

private val CARDINAL_POINTS = listOf(
    0f to "С",
    45f to "СВ",
    90f to "В",
    135f to "ЮВ",
    180f to "Ю",
    225f to "ЮЗ",
    270f to "З",
    315f to "СЗ",
)

const val COMPASS_OVERLAY_HEIGHT_DP = 120

/**
 * Deadband threshold in degrees to suppress tiny micro-jitter and GPS noise.
 * Only angular deviations >= 1.5° trigger an update, preventing erratic needle vibration.
 */
private const val HEADING_JITTER_THRESHOLD_DEG = 1.5f

/**
 * Arc compass overlaid onto the road in ADAS rear-view mode.
 * Styled similarly to PDC front parking arcs: curved upwards towards the road horizon,
 * fading smoothly at the lateral extremities, with tick marks, cardinal directions (С, СВ, В, etc.),
 * and a small red arrow indicator for the current travel direction.
 *
 * Can be freely dragged vertically in the ADAS area.
 */
@Composable
fun LauncherCompassOverlay(
    locValues: LocValues,
    onDragY: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrackerHeading by LauncherGpsCompassTracker.headingFlow.collectAsStateWithLifecycle()

    var targetHeading by remember { mutableFloatStateOf(LauncherGpsCompassTracker.currentHeading) }

    LaunchedEffect(currentTrackerHeading) {
        targetHeading = currentTrackerHeading
    }

    LaunchedEffect(locValues.latitude, locValues.longitude, locValues.hasGpsFix) {
        val newHeading = LauncherGpsCompassTracker.onLocationUpdate(locValues)
        targetHeading = newHeading
    }

    // Continuous heading accumulator to avoid 360° wraps during animation and eliminate jitter
    var continuousHeading by remember { mutableFloatStateOf(targetHeading) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetHeading) {
        if (!isInitialized) {
            isInitialized = true
            continuousHeading = targetHeading
            return@LaunchedEffect
        }
        val diff = ((targetHeading - continuousHeading) % 360f + 540f) % 360f - 180f
        if (abs(diff) >= HEADING_JITTER_THRESHOLD_DEG) {
            continuousHeading += diff
        }
    }

    // 1-second smoothing animation to prevent erratic heading jumping
    val animatedHeading by animateFloatAsState(
        targetValue = continuousHeading,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "compassHeading",
    )

    val displayHeading = ((animatedHeading % 360f) + 360f) % 360f

    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val paintTextFill = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
        }
    }
    val paintDegreeFill = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 11.5.dp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            style = Paint.Style.FILL
        }
    }
    val paintDegreeStroke = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 11.5.dp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = with(density) { 3.2.dp.toPx() }
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    val isDark = LauncherThemeState.darkTheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(COMPASS_OVERLAY_HEIGHT_DP.dp)
            .graphicsLayer {
                alpha = if (isDragging) 0.85f else 0.70f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragCancel = { isDragging = false },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDragY(dragAmount)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val cx = w / 2f

            // Top apex of the arc
            val arcTopY = 42.dp.toPx()

            // Semi-axes for the perspective arc - scaled proportionally to reach the lateral edges of ADAS zone
            val edgeMargin = 8.dp.toPx()
            val targetHalfWidth = (cx - edgeMargin).coerceAtLeast(80.dp.toPx())
            val phiMaxRad = Math.toRadians(45.0).toFloat()
            val rx = targetHalfWidth / sin(phiMaxRad)
            val ry = rx * 0.22f

            // 1. Draw smooth uniform blue/cyan arc as a single continuous Path without segment cells
            val arcSteps = 96
            val arcPath = Path().apply {
                for (i in 0..arcSteps) {
                    val t = i.toFloat() / arcSteps
                    val alpha = -phiMaxRad + t * 2f * phiMaxRad
                    val x = cx + sin(alpha) * rx
                    val y = arcTopY + (1f - cos(alpha)) * ry
                    if (i == 0) {
                        moveTo(x, y)
                    } else {
                        lineTo(x, y)
                    }
                }
            }

            val arcColor = if (isDark) {
                if (isDragging) Color(0xFF7DD3FC) else Color(0xFF38BDF8)
            } else {
                if (isDragging) Color(0xFF0284C7) else Color(0xFF0EA5E9)
            }

            val arcBrush = Brush.horizontalGradient(
                0.00f to arcColor.copy(alpha = 0f),
                0.06f to arcColor,
                0.94f to arcColor,
                1.00f to arcColor.copy(alpha = 0f),
                startX = cx - targetHalfWidth,
                endX = cx + targetHalfWidth,
            )

            // Draw single continuous smooth arc stroke
            drawPath(
                path = arcPath,
                brush = arcBrush,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // 2. Draw Ticks & Cardinal Labels
            val fovDeg = 120f // Total angular span visible on the arc

            for (deg in 0 until 360 step 15) {
                val degF = deg.toFloat()
                val diff = ((degF - displayHeading + 540f) % 360f) - 180f

                if (abs(diff) <= fovDeg / 2f) {
                    val u = diff / (fovDeg / 2f) // -1 .. 1
                    val alpha = u * phiMaxRad
                    val fade = cos(u * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)

                    val px = cx + sin(alpha) * rx
                    val py = arcTopY + (1f - cos(alpha)) * ry

                    // Outward normal vector (pointing upwards/forwards)
                    val nx = -sin(alpha)
                    val ny = -cos(alpha)

                    val isMainCardinal = deg % 90 == 0
                    val isIntermediate = deg % 45 == 0 && !isMainCardinal

                    val tickLen = when {
                        isMainCardinal -> 13.dp.toPx()
                        isIntermediate -> 9.dp.toPx()
                        else -> 5.dp.toPx()
                    }

                    val tickStroke = when {
                        isMainCardinal -> 2.4.dp.toPx()
                        isIntermediate -> 1.8.dp.toPx()
                        else -> 1.2.dp.toPx()
                    }

                    val tickAlpha = (fade * 0.90f).coerceIn(0f, 1f)
                    val tickColor = if (isDark) {
                        when {
                            deg == 0 -> Color(0xFFFF4444)
                            isMainCardinal -> Color(0xFFE2E8F0)
                            isIntermediate -> Color(0xFF9CA3AF)
                            else -> Color(0xFF64748B)
                        }
                    } else {
                        when {
                            deg == 0 -> Color(0xFFDC2626)
                            isMainCardinal -> Color(0xFF2D3748)
                            isIntermediate -> Color(0xFF4B5563)
                            else -> Color(0xFF94A3B8)
                        }
                    }

                    // Contrast dark backing behind ticks in dark mode so they pop on dark road
                    if (isDark && (isMainCardinal || isIntermediate)) {
                        drawLine(
                            color = Color(0x99000000).copy(alpha = tickAlpha),
                            start = Offset(px, py),
                            end = Offset(px + nx * tickLen, py + ny * tickLen),
                            strokeWidth = tickStroke + 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    drawLine(
                        color = tickColor.copy(alpha = tickAlpha),
                        start = Offset(px, py),
                        end = Offset(px + nx * tickLen, py + ny * tickLen),
                        strokeWidth = tickStroke,
                        cap = StrokeCap.Round,
                    )

                    // Cardinal text label for 45° steps
                    if (deg % 45 == 0) {
                        val label = CARDINAL_POINTS.firstOrNull { it.first == degF }?.second
                        if (label != null) {
                            val isMain = deg % 90 == 0
                            val fontSize = if (isMain) 14.dp.toPx() else 11.5.dp.toPx()
                            paintTextFill.textSize = fontSize

                            val textDist = tickLen + (if (isMain) 13.dp.toPx() else 11.dp.toPx())
                            val tx = px + nx * textDist
                            val ty = py + ny * textDist + 4.dp.toPx() // baseline adjust

                            // Boost visibility towards edges so letters don't wash out
                            val textAlpha = (sqrt(fade) * 255f).roundToInt().coerceIn(70, 255)

                            // Letters: graphite in light theme, light grey in dark theme
                            val letterColor = if (isDark) {
                                if (isMain) Color(0xFFE2E8F0) else Color(0xFF9CA3AF)
                            } else {
                                if (isMain) Color(0xFF2D3748) else Color(0xFF4B5563)
                            }

                            paintTextFill.setARGB(
                                textAlpha,
                                (letterColor.red * 255f).roundToInt(),
                                (letterColor.green * 255f).roundToInt(),
                                (letterColor.blue * 255f).roundToInt(),
                            )
                            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, paintTextFill)
                        }
                    }
                }
            }

            // 3. Red Heading Triangle placed BELOW the arc, pointing UPWARDS at current course
            val arrowApexY = arcTopY + 4.dp.toPx()
            val arrowHeight = 9.dp.toPx()
            val arrowBaseY = arrowApexY + arrowHeight
            val arrowHalfW = 6.5.dp.toPx()

            val trianglePath = Path().apply {
                moveTo(cx, arrowApexY)
                lineTo(cx - arrowHalfW, arrowBaseY)
                lineTo(cx + arrowHalfW, arrowBaseY)
                close()
            }

            if (isDark) {
                // Subtle dark outline behind triangle for contrast against dark road
                drawPath(
                    path = trianglePath,
                    color = Color(0xCC000000),
                    style = Stroke(
                        width = 2.dp.toPx(),
                    ),
                )
                // Glow around triangle
                drawPath(
                    path = trianglePath,
                    color = Color(0x44FF2222),
                )
                // Solid red arrow
                drawPath(
                    path = trianglePath,
                    color = Color(0xFFFF3B30),
                )
            } else {
                drawPath(
                    path = trianglePath,
                    color = Color(0xFFDC2626),
                )
            }

            // Current heading in degrees placed DOWN, UNDER the triangle
            val degreeText = "${displayHeading.roundToInt()}°"
            val degreeY = arrowBaseY + 13.dp.toPx()
            if (isDark) {
                paintDegreeStroke.setARGB(220, 0, 0, 0)
                drawContext.canvas.nativeCanvas.drawText(
                    degreeText,
                    cx,
                    degreeY,
                    paintDegreeStroke,
                )
                paintDegreeFill.setARGB(255, 255, 90, 90)
            } else {
                paintDegreeFill.setARGB(255, 220, 38, 38)
            }
            drawContext.canvas.nativeCanvas.drawText(
                degreeText,
                cx,
                degreeY,
                paintDegreeFill,
            )
        }
    }
}
