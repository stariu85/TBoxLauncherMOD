package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import vad.dashing.tbox.R
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdWidgetCameraMode

/**
 * Floating Camera Widget inside the ADAS zone:
 * - Main 50dp circle icon: freely draggable on long press, tap toggles fly-out menu.
 * - 3 flying 80dp circular buttons: Left/Right, Up/Down, 360 camera triggers.
 */
@Composable
internal fun LauncherCamdFloatingWidget(
    xRatio: Float,
    yRatio: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val activeMode by CamdCameraRepository.activeWidgetCameraMode.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val mainSizeDp = 50.dp
    val flyoutSizeDp = 80.dp
    val mainSizePx = with(density) { mainSizeDp.toPx() }

    val mainCenterX = containerWidthPx * xRatio
    val mainCenterY = containerHeightPx * yRatio

    val leftPx = (mainCenterX - mainSizePx / 2f).coerceIn(0f, (containerWidthPx - mainSizePx).coerceAtLeast(0f))
    val topPx = (mainCenterY - mainSizePx / 2f).coerceIn(0f, (containerHeightPx - mainSizePx).coerceAtLeast(0f))

    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
        label = "camdWidgetExpand",
    )

    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1.0f,
        animationSpec = tween(150),
        label = "camdWidgetDragScale",
    )

    // Fan-out direction: if widget is located in the right half of ADAS panel, fan out to left; else to right
    val fanOutToLeft = xRatio > 0.5f

    Box(
        modifier = modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .zIndex(180f),
        contentAlignment = Alignment.Center,
    ) {
        // 3 Flying Action Buttons (80dp each)
        if (expandProgress > 0.01f) {
            val radialDistanceDp = 105.dp
            val baseAngles = if (fanOutToLeft) {
                // Towards left: 140°, 180°, 220°
                listOf(140.0, 180.0, 220.0)
            } else {
                // Towards right: -40°, 0°, 40°
                listOf(-40.0, 0.0, 40.0)
            }

            // Button 1: Left / Right
            CamdFlyingButton(
                iconRes = R.drawable.ic_camd_widget_left_right,
                contentDescription = "Камеры Право / Лево",
                size = flyoutSizeDp,
                angleDeg = baseAngles[0],
                distance = radialDistanceDp,
                progress = expandProgress,
                isActive = activeMode == CamdWidgetCameraMode.LEFT_RIGHT,
                onClick = {
                    CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.LEFT_RIGHT)
                },
            )

            // Button 2: Up / Down
            CamdFlyingButton(
                iconRes = R.drawable.ic_camd_widget_up_down,
                contentDescription = "Камеры Верх / Низ",
                size = flyoutSizeDp,
                angleDeg = baseAngles[1],
                distance = radialDistanceDp,
                progress = expandProgress,
                isActive = activeMode == CamdWidgetCameraMode.UP_DOWN,
                onClick = {
                    CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.UP_DOWN)
                },
            )

            // Button 3: 360
            CamdFlyingButton(
                iconRes = R.drawable.ic_camd_widget_360,
                contentDescription = "Камеры 360",
                size = flyoutSizeDp,
                angleDeg = baseAngles[2],
                distance = radialDistanceDp,
                progress = expandProgress,
                isActive = activeMode == CamdWidgetCameraMode.ALL_360,
                onClick = {
                    CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.ALL_360)
                },
            )
        }

        // Main Eye Icon (50dp circle)
        val hasActiveCameras = activeMode != CamdWidgetCameraMode.NONE
        val mainBgColor = if (hasActiveCameras) {
            LauncherColors.CardDarkElevated
        } else {
            LauncherColors.CardDark
        }
        val mainBorderColor = if (hasActiveCameras || isExpanded) {
            LauncherColors.AccentCyan
        } else {
            LauncherColors.SettingsBorder
        }
        val mainIconTint = if (hasActiveCameras || isExpanded) {
            LauncherColors.AccentCyan
        } else {
            LauncherColors.TextPrimary
        }

        Box(
            modifier = Modifier
                .size(mainSizeDp)
                .scale(dragScale)
                .shadow(if (isDragging) 12.dp else 6.dp, CircleShape)
                .clip(CircleShape)
                .background(mainBgColor)
                .border(if (hasActiveCameras || isExpanded) 2.dp else 1.2.dp, mainBorderColor, CircleShape)
                .pointerInput(containerWidthPx, containerHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = SystemClock.uptimeMillis()
                        val downPos = down.position
                        var dragStarted = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!pointer.pressed) {
                                if (dragStarted) {
                                    pointer.consume()
                                    isDragging = false
                                    onDragEnd()
                                } else {
                                    val elapsed = SystemClock.uptimeMillis() - downTime
                                    if (elapsed < 400L) {
                                        isExpanded = !isExpanded
                                    }
                                }
                                break
                            }

                            val elapsed = SystemClock.uptimeMillis() - downTime
                            val currentPos = pointer.position
                            val deltaX = currentPos.x - downPos.x
                            val deltaY = currentPos.y - downPos.y
                            val distSq = deltaX * deltaX + deltaY * deltaY

                            if (!dragStarted) {
                                if (elapsed >= 350L || distSq > 144f) {
                                    dragStarted = true
                                    isDragging = true
                                    pointer.consume()
                                }
                            } else {
                                val frameDelta = pointer.position - pointer.previousPosition
                                pointer.consume()
                                onDrag(frameDelta)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camd_widget_eye),
                contentDescription = "Виджет камер",
                tint = mainIconTint,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun CamdFlyingButton(
    iconRes: Int,
    contentDescription: String,
    size: Dp,
    angleDeg: Double,
    distance: Dp,
    progress: Float,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val angleRad = Math.toRadians(angleDeg)
    val targetOffsetXDp = (distance.value * cos(angleRad)).dp * progress
    val targetOffsetYDp = (distance.value * sin(angleRad)).dp * progress

    val bgColor = if (isActive) {
        LauncherColors.CardDarkElevated
    } else {
        LauncherColors.CardDark
    }

    val borderColor = if (isActive) {
        LauncherColors.AccentCyan
    } else {
        LauncherColors.SettingsBorder
    }

    val iconTint = if (isActive) {
        LauncherColors.AccentCyan
    } else {
        LauncherColors.TextPrimary
    }

    Box(
        modifier = Modifier
            .offset(x = targetOffsetXDp, y = targetOffsetYDp)
            .graphicsLayer {
                scaleX = progress
                scaleY = progress
                alpha = progress.coerceIn(0f, 1f)
            }
            .size(size)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(bgColor)
            .border(if (isActive) 2.5.dp else 1.5.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(42.dp),
        )
    }
}
