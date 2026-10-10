package vad.dashing.tbox.ui.launcher

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.isActive
import vad.dashing.tbox.LauncherWindowState
import vad.dashing.tbox.R
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun LauncherVirtualRoad(
    speedKmh: Float,
    @Suppress("UNUSED_PARAMETER") steerAngleDeg: Float,
    adas: LauncherAdasState = LauncherAdasState(),
    modifier: Modifier = Modifier,
    steerPreview: Boolean = false,
    /** Show road only in Drive (D), not when the engine merely starts. */
    inDriveGear: Boolean = false,
) {
    val bsdLeftActive = inDriveGear && adas.rearThreats.bsdLeft != LauncherRearThreatLevel.Off
    val bsdRightActive = inDriveGear && adas.rearThreats.bsdRight != LauncherRearThreatLevel.Off

    val bsdLeftProgress by animateFloatAsState(
        targetValue = if (bsdLeftActive) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "bsdLeftProgress",
    )

    val bsdRightProgress by animateFloatAsState(
        targetValue = if (bsdRightActive) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "bsdRightProgress",
    )

    val driveTarget = when {
        inDriveGear -> 1f
        steerPreview -> 1f
        else -> 0f
    }
    val driveBlend by animateFloatAsState(
        targetValue = driveTarget,
        animationSpec = tween(280),
        label = "roadDriveBlend",
    )

    var roadPhase by remember { mutableFloatStateOf(0f) }
    val currentSpeedKmh by rememberUpdatedState(speedKmh)
    val currentDriveBlend by rememberUpdatedState(driveBlend)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                val started = lifecycleOwner.lifecycle.currentState
                    .isAtLeast(Lifecycle.State.STARTED)
                val hidden = LauncherWindowState.hiddenForExternalApp
                val spd = currentSpeedKmh
                val blend = currentDriveBlend
                if (started && !hidden && spd > 0.5f && blend > 0.02f) {
                    roadPhase += spd * 0.03f
                }
            }
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val presetsRevision by LauncherAppConfigStore.cruisePresetsRevisionFlow.collectAsStateWithLifecycle()
    val storedTimeGap = remember(context, presetsRevision) {
        LauncherAppConfigStore.timeGapLevel(context)
    }
    val textSizeRevision by LauncherAppConfigStore.adasDistanceTextSizeRevisionFlow.collectAsStateWithLifecycle()
    val distanceTextSizeSp = remember(context, textSizeRevision) {
        LauncherAppConfigStore.adasDistanceTextSize(context)
    }
    val offsetRevision by LauncherAppConfigStore.adasDistanceLabelOffsetRevisionFlow.collectAsStateWithLifecycle()
    val distanceLabelOffsetRatio = remember(context, offsetRevision) {
        LauncherAppConfigStore.adasDistanceLabelOffset(context)
    }

    val leadSprites = rememberLeadObjectSprites()
    val leadVisual = rememberLeadObjectVisual(adas)
    val lanesActive = adas.leftLane != LauncherAdasLaneVisualization.Hidden ||
        adas.rightLane != LauncherAdasLaneVisualization.Hidden
    val isCruiseActive = adas.accActive || adas.accStandby || ((adas.accSetSpeedKmh ?: 0) > 0)

    val laneAnimProgress by animateFloatAsState(
        targetValue = if (lanesActive) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "laneGlowAnimProgress",
    )

    val cruiseBeamProgress by animateFloatAsState(
        targetValue = if (isCruiseActive) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "cruiseBeamAnimProgress",
    )

    val bsdRoadPulse by rememberInfiniteTransition(label = "bsdRoadPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bsdRoadPulseValue",
    )

    if (driveBlend <= 0.01f) return
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = driveBlend },
    ) {
        drawVirtualRoad(
            roadPhase = roadPhase,
            speedKmh = speedKmh,
            adas = adas,
            leadSprites = leadSprites,
            leadVisual = leadVisual,
            distanceTextSizeSp = distanceTextSizeSp,
            distanceLabelOffsetRatio = distanceLabelOffsetRatio,
            density = density,
            storedTimeGap = storedTimeGap,
            laneProgress = laneAnimProgress,
            beamProgress = cruiseBeamProgress,
            bsdPulse = bsdRoadPulse,
            bsdLeftProgress = bsdLeftProgress,
            bsdRightProgress = bsdRightProgress,
        )
    }
}

@Composable
internal fun LauncherEggRaceCarsLayer(
    cars: List<LauncherEggRaceCar>,
    sprites: RaceOncomingSprites,
    modifier: Modifier = Modifier,
    minDepth: Float = 0f,
    maxDepth: Float = 1f,
) {
    val visible = cars.filter { it.depth >= minDepth && it.depth < maxDepth }
    if (visible.isEmpty()) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.24f
        val vanishX = w / 2f
        fun halfWidthAt(t: Float): Float =
            (w * 0.14f) * (1f - t).pow(1.25f) + (w * 1.10f) * t.pow(1.05f)
        fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.30f
        fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
        fun centerXAt(t: Float): Float = vanishX
        drawRaceCars(
            cars = visible,
            sprites = sprites,
            centerXAt = ::centerXAt,
            laneOffsetAt = ::laneOffsetAt,
            halfWidthAt = ::halfWidthAt,
            yAt = ::yAt,
        )
    }
}


internal class RaceOncomingSprites(
    val left: List<ImageBitmap>,
    val center: List<ImageBitmap>,
    val right: List<ImageBitmap>,
) {
    fun forLane(lane: Int, paintIndex: Int): ImageBitmap {
        val pack = when {
            lane < 0 -> left
            lane > 0 -> right
            else -> center
        }
        return pack[paintIndex.mod(pack.size)]
    }
}

@Composable
private fun rememberRoadSprite(id: Int): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(id, res) { ImageBitmap.imageResource(res, id) }
}

private data class LeadObjectSprites(
    val car: ImageBitmap,
    val truck: ImageBitmap,
    val motorcycle: ImageBitmap,
    val pedestrian: ImageBitmap,
) {
    fun forType(type: LauncherAdasFrontObjectType): ImageBitmap? = when (type) {
        LauncherAdasFrontObjectType.Car -> car
        LauncherAdasFrontObjectType.Truck -> truck
        LauncherAdasFrontObjectType.Motorcycle -> motorcycle
        LauncherAdasFrontObjectType.Pedestrian -> pedestrian
        else -> null
    }
}

private data class LeadObjectVisual(
    val type: LauncherAdasFrontObjectType,
    val distanceM: Int,
    val alpha: Float,
)

@Composable
private fun rememberLeadObjectVisual(adas: LauncherAdasState): LeadObjectVisual? {
    val targetType = adas.frontObject.takeIf { it.valid }?.type
        ?.takeUnless { it == LauncherAdasFrontObjectType.None }
    val targetDistance = adas.frontObject.displayDistanceM
    var shownType by remember { mutableStateOf(targetType) }
    var lastDistance by remember { mutableStateOf(targetDistance ?: 35) }
    val alpha = remember { Animatable(if (targetType != null) 1f else 0f) }
    val alphaValue by alpha.asState()

    SideEffect {
        if (targetDistance != null) lastDistance = targetDistance
    }

    LaunchedEffect(targetType) {
        val current = shownType
        when {
            targetType != null && current == targetType -> {
                if (alpha.value < 0.999f) {
                    alpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                }
            }
            targetType != null && current == null -> {
                shownType = targetType
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            targetType != null -> {
                alpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                shownType = targetType
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            current != null -> {
                alpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                shownType = null
            }
        }
    }

    val type = shownType ?: return null
    if (alphaValue < 0.01f) return null
    return LeadObjectVisual(type = type, distanceM = lastDistance, alpha = alphaValue)
}

@Composable
private fun rememberLeadObjectSprites(): LeadObjectSprites {
    val dark = LauncherThemeState.darkTheme
    return LeadObjectSprites(
        car = rememberRoadSprite(
            if (dark) R.drawable.ic_adas_lead_car_dark else R.drawable.ic_adas_lead_car,
        ),
        truck = rememberRoadSprite(
            if (dark) R.drawable.ic_adas_lead_truck_dark else R.drawable.ic_adas_lead_truck,
        ),
        motorcycle = rememberRoadSprite(
            if (dark) R.drawable.ic_adas_lead_moto_dark else R.drawable.ic_adas_lead_moto,
        ),
        pedestrian = rememberRoadSprite(
            if (dark) R.drawable.ic_adas_lead_pedestrian_dark else R.drawable.ic_adas_lead_pedestrian,
        ),
    )
}

@Composable
internal fun rememberRaceOncomingSprites(): RaceOncomingSprites {
    val res = LocalContext.current.resources
    return remember(res) {
        fun load(id: Int) = ImageBitmap.imageResource(res, id)
        RaceOncomingSprites(
            left = listOf(
                load(R.drawable.race_oncoming_l_cheqi01),
                load(R.drawable.race_oncoming_l_cheqi02),
                load(R.drawable.race_oncoming_l_cheqi03),
                load(R.drawable.race_oncoming_l_cheqi04),
                load(R.drawable.race_oncoming_l_cheqi05),
                load(R.drawable.race_oncoming_l_cheqi06),
            ),
            center = listOf(
                load(R.drawable.race_oncoming_c_cheqi01),
                load(R.drawable.race_oncoming_c_cheqi02),
                load(R.drawable.race_oncoming_c_cheqi03),
                load(R.drawable.race_oncoming_c_cheqi04),
                load(R.drawable.race_oncoming_c_cheqi05),
                load(R.drawable.race_oncoming_c_cheqi06),
            ),
            right = listOf(
                load(R.drawable.race_oncoming_r_cheqi01),
                load(R.drawable.race_oncoming_r_cheqi02),
                load(R.drawable.race_oncoming_r_cheqi03),
                load(R.drawable.race_oncoming_r_cheqi04),
                load(R.drawable.race_oncoming_r_cheqi05),
                load(R.drawable.race_oncoming_r_cheqi06),
            ),
        )
    }
}

private fun DrawScope.drawRoadCarSprite(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    destW: Float,
    flipX: Boolean = false,
    alpha: Float = 1f,
    /** 1 = sprite bottom sits on [cy]; lower values sink the car into the road. */
    ground: Float = 0.88f,
) {
    val aspect = image.height.toFloat() / image.width.toFloat()
    val w = destW.coerceAtLeast(2f)
    val h = (w * aspect).coerceAtLeast(2f)
    translate(cx, cy) {
        scale(if (flipX) -1f else 1f, 1f, pivot = Offset.Zero) {
            drawImage(
                image = image,
                dstOffset = IntOffset((-w / 2f).roundToInt(), (-h * ground).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private fun DrawScope.drawVirtualRoad(
    roadPhase: Float,
    speedKmh: Float,
    adas: LauncherAdasState,
    leadSprites: LeadObjectSprites,
    leadVisual: LeadObjectVisual?,
    distanceTextSizeSp: Int = 14,
    distanceLabelOffsetRatio: Float = 0.50f,
    density: Float = 1.5f,
    storedTimeGap: Int = 2,
    laneProgress: Float = 1f,
    beamProgress: Float = 1f,
    bsdPulse: Float = 1f,
    bsdLeftProgress: Float = 0f,
    bsdRightProgress: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.24f
    // Keep road straight — car body no longer yaws with steering in drive mode.
    val vanishX = w / 2f
    // Cap ego lane geometry at reference panel width 240dp so the car model and lane lines stay calibrated.
    val roadW = w.coerceAtMost(240f * density)
    fun halfWidthAt(t: Float): Float =
        (w * 0.14f) * (1f - t).pow(1.25f) + (w * 1.10f) * t.pow(1.05f)
    // Ego (center) lane half-width — ACC beam and LKA sit on this strip.
    fun laneOffsetAt(t: Float): Float =
        ((roadW * 0.14f) * (1f - t).pow(1.25f) + (roadW * 1.10f) * t.pow(1.05f)) * 0.30f
    fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
    fun centerXAt(t: Float): Float = vanishX

    drawRoadSurface(
        horizonY = horizonY,
        centerXAt = ::centerXAt,
        halfWidthAt = ::halfWidthAt,
        yAt = ::yAt,
    )

    drawAdasLaneAssist(
        adas = adas,
        laneProgress = laneProgress,
        centerXAt = ::centerXAt,
        laneOffsetAt = ::laneOffsetAt,
        yAt = ::yAt,
    )

    val dashSpacing = (58f + speedKmh * 0.42f).coerceIn(48f, 128f)
    val phase = roadPhase % dashSpacing
    var y = horizonY + phase

    while (y < h) {
        val t = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        val dashLen = (22f + t * 40f).coerceAtLeast(16f)
        val nextT = (((y + dashLen) - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        // Stronger in the mid-road, dissolving into the horizon haze and
        // fading out again at the bottom edge (under the player).
        val fade = distanceFade(t) * bottomEdgeFade(t)
        // Ego lane borders plus the two neighbouring lanes, as on a real HMI.
        listOf(
            -1f to 1f,
            1f to 1f,
            -3f to 0.55f,
            3f to 0.55f,
        ).forEach { (side, weight) ->
            val x1 = centerXAt(t) + side * laneOffsetAt(t)
            val x2 = centerXAt(nextT) + side * laneOffsetAt(nextT)
            if (x1 < -w * 0.7f || x1 > w * 1.7f) return@forEach
            val isEgoLane = abs(side) in 0.5f..2.0f
            val baseAlpha = if (isEgoLane && laneProgress > 0f) 0.85f else 0.50f
            val alpha = baseAlpha * fade * weight
            if (alpha < 0.02f) return@forEach

            val laneState = when {
                side < -0.5f && side > -2.0f -> adas.leftLane
                side > 0.5f && side < 2.0f -> adas.rightLane
                else -> LauncherAdasLaneVisualization.Hidden
            }
            val inactiveDashColor = Color(0xFF9CA3AF)
            val lkaActive = adas.lkaStatusCode == 2

            val baseDashColor = when {
                !isEgoLane -> inactiveDashColor
                laneState == LauncherAdasLaneVisualization.Warning -> Color(0xFFEF4444)
                laneState != LauncherAdasLaneVisualization.Hidden -> if (lkaActive) LkaBlueCore else LdwBrightGreen
                else -> inactiveDashColor
            }

            drawLine(
                color = baseDashColor.copy(alpha = alpha),
                start = Offset(x1, y),
                end = Offset(x2, y + dashLen),
                strokeWidth = (1.6f + 1.6f * t) * (if (isEgoLane && laneProgress > 0f) 1.25f else 1.0f),
            )
        }
        y += dashSpacing * (0.55f + 0.85f * t)
    }

    drawHorizonHaze(horizonY = horizonY, canvasHeight = h)

    drawBsdBeam(
        threats = adas.rearThreats,
        pulse = bsdPulse,
        leftProgress = bsdLeftProgress,
        rightProgress = bsdRightProgress,
        centerXAt = ::centerXAt,
        laneOffsetAt = ::laneOffsetAt,
        yAt = ::yAt,
    )

    val now = android.os.SystemClock.uptimeMillis()
    val showDistanceSteps = beamProgress > 0.05f && (adas.timeGapFlashUntilMs > now)
    val timeGapLevel = (adas.timeGapLevel?.takeIf { it in 1..3 } ?: storedTimeGap).coerceIn(1, 3)

    val frontDistance = leadVisual?.distanceM ?: adas.frontObject.displayDistanceM
    if (leadVisual != null) {
        if (beamProgress > 0.005f) {
            drawAccBeam(
                objectDistanceM = frontDistance,
                beamProgress = beamProgress,
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
                yAt = ::yAt,
                alert = adas.fcwActive || adas.distanceWarning,
            )
        }
        if (showDistanceSteps) {
            drawAccDistanceSteps(
                activeLevel = timeGapLevel,
                targetDepth = distanceToRoadDepth(leadVisual.distanceM),
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
                yAt = ::yAt,
                tint = if (adas.fcwActive || adas.distanceWarning) Color(0xFFEF4444) else LauncherColors.AccentCyan,
            )
        }
        drawFrontObject(
            adas = adas,
            type = leadVisual.type,
            distanceM = leadVisual.distanceM,
            objectAlpha = leadVisual.alpha,
            leadSprites = leadSprites,
            centerXAt = ::centerXAt,
            halfWidthAt = ::halfWidthAt,
            yAt = ::yAt,
            distanceTextSizeSp = distanceTextSizeSp,
            distanceLabelOffsetRatio = distanceLabelOffsetRatio,
        )
    } else {
        if (beamProgress > 0.005f) {
            // No target ahead — stretch the ACC path to the horizon.
            drawAccBeam(
                objectDistanceM = null,
                beamProgress = beamProgress,
                toHorizon = true,
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
                yAt = ::yAt,
                alert = false,
            )
        }
        if (showDistanceSteps) {
            drawAccDistanceSteps(
                activeLevel = timeGapLevel,
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
                yAt = ::yAt,
                tint = LauncherColors.AccentCyan,
            )
        }
    }

}

/**
 * Distance falloff for everything painted on the road: full strength near ego,
 * dissolving into the haze towards the horizon.
 */
private fun distanceFade(t: Float): Float = (t.coerceIn(0f, 1f)).pow(0.62f)

/** Dissolve lane dashes in the last third so they do not hit the panel edge. */
private fun bottomEdgeFade(t: Float): Float {
    val start = 0.62f
    if (t <= start) return 1f
    val u = ((t - start) / (1f - start)).coerceIn(0f, 1f)
    return (1f - u).pow(1.45f)
}

/** Asphalt wedge from the horizon down to ego, dark and fading out with distance. */
private fun DrawScope.drawRoadSurface(
    horizonY: Float,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    val surface = Path().apply {
        moveTo(centerXAt(0f) - halfWidthAt(0f), yAt(0f))
        lineTo(centerXAt(0f) + halfWidthAt(0f), yAt(0f))
        for (i in 1..10) {
            val t = i / 10f
            lineTo(centerXAt(t) + halfWidthAt(t), yAt(t))
        }
        for (i in 10 downTo 1) {
            val t = i / 10f
            lineTo(centerXAt(t) - halfWidthAt(t), yAt(t))
        }
        close()
    }
    drawPath(
        path = surface,
        brush = Brush.verticalGradient(
            0f to Color(0xFF1B2634).copy(alpha = 0f),
            0.35f to Color(0xFF1B2634).copy(alpha = 0.55f),
            1f to Color(0xFF232F3E).copy(alpha = 0.92f),
            startY = horizonY,
            endY = size.height,
        ),
    )
}

/** Fog band at the horizon that swallows the road and the markings. */
private fun DrawScope.drawHorizonHaze(horizonY: Float, canvasHeight: Float) {
    val bg = LauncherColors.LeftPanelBg
    val hazeBottom = horizonY + (canvasHeight - horizonY) * 0.34f
    drawRect(
        brush = Brush.verticalGradient(
            0f to bg,
            0.45f to bg.copy(alpha = 0.72f),
            1f to bg.copy(alpha = 0f),
            startY = horizonY - canvasHeight * 0.06f,
            endY = hazeBottom,
        ),
        topLeft = Offset(0f, horizonY - canvasHeight * 0.06f),
        size = Size(size.width, hazeBottom - horizonY + canvasHeight * 0.06f),
    )
}

/**
 * BSD adjacent lane glowing beam: a glowing corridor beam along the lane to the right or left
 * of the car model, drawn in the same style as [drawAccBeam].
 */
private fun DrawScope.drawBsdBeam(
    threats: LauncherRearThreats,
    pulse: Float,
    leftProgress: Float,
    rightProgress: Float,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    if (leftProgress < 0.005f && rightProgress < 0.005f) return

    val farDepth = 0.62f // Start of BSD beam at 62% from horizon
    val nearDepth = 1.00f

    listOf(
        Triple(threats.bsdLeft, -1f, leftProgress),  // Lane to the left of the car model
        Triple(threats.bsdRight, 1f, rightProgress),  // Lane to the right of the car model
    ).forEach { (level, side, progress) ->
        if (progress < 0.005f) return@forEach

        val (tint, isPulsing, maxAlpha) = when (level) {
            LauncherRearThreatLevel.Alert -> Triple(Color(0xFFFF1744), true, 0.65f) // High-intensity bright red, pulsing
            LauncherRearThreatLevel.Caution -> Triple(Color(0xFFFFAB00), false, 0.52f) // Saturated vibrant yellow/amber, steady
            LauncherRearThreatLevel.Off -> Triple(Color(0xFFFFAB00), false, 0.52f) // Fade-out color
        }

        val farCenter = centerXAt(farDepth) + side * 2.0f * laneOffsetAt(farDepth)
        val farHalf = laneOffsetAt(farDepth) * 0.85f

        val nearCenter = centerXAt(nearDepth) + side * 2.0f * laneOffsetAt(nearDepth)
        val nearHalf = laneOffsetAt(nearDepth) * 0.85f

        val beam = Path().apply {
            moveTo(farCenter - farHalf, yAt(farDepth))
            lineTo(farCenter + farHalf, yAt(farDepth))
            lineTo(nearCenter + nearHalf, yAt(nearDepth))
            lineTo(nearCenter - nearHalf, yAt(nearDepth))
            close()
        }

        val pulseFactor = if (isPulsing) pulse.coerceIn(0.25f, 1f) else 1.0f
        val effectiveMaxAlpha = maxAlpha * progress.coerceIn(0f, 1f) * pulseFactor

        drawPath(
            path = beam,
            brush = Brush.verticalGradient(
                0.00f to tint.copy(alpha = 0f),
                0.20f to tint.copy(alpha = effectiveMaxAlpha * 0.35f),
                0.55f to tint.copy(alpha = effectiveMaxAlpha),
                0.85f to tint.copy(alpha = effectiveMaxAlpha * 0.55f),
                1.00f to tint.copy(alpha = 0f),
                startY = yAt(farDepth),
                endY = yAt(nearDepth),
            ),
        )
    }
}

/** ACC following beam: a glowing wedge from ego up to the tracked target,
 *  confined to the center (ego) lane — not the outer road shoulders. */
private fun DrawScope.drawAccBeam(
    objectDistanceM: Int?,
    beamProgress: Float = 1f,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
    alert: Boolean,
    toHorizon: Boolean = false,
) {
    if (beamProgress < 0.005f) return

    val fullTargetDepth = if (toHorizon || objectDistanceM == null) {
        0.05f
    } else {
        distanceToRoadDepth(objectDistanceM)
    }
    // Start in front of the 3D car's front bumper on the road ahead.
    val egoDepth = 0.72f
    val animatedTargetDepth = egoDepth - (egoDepth - fullTargetDepth) * beamProgress.coerceIn(0f, 1f)

    val nearHalf = laneOffsetAt(egoDepth) * 0.85f
    val farHalf = laneOffsetAt(animatedTargetDepth) * 0.85f
    val beam = Path().apply {
        moveTo(centerXAt(egoDepth) - nearHalf, yAt(egoDepth))
        lineTo(centerXAt(animatedTargetDepth) - farHalf, yAt(animatedTargetDepth))
        lineTo(centerXAt(animatedTargetDepth) + farHalf, yAt(animatedTargetDepth))
        lineTo(centerXAt(egoDepth) + nearHalf, yAt(egoDepth))
        close()
    }
    val tint = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    val alphaFactor = beamProgress.coerceIn(0f, 1f)
    drawPath(
        path = beam,
        brush = Brush.verticalGradient(
            0.00f to tint.copy(alpha = 0f),
            0.20f to tint.copy(alpha = 0.08f * alphaFactor),
            0.50f to tint.copy(alpha = 0.22f * alphaFactor),
            0.80f to tint.copy(alpha = 0.08f * alphaFactor),
            1.00f to tint.copy(alpha = 0f),
            startY = yAt(animatedTargetDepth),
            endY = yAt(egoDepth),
        ),
    )
}

private fun DrawScope.drawAccDistanceSteps(
    activeLevel: Int,
    targetDepth: Float? = null,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
    tint: Color,
) {
    // 3 steps in front of the car on the road ahead (1 = Near/Близко, 2 = Mid/Средне, 3 = Far/Далеко)
    val egoDepth = 0.72f
    val depths = if (targetDepth != null) {
        val startD = (egoDepth - 0.16f).coerceAtMost(0.54f)
        val endD = (targetDepth + 0.04f).coerceAtMost(startD - 0.06f)
        floatArrayOf(
            startD,
            startD - (startD - endD) * 0.5f,
            endD,
        )
    } else {
        floatArrayOf(0.42f, 0.28f, 0.16f)
    }
    depths.forEachIndexed { idx, t ->
        val stepNumber = idx + 1
        val isActive = stepNumber <= activeLevel
        val y = yAt(t) - 20.dp.toPx()
        val cx = centerXAt(t)
        val halfW = laneOffsetAt(t) * 0.72f
        val chevronH = 8.dp.toPx() * (0.6f + t * 0.4f)
        val stepPath = Path().apply {
            moveTo(cx - halfW, y + chevronH)
            lineTo(cx, y)
            lineTo(cx + halfW, y + chevronH)
        }
        drawPath(
            path = stepPath,
            color = if (isActive) tint.copy(alpha = 0.90f) else tint.copy(alpha = 0.20f),
            style = Stroke(
                width = if (isActive) 4.5.dp.toPx() else 2.0.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private val LdwBrightGreen = Color(0xFF22C55E)
private val LkaBlueCore = Color(0xFF3B82F6)
private val LkaBlueOuter = Color(0xFF60A5FA)

private fun DrawScope.drawAdasLaneAssist(
    adas: LauncherAdasState,
    laneProgress: Float,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    if (laneProgress < 0.01f) return

    val minT = (1f - laneProgress).coerceIn(0f, 1f)
    val alphaFactor = laneProgress.coerceIn(0f, 1f)

    listOf(
        adas.leftLane to -1f,
        adas.rightLane to 1f,
    ).forEach { (lane, side) ->
        if (lane == LauncherAdasLaneVisualization.Hidden && laneProgress >= 0.99f) return@forEach
        val isWarning = lane == LauncherAdasLaneVisualization.Warning

        val lanePath = Path()
        var first = true
        val steps = 16
        for (i in 0..steps) {
            val fraction = i / steps.toFloat()
            val t = 1f - (1f - minT) * fraction
            val pt = Offset(
                centerXAt(t) + side * laneOffsetAt(t),
                yAt(t),
            )
            if (first) {
                lanePath.moveTo(pt.x, pt.y)
                first = false
            } else {
                lanePath.lineTo(pt.x, pt.y)
            }
        }

        if (isWarning) {
            val warningColor = Color(0xFFEF4444)
            drawPath(
                path = lanePath,
                color = warningColor.copy(alpha = 0.35f * alphaFactor),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = lanePath,
                color = warningColor.copy(alpha = 0.90f * alphaFactor),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else if (adas.lkaStatusCode == 2) {
            // NGP / LKA active: Solid Blue with subtle, soft glow animating bottom-to-top
            drawPath(
                path = lanePath,
                color = LkaBlueOuter.copy(alpha = 0.18f * alphaFactor),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = lanePath,
                color = LkaBlueCore.copy(alpha = 0.35f * alphaFactor),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = lanePath,
                color = LkaBlueCore.copy(alpha = 0.85f * alphaFactor),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun DrawScope.drawRaceCars(
    cars: List<LauncherEggRaceCar>,
    sprites: RaceOncomingSprites,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    cars.sortedBy { it.depth }.forEach { car ->
        val t = car.depth.coerceIn(0.02f, 0.90f)
        val cx = centerXAt(t) + car.lane * 2f * laneOffsetAt(t)
        val cy = yAt(t)
        val roadHalf = halfWidthAt(t)
        val approach = (t / 0.48f).coerceIn(0f, 1f)
        val destW = roadHalf * (0.38f + 0.22f * approach)
        drawRoadCarSprite(
            image = sprites.forLane(car.lane, car.paintIndex),
            cx = cx,
            cy = cy,
            destW = destW,
        )
    }
}

private fun DrawScope.drawFrontObject(
    adas: LauncherAdasState,
    type: LauncherAdasFrontObjectType,
    distanceM: Int,
    objectAlpha: Float,
    leadSprites: LeadObjectSprites,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
    distanceTextSizeSp: Int = 14,
    distanceLabelOffsetRatio: Float = 0.50f,
) {
    val depth = distanceToRoadDepth(distanceM)
    val cx = centerXAt(depth)
    val cy = yAt(depth)
    val roadHalf = halfWidthAt(depth)
    val alert = adas.fcwActive || adas.distanceWarning || adas.aebHint || adas.accTakeOver
    val baseColor = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    val fillColor = baseColor.copy(alpha = (if (alert) 0.58f else 0.40f) * objectAlpha)
    val strokeColor = baseColor.copy(alpha = 0.92f * objectAlpha)

    val sprite = leadSprites.forType(type)
    if (sprite != null) {
        val destW = leadSpriteWidth(type, roadHalf)
        val destH = destW * sprite.height / sprite.width.toFloat()
        if (alert) {
            drawOval(
                color = Color(0xFFEF4444).copy(alpha = 0.32f * objectAlpha),
                topLeft = Offset(cx - destW * 0.52f, cy - destH * 0.08f),
                size = Size(destW * 1.04f, destH * 0.18f),
            )
        }
        drawRoadCarSprite(
            image = sprite,
            cx = cx,
            cy = cy,
            destW = destW,
            alpha = objectAlpha,
            ground = 1f,
        )
    } else {
        val (objW, objH) = objectSizeForType(type, roadHalf)
        when (type) {
            LauncherAdasFrontObjectType.Bicycle ->
                drawLeadBicycleBody(cx = cx, cy = cy, objW = objW, objH = objH * 1.10f)
            LauncherAdasFrontObjectType.Bus ->
                drawLeadBusBody(cx = cx, cy = cy, objW = objW * 1.15f, objH = objH * 1.20f)
            else -> drawFrontObjectSilhouette(
                type = type,
                cx = cx,
                cy = cy,
                objW = objW,
                objH = objH,
                fill = fillColor,
                stroke = strokeColor,
            )
        }
    }

    val distanceColor = when {
        alert -> Color(0xFFEF4444)
        distanceM < 5 -> Color(0xFFEF4444)
        distanceM < 10 -> Color(0xFFFFA000)
        else -> LauncherColors.AccentCyan
    }
    val labelColor = distanceColor.copy(alpha = objectAlpha)

    val text = "${distanceM} м"
    val baseSizeSp = distanceTextSizeSp.toFloat()
    val paint = Paint().apply {
        isAntiAlias = true
        color = labelColor.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = (baseSizeSp * 1.0f).coerceIn(12f, 50f)
        typeface = Typeface.DEFAULT_BOLD
    }

    val textWidth = paint.measureText(text)
    val textHeight = abs(paint.ascent()) + paint.descent()
    val paddingX = 10f
    val paddingY = 4f
    val halfPillHeight = textHeight / 2f + paddingY

    val leadCarBottomY = cy
    val egoCarFrontY = yAt(0.49f) // Exact top windshield boundary of 3D car model on screen
    val targetLabelCy = leadCarBottomY + 8.dp.toPx() + halfPillHeight
    val maxAllowedCy = egoCarFrontY - halfPillHeight + 14.dp.toPx() // lowered by 16dp at min distance

    val labelCx = cx
    val labelCy = minOf(targetLabelCy, maxAllowedCy)

    val pillRect = RectF(
        labelCx - textWidth / 2f - paddingX,
        labelCy - textHeight / 2f - paddingY,
        labelCx + textWidth / 2f + paddingX,
        labelCy + textHeight / 2f + paddingY,
    )

    // Soft dark background pill for contrast against asphalt (no stroke border)
    drawContext.canvas.nativeCanvas.drawRoundRect(
        pillRect,
        10f,
        10f,
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb((0xB0 * objectAlpha).toInt(), 0x0F, 0x17, 0x2A)
            style = Paint.Style.FILL
        },
    )

    drawContext.canvas.nativeCanvas.drawText(
        text,
        labelCx,
        labelCy + (textHeight / 2f) - paint.descent(),
        paint,
    )
}

private fun DrawScope.drawLeadBicycleBody(cx: Float, cy: Float, objW: Float, objH: Float) {
    val w = objW
    val h = objH
    val wheelW = w * 0.36f
    val wheelH = h * 0.20f
    drawOval(Color(0xFF14161A), Offset(cx - wheelW / 2f, cy - wheelH * 0.50f), Size(wheelW, wheelH))
    drawOval(
        Color(0xFF9AA3AE),
        Offset(cx - wheelW * 0.18f, cy - wheelH * 0.22f),
        Size(wheelW * 0.36f, wheelH * 0.36f),
        style = Stroke(width = 2f),
    )
    drawLine(
        color = Color(0xFFD7DCE4),
        start = Offset(cx, cy - h * 0.18f),
        end = Offset(cx, cy - h * 0.78f),
        strokeWidth = 2.2f,
    )
    drawLine(
        color = Color(0xFFD7DCE4),
        start = Offset(cx - w * 0.28f, cy - h * 0.72f),
        end = Offset(cx + w * 0.28f, cy - h * 0.72f),
        strokeWidth = 2.2f,
    )
    drawCircle(Color(0xFF2A3340), radius = w * 0.13f, center = Offset(cx, cy - h * 0.90f))
}

private fun DrawScope.drawLeadBusBody(cx: Float, cy: Float, objW: Float, objH: Float) {
    val w = objW
    val h = objH
    drawOval(
        color = Color.Black.copy(alpha = 0.30f),
        topLeft = Offset(cx - w * 0.56f, cy - h * 0.04f),
        size = Size(w * 1.12f, h * 0.13f),
    )
    drawRoundRect(
        color = Color(0xFFD5C35A),
        topLeft = Offset(cx - w * 0.48f, cy - h * 1.02f),
        size = Size(w * 0.96f, h * 0.96f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
    )
    drawRoundRect(
        color = Color(0xFF2A3340),
        topLeft = Offset(cx - w * 0.36f, cy - h * 0.88f),
        size = Size(w * 0.72f, h * 0.20f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawRoundRect(
        color = Color(0xFFC43B44),
        topLeft = Offset(cx - w * 0.40f, cy - h * 0.26f),
        size = Size(w * 0.80f, h * 0.07f),
        cornerRadius = CornerRadius(h * 0.03f, h * 0.03f),
    )
}

private fun DrawScope.drawFrontObjectSilhouette(
    type: LauncherAdasFrontObjectType,
    cx: Float,
    cy: Float,
    objW: Float,
    objH: Float,
    fill: Color,
    stroke: Color,
) {
    when (type) {
        LauncherAdasFrontObjectType.Bus -> {
            // Tall coach with window strip.
            val body = Size(objW, objH)
            val topLeft = Offset(cx - objW / 2f, cy - objH)
            drawRoundRect(fill, topLeft, body, CornerRadius(objW * 0.12f, objW * 0.12f))
            drawRoundRect(stroke, topLeft, body, CornerRadius(objW * 0.12f, objW * 0.12f), Stroke(2f))
            val winY = cy - objH * 0.72f
            drawRoundRect(
                color = stroke.copy(alpha = 0.55f),
                topLeft = Offset(cx - objW * 0.38f, winY),
                size = Size(objW * 0.76f, objH * 0.18f),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
        LauncherAdasFrontObjectType.Truck -> {
            // Cab + cargo box.
            val cabW = objW * 0.55f
            val cabH = objH * 0.55f
            val boxW = objW
            val boxH = objH * 0.72f
            drawRoundRect(
                fill,
                Offset(cx - boxW / 2f, cy - boxH),
                Size(boxW, boxH),
                CornerRadius(objW * 0.08f, objW * 0.08f),
            )
            drawRoundRect(
                fill,
                Offset(cx - cabW / 2f, cy - objH),
                Size(cabW, cabH),
                CornerRadius(objW * 0.10f, objW * 0.10f),
            )
            drawRoundRect(
                stroke,
                Offset(cx - boxW / 2f, cy - boxH),
                Size(boxW, boxH),
                CornerRadius(objW * 0.08f, objW * 0.08f),
                Stroke(2f),
            )
            drawRoundRect(
                stroke,
                Offset(cx - cabW / 2f, cy - objH),
                Size(cabW, cabH),
                CornerRadius(objW * 0.10f, objW * 0.10f),
                Stroke(2f),
            )
        }
        LauncherAdasFrontObjectType.Car -> {
            // Sedan: body + cabin trapezoid.
            val bodyH = objH * 0.55f
            val cabinH = objH * 0.48f
            drawRoundRect(
                fill,
                Offset(cx - objW / 2f, cy - bodyH),
                Size(objW, bodyH),
                CornerRadius(objW * 0.22f, objW * 0.22f),
            )
            val cabinPath = Path().apply {
                moveTo(cx - objW * 0.28f, cy - bodyH)
                lineTo(cx - objW * 0.18f, cy - bodyH - cabinH)
                lineTo(cx + objW * 0.18f, cy - bodyH - cabinH)
                lineTo(cx + objW * 0.28f, cy - bodyH)
                close()
            }
            drawPath(cabinPath, color = fill)
            drawPath(cabinPath, color = stroke, style = Stroke(width = 2f))
            drawRoundRect(
                stroke,
                Offset(cx - objW / 2f, cy - bodyH),
                Size(objW, bodyH),
                CornerRadius(objW * 0.22f, objW * 0.22f),
                Stroke(2f),
            )
        }
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle -> {
            val wheelR = objW * 0.16f
            drawCircle(fill, wheelR, Offset(cx - objW * 0.28f, cy - wheelR))
            drawCircle(fill, wheelR, Offset(cx + objW * 0.28f, cy - wheelR))
            drawCircle(stroke, wheelR, Offset(cx - objW * 0.28f, cy - wheelR), style = Stroke(2f))
            drawCircle(stroke, wheelR, Offset(cx + objW * 0.28f, cy - wheelR), style = Stroke(2f))
            drawLine(
                color = stroke,
                start = Offset(cx - objW * 0.28f, cy - wheelR * 1.6f),
                end = Offset(cx + objW * 0.28f, cy - wheelR * 1.6f),
                strokeWidth = 2.5f,
            )
            drawCircle(stroke, objW * 0.12f, Offset(cx, cy - objH * 0.85f))
        }
        LauncherAdasFrontObjectType.Pedestrian -> {
            drawCircle(fill, objW * 0.22f, Offset(cx, cy - objH * 0.82f))
            drawCircle(stroke, objW * 0.22f, Offset(cx, cy - objH * 0.82f), style = Stroke(2f))
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.60f),
                end = Offset(cx, cy - objH * 0.18f),
                strokeWidth = 2.5f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx - objW * 0.28f, cy - objH * 0.45f),
                end = Offset(cx + objW * 0.28f, cy - objH * 0.45f),
                strokeWidth = 2.2f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.18f),
                end = Offset(cx - objW * 0.22f, cy),
                strokeWidth = 2.2f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.18f),
                end = Offset(cx + objW * 0.22f, cy),
                strokeWidth = 2.2f,
            )
        }
        else -> {
            val topLeft = Offset(cx - objW / 2f, cy - objH)
            drawRoundRect(fill, topLeft, Size(objW, objH), CornerRadius(objW * 0.18f, objW * 0.18f))
            drawRoundRect(
                stroke,
                topLeft,
                Size(objW, objH),
                CornerRadius(objW * 0.18f, objW * 0.18f),
                Stroke(2f),
            )
        }
    }
}

/** Round speed limit sign in the 3D area (not on the full-panel road canvas / header). */
@Composable
fun LauncherSpeedLimitOverlay(
    adas: LauncherAdasState,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val limit = adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
    if (limit == null) return

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.18f
                    scaleY = 1.18f
                    alpha = 0.85f
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val longPressSucceeded = try {
                        withTimeout(2000L) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    return@withTimeout false
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    return@withTimeout false
                                }
                            }
                            false
                        }
                    } catch (e: PointerEventTimeoutCancellationException) {
                        true
                    }

                    if (longPressSucceeded) {
                        isDragging = true
                        onDragStart()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero) {
                                change.consume()
                                onDrag(dragAmount)
                            }
                        }
                        isDragging = false
                        onDragEnd()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawSpeedLimitSign(
                limitKmh = limit,
                warning = adas.speedLimitWarning,
                accent = if (adas.speedLimitWarning) Color(0xFFEF4444) else Color(0xFFE11D48),
            )
        }
    }
}

private fun DrawScope.drawSpeedLimitSign(
    limitKmh: Int,
    warning: Boolean,
    accent: Color = if (warning) Color(0xFFEF4444) else Color(0xFFE11D48),
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = minOf(cx, cy) - 3f
    drawCircle(color = Color.White.copy(alpha = 0.92f), radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = accent,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 4.5f),
    )
    val paint = Paint().apply {
        isAntiAlias = true
        color = Color(0xFF111827).toArgb()
        textAlign = Paint.Align.CENTER
        textSize = radius * 0.95f
        typeface = Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        limitKmh.toString(),
        cx,
        cy - (paint.ascent() + paint.descent()) / 2f,
        paint,
    )
}

private fun leadSpriteWidth(type: LauncherAdasFrontObjectType, roadHalf: Float): Float {
    val base = roadHalf * 0.60f
    return when (type) {
        LauncherAdasFrontObjectType.Truck -> base * 1.16f
        LauncherAdasFrontObjectType.Motorcycle -> roadHalf * 0.57f
        LauncherAdasFrontObjectType.Pedestrian -> roadHalf * 0.46f
        else -> base
    }
}

private fun objectSizeForType(type: LauncherAdasFrontObjectType, roadHalf: Float): Pair<Float, Float> {
    // Scaled against the (now much wider) road, so a lead car keeps a plausible size.
    val base = roadHalf * 0.26f
    return when (type) {
        LauncherAdasFrontObjectType.Bus ->
            base * 1.15f to base * 1.55f
        LauncherAdasFrontObjectType.Truck ->
            base * 1.05f to base * 1.45f
        LauncherAdasFrontObjectType.Car ->
            base * 0.95f to base * 0.95f
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle ->
            base * 0.62f to base * 0.90f
        LauncherAdasFrontObjectType.Pedestrian ->
            base * 0.45f to base * 1.05f
        else -> base * 0.80f to base * 0.95f
    }
}

