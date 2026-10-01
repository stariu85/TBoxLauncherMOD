package vad.dashing.tbox.ui.launcher

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val driveTarget = when {
        inDriveGear -> 1f
        speedKmh > 0.5f -> 1f
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
    distanceLabelOffsetRatio: Float = 0.15f,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.24f
    // Keep road straight — car body no longer yaws with steering in drive mode.
    val vanishX = w / 2f
    // The road deliberately overflows the panel at the bottom: neighbouring lanes run
    // off both edges, which is what keeps the ego car from looking oversized.
    fun halfWidthAt(t: Float): Float =
        (w * 0.14f) * (1f - t).pow(1.25f) + (w * 1.10f) * t.pow(1.05f)
    // Ego (center) lane half-width — ACC beam and LKA sit on this strip, not the
    // outer road shoulders.
    fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.30f
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
            val alpha = 0.50f * fade * weight
            if (alpha < 0.02f) return@forEach
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(x1, y),
                end = Offset(x2, y + dashLen),
                strokeWidth = 1.6f + 1.6f * t,
            )
        }
        y += dashSpacing * (0.55f + 0.85f * t)
    }

    drawHorizonHaze(horizonY = horizonY, canvasHeight = h)

    val frontDistance = leadVisual?.distanceM ?: adas.frontObject.displayDistanceM
    if (leadVisual != null) {
        if ((adas.accActive || adas.accStandby) && adas.timeGapLevel != null) {
            drawAccBeam(
                objectDistanceM = frontDistance,
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
                yAt = ::yAt,
                alert = adas.fcwActive || adas.distanceWarning,
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
    } else if ((adas.accActive || adas.accStandby) && adas.timeGapLevel != null) {
        // No target ahead — stretch the ACC path to the horizon.
        drawAccBeam(
            objectDistanceM = null,
            toHorizon = true,
            centerXAt = ::centerXAt,
            laneOffsetAt = ::laneOffsetAt,
            yAt = ::yAt,
            alert = false,
        )
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

/** ACC following beam: a glowing wedge from ego up to the tracked target,
 *  confined to the center (ego) lane — not the outer road shoulders. */
private fun DrawScope.drawAccBeam(
    objectDistanceM: Int?,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
    alert: Boolean,
    toHorizon: Boolean = false,
) {
    val targetDepth = if (toHorizon || objectDistanceM == null) {
        0.02f
    } else {
        distanceToRoadDepth(objectDistanceM)
    }
    // Start under the 3D car (near the bottom of the road) and run to the
    // horizon / lead object. Soft fade at both ends.
    val egoDepth = 0.96f
    val nearHalf = laneOffsetAt(egoDepth) * 0.92f
    val farHalf = laneOffsetAt(targetDepth) * 0.72f
    val beam = Path().apply {
        moveTo(centerXAt(egoDepth) - nearHalf, yAt(egoDepth))
        lineTo(centerXAt(targetDepth) - farHalf, yAt(targetDepth))
        lineTo(centerXAt(targetDepth) + farHalf, yAt(targetDepth))
        lineTo(centerXAt(egoDepth) + nearHalf, yAt(egoDepth))
        close()
    }
    val tint = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    drawPath(
        path = beam,
        brush = Brush.verticalGradient(
            0.00f to tint.copy(alpha = 0f),
            0.16f to tint.copy(alpha = 0.10f),
            0.48f to tint.copy(alpha = 0.36f),
            0.82f to tint.copy(alpha = 0.16f),
            1.00f to tint.copy(alpha = 0f),
            startY = yAt(targetDepth),
            endY = yAt(egoDepth),
        ),
    )
}

private fun DrawScope.drawAdasLaneAssist(
    adas: LauncherAdasState,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    fun laneColor(side: LauncherAdasLaneVisualization, warning: Boolean): Color = when {
        warning -> Color(0xFFEF4444).copy(alpha = 0.72f)
        side == LauncherAdasLaneVisualization.Intervention -> LauncherColors.AccentCyan.copy(alpha = 0.55f)
        side == LauncherAdasLaneVisualization.Tracking -> LauncherColors.AccentBlue.copy(alpha = 0.42f)
        else -> Color.Transparent
    }
    listOf(
        adas.leftLane to -1f,
        adas.rightLane to 1f,
    ).forEach { (lane, side) ->
        if (lane == LauncherAdasLaneVisualization.Hidden) return@forEach
        val warning = lane == LauncherAdasLaneVisualization.Warning
        val color = laneColor(lane, warning)
        if (color == Color.Transparent) return@forEach
        // Track the borders of the center (ego) lane, not the outer road edges.
        var prev = Offset(
            centerXAt(1f) + side * laneOffsetAt(1f),
            yAt(1f),
        )
        for (i in 8 downTo 0) {
            val t = i / 8f
            val next = Offset(
                centerXAt(t) + side * laneOffsetAt(t),
                yAt(t),
            )
            drawLine(color = color, start = prev, end = next, strokeWidth = if (warning) 3.5f else 2.5f)
            prev = next
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
    distanceLabelOffsetRatio: Float = 0.15f,
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

    // Distance label position along the ACC beam between lead car depth and ego car depth (0.92f)
    val egoDepth = 0.92f
    val labelDepth = (depth + (egoDepth - depth) * distanceLabelOffsetRatio).coerceIn(0.02f, 0.88f)
    val labelCx = centerXAt(labelDepth)
    val labelCy = yAt(labelDepth) + 10f

    val labelColor = (if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan)
        .copy(alpha = objectAlpha)

    val text = "${distanceM} м"
    val baseSizeSp = distanceTextSizeSp.toFloat()
    val paint = Paint().apply {
        isAntiAlias = true
        color = labelColor.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = (baseSizeSp * (0.85f + labelDepth * 0.3f)).coerceIn(12f, 50f)
        typeface = Typeface.DEFAULT_BOLD
    }

    val textWidth = paint.measureText(text)
    val textHeight = abs(paint.ascent()) + paint.descent()
    val paddingX = 10f
    val paddingY = 4f
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

/** Round SLA/TSR signs in the 3D area (not on the full-panel road canvas / header). */
@Composable
fun LauncherSpeedLimitOverlay(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val sla = adas.speedLimitKmh
    val tsr = adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
    if (sla == null && tsr == null) return
    val two = sla != null && tsr != null
    Canvas(modifier = modifier.size(if (two) 118.dp else 56.dp, 58.dp)) {
        if (sla != null) {
            drawSpeedLimitSign(
                limitKmh = sla,
                warning = adas.speedLimitWarning,
                canvasWidth = size.width,
                offsetFromRight = 4f,
                accent = if (adas.speedLimitWarning) Color(0xFFEF4444) else Color(0xFFE11D48),
                caption = null,
            )
        }
        if (tsr != null) {
            drawSpeedLimitSign(
                limitKmh = tsr,
                warning = false,
                canvasWidth = size.width,
                offsetFromRight = if (sla != null) 62f else 4f,
                accent = Color(0xFF2563EB),
                caption = null,
            )
        }
    }
}

private fun DrawScope.drawSpeedLimitSign(
    limitKmh: Int,
    warning: Boolean,
    canvasWidth: Float,
    offsetFromRight: Float = 14f,
    accent: Color = if (warning) Color(0xFFEF4444) else Color(0xFFE11D48),
    caption: String? = null,
) {
    val radius = 22f
    val cx = canvasWidth - radius - offsetFromRight
    val cy = radius + 6f
    drawCircle(color = Color.White.copy(alpha = 0.92f), radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = accent,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 4.5f),
    )
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color(0xFF111827).toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        limitKmh.toString(),
        cx,
        cy - (paint.ascent() + paint.descent()) / 2f,
        paint,
    )
    if (!caption.isNullOrBlank()) {
        val cap = android.graphics.Paint().apply {
            isAntiAlias = true
            color = accent.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(
            caption,
            cx,
            cy + radius + 12f,
            cap,
        )
    }
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

