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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.LocValues
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * State holder tracking vehicle heading strictly from GPS coordinate changes.
 */
internal class LauncherGpsCompassTracker {
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0
    var currentHeading: Float = 0f
        private set

    fun onLocationUpdate(loc: LocValues): Float {
        val lat = loc.latitude
        val lon = loc.longitude

        // Validate coordinates
        if (!loc.hasGpsFix || abs(lat) < 0.001 || abs(lon) < 0.001) {
            return currentHeading
        }

        if (abs(lastLat) < 0.001 || abs(lastLon) < 0.001) {
            lastLat = lat
            lastLon = lon
            return currentHeading
        }

        val distanceArr = FloatArray(1)
        Location.distanceBetween(lastLat, lastLon, lat, lon, distanceArr)
        val distance = distanceArr[0]

        if (distance >= MIN_GPS_DISPLACEMENT_METERS) {
            currentHeading = computeGpsBearing(lastLat, lastLon, lat, lon)
            lastLat = lat
            lastLon = lon
        }

        return currentHeading
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
    val tracker = remember { LauncherGpsCompassTracker() }

    var targetHeading by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(locValues.latitude, locValues.longitude, locValues.hasGpsFix) {
        val newHeading = tracker.onLocationUpdate(locValues)
        targetHeading = newHeading
    }

    // Continuous heading accumulator to avoid 360° wraps during animation
    var continuousHeading by remember { mutableFloatStateOf(targetHeading) }
    LaunchedEffect(targetHeading) {
        val diff = ((targetHeading - continuousHeading + 540f) % 360f) - 180f
        continuousHeading += diff
    }

    val animatedHeading by animateFloatAsState(
        targetValue = continuousHeading,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "compassHeading",
    )

    val displayHeading = ((animatedHeading % 360f) + 360f) % 360f

    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val paintText = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 11.dp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val paintDegree = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 9.dp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
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
            val arcTopY = 38.dp.toPx()

            // Semi-axes for the perspective arc
            val rx = (w * 0.44f).coerceIn(120.dp.toPx(), 220.dp.toPx())
            val ry = 28.dp.toPx()
            val phiMaxRad = Math.toRadians(42.0).toFloat()

            // 1. Draw glowing fading arc in segments
            val steps = 44
            val glowColor = if (isDragging) Color(0xFF67E8F9) else Color(0xFF38BDF8)
            val coreColor = if (isDragging) Color(0xFFFFFFFF) else Color(0xFFE0F2FE)

            for (i in 0 until steps) {
                val t0 = i.toFloat() / steps
                val t1 = (i + 1).toFloat() / steps
                val alpha0 = -phiMaxRad + t0 * 2f * phiMaxRad
                val alpha1 = -phiMaxRad + t1 * 2f * phiMaxRad

                val midAlpha = (alpha0 + alpha1) / 2f
                val u = midAlpha / phiMaxRad // -1 .. 1
                val fade = cos(u * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)

                val x0 = cx + sin(alpha0) * rx
                val y0 = arcTopY + (1f - cos(alpha0)) * ry
                val x1 = cx + sin(alpha1) * rx
                val y1 = arcTopY + (1f - cos(alpha1)) * ry

                val outerAlpha = (fade * (if (isDragging) 0.85f else 0.60f)).coerceIn(0f, 1f)
                val innerAlpha = (fade * (if (isDragging) 1.0f else 0.85f)).coerceIn(0f, 1f)

                // Outer neon glow stroke
                drawLine(
                    color = glowColor.copy(alpha = outerAlpha),
                    start = Offset(x0, y0),
                    end = Offset(x1, y1),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Inner bright stroke
                drawLine(
                    color = coreColor.copy(alpha = innerAlpha),
                    start = Offset(x0, y0),
                    end = Offset(x1, y1),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

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
                        isMainCardinal -> 9.dp.toPx()
                        isIntermediate -> 6.5.dp.toPx()
                        else -> 3.5.dp.toPx()
                    }

                    val tickAlpha = (fade * 0.80f).coerceIn(0f, 1f)
                    val tickColor = if (deg == 0) Color(0xFFFF4444) else Color(0xFFBAE6FD)

                    drawLine(
                        color = tickColor.copy(alpha = tickAlpha),
                        start = Offset(px, py),
                        end = Offset(px + nx * tickLen, py + ny * tickLen),
                        strokeWidth = if (isMainCardinal) 1.8.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // Cardinal text label for 45° steps
                    if (deg % 45 == 0) {
                        val label = CARDINAL_POINTS.firstOrNull { it.first == degF }?.second
                        if (label != null) {
                            val textDist = tickLen + 11.dp.toPx()
                            val tx = px + nx * textDist
                            val ty = py + ny * textDist + 3.5.dp.toPx() // baseline adjust

                            val textAlpha = (fade * 255f).roundToInt().coerceIn(0, 255)
                            if (deg == 0) {
                                // North in vivid red
                                paintText.setARGB(textAlpha, 255, 68, 68)
                            } else {
                                paintText.setARGB(textAlpha, 240, 246, 252)
                            }
                            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, paintText)
                        }
                    }
                }
            }

            // 3. Small Red Heading Triangle (indicates car forward course)
            val arrowApexY = arcTopY - 2.dp.toPx()
            val arrowBaseY = arcTopY - 10.dp.toPx()
            val arrowHalfW = 5.5.dp.toPx()

            val trianglePath = Path().apply {
                moveTo(cx, arrowApexY)
                lineTo(cx - arrowHalfW, arrowBaseY)
                lineTo(cx + arrowHalfW, arrowBaseY)
                close()
            }

            // Glow around triangle
            drawPath(
                path = trianglePath,
                color = Color(0x66FF2222),
            )
            // Solid red arrow
            drawPath(
                path = trianglePath,
                color = Color(0xFFFF2222),
            )

            // Current heading in degrees above the arrow
            val degreeText = "${displayHeading.roundToInt()}°"
            paintDegree.setARGB(220, 255, 80, 80)
            drawContext.canvas.nativeCanvas.drawText(
                degreeText,
                cx,
                arrowBaseY - 3.dp.toPx(),
                paintDegree,
            )
        }
    }
}
