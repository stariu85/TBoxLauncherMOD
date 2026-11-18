package vad.dashing.tbox.ui.launcher

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.R
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdWidgetCameraMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Camera widget button placed in the dock (bottom bar):
 * - Tapping toggles a fly-out arc of 3 circular camera perspective buttons (Left/Right, Up/Down, 360).
 * - Buttons fly upward along an arc, never touching each other, with their bottom point above the dock.
 * - Auto-hides if no button is pressed within 5 seconds.
 */
@Composable
internal fun CamdDockWidgetButton(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val activeMode by CamdCameraRepository.activeWidgetCameraMode.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }
    var lastInteractionKey by remember { mutableIntStateOf(0) }

    // Auto-hide fly-out buttons if no button is pressed within 5 seconds
    LaunchedEffect(isExpanded, lastInteractionKey) {
        if (isExpanded) {
            delay(5000L)
            isExpanded = false
        }
    }

    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
        label = "camdDockWidgetExpand",
    )

    val hasActiveCameras = activeMode != CamdWidgetCameraMode.NONE
    val iconTint = if (hasActiveCameras || isExpanded) {
        LauncherColors.AccentCyan
    } else {
        if (LauncherThemeState.darkTheme) Color(0xFF9CA3AF) else Color(0xFF374151)
    }

    val density = LocalDensity.current
    val originOffsetY = 70.dp
    val popupOffsetYDp = (-70).dp
    val popupOffsetYPx = with(density) { popupOffsetYDp.roundToPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isExpanded || expandProgress > 0.01f) {
            Popup(
                alignment = Alignment.Center,
                offset = IntOffset(0, popupOffsetYPx),
                onDismissRequest = { isExpanded = false },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                Box(
                    modifier = Modifier.size(width = 300.dp, height = 240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val flyoutSizeDp = (60f * 1.3f).dp
                    val radialDistanceDp = 135.dp

                    // Left button: Left / Right (-135°)
                    CamdFlyingButton(
                        iconRes = R.drawable.ic_camd_widget_left_right,
                        contentDescription = "Камеры Право / Лево",
                        size = flyoutSizeDp,
                        angleDeg = -135.0,
                        distance = radialDistanceDp,
                        originOffsetY = originOffsetY,
                        progress = expandProgress,
                        isActive = activeMode == CamdWidgetCameraMode.LEFT_RIGHT,
                        onClick = {
                            lastInteractionKey++
                            CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.LEFT_RIGHT)
                        },
                    )

                    // Center button: Up / Down (-90°)
                    CamdFlyingButton(
                        iconRes = R.drawable.ic_camd_widget_up_down,
                        contentDescription = "Камеры Верх / Низ",
                        size = flyoutSizeDp,
                        angleDeg = -90.0,
                        distance = radialDistanceDp,
                        originOffsetY = originOffsetY,
                        progress = expandProgress,
                        isActive = activeMode == CamdWidgetCameraMode.UP_DOWN,
                        onClick = {
                            lastInteractionKey++
                            CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.UP_DOWN)
                        },
                    )

                    // Right button: 360 (-45°)
                    CamdFlyingButton(
                        iconRes = R.drawable.ic_camd_widget_360,
                        contentDescription = "Камеры 360",
                        size = flyoutSizeDp,
                        angleDeg = -45.0,
                        distance = radialDistanceDp,
                        originOffsetY = originOffsetY,
                        progress = expandProgress,
                        isActive = activeMode == CamdWidgetCameraMode.ALL_360,
                        onClick = {
                            lastInteractionKey++
                            CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.ALL_360)
                        },
                    )
                }
            }
        }

        LauncherDockIcon(
            onClick = {
                isExpanded = !isExpanded
                if (isExpanded) {
                    lastInteractionKey++
                }
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_camd_widget_eye),
                contentDescription = "Виджет камер",
                colorFilter = ColorFilter.tint(iconTint),
                modifier = Modifier.size(dockDp(26f * 1.15f)),
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
    originOffsetY: Dp,
    progress: Float,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val angleRad = Math.toRadians(angleDeg)
    val targetOffsetXDp = (distance.value * cos(angleRad)).dp * progress
    val targetOffsetYDp = originOffsetY + (distance.value * sin(angleRad)).dp * progress

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
            modifier = Modifier.size((32f * 1.3f).dp),
        )
    }
}
