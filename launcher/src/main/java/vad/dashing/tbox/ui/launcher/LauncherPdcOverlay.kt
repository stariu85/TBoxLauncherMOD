package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Projected ground-plane PDC rings: body center plus ring polylines (inner→outer).
 * Each ring point carries its WORLD angle around the car (degrees, 0° = straight
 * ahead, 180° = straight back, positive toward the left side), so the overlay slices
 * per-zone arcs in world space — independent of the camera, which keeps the rear band
 * alive in the drive chase camera where screen-projected anchors leave the viewport.
 */
data class LauncherPdcRingFrame(
    val center: Offset,
    val rings: List<List<Pair<Float, Offset>>>,
    /** World-space zone center angle per sensor channel (same convention as [rings]). */
    val channelAngles: Map<LauncherPdcChannel, Float>,
    /** Screen px per world metre near the front of the band (stroke scaling). */
    val pxPerMeterFront: Float,
    /** Screen px per world metre near the rear of the band (stroke scaling). */
    val pxPerMeterRear: Float,
)

/**
 * Ultrasonic parking sensors (PDC) rendered as a shared sonar band hugging the body
 * contour (positions projected from the 3D model through the scene camera). The band
 * is split into per-sensor zones; each zone lights 1–3 concentric rings by distance
 * (far → outer ring only, near → all three, pulsing). Only zones with a live reading
 * appear, so the strip matches the actual trim (e.g. 4 rear + 2 front diagonal).
 */
@Composable
fun LauncherPdcOverlay(
    pdc: LauncherPdcZones,
    rings: LauncherPdcRingFrame?,
    driving: Boolean,
    pasOn: Boolean = false,
    topView: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (DEBUG_SHOW_FULL_RINGS) {
        if (rings != null) {
            Canvas(modifier = modifier.fillMaxSize()) {
                rings.rings.forEachIndexed { index, ring ->
                    if (ring.size < 2) return@forEachIndexed
                    val path = Path()
                    ring.forEachIndexed { i, (_, pt) ->
                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    path.close()
                    drawPath(
                        path,
                        color = Color(0xFF9AA4B2).copy(alpha = 0.9f - index * 0.2f),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
        return
    }

    val showStandby = pasOn && topView && !pdc.hasAny
    if (!pdc.hasAny && !showStandby) return

    val pulse by rememberInfiniteTransition(label = "pdcPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
        label = "pdcPulseValue",
    )

    val standbyJitter by rememberInfiniteTransition(label = "pdcStandbyJitter").animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "standbyJitterValue",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (pdc.hasAny) {
            if (rings != null) {
                drawPdcBands(pdc, rings, driving, pulse)
            }
        } else if (showStandby) {
            drawPdcStandbyArcs(rings, standbyJitter)
        }
    }
}

// Temporary: render all three rings fully to calibrate the body ellipse visually.
private const val DEBUG_SHOW_FULL_RINGS = false

private val FRONT_CHANNELS = listOf(
    LauncherPdcChannel.FrontSideLeft,
    LauncherPdcChannel.FrontLeft,
    LauncherPdcChannel.FrontMidLeft,
    LauncherPdcChannel.FrontMidRight,
    LauncherPdcChannel.FrontRight,
    LauncherPdcChannel.FrontSideRight,
)
private val REAR_CHANNELS = listOf(
    LauncherPdcChannel.RearSideLeft,
    LauncherPdcChannel.RearLeft,
    LauncherPdcChannel.RearMidLeft,
    LauncherPdcChannel.RearMidRight,
    LauncherPdcChannel.RearRight,
    LauncherPdcChannel.RearSideRight,
)

private class PdcSeg(
    val angleDeg: Float,
    val pxPerMeter: Float,
    val level: LauncherPdcLevel,
)

private fun DrawScope.drawPdcBands(
    pdc: LauncherPdcZones,
    frame: LauncherPdcRingFrame,
    driving: Boolean,
    pulse: Float,
) {
    fun segsFor(channels: List<LauncherPdcChannel>, pxPerMeter: Float): List<PdcSeg> =
        channels.mapNotNull { ch ->
            val level = LauncherPdcZones.levelFor(pdc.distanceCm(ch))
            // Only channels with a live reading appear — the band automatically matches
            // the trim (e.g. 4 rear + 2 front diagonal) without overlap.
            if (level == LauncherPdcLevel.None) return@mapNotNull null
            val angle = frame.channelAngles[ch] ?: return@mapNotNull null
            PdcSeg(
                angleDeg = angle,
                pxPerMeter = pxPerMeter,
                level = level,
            )
        }.sortedBy { it.angleDeg }

    listOf(
        // In the drive camera the front sensors are hidden; the rear band is enlarged
        // geometrically (rear hemisphere boost) so it stays readable in perspective.
        false to if (driving) emptyList() else segsFor(FRONT_CHANNELS, frame.pxPerMeterFront),
        true to segsFor(REAR_CHANNELS, frame.pxPerMeterRear),
    ).forEach { (isRear, segs) ->
        if (segs.isEmpty()) return@forEach
        // Each zone is a compact arc centred on its sensor's world angle, capped by
        // half the distance to the neighbours so segments never overlap.
        val maxHalfSpan = if (isRear) 11f else 10f
        segs.forEachIndexed { i, seg ->
            val prevGap = if (i > 0) angularDiffDeg(seg.angleDeg, segs[i - 1].angleDeg) else 40f
            val nextGap = if (i < segs.lastIndex) angularDiffDeg(segs[i + 1].angleDeg, seg.angleDeg) else 40f
            // A 1.5° gap keeps neighbours visually separate without starving the arc.
            val halfSpan = (minOf(prevGap, nextGap) / 2f - 0.75f).coerceIn(4f, maxHalfSpan)
            drawPdcSegment(frame, seg, seg.angleDeg - halfSpan, halfSpan * 2f, driving, pulse)
        }
    }
}

private fun angularDiffDeg(a: Float, b: Float): Float {
    var d = abs(a - b) % 360f
    if (d > 180f) d = 360f - d
    return d
}

private fun DrawScope.drawPdcSegment(
    frame: LauncherPdcRingFrame,
    seg: PdcSeg,
    startAngle: Float,
    sweep: Float,
    driving: Boolean,
    pulse: Float,
    extraRadialBoost: Float? = null,
    forceAllRings: Boolean = false,
) {
    if (sweep < 3f) return
    val color = when (seg.level) {
        LauncherPdcLevel.Near -> Color(0xFFEF4444)
        LauncherPdcLevel.Mid -> Color(0xFFF59E0B)
        LauncherPdcLevel.Far -> Color(0xFF22C55E)
        LauncherPdcLevel.None -> return
    }
    val alphaScale = if (seg.level == LauncherPdcLevel.Near) pulse else 1f

    // Approaching obstacle "advances" inward: far lights only the outer ring.
    val ringIndex: IntArray = when {
        forceAllRings -> intArrayOf(0, 1, 2)
        seg.level == LauncherPdcLevel.Far -> intArrayOf(2)
        seg.level == LauncherPdcLevel.Mid -> intArrayOf(1, 2)
        seg.level == LauncherPdcLevel.Near -> intArrayOf(0, 1, 2)
        else -> return
    }
    // Drive camera: slight extra outward stretch on top of the geometric rear boost.
    val radialBoost = extraRadialBoost ?: if (driving) 1.05f else 1f
    val strokeWidth = (seg.pxPerMeter * if (driving) 0.13f else 0.12f).coerceIn(6f, 14f)

    ringIndex.forEach { ring ->
        val ringPts = frame.rings.getOrNull(ring) ?: return@forEach
        val pts = ringArcPoints(ringPts, startAngle, sweep)
        if (pts.size < 2) return@forEach
        val path = Path()
        pts.forEachIndexed { index, pt ->
            val x = frame.center.x + (pt.x - frame.center.x) * radialBoost
            val y = frame.center.y + (pt.y - frame.center.y) * radialBoost
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val alpha = (1f - ring * 0.16f) * alphaScale
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Consecutive ring samples in [start, start+sweep]; falls back to nearest-angle samples. */
private fun ringArcPoints(
    ringPts: List<Pair<Float, Offset>>,
    startAngle: Float,
    sweep: Float,
): List<Offset> {
    if (ringPts.size < 2) return emptyList()
    val endAngle = startAngle + sweep
    val slice = ringPts
        .filter { angleWithinDeg(it.first, startAngle, endAngle) }
        .sortedBy { ((it.first - startAngle + 360f) % 360f) }
        .map { it.second }
    if (slice.size >= 2) return slice
    val steps = 6
    return (0..steps).mapNotNull { i ->
        val a = startAngle + sweep * (i / steps.toFloat())
        ringPts.minByOrNull { angularDiffDeg(it.first, a) }?.second
    }
}

private fun angleWithinDeg(angle: Float, start: Float, end: Float): Boolean {
    fun norm(a: Float) = ((a % 360f) + 360f) % 360f
    val a = norm(angle)
    val s = norm(start)
    val e = norm(end)
    return if (s <= e) a in s..e else a >= s || a <= e
}

/**
 * BSD as the same sonar band as PDC, on the rear corners (not the mid-sides).
 * Short ~26° slices stay readable in the drive camera, where the true side
 * of the ring falls under the 3D body / off the chase-cam frustum.
 */
@Composable
fun LauncherBsdOverlay(
    @Suppress("UNUSED_PARAMETER") threats: LauncherRearThreats,
    @Suppress("UNUSED_PARAMETER") rings: LauncherPdcRingFrame?,
    @Suppress("UNUSED_PARAMETER") driving: Boolean,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    // BSD is rendered exclusively on the road canvas in LauncherVirtualRoad.
}

private fun DrawScope.drawBsdDiagonals(
    threats: LauncherRearThreats,
    frame: LauncherPdcRingFrame,
    driving: Boolean,
    pulse: Float,
) {
    val px = maxOf(frame.pxPerMeterRear, frame.pxPerMeterFront)
    // World angle: 0° nose, 90° left, 180° tail, 270° right.
    // Sit on the rear quarters. Visual left/right of the body is mirrored vs the
    // OEM getLeftSts/getRightSts mapping, so left threat lights the 196° slice.
    val sweep = 26f
    listOf(
        threats.bsdLeft to 196f,
        threats.bsdRight to 138f,
    ).forEach { (level, startDeg) ->
        if (level == LauncherRearThreatLevel.Off) return@forEach
        val pdcLevel = when (level) {
            LauncherRearThreatLevel.Alert -> LauncherPdcLevel.Near
            LauncherRearThreatLevel.Caution -> LauncherPdcLevel.Mid
            LauncherRearThreatLevel.Off -> return@forEach
        }
        drawPdcSegment(
            frame = frame,
            seg = PdcSeg(
                angleDeg = startDeg + sweep * 0.5f,
                pxPerMeter = px,
                level = pdcLevel,
            ),
            startAngle = startDeg,
            sweep = sweep,
            driving = driving,
            pulse = pulse,
            extraRadialBoost = if (driving) 1.10f else 1.06f,
            forceAllRings = true,
        )
    }
}

private fun normFrom(angle: Float, start: Float): Float =
    ((angle - start) % 360f + 360f) % 360f

/**
 * Renders 2 thin slightly trembling arcs at 60dp distance in front and behind
 * the car model when parking sensors are ON in top-view mode with no obstacles detected.
 */
private fun DrawScope.drawPdcStandbyArcs(
    frame: LauncherPdcRingFrame?,
    standbyJitter: Float,
) {
    val px60 = 60.dp.toPx()
    val gap = 3.dp.toPx()
    val lineOffsets = listOf(px60 - gap, px60 + gap)
    val strokeWidth = 1.8.dp.toPx()
    val baseColor = Color(0xFF38BDF8) // Sleek cyan/sky blue
    val maxJitter = 1.2.dp.toPx()

    if (frame != null) {
        val ring0 = frame.rings.firstOrNull() ?: return
        val center = frame.center

        val frontPts = ring0.filter { angleWithinDeg(it.first, -35f, 35f) }
            .sortedBy { normFrom(it.first, -35f) }
        val rearPts = ring0.filter { angleWithinDeg(it.first, 145f, 215f) }
            .sortedBy { normFrom(it.first, 145f) }

        listOf(frontPts, rearPts).forEach { pts ->
            if (pts.size < 2) return@forEach
            lineOffsets.forEachIndexed { lineIdx, extraDist ->
                val path = Path()
                pts.forEachIndexed { ptIdx, (_, ringPt) ->
                    val dx = ringPt.x - center.x
                    val dy = ringPt.y - center.y
                    val dist0 = hypot(dx, dy)
                    if (dist0 < 1f) return@forEachIndexed
                    val nx = dx / dist0
                    val ny = dy / dist0

                    val jitter = (
                        sin(standbyJitter * 14f + ptIdx * 0.8f + lineIdx * 2.5f) * 0.6f +
                        cos(standbyJitter * 23f - ptIdx * 1.2f + lineIdx * 1.7f) * 0.4f
                    ) * maxJitter

                    val r = dist0 + extraDist + jitter
                    val x = center.x + nx * r
                    val y = center.y + ny * r

                    if (ptIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val alpha = if (lineIdx == 0) 0.65f else 0.40f
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = alpha),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    } else {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val bodyW = size.width * 0.44f * 0.55f
        val bodyH = size.height * 0.78f * 0.55f
        val frontY = cy - bodyH / 2f
        val rearY = cy + bodyH / 2f

        lineOffsets.forEachIndexed { lineIdx, extraDist ->
            val steps = 20

            // Front arc
            val pathFront = Path()
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val angleRad = Math.toRadians((-38.0 + t * 76.0)).toFloat()
                val jitter = (
                    sin(standbyJitter * 14f + i * 0.8f + lineIdx * 2.5f) * 0.6f +
                    cos(standbyJitter * 23f - i * 1.2f + lineIdx * 1.7f) * 0.4f
                ) * maxJitter
                val r = extraDist + jitter
                val x = cx + sin(angleRad) * (bodyW * 0.8f)
                val y = frontY - cos(angleRad) * r
                if (i == 0) pathFront.moveTo(x, y) else pathFront.lineTo(x, y)
            }
            val alpha = if (lineIdx == 0) 0.65f else 0.40f
            drawPath(
                path = pathFront,
                color = baseColor.copy(alpha = alpha),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Rear arc
            val pathRear = Path()
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val angleRad = Math.toRadians((-38.0 + t * 76.0)).toFloat()
                val jitter = (
                    sin(standbyJitter * 14f + i * 0.8f + lineIdx * 2.5f) * 0.6f +
                    cos(standbyJitter * 23f - i * 1.2f + lineIdx * 1.7f) * 0.4f
                ) * maxJitter
                val r = extraDist + jitter
                val x = cx + sin(angleRad) * (bodyW * 0.8f)
                val y = rearY + cos(angleRad) * r
                if (i == 0) pathRear.moveTo(x, y) else pathRear.lineTo(x, y)
            }
            drawPath(
                path = pathRear,
                color = baseColor.copy(alpha = alpha),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
