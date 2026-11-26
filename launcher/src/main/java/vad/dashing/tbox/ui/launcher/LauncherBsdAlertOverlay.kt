package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdSocketClient
import vad.dashing.tbox.camd.SideCameraWindowBounds
import vad.dashing.tbox.ui.ProvideUiClickSounds

/**
 * Full-screen overlay component for BSD Alert (high-priority blind spot warning).
 * Draws 100.dp vertical gradient bars on the left/right screen edges:
 * solid bright red at the outer edge, fading smoothly towards the center.
 * If turn signal cameras in circles or rectangles are enabled, blinks the camera window
 * outline instead of screen edge stripes.
 * Blinks with the exact same frequency as the road lane beam (tween 650ms).
 */
@Composable
fun LauncherBsdAlertOverlay(
    modifier: Modifier = Modifier,
) {
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
    LauncherBsdAlertOverlay(adas = adas, modifier = modifier)
}

@Composable
fun LauncherBsdAlertOverlay(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasBsdAlertOverlayRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.adasBsdAlertOverlayEnabled(context)
    }

    if (!enabled) return

    val bsdLeftAlert = adas.rearThreats.bsdLeft == LauncherRearThreatLevel.Alert
    val bsdRightAlert = adas.rearThreats.bsdRight == LauncherRearThreatLevel.Alert

    if (!bsdLeftAlert && !bsdRightAlert) return

    val camdConfigRev by LauncherAppConfigStore.camdConfigRevisionFlow.collectAsStateWithLifecycle()
    val isCamdInstalled = remember(context) { CamdSocketClient.isInstalled(context) }
    val turnSignalCameras = remember(context, camdConfigRev) {
        LauncherAppConfigStore.camdTurnSignalCameras(context)
    }
    val shapeCircle = remember(context, camdConfigRev) {
        LauncherAppConfigStore.camdSideCamerasShapeCircle(context)
    }
    val rectCorners = remember(context, camdConfigRev) {
        LauncherAppConfigStore.camdSideCamerasRectCorners(context)
    }

    val useCameraWindowBorder = CamdCameraRepository.shouldBlinkCameraWindowBorder(
        isCamdInstalled = isCamdInstalled,
        turnSignalCameras = turnSignalCameras,
        shapeCircle = shapeCircle,
        rectCorners = rectCorners,
    )

    // Keep State<Float> reference so pulse is read exclusively inside the draw phase
    val pulseState = rememberInfiniteTransition(label = "bsdOverlayPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bsdOverlayPulseValue",
    )

    val alertRed = Color(0xFFFF1744)

    if (useCameraWindowBorder) {
        val leftBounds = remember(context, camdConfigRev, shapeCircle, rectCorners) {
            CamdCameraRepository.calculateSideCameraWindowBounds(
                context = context,
                isRightSide = false,
                shapeCircleOverride = shapeCircle,
                rectCornersOverride = rectCorners,
            )
        }
        val rightBounds = remember(context, camdConfigRev, shapeCircle, rectCorners) {
            CamdCameraRepository.calculateSideCameraWindowBounds(
                context = context,
                isRightSide = true,
                shapeCircleOverride = shapeCircle,
                rectCornersOverride = rectCorners,
            )
        }

        Box(modifier = modifier.fillMaxSize()) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val strokeWidthPx = 80.dp.toPx()
                        val leftCache = leftBounds?.let { buildCameraWindowBrushCache(it, alertRed, strokeWidthPx) }
                        val rightCache = rightBounds?.let { buildCameraWindowBrushCache(it, alertRed, strokeWidthPx) }
                        onDrawBehind {
                            val pulse = pulseState.value
                            if (bsdLeftAlert && leftCache != null) {
                                drawCameraWindowAlert(leftCache, pulse)
                            }
                            if (bsdRightAlert && rightCache != null) {
                                drawCameraWindowAlert(rightCache, pulse)
                            }
                        }
                    },
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            // Left 150.dp bar (solid red at x=0 -> transparent at x=150dp)
            if (bsdLeftAlert) {
                Spacer(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(150.dp)
                        .fillMaxHeight()
                        .drawWithCache {
                            val brush = Brush.horizontalGradient(
                                0.00f to alertRed.copy(alpha = 0.85f),
                                0.30f to alertRed.copy(alpha = 0.55f),
                                0.70f to alertRed.copy(alpha = 0.20f),
                                1.00f to alertRed.copy(alpha = 0f),
                                startX = 0f,
                                endX = size.width,
                            )
                            val rectSize = Size(size.width, size.height)
                            onDrawBehind {
                                drawRect(
                                    brush = brush,
                                    topLeft = Offset.Zero,
                                    size = rectSize,
                                    alpha = pulseState.value,
                                )
                            }
                        },
                )
            }

            // Right 150.dp bar (transparent at x=0 -> solid red at x=150dp)
            if (bsdRightAlert) {
                Spacer(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(150.dp)
                        .fillMaxHeight()
                        .drawWithCache {
                            val brush = Brush.horizontalGradient(
                                0.00f to alertRed.copy(alpha = 0f),
                                0.30f to alertRed.copy(alpha = 0.20f),
                                0.70f to alertRed.copy(alpha = 0.55f),
                                1.00f to alertRed.copy(alpha = 0.85f),
                                startX = 0f,
                                endX = size.width,
                            )
                            val rectSize = Size(size.width, size.height)
                            onDrawBehind {
                                drawRect(
                                    brush = brush,
                                    topLeft = Offset.Zero,
                                    size = rectSize,
                                    alpha = pulseState.value,
                                )
                            }
                        },
                )
            }
        }
    }
}

private sealed class CameraWindowBrushCache {
    class Circle(
        val brush: Brush,
        val center: Offset,
        val radius: Float,
    ) : CameraWindowBrushCache()

    class Rect(
        val topBrush: Brush,
        val topPos: Offset,
        val topSize: Size,
        val bottomBrush: Brush,
        val bottomPos: Offset,
        val bottomSize: Size,
        val leftBrush: Brush,
        val leftPos: Offset,
        val leftSize: Size,
        val rightBrush: Brush,
        val rightPos: Offset,
        val rightSize: Size,
    ) : CameraWindowBrushCache()
}

private fun buildCameraWindowBrushCache(
    bounds: SideCameraWindowBounds,
    color: Color,
    strokeWidth: Float,
): CameraWindowBrushCache {
    return if (bounds.isCircle) {
        val center = Offset(bounds.xPx + (bounds.widthPx / 2f), bounds.yPx + (bounds.heightPx / 2f))
        val radius = bounds.widthPx / 2f
        val effectiveStroke = strokeWidth.coerceAtMost(radius)
        val innerRadius = (radius - effectiveStroke).coerceAtLeast(0f)
        val startRatio = if (radius > 0f) (innerRadius / radius).coerceIn(0f, 1f) else 0f
        CameraWindowBrushCache.Circle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    startRatio to Color.Transparent,
                    (startRatio + ((1f - startRatio) * 0.30f)) to color.copy(alpha = 0.20f),
                    (startRatio + ((1f - startRatio) * 0.70f)) to color.copy(alpha = 0.55f),
                    1.00f to color.copy(alpha = 0.85f),
                ),
                center = center,
                radius = radius.coerceAtLeast(1f),
            ),
            center = center,
            radius = radius,
        )
    } else {
        val left = bounds.xPx
        val top = bounds.yPx
        val width = bounds.widthPx
        val height = bounds.heightPx
        val right = left + width
        val bottom = top + height
        val effectiveStroke = strokeWidth.coerceAtMost(minOf(width, height) / 2f)

        CameraWindowBrushCache.Rect(
            topBrush = Brush.verticalGradient(
                0.00f to color.copy(alpha = 0.85f),
                0.30f to color.copy(alpha = 0.55f),
                0.70f to color.copy(alpha = 0.20f),
                1.00f to color.copy(alpha = 0f),
                startY = top,
                endY = top + effectiveStroke,
            ),
            topPos = Offset(left, top),
            topSize = Size(width, effectiveStroke),
            bottomBrush = Brush.verticalGradient(
                0.00f to color.copy(alpha = 0f),
                0.30f to color.copy(alpha = 0.20f),
                0.70f to color.copy(alpha = 0.55f),
                1.00f to color.copy(alpha = 0.85f),
                startY = bottom - effectiveStroke,
                endY = bottom,
            ),
            bottomPos = Offset(left, bottom - effectiveStroke),
            bottomSize = Size(width, effectiveStroke),
            leftBrush = Brush.horizontalGradient(
                0.00f to color.copy(alpha = 0.85f),
                0.30f to color.copy(alpha = 0.55f),
                0.70f to color.copy(alpha = 0.20f),
                1.00f to color.copy(alpha = 0f),
                startX = left,
                endX = left + effectiveStroke,
            ),
            leftPos = Offset(left, top),
            leftSize = Size(effectiveStroke, height),
            rightBrush = Brush.horizontalGradient(
                0.00f to color.copy(alpha = 0f),
                0.30f to color.copy(alpha = 0.20f),
                0.70f to color.copy(alpha = 0.55f),
                1.00f to color.copy(alpha = 0.85f),
                startX = right - effectiveStroke,
                endX = right,
            ),
            rightPos = Offset(right - effectiveStroke, top),
            rightSize = Size(effectiveStroke, height),
        )
    }
}

private fun DrawScope.drawCameraWindowAlert(
    cache: CameraWindowBrushCache,
    pulse: Float,
) {
    when (cache) {
        is CameraWindowBrushCache.Circle -> {
            drawCircle(
                brush = cache.brush,
                radius = cache.radius,
                center = cache.center,
                alpha = pulse,
            )
        }
        is CameraWindowBrushCache.Rect -> {
            drawRect(brush = cache.topBrush, topLeft = cache.topPos, size = cache.topSize, alpha = pulse)
            drawRect(brush = cache.bottomBrush, topLeft = cache.bottomPos, size = cache.bottomSize, alpha = pulse)
            drawRect(brush = cache.leftBrush, topLeft = cache.leftPos, size = cache.leftSize, alpha = pulse)
            drawRect(brush = cache.rightBrush, topLeft = cache.rightPos, size = cache.rightSize, alpha = pulse)
        }
    }
}

/**
 * System overlay window manager that presents [LauncherBsdAlertOverlay]
 * above all external application windows when SYSTEM_ALERT_WINDOW permission is available.
 */
internal object LauncherBsdAlertOverlayWindow {
    private const val TAG = "LauncherBsdAlertOverlayWindow"

    private val overlayWindow = ManagedComposeOverlayWindow(TAG)
    private val _isShowingFlow = MutableStateFlow(false)
    val isShowingFlow: StateFlow<Boolean> = _isShowingFlow.asStateFlow()

    fun isShowing(): Boolean = overlayWindow.isShowing

    fun update(context: Context, adas: LauncherAdasState) {
        if (!LauncherAppConfigStore.adasBsdAlertOverlayEnabled(context)) {
            hide()
            return
        }

        val hasAlert = adas.rearThreats.bsdLeft == LauncherRearThreatLevel.Alert ||
            adas.rearThreats.bsdRight == LauncherRearThreatLevel.Alert

        if (!hasAlert) {
            hide()
            return
        }

        if (isShowing()) return

        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val shown = overlayWindow.show(
            context = activity,
            params = params,
            viewModelStoreOwner = activity,
        ) {
            ProvideUiClickSounds {
                val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
                val currentAdas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
                LauncherBsdAlertOverlay(adas = currentAdas)
            }
        }
        _isShowingFlow.value = shown
    }

    fun hide() {
        overlayWindow.hide()
        _isShowingFlow.value = false
    }
}
