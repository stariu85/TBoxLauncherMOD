package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.valueToString
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
    speedKmh: Float = 0f,
    steerAngleDeg: Float = 0f,
    isRearView: Boolean = false,
    inertiaEnabled: Boolean = true,
) {
    if (!enabled) return
    val needsAttentionAny = state.hasAttention(lowPressureThreshold)
    if (!showAll && !needsAttentionAny) return

    val currentSpeedState = rememberUpdatedState(speedKmh)
    val currentSteerState = rememberUpdatedState(steerAngleDeg)
    val targetYAnim = remember { Animatable(0f) }
    val targetXAnim = remember { Animatable(0f) }

    LaunchedEffect(inertiaEnabled, isRearView) {
        if (!inertiaEnabled || !isRearView) {
            launch { targetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessVeryLow)) }
            launch { targetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessVeryLow)) }
            return@LaunchedEffect
        }
        var lastSpeed = currentSpeedState.value
        var accumulatedY = 0f
        var accumulatedX = 0f

        while (isActive) {
            delay(40L) // 25 Hz calculation rate for smooth continuous motion
            val currentSpeed = currentSpeedState.value
            val currentSteer = currentSteerState.value

            val delta = currentSpeed - lastSpeed
            lastSpeed = currentSpeed

            // Y axis: longitudinal acceleration (back = +dp, braking = -dp)
            val impulseY = (delta * 18f).coerceIn(-25f, 25f)
            accumulatedY = (accumulatedY * 0.88f + impulseY * 0.12f).coerceIn(-25f, 25f)

            // X axis: lateral centrifugal force from steering angle and speed
            // Right turn (steer > 0) -> badges fly LEFT (-25dp)
            // Left turn (steer < 0) -> badges fly RIGHT (+25dp)
            val steerNorm = (currentSteer / 360f).coerceIn(-1f, 1f)
            val speedFactor = (currentSpeed / 30f).coerceIn(0f, 1f)
            val targetCentrifugalX = -steerNorm * speedFactor * 25f

            accumulatedX = (accumulatedX * 0.88f + targetCentrifugalX * 0.12f).coerceIn(-25f, 25f)

            launch {
                targetYAnim.animateTo(
                    targetValue = accumulatedY,
                    animationSpec = spring(
                        dampingRatio = 0.38f, // Loose pendulum oscillation (bouncy)
                        stiffness = 10f,     // Very low stiffness (loose spring / ball on thread)
                    ),
                )
            }
            launch {
                targetXAnim.animateTo(
                    targetValue = accumulatedX,
                    animationSpec = spring(
                        dampingRatio = 0.38f, // Loose pendulum oscillation (bouncy)
                        stiffness = 10f,     // Very low stiffness (loose spring / ball on thread)
                    ),
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val badgeWidth = if (compact) 62.dp else 98.dp
        val badgeHeight = if (compact) 28.dp else 68.dp
        val badgeWidthPx = with(density) { badgeWidth.toPx() }
        val badgeHeightPx = with(density) { badgeHeight.toPx() }
        val visibleCorners = LauncherWheelCorner.entries.filter { corner ->
            val wheel = state.wheel(corner)
            if (showAll) {
                wheel.available || !state.hasAvailableData
            } else {
                wheel.needsAttention(lowPressureThreshold)
            }
        }
        val layout = layoutTireCallouts(
            corners = visibleCorners,
            anchors = wheelAnchorsPx,
            containerWidth = boxWidthPx,
            containerHeight = boxHeightPx,
            calloutWidth = badgeWidthPx,
            calloutHeight = badgeHeightPx,
            gap = with(density) { 18.dp.toPx() },
        )

        val yOffsetDp = targetYAnim.value
        val xOffsetDp = targetXAnim.value

        Canvas(modifier = Modifier.fillMaxSize()) {
            visibleCorners.forEach { corner ->
                val anchor = wheelAnchorsPx[corner] ?: return@forEach
                val rect = layout[corner] ?: return@forEach

                val cornerXOffsetPx = with(density) { xOffsetDp.dp.toPx() }
                val cornerYOffsetPx = with(density) { yOffsetDp.dp.toPx() }

                val shiftedLeft = rect.left + cornerXOffsetPx
                val shiftedTop = rect.top + cornerYOffsetPx

                val endX = if (shiftedLeft < anchor.x) shiftedLeft + rect.width else shiftedLeft
                val wheel = state.wheel(corner)
                drawLine(
                    color = if (wheel.warningSts != 0) {
                        LauncherColors.WarningRed.copy(alpha = 0.85f)
                    } else {
                        LauncherColors.AccentCyan.copy(alpha = 0.55f)
                    },
                    start = anchor,
                    end = Offset(endX, shiftedTop + rect.height / 2f),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                    cap = StrokeCap.Round,
                )
            }
        }
        for (corner in visibleCorners) {
            val rect = layout[corner] ?: continue
            val cornerXOffsetPx = with(density) { xOffsetDp.dp.toPx() }
            val cornerYOffsetPx = with(density) { yOffsetDp.dp.toPx() }

            val shiftedLeft = rect.left + cornerXOffsetPx
            val shiftedTop = rect.top + cornerYOffsetPx

            LauncherTireBadge(
                corner = corner,
                wheel = state.wheel(corner),
                compact = compact,
                lowPressureThreshold = lowPressureThreshold,
                modifier = Modifier
                    .offset { IntOffset(shiftedLeft.roundToInt(), shiftedTop.roundToInt()) }
                    .width(badgeWidth),
            )
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
) {
    val label = stringResource(
        when (corner) {
            LauncherWheelCorner.FL -> R.string.launcher_vs_wheel_fl
            LauncherWheelCorner.FR -> R.string.launcher_vs_wheel_fr
            LauncherWheelCorner.RL -> R.string.launcher_vs_wheel_rl
            LauncherWheelCorner.RR -> R.string.launcher_vs_wheel_rr
        },
    )
    val tint = when {
        wheel.warningSts != 0 ||
            (wheel.pressureBar != null && wheel.pressureBar < lowPressureThreshold) ->
            LauncherColors.WarningRed
        wheel.calibrating || (wheel.available && wheel.pressureBar == null) ->
            LauncherColors.WarningAmber
        wheel.available -> LauncherColors.LeftTextPrimary
        else -> LauncherColors.LeftTextSecondary
    }
    val pressureText = when {
        !wheel.available || wheel.calibrating || wheel.pressureBar == null ->
            stringResource(R.string.launcher_tire_invalid)
        // Compact pill is too narrow for the unit — value alone stays on one line.
        compact -> valueToString(wheel.pressureBar, 1)
        else -> "${valueToString(wheel.pressureBar, 1)} ${stringResource(R.string.unit_bar)}"
    }
    val tempText = wheel.temperatureC
        ?.takeIf { wheel.available && !wheel.calibrating }
        ?.let { "$it${stringResource(R.string.unit_celsius)}" }

    val badgeBg = LauncherColors.LeftPanelCard

    Column(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(8.dp))
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            Text(
                text = label,
                color = tint.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = pressureText,
            color = tint,
            fontSize = if (compact) 13.8.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (!compact && tempText != null) {
            Text(
                text = tempText,
                color = tint.copy(alpha = 0.9f),
                fontSize = 12.7.sp,
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
