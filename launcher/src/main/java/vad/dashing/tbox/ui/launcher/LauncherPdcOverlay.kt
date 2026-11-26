package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherWindowState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val PDC_CLOCK_TICK_DIVISOR = 2 // ~15 FPS, phase-locked to every 2nd 30 FPS road tick
internal const val PDC_PULSE_HALF_PERIOD_MS = 380f
internal const val PDC_STANDBY_JITTER_PERIOD_MS = 1_100f
// LCM(760, 1100) = 41_800 ms so wrapping elapsed time preserves both waveforms seamlessly.
internal const val PDC_ANIM_WRAP_PERIOD_MS = 41_800f

internal fun computePdcPulseValue(elapsedMs: Float): Float {
    val fullPeriod = PDC_PULSE_HALF_PERIOD_MS * 2f
    val mod = ((elapsedMs % fullPeriod) + fullPeriod) % fullPeriod
    val linearFraction = if (mod <= PDC_PULSE_HALF_PERIOD_MS) {
        mod / PDC_PULSE_HALF_PERIOD_MS
    } else {
        (fullPeriod - mod) / PDC_PULSE_HALF_PERIOD_MS
    }
    return 0.6f + 0.4f * FastOutSlowInEasing.transform(linearFraction.coerceIn(0f, 1f))
}

internal fun computePdcStandbyJitterPhase(elapsedMs: Float): Float {
    val mod = ((elapsedMs % PDC_STANDBY_JITTER_PERIOD_MS) + PDC_STANDBY_JITTER_PERIOD_MS) % PDC_STANDBY_JITTER_PERIOD_MS
    return (mod / PDC_STANDBY_JITTER_PERIOD_MS) * (2f * Math.PI).toFloat()
}

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
 * Physical parking sensor channels present on the vehicle (2 front main + 4 rear).
 */
val FRONT_CHANNELS = listOf(
    LauncherPdcChannel.FrontLeft,
    LauncherPdcChannel.FrontRight,
)
val REAR_CHANNELS = listOf(
    LauncherPdcChannel.RearLeft,
    LauncherPdcChannel.RearMidLeft,
    LauncherPdcChannel.RearMidRight,
    LauncherPdcChannel.RearRight,
)
val PHYSICAL_PDC_CHANNELS = FRONT_CHANNELS + REAR_CHANNELS

fun hasPhysicalPdcObstacle(pdc: LauncherPdcZones): Boolean =
    PHYSICAL_PDC_CHANNELS.any { (pdc.distanceCm(it)) in (LauncherPdcZones.PDC_MIN_CM)..(LauncherPdcZones.PDC_MAX_CM) }

/**
 * Ultrasonic parking sensors (PDC) rendered as 2 thin, trembling arc lines in front
 * and behind the car model. In standby mode, the arcs sit at 82.5dp from the car model bumper.
 * When an obstacle is detected, an elastic smoothstep curve bends the arc
 * inward towards the 3D car model body down to 5dp at 1cm, without dragging adjacent clear sections.
 *
 * Driven by [syncedClock] with [PDC_CLOCK_TICK_DIVISOR] (~15 FPS) so PDC ticks on
 * every 2nd frame of [LauncherVirtualRoad] on the exact same VSYNC without staggering.
 */
@Composable
fun LauncherPdcOverlay(
    pdc: LauncherPdcZones,
    rings: LauncherPdcRingFrame?,
    driving: Boolean,
    pasOn: Boolean = false,
    @Suppress("UNUSED_PARAMETER") topView: Boolean = false,
    modifier: Modifier = Modifier,
    syncedClock: LauncherSynced30FpsClock? = null,
) {
    val context = LocalContext.current
    val pdcModeRevision by LauncherAppConfigStore.pdcVisualModeRevisionFlow
        .collectAsStateWithLifecycle()
    val visualMode = remember(context, pdcModeRevision) {
        LauncherAppConfigStore.pdcVisualMode(context)
    }

    if (visualMode == PDC_VISUAL_OFF) return

    val hasPhysicalObstacle = hasPhysicalPdcObstacle(pdc)
    val showOverlay = pasOn || hasPhysicalObstacle

    val overlayAlphaState = animateFloatAsState(
        targetValue = if (showOverlay) 1f else 0f,
        animationSpec = if (showOverlay) tween(200) else tween(1000, easing = LinearEasing),
        label = "pdcOverlayAlpha",
    )

    if (!showOverlay && overlayAlphaState.value <= 0.001f) return

    var lastValidFrame by remember { mutableStateOf<LauncherPdcRingFrame?>(null) }
    if (rings != null) {
        lastValidFrame = rings
    }
    val effectiveFrame = rings ?: lastValidFrame

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hiddenForExternalApp by LauncherWindowState.hiddenForExternalAppFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val canAnimate = isLifecycleStarted && !hiddenForExternalApp
    val needsPdcAnimation = when (visualMode) {
        PDC_VISUAL_ARCS -> true
        PDC_VISUAL_CLASSIC -> pdc.hasAny && effectiveFrame != null
        else -> false
    }
    val shouldTickPdc = canAnimate && needsPdcAnimation
    val activeClock = rememberSynced30FpsClock(syncedClock)
    val pdcAnimElapsedMsState = remember { mutableFloatStateOf(0f) }

    DisposableEffect(activeClock, shouldTickPdc) {
        if (!shouldTickPdc) {
            return@DisposableEffect onDispose {}
        }
        val unregister = activeClock.registerListener(tickDivisor = PDC_CLOCK_TICK_DIVISOR) { _, deltaNs ->
            val dtMs = (deltaNs / 1_000_000f).coerceIn(0f, 150f)
            pdcAnimElapsedMsState.floatValue =
                (pdcAnimElapsedMsState.floatValue + dtMs) % PDC_ANIM_WRAP_PERIOD_MS
        }
        onDispose { unregister() }
    }

    val cachedArcGeometry = remember(effectiveFrame) {
        effectiveFrame?.let { frame ->
            val ring0 = frame.rings.firstOrNull() ?: return@let null
            val frontPts = ring0.filter { angleWithinDeg(it.first, -35f, 35f) }
                .sortedBy { normFrom(it.first, -35f) }
            val rearPts = ring0.filter { angleWithinDeg(it.first, 145f, 215f) }
                .sortedBy { normFrom(it.first, 145f) }
            val frontNosePt = frontPts.minByOrNull { angularDiffDeg(it.first, 0f) }?.second
            val frontNoseDist = if (frontNosePt != null) {
                hypot(frontNosePt.x - frame.center.x, frontNosePt.y - frame.center.y)
            } else {
                100f
            }
            val rearTailPt = rearPts.minByOrNull { angularDiffDeg(it.first, 180f) }?.second
            val rearTailDist = if (rearTailPt != null) {
                hypot(rearTailPt.x - frame.center.x, rearTailPt.y - frame.center.y)
            } else {
                100f
            }
            CachedPdcArcGeometry(
                frontPts = frontPts,
                rearPts = rearPts,
                frontBumperRadius = frontNoseDist * 0.76f,
                rearBumperRadius = rearTailDist * 0.76f,
            )
        }
    }
    val reusablePath = remember { Path() }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val overlayAlpha = overlayAlphaState.value.coerceIn(0f, 1f)
        if (overlayAlpha <= 0.001f) return@Canvas
        val elapsedMs = pdcAnimElapsedMsState.floatValue
        val pulse = computePdcPulseValue(elapsedMs)
        val standbyJitter = computePdcStandbyJitterPhase(elapsedMs)
        when (visualMode) {
            PDC_VISUAL_ARCS -> {
                drawBendingPdcArcs(
                    pdc = pdc,
                    frame = effectiveFrame,
                    cachedGeometry = cachedArcGeometry,
                    standbyJitter = standbyJitter,
                    pulse = pulse,
                    overlayAlpha = overlayAlpha,
                )
            }
            PDC_VISUAL_CLASSIC -> {
                if (pdc.hasAny && effectiveFrame != null) {
                    drawPdcBands(
                        pdc = pdc,
                        frame = effectiveFrame,
                        driving = driving,
                        pulse = pulse,
                        overlayAlpha = overlayAlpha,
                        reusablePath = reusablePath,
                    )
                }
            }
        }
    }
}

private class CachedPdcArcGeometry(
    val frontPts: List<Pair<Float, Offset>>,
    val rearPts: List<Pair<Float, Offset>>,
    val frontBumperRadius: Float,
    val rearBumperRadius: Float,
)

private fun circularMidAngleDeg(thetaA: Float, thetaB: Float): Float {
    val diff = ((thetaB - thetaA + 540f) % 360f) - 180f
    return (thetaA + diff / 2f + 360f) % 360f
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
        if (distCm in LauncherPdcZones.PDC_MIN_CM..LauncherPdcZones.PDC_MAX_CM) {
            val pCh = ((LauncherPdcZones.PDC_MAX_CM - distCm.toFloat()) / (LauncherPdcZones.PDC_MAX_CM - LauncherPdcZones.PDC_MIN_CM)).coerceIn(0f, 1f)
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
    overlayAlpha: Float,
): Color {
    val baseAlpha = if (lineIdx == 0) 0.70f else 0.45f

    if (p <= 0.08f) {
        return Color(0xFF38BDF8).copy(alpha = (baseAlpha * overlayAlpha).coerceIn(0f, 1f))
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

    return blendedColor.copy(alpha = (alpha * overlayAlpha).coerceIn(0f, 1f))
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
    cachedGeometry: CachedPdcArcGeometry?,
    standbyJitter: Float,
    pulse: Float,
    overlayAlpha: Float,
) {
    val gap = 3.dp.toPx()
    val strokeWidth = 2.0.dp.toPx()
    val standbyDistPx = 82.5.dp.toPx() // 82.5dp from 3D bumper in standby
    val targetMinDistPx = 5.dp.toPx() // 5dp at maximum close approach
    val maxBendPx = standbyDistPx - targetMinDistPx // 82.5dp - 5dp = 77.5dp
    val maxJitter = 1.2.dp.toPx()

    if (frame != null && cachedGeometry != null) {
        val center = frame.center
        val channelAngles = frame.channelAngles

        fun drawBumperGroup(
            pts: List<Pair<Float, Offset>>,
            channels: List<LauncherPdcChannel>,
            bumperRadius: Float,
            isFront: Boolean,
        ) {
            if (pts.size >= 2) {
                for (lineIdx in 0..1) {
                    val lineOffsetGap = if (lineIdx == 0) -gap else gap
                    var prevPos = computeArcPoint(
                        center = center,
                        ringPt = pts[0].second,
                        theta = pts[0].first,
                        lineIdx = lineIdx,
                        ptIdx = 0,
                        bumperRadiusPx = bumperRadius,
                        lineOffsetGap = lineOffsetGap,
                        maxBendPx = maxBendPx,
                        maxJitter = maxJitter,
                        standbyJitter = standbyJitter,
                        pdc = pdc,
                        channels = channels,
                        channelAngles = channelAngles,
                    )
                    for (i in 0 until pts.lastIndex) {
                        val thetaA = pts[i].first
                        val (thetaB, ringPtB) = pts[i + 1]
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

                        // Circular midpoint avoids wrap-around bug across 356° -> 0° at nose center.
                        val midTheta = circularMidAngleDeg(thetaA, thetaB)
                        val pMid = proximityAtAngle(midTheta, channels, pdc, channelAngles)
                        val segmentColor = computeArcColor(pMid, lineIdx, pulse, overlayAlpha)

                        drawLine(
                            color = segmentColor,
                            start = prevPos,
                            end = posB,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        prevPos = posB
                    }
                }
            } else {
                drawFallbackSingleBumperArc(
                    isFront = isFront,
                    pdc = pdc,
                    standbyJitter = standbyJitter,
                    pulse = pulse,
                    overlayAlpha = overlayAlpha,
                    gapPx = gap,
                    strokeWidth = strokeWidth,
                    maxBendPx = maxBendPx,
                    maxJitter = maxJitter,
                )
            }
        }

        drawBumperGroup(cachedGeometry.frontPts, FRONT_CHANNELS, cachedGeometry.frontBumperRadius, isFront = true)
        drawBumperGroup(cachedGeometry.rearPts, REAR_CHANNELS, cachedGeometry.rearBumperRadius, isFront = false)
    } else {
        drawFallbackBendingArcs(pdc, standbyJitter, pulse, overlayAlpha, gap, strokeWidth, maxBendPx, maxJitter)
    }
}

private fun DrawScope.drawFallbackSingleBumperArc(
    isFront: Boolean,
    pdc: LauncherPdcZones,
    standbyJitter: Float,
    pulse: Float,
    overlayAlpha: Float,
    gapPx: Float,
    strokeWidth: Float,
    maxBendPx: Float,
    maxJitter: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val steps = 24
    val channels = if (isFront) FRONT_CHANNELS else REAR_CHANNELS
    val channelAngles = if (isFront) {
        mapOf(
            LauncherPdcChannel.FrontLeft to -28f,
            LauncherPdcChannel.FrontRight to 28f,
        )
    } else {
        mapOf(
            LauncherPdcChannel.RearLeft to 145f,
            LauncherPdcChannel.RearMidLeft to 168f,
            LauncherPdcChannel.RearMidRight to 192f,
            LauncherPdcChannel.RearRight to 215f,
        )
    }

    val lineGaps = listOf(-gapPx, gapPx)

    lineGaps.forEachIndexed { lineIdx, lineOffsetGap ->
        val baseStandbyPx = 82.5.dp.toPx() + lineOffsetGap

        for (i in 0 until steps) {
            val tA = i.toFloat() / steps
            val tB = (i + 1).toFloat() / steps

            val angleRadA = Math.toRadians((-38.0 + tA * 76.0)).toFloat()
            val angleRadB = Math.toRadians((-38.0 + tB * 76.0)).toFloat()
            val thetaA = if (isFront) Math.toDegrees(angleRadA.toDouble()).toFloat() else 180f + Math.toDegrees(angleRadA.toDouble()).toFloat()
            val thetaB = if (isFront) Math.toDegrees(angleRadB.toDouble()).toFloat() else 180f + Math.toDegrees(angleRadB.toDouble()).toFloat()

            val pA = proximityAtAngle(thetaA, channels, pdc, channelAngles)
            val pB = proximityAtAngle(thetaB, channels, pdc, channelAngles)

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

            val posA = if (isFront) Offset(cx + sin(angleRadA) * rA, cy - cos(angleRadA) * rA) else Offset(cx + sin(angleRadA) * rA, cy + cos(angleRadA) * rA)
            val posB = if (isFront) Offset(cx + sin(angleRadB) * rB, cy - cos(angleRadB) * rB) else Offset(cx + sin(angleRadB) * rB, cy + cos(angleRadB) * rB)

            val pMid = (pA + pB) / 2f
            val color = computeArcColor(pMid, lineIdx, pulse, overlayAlpha)

            drawLine(color = color, start = posA, end = posB, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawFallbackBendingArcs(
    pdc: LauncherPdcZones,
    standbyJitter: Float,
    pulse: Float,
    overlayAlpha: Float,
    gapPx: Float,
    strokeWidth: Float,
    maxBendPx: Float,
    maxJitter: Float,
) {
    drawFallbackSingleBumperArc(true, pdc, standbyJitter, pulse, overlayAlpha, gapPx, strokeWidth, maxBendPx, maxJitter)
    drawFallbackSingleBumperArc(false, pdc, standbyJitter, pulse, overlayAlpha, gapPx, strokeWidth, maxBendPx, maxJitter)
}

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
    overlayAlpha: Float,
    reusablePath: Path,
) {
    fun segsFor(channels: List<LauncherPdcChannel>, pxPerMeter: Float): List<PdcSeg> =
        channels.mapNotNull { ch ->
            val level = LauncherPdcZones.levelFor(pdc.distanceCm(ch))
            if (level == LauncherPdcLevel.None) return@mapNotNull null
            val angle = frame.channelAngles[ch] ?: return@mapNotNull null
            PdcSeg(
                angleDeg = angle,
                pxPerMeter = pxPerMeter,
                level = level,
            )
        }.sortedBy { it.angleDeg }

    fun drawSegGroup(isRear: Boolean, segs: List<PdcSeg>) {
        if (segs.isEmpty()) return
        val maxHalfSpan = if (isRear) 11f else 10f
        segs.forEachIndexed { i, seg ->
            val prevGap = if (i > 0) angularDiffDeg(seg.angleDeg, segs[i - 1].angleDeg) else 40f
            val nextGap = if (i < segs.lastIndex) angularDiffDeg(segs[i + 1].angleDeg, seg.angleDeg) else 40f
            val halfSpan = (minOf(prevGap, nextGap) / 2f - 0.75f).coerceIn(4f, maxHalfSpan)
            drawPdcSegment(frame, seg, seg.angleDeg - halfSpan, halfSpan * 2f, driving, pulse, overlayAlpha, reusablePath)
        }
    }

    if (!driving) {
        drawSegGroup(isRear = false, segs = segsFor(FRONT_CHANNELS, frame.pxPerMeterFront))
    }
    drawSegGroup(isRear = true, segs = segsFor(REAR_CHANNELS, frame.pxPerMeterRear))
}

private fun DrawScope.drawPdcSegment(
    frame: LauncherPdcRingFrame,
    seg: PdcSeg,
    startAngle: Float,
    sweep: Float,
    driving: Boolean,
    pulse: Float,
    overlayAlpha: Float,
    reusablePath: Path,
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

    val ringIndex: IntArray = when {
        forceAllRings -> intArrayOf(0, 1, 2)
        seg.level == LauncherPdcLevel.Far -> intArrayOf(2)
        seg.level == LauncherPdcLevel.Mid -> intArrayOf(1, 2)
        seg.level == LauncherPdcLevel.Near -> intArrayOf(0, 1, 2)
        else -> return
    }
    val radialBoost = extraRadialBoost ?: if (driving) 1.05f else 1f
    val strokeWidth = (seg.pxPerMeter * if (driving) 0.13f else 0.12f).coerceIn(6f, 14f)
    val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    ringIndex.forEach { ring ->
        val ringPts = frame.rings.getOrNull(ring) ?: return@forEach
        val pts = ringArcPoints(ringPts, startAngle, sweep)
        if (pts.size < 2) return@forEach
        reusablePath.reset()
        pts.forEachIndexed { index, pt ->
            val x = frame.center.x + (pt.x - frame.center.x) * radialBoost
            val y = frame.center.y + (pt.y - frame.center.y) * radialBoost
            if (index == 0) reusablePath.moveTo(x, y) else reusablePath.lineTo(x, y)
        }
        val alpha = ((1f - ring * 0.16f) * alphaScale * overlayAlpha).coerceIn(0f, 1f)
        drawPath(
            path = reusablePath,
            color = color.copy(alpha = alpha),
            style = strokeStyle,
        )
    }
}

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
