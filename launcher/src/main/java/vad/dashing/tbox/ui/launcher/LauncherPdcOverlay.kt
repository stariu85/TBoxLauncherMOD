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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Projected ground-plane PDC rings: body center plus ring polylines (inner→outer).
 * Each ring point carries its WORLD angle around the car (degrees, 0° = straight
 * ahead, 180° = straight back, positive toward the left side).
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
 * Physical parking sensor channels present on the vehicle (2 front corner + 4 rear).
 */
private val FRONT_CHANNELS = listOf(
    LauncherPdcChannel.FrontSideLeft,
    LauncherPdcChannel.FrontSideRight,
)
private val REAR_CHANNELS = listOf(
    LauncherPdcChannel.RearLeft,
    LauncherPdcChannel.RearMidLeft,
    LauncherPdcChannel.RearMidRight,
    LauncherPdcChannel.RearRight,
)
private val PHYSICAL_PDC_CHANNELS = FRONT_CHANNELS + REAR_CHANNELS

/**
 * Ultrasonic parking sensors (PDC) rendered as 2 thin, trembling arc lines in front
 * and behind the car model. In standby mode, the arcs sit at 82.5dp from the car model bumper.
 * When an obstacle is detected, an elastic smoothstep curve bends the arc
 * inward towards the 3D car model body down to 5dp at 1cm, without dragging adjacent clear sections.
 */
@Composable
fun LauncherPdcOverlay(
    pdc: LauncherPdcZones,
    rings: LauncherPdcRingFrame?,
    @Suppress("UNUSED_PARAMETER") driving: Boolean,
    pasOn: Boolean = false,
    topView: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hasPhysicalObstacle = PHYSICAL_PDC_CHANNELS.any { pdc.distanceCm(it) in 1..LauncherPdcZones.PDC_MAX_CM }
    val showOverlay = (pasOn && topView) || hasPhysicalObstacle
    if (!showOverlay) return

    val pulse by rememberInfiniteTransition(label = "pdcPulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "pdcPulseValue",
    )

    val standbyJitter by rememberInfiniteTransition(label = "pdcStandbyJitter").animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "standbyJitterValue",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBendingPdcArcs(
            pdc = pdc,
            frame = rings,
            standbyJitter = standbyJitter,
            pulse = pulse,
        )
    }
}

private fun normFrom(angle: Float, start: Float): Float =
    ((angle - start) % 360f + 360f) % 360f

private fun angularDiffDeg(a: Float, b: Float): Float {
    var d = abs(a - b) % 360f
    if (d > 180f) d = 360f - d
    return d
}

private fun angleWithinDeg(angle: Float, start: Float, end: Float): Boolean {
    fun norm(a: Float) = ((a % 360f) + 360f) % 360f
    val a = norm(angle)
    val s = norm(start)
    val e = norm(end)
    return if (s <= e) a in s..e else a >= s || a <= e
}

/**
 * Calculates continuous smooth obstacle proximity (0.0 = clear, 1.0 = immediate danger at 1cm)
 * at a given world angle [theta] across active physical sensor channels using an elastic smoothstep curve.
 */
private fun proximityAtAngle(
    theta: Float,
    channels: List<LauncherPdcChannel>,
    pdc: LauncherPdcZones,
    channelAngles: Map<LauncherPdcChannel, Float>,
): Float {
    var maxProximity = 0f
    val sensorRadiusDeg = 28f

    channels.forEach { ch ->
        val distCm = pdc.distanceCm(ch)
        if (distCm in 1..LauncherPdcZones.PDC_MAX_CM) {
            val pCh = ((LauncherPdcZones.PDC_MAX_CM - distCm.toFloat()) / (LauncherPdcZones.PDC_MAX_CM - 1f)).coerceIn(0f, 1f)
            val chAngle = channelAngles[ch] ?: return@forEach
            val diff = angularDiffDeg(theta, chAngle)
            if (diff < sensorRadiusDeg) {
                val t = diff / sensorRadiusDeg
                // Smoothstep elastic falloff: 1 - (3*t^2 - 2*t^3)
                val elasticFalloff = 1f - (3f * t * t - 2f * t * t * t)
                val influence = pCh * elasticFalloff
                if (influence > maxProximity) {
                    maxProximity = influence
                }
            }
        }
    }

    return maxProximity.coerceIn(0f, 1f)
}

private fun lerpColor(c1: Color, c2: Color, t: Float): Color {
    val cl = t.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * cl,
        green = c1.green + (c2.green - c1.green) * cl,
        blue = c1.blue + (c2.blue - c1.blue) * cl,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * cl,
    )
}

private fun computeArcColor(
    p: Float,
    lineIdx: Int,
    pulse: Float,
): Color {
    val baseAlpha = if (lineIdx == 0) 0.70f else 0.45f

    if (p <= 0.08f) {
        return Color(0xFF38BDF8).copy(alpha = baseAlpha)
    }

    val pEffective = ((p - 0.08f) / 0.92f).coerceIn(0f, 1f)

    val cyan = Color(0xFF38BDF8)
    val amber = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)
    val brightRed = Color(0xFFFF1744)

    val blendedColor = when {
        pEffective <= 0.45f -> lerpColor(cyan, amber, pEffective / 0.45f)
        pEffective <= 0.80f -> lerpColor(amber, red, (pEffective - 0.45f) / 0.35f)
        else -> lerpColor(red, brightRed, ((pEffective - 0.80f) / 0.20f).coerceIn(0f, 1f))
    }

    val alpha = if (pEffective >= 0.75f) {
        (baseAlpha + (1f - baseAlpha) * pulse * 0.7f).coerceIn(0f, 1f)
    } else {
        (baseAlpha + pEffective * 0.25f).coerceIn(0f, 1f)
    }

    return blendedColor.copy(alpha = alpha)
}

private fun DrawScope.computeArcPoint(
    center: Offset,
    ringPt: Offset,
    theta: Float,
    lineIdx: Int,
    ptIdx: Int,
    bumperRadiusPx: Float,
    lineOffsetGap: Float,
    maxBendPx: Float,
    maxJitter: Float,
    standbyJitter: Float,
    pdc: LauncherPdcZones,
    channels: List<LauncherPdcChannel>,
    channelAngles: Map<LauncherPdcChannel, Float>,
): Offset {
    val dx = ringPt.x - center.x
    val dy = ringPt.y - center.y
    val dist0 = hypot(dx, dy)
    if (dist0 < 1f) return ringPt

    val nx = dx / dist0
    val ny = dy / dist0

    val rBumper = bumperRadiusPx.coerceAtLeast(10f)
    val rStandby = rBumper + 82.5.dp.toPx() + lineOffsetGap

    val p = proximityAtAngle(theta, channels, pdc, channelAngles)
    val bend = p * maxBendPx

    val jitter = (
        sin(standbyJitter * 14f + ptIdx * 0.8f + lineIdx * 2.5f) * 0.6f +
        cos(standbyJitter * 23f - ptIdx * 1.2f + lineIdx * 1.7f) * 0.4f
    ) * maxJitter

    val minAllowedRadius = rBumper + 5.dp.toPx() + (if (lineIdx == 0) 0f else 6.dp.toPx())
    val r = (rStandby - bend + jitter).coerceAtLeast(minAllowedRadius)
    return Offset(center.x + nx * r, center.y + ny * r)
}

private fun DrawScope.drawBendingPdcArcs(
    pdc: LauncherPdcZones,
    frame: LauncherPdcRingFrame?,
    standbyJitter: Float,
    pulse: Float,
) {
    val gap = 3.dp.toPx()
    val lineGaps = listOf(-gap, gap)
    val strokeWidth = 2.0.dp.toPx()
    val standbyDistPx = 82.5.dp.toPx() // 82.5dp from 3D bumper in standby
    val targetMinDistPx = 5.dp.toPx() // 5dp at maximum close approach
    val maxBendPx = standbyDistPx - targetMinDistPx // 82.5dp - 5dp = 77.5dp
    val maxJitter = 1.2.dp.toPx()

    if (frame != null) {
        val ring0 = frame.rings.firstOrNull() ?: return
        val center = frame.center
        val channelAngles = frame.channelAngles

        val frontPts = ring0.filter { angleWithinDeg(it.first, -35f, 35f) }
            .sortedBy { normFrom(it.first, -35f) }
        val rearPts = ring0.filter { angleWithinDeg(it.first, 145f, 215f) }
            .sortedBy { normFrom(it.first, 145f) }

        val frontNosePt = frontPts.minByOrNull { angularDiffDeg(it.first, 0f) }?.second
        val frontNoseDist = if (frontNosePt != null) hypot(frontNosePt.x - center.x, frontNosePt.y - center.y) else 100f
        val frontBumperRadius = frontNoseDist * 0.76f

        val rearTailPt = rearPts.minByOrNull { angularDiffDeg(it.first, 180f) }?.second
        val rearTailDist = if (rearTailPt != null) hypot(rearTailPt.x - center.x, rearTailPt.y - center.y) else 100f
        val rearBumperRadius = rearTailDist * 0.76f

        val arcGroups = listOf(
            Triple(frontPts, FRONT_CHANNELS, frontBumperRadius),
            Triple(rearPts, REAR_CHANNELS, rearBumperRadius),
        )

        arcGroups.forEach { (pts, channels, bumperRadius) ->
            if (pts.size < 2) return@forEach

            lineGaps.forEachIndexed { lineIdx, lineOffsetGap ->
                for (i in 0 until pts.lastIndex) {
                    val (thetaA, ringPtA) = pts[i]
                    val (thetaB, ringPtB) = pts[i + 1]

                    val posA = computeArcPoint(
                        center = center,
                        ringPt = ringPtA,
                        theta = thetaA,
                        lineIdx = lineIdx,
                        ptIdx = i,
                        bumperRadiusPx = bumperRadius,
                        lineOffsetGap = lineOffsetGap,
                        maxBendPx = maxBendPx,
                        maxJitter = maxJitter,
                        standbyJitter = standbyJitter,
                        pdc = pdc,
                        channels = channels,
                        channelAngles = channelAngles,
                    )
                    val posB = computeArcPoint(
                        center = center,
                        ringPt = ringPtB,
                        theta = thetaB,
                        lineIdx = lineIdx,
                        ptIdx = i + 1,
                        bumperRadiusPx = bumperRadius,
                        lineOffsetGap = lineOffsetGap,
                        maxBendPx = maxBendPx,
                        maxJitter = maxJitter,
                        standbyJitter = standbyJitter,
                        pdc = pdc,
                        channels = channels,
                        channelAngles = channelAngles,
                    )

                    val midTheta = (thetaA + thetaB) / 2f
                    val pMid = proximityAtAngle(midTheta, channels, pdc, channelAngles)
                    val segmentColor = computeArcColor(pMid, lineIdx, pulse)

                    drawLine(
                        color = segmentColor,
                        start = posA,
                        end = posB,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    } else {
        drawFallbackBendingArcs(pdc, standbyJitter, pulse, gap, strokeWidth, maxBendPx, maxJitter)
    }
}

private fun DrawScope.drawFallbackBendingArcs(
    pdc: LauncherPdcZones,
    standbyJitter: Float,
    pulse: Float,
    gapPx: Float,
    strokeWidth: Float,
    maxBendPx: Float,
    maxJitter: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val steps = 24
    val channelAnglesFront = mapOf(
        LauncherPdcChannel.FrontSideLeft to -42f,
        LauncherPdcChannel.FrontSideRight to 42f,
    )
    val channelAnglesRear = mapOf(
        LauncherPdcChannel.RearLeft to 142f,
        LauncherPdcChannel.RearMidLeft to 166f,
        LauncherPdcChannel.RearMidRight to 194f,
        LauncherPdcChannel.RearRight to 218f,
    )

    val lineGaps = listOf(-gapPx, gapPx)

    lineGaps.forEachIndexed { lineIdx, lineOffsetGap ->
        val baseStandbyPx = 82.5.dp.toPx() + lineOffsetGap

        for (i in 0 until steps) {
            val tA = i.toFloat() / steps
            val tB = (i + 1).toFloat() / steps

            val angleRadA = Math.toRadians((-38.0 + tA * 76.0)).toFloat()
            val angleRadB = Math.toRadians((-38.0 + tB * 76.0)).toFloat()
            val thetaA = Math.toDegrees(angleRadA.toDouble()).toFloat()
            val thetaB = Math.toDegrees(angleRadB.toDouble()).toFloat()

            val pA = proximityAtAngle(thetaA, FRONT_CHANNELS, pdc, channelAnglesFront)
            val pB = proximityAtAngle(thetaB, FRONT_CHANNELS, pdc, channelAnglesFront)

            val jitterA = (
                sin(standbyJitter * 14f + i * 0.8f + lineIdx * 2.5f) * 0.6f +
                cos(standbyJitter * 23f - i * 1.2f + lineIdx * 1.7f) * 0.4f
            ) * maxJitter
            val jitterB = (
                sin(standbyJitter * 14f + (i + 1) * 0.8f + lineIdx * 2.5f) * 0.6f +
                cos(standbyJitter * 23f - (i + 1) * 1.2f + lineIdx * 1.7f) * 0.4f
            ) * maxJitter

            val rA = (baseStandbyPx - pA * maxBendPx + jitterA).coerceAtLeast(5.dp.toPx())
            val rB = (baseStandbyPx - pB * maxBendPx + jitterB).coerceAtLeast(5.dp.toPx())

            val posA = Offset(cx + sin(angleRadA) * rA, cy - cos(angleRadA) * rA)
            val posB = Offset(cx + sin(angleRadB) * rB, cy - cos(angleRadB) * rB)

            val pMid = (pA + pB) / 2f
            val color = computeArcColor(pMid, lineIdx, pulse)

            drawLine(color = color, start = posA, end = posB, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }

        for (i in 0 until steps) {
            val tA = i.toFloat() / steps
            val tB = (i + 1).toFloat() / steps

            val angleRadA = Math.toRadians((-38.0 + tA * 76.0)).toFloat()
            val angleRadB = Math.toRadians((-38.0 + tB * 76.0)).toFloat()
            val thetaA = 180f + Math.toDegrees(angleRadA.toDouble()).toFloat()
            val thetaB = 180f + Math.toDegrees(angleRadB.toDouble()).toFloat()

            val pA = proximityAtAngle(thetaA, REAR_CHANNELS, pdc, channelAnglesRear)
            val pB = proximityAtAngle(thetaB, REAR_CHANNELS, pdc, channelAnglesRear)

            val jitterA = (
                sin(standbyJitter * 14f + i * 0.8f + lineIdx * 2.5f) * 0.6f +
                cos(standbyJitter * 23f - i * 1.2f + lineIdx * 1.7f) * 0.4f
            ) * maxJitter
            val jitterB = (
                sin(standbyJitter * 14f + (i + 1) * 0.8f + lineIdx * 2.5f) * 0.6f +
                cos(standbyJitter * 23f - (i + 1) * 1.2f + lineIdx * 1.7f) * 0.4f
            ) * maxJitter

            val rA = (baseStandbyPx - pA * maxBendPx + jitterA).coerceAtLeast(5.dp.toPx())
            val rB = (baseStandbyPx - pB * maxBendPx + jitterB).coerceAtLeast(5.dp.toPx())

            val posA = Offset(cx + sin(angleRadA) * rA, cy + cos(angleRadA) * rA)
            val posB = Offset(cx + sin(angleRadB) * rB, cy + cos(angleRadB) * rB)

            val pMid = (pA + pB) / 2f
            val color = computeArcColor(pMid, lineIdx, pulse)

            drawLine(color = color, start = posA, end = posB, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}

/**
 * BSD overlay stub (rendered on virtual road canvas).
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
