package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.valueToString
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * OEM-style TPMS callouts beside the 3D car — shown when enabled according to
 * user settings (`showAll` or when wheels need attention below `lowPressureThreshold`).
 */
@Composable
fun LauncherTireBadges(
    state: LauncherTireState,
    modifier: Modifier = Modifier,
    wheelAnchorsPx: Map<LauncherWheelCorner, Offset> = emptyMap(),
    showAll: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    lowPressureThreshold: Float = LauncherTireWheelState.LOW_PRESSURE_BAR,
    allowLowTempNotification: Boolean = true,
    speedKmh: Float = 0f,
    steerAngleDeg: Float = 0f,
    isRearView: Boolean = false,
    inertiaEnabled: Boolean = true,
    badgeShape: LauncherTireBadgeShape = LauncherTireBadgeShape.RECTANGLE,
    showHundredths: Boolean = false,
    showTemperature: Boolean = false,
) {
    if (!enabled) return
    val needsAttentionAny = state.hasAttention(lowPressureThreshold, allowLowTempNotification)
    if (!showAll && !needsAttentionAny) return

    val currentSpeedState = rememberUpdatedState(speedKmh)
    val currentSteerState = rememberUpdatedState(steerAngleDeg)
    val currentInertiaActiveState = rememberUpdatedState(inertiaEnabled && isRearView)
    val activeClock = rememberSynced30FpsClock()
    val offsetYState = remember { mutableFloatStateOf(0f) }
    val offsetXState = remember { mutableFloatStateOf(0f) }
    val springY = remember { SyncedSpring1D(stiffness = 10f, dampingRatio = 0.38f) }
    val springX = remember { SyncedSpring1D(stiffness = 10f, dampingRatio = 0.38f) }
    var isInertiaRunning by remember { mutableStateOf(false) }

    LaunchedEffect(speedKmh, steerAngleDeg, inertiaEnabled, isRearView) {
        val activeInput = inertiaEnabled && isRearView && (speedKmh > 0.1f || abs(steerAngleDeg) > 0.5f)
        val needsReturn = abs(springX.position) >= 0.05f ||
            abs(springY.position) >= 0.05f ||
            abs(springX.velocity) >= 0.05f ||
            abs(springY.velocity) >= 0.05f
        if (activeInput || needsReturn) {
            isInertiaRunning = true
        }
    }

    DisposableEffect(activeClock, isInertiaRunning) {
        if (!isInertiaRunning) return@DisposableEffect onDispose {}
        var lastSpeed = currentSpeedState.value
        var accumulatedY = 0f
        var accumulatedX = 0f
        val unregister = activeClock.registerListener(tickDivisor = 1) { _, deltaNs ->
            val dtSec = (deltaNs / 1_000_000_000f).coerceIn(0f, 0.05f)
            val active = currentInertiaActiveState.value
            val currentSpeed = currentSpeedState.value
            val currentSteer = currentSteerState.value

            val targetY: Float
            val targetX: Float
            if (active) {
                val delta = currentSpeed - lastSpeed
                lastSpeed = currentSpeed
                val impulseY = (delta * 45f).coerceIn(-40f, 40f)
                accumulatedY = (accumulatedY * 0.30f + impulseY * 0.70f).coerceIn(-40f, 40f)
                if (abs(accumulatedY) < 0.05f) accumulatedY = 0f

                val steerNorm = (currentSteer / 360f).coerceIn(-1f, 1f)
                val speedFactor = (currentSpeed / 15f).coerceIn(0f, 1f)
                val targetCentrifugalX = -steerNorm * speedFactor * 50f
                accumulatedX = (accumulatedX * 0.30f + targetCentrifugalX * 0.70f).coerceIn(-40f, 40f)
                if (abs(accumulatedX) < 0.05f) accumulatedX = 0f

                targetY = accumulatedY
                targetX = accumulatedX
            } else {
                lastSpeed = currentSpeed
                accumulatedY = 0f
                accumulatedX = 0f
                targetY = 0f
                targetX = 0f
            }

            val movingY = springY.step(targetY, dtSec)
            val movingX = springX.step(targetX, dtSec)
            offsetYState.floatValue = springY.position
            offsetXState.floatValue = springX.position

            val idleInput = !active || (currentSpeed <= 0.1f && abs(currentSteer) <= 0.5f && targetY == 0f && targetX == 0f)
            if (idleInput && !movingY && !movingX) {
                isInertiaRunning = false
            }
        }
        onDispose { unregister() }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val badgeHeight = if (compact) {
            if (showTemperature) 58.dp else 40.dp
        } else {
            if (showTemperature) 78.dp else 52.dp
        }
        val badgeWidth = if (badgeShape == LauncherTireBadgeShape.ROUND) {
            badgeHeight
        } else {
            if (compact) {
                if (showTemperature) 70.dp else 62.dp
            } else {
                if (showTemperature) 98.dp else 92.dp
            }
        }
        val badgeWidthPx = with(density) { badgeWidth.toPx() }
        val badgeHeightPx = with(density) { badgeHeight.toPx() }
        val visibleCorners = remember(state, showAll, lowPressureThreshold, allowLowTempNotification) {
            LauncherWheelCorner.entries.filter { corner ->
                val wheel = state.wheel(corner)
                if (showAll) {
                    wheel.available || !state.hasAvailableData
                } else {
                    wheel.needsAttention(lowPressureThreshold) ||
                        (allowLowTempNotification && wheel.isLowTemperature())
                }
            }
        }
        val gapPx = with(density) { 18.dp.toPx() }
        val layout = remember(visibleCorners, wheelAnchorsPx, boxWidthPx, boxHeightPx, badgeWidthPx, badgeHeightPx, gapPx) {
            layoutTireCallouts(
                corners = visibleCorners,
                anchors = wheelAnchorsPx,
                containerWidth = boxWidthPx,
                containerHeight = boxHeightPx,
                calloutWidth = badgeWidthPx,
                calloutHeight = badgeHeightPx,
                gap = gapPx,
            )
        }

        // Read animated inertia offsets strictly in DrawScope (Canvas + drawWithContent translate)
        // so 30 FPS spring updates never trigger relayout, owner.onLayoutChange, or
        // AndroidComposeViewAccessibilityDelegateCompat.checkForSemanticsChanges.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerXOffsetPx = offsetXState.floatValue.dp.toPx()
            val cornerYOffsetPx = offsetYState.floatValue.dp.toPx()
            val strokePx = 1.5.dp.toPx()
            visibleCorners.forEach { corner ->
                val anchor = wheelAnchorsPx[corner] ?: return@forEach
                val rect = layout[corner] ?: return@forEach

                val shiftedLeft = rect.left + cornerXOffsetPx
                val shiftedTop = rect.top + cornerYOffsetPx

                val endX = if (shiftedLeft < anchor.x) shiftedLeft + rect.width else shiftedLeft
                val wheel = state.wheel(corner)
                val isLowPress = wheel.isLowPressure(lowPressureThreshold)
                val isLowTemp = allowLowTempNotification && wheel.isLowTemperature()
                drawLine(
                    color = when {
                        isLowPress -> LauncherColors.WarningRed.copy(alpha = 0.85f)
                        isLowTemp -> Color(0xFF38BDF8).copy(alpha = 0.85f)
                        else -> LauncherColors.AccentCyan.copy(alpha = 0.55f)
                    },
                    start = anchor,
                    end = Offset(endX, shiftedTop + rect.height / 2f),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    translate(
                        left = offsetXState.floatValue.dp.toPx(),
                        top = offsetYState.floatValue.dp.toPx(),
                    ) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            for (corner in visibleCorners) {
                val rect = layout[corner] ?: continue

                LauncherTireBadge(
                    corner = corner,
                    wheel = state.wheel(corner),
                    compact = compact,
                    lowPressureThreshold = lowPressureThreshold,
                    allowLowTempNotification = allowLowTempNotification,
                    badgeShape = badgeShape,
                    badgeHeight = badgeHeight,
                    showHundredths = showHundredths,
                    showTemperature = showTemperature,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                rect.left.roundToInt(),
                                rect.top.roundToInt(),
                            )
                        }
                        .width(badgeWidth),
                )
            }
        }
    }
}

@Composable
private fun LauncherTireBadge(
    corner: LauncherWheelCorner,
    wheel: LauncherTireWheelState,
    compact: Boolean,
    modifier: Modifier = Modifier,
    lowPressureThreshold: Float = LauncherTireWheelState.LOW_PRESSURE_BAR,
    allowLowTempNotification: Boolean = true,
    badgeShape: LauncherTireBadgeShape = LauncherTireBadgeShape.RECTANGLE,
    badgeHeight: Dp = if (compact) 40.dp else 68.dp,
    showHundredths: Boolean = false,
    showTemperature: Boolean = false,
) {
    val isRound = badgeShape == LauncherTireBadgeShape.ROUND
    val shape = if (isRound) CircleShape else RoundedCornerShape(8.dp)

    val label = stringResource(
        when (corner) {
            LauncherWheelCorner.FL -> R.string.launcher_vs_wheel_fl
            LauncherWheelCorner.FR -> R.string.launcher_vs_wheel_fr
            LauncherWheelCorner.RL -> R.string.launcher_vs_wheel_rl
            LauncherWheelCorner.RR -> R.string.launcher_vs_wheel_rr
        },
    )
    val isLowPress = wheel.isLowPressure(lowPressureThreshold)
    val isLowTemp = allowLowTempNotification && wheel.isLowTemperature()
    val tint = when {
        isLowPress -> LauncherColors.WarningRed
        isLowTemp -> Color(0xFF38BDF8)
        wheel.calibrating || (wheel.available && wheel.pressureBar == null) ->
            LauncherColors.LeftTextSecondary
        wheel.available -> LauncherColors.LeftTextPrimary
        else -> LauncherColors.LeftTextSecondary
    }
    val pressureAccuracy = if (showHundredths) 2 else 1
    val pressureText = when {
        !wheel.available || wheel.calibrating || wheel.pressureBar == null ->
            stringResource(R.string.launcher_tire_invalid)
        compact -> valueToString(wheel.pressureBar, pressureAccuracy)
        else -> "${valueToString(wheel.pressureBar, pressureAccuracy)} ${stringResource(R.string.unit_bar)}"
    }
    val tempText = wheel.temperatureC
        ?.takeIf { wheel.available && !wheel.calibrating }
        ?.let { "$it${stringResource(R.string.unit_degree)}" }
        ?: "—${stringResource(R.string.unit_degree)}"

    val badgeBg = LauncherColors.LeftPanelCard

    Column(
        modifier = modifier
            .height(badgeHeight)
            .background(badgeBg, shape)
            .padding(
                horizontal = if (isRound) 3.dp else (if (compact) 4.dp else 6.dp),
                vertical = if (compact) (if (showTemperature) 2.dp else 3.dp) else (if (showTemperature) 3.dp else 4.dp),
            )
            .then(if (isRound && !showTemperature) Modifier.offset(y = (-2).dp) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!compact) {
            Text(
                text = label,
                color = tint.copy(alpha = 0.85f),
                fontSize = if (isRound) 10.sp else 11.sp,
                lineHeight = if (isRound) 10.5.sp else 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = pressureText,
            color = tint,
            fontSize = if (showTemperature) {
                if (compact) (if (isRound) 12.5.sp else 13.5.sp) else (if (isRound) 13.sp else 14.sp)
            } else {
                if (compact) (if (isRound) 13.5.sp else 14.5.sp) else (if (isRound) 13.5.sp else 14.5.sp)
            },
            lineHeight = if (showTemperature) {
                if (compact) (if (isRound) 13.sp else 14.sp) else (if (isRound) 13.5.sp else 14.5.sp)
            } else {
                if (compact) (if (isRound) 14.sp else 15.sp) else (if (isRound) 14.sp else 15.sp)
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = if (compact && !isRound && !showTemperature) Modifier.offset(y = (-3).dp) else Modifier,
        )
        if (showTemperature) {
            Box(
                modifier = Modifier
                    .padding(vertical = 1.5.dp)
                    .fillMaxWidth(if (isRound) 0.55f else 0.75f)
                    .height(1.dp)
                    .background(tint.copy(alpha = 0.35f)),
            )
            Text(
                text = tempText,
                color = tint.copy(alpha = 0.9f),
                fontSize = if (compact) (if (isRound) 10.5.sp else 11.5.sp) else (if (isRound) 11.sp else 12.sp),
                lineHeight = if (compact) (if (isRound) 11.sp else 12.sp) else (if (isRound) 11.5.sp else 12.5.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

internal data class TireCalloutRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Places callouts from projected wheel pivots, clamps them to bounds and resolves vertical
 * collisions independently on each side. Fallback anchors are used only before the first frame.
 */
internal fun layoutTireCallouts(
    corners: List<LauncherWheelCorner>,
    anchors: Map<LauncherWheelCorner, Offset>,
    containerWidth: Float,
    containerHeight: Float,
    calloutWidth: Float,
    calloutHeight: Float,
    gap: Float,
): Map<LauncherWheelCorner, TireCalloutRect> {
    if (
        containerWidth <= 0f || containerHeight <= 0f ||
        calloutWidth <= 0f || calloutHeight <= 0f
    ) {
        return emptyMap()
    }
    val margin = gap.coerceAtLeast(0f)
    val maxLeft = (containerWidth - calloutWidth - margin).coerceAtLeast(margin)
    val maxTop = (containerHeight - calloutHeight - margin).coerceAtLeast(margin)
    val candidates = corners.associateWith { corner ->
        val anchor = anchors[corner] ?: fallbackWheelAnchor(corner, containerWidth, containerHeight)
        val onLeft = anchor.x < containerWidth / 2f
        val left = if (onLeft) {
            anchor.x - calloutWidth - gap
        } else {
            anchor.x + gap
        }.coerceIn(margin, maxLeft)
        TireCalloutRect(
            left = left,
            top = (anchor.y - calloutHeight / 2f).coerceIn(margin, maxTop),
            width = calloutWidth,
            height = calloutHeight,
        )
    }.toMutableMap()

    candidates.keys.groupBy { candidates.getValue(it).left < containerWidth / 2f }
        .values
        .forEach { sideCorners ->
            val sorted = sideCorners.sortedBy { candidates.getValue(it).top }
            var nextTop = margin
            sorted.forEach { corner ->
                val rect = candidates.getValue(corner)
                val top = rect.top.coerceAtLeast(nextTop).coerceAtMost(maxTop)
                candidates[corner] = rect.copy(top = top)
                nextTop = top + calloutHeight + gap
            }
            var previousTop = maxTop + calloutHeight + gap
            sorted.asReversed().forEach { corner ->
                val rect = candidates.getValue(corner)
                val top = rect.top.coerceAtMost(previousTop - calloutHeight - gap)
                    .coerceAtLeast(margin)
                candidates[corner] = rect.copy(top = top)
                previousTop = top
            }
        }
    return candidates
}

private fun fallbackWheelAnchor(
    corner: LauncherWheelCorner,
    width: Float,
    height: Float,
): Offset = when (corner) {
    LauncherWheelCorner.FL -> Offset(width * 0.34f, height * 0.32f)
    LauncherWheelCorner.FR -> Offset(width * 0.66f, height * 0.32f)
    LauncherWheelCorner.RL -> Offset(width * 0.34f, height * 0.68f)
    LauncherWheelCorner.RR -> Offset(width * 0.66f, height * 0.68f)
}
