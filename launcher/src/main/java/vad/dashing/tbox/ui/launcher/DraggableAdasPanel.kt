package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DraggableAdasPanel(
    panelId: String,
    containerHeightPx: Float,
    containerWindowTopPx: Float = 0f,
    topPanels: List<String>,
    bottomPanels: List<String>,
    onLayoutChanged: (topPanels: List<String>, bottomPanels: List<String>) -> Unit,
    showBadges: Boolean = false,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialPanelTopPx by remember { mutableFloatStateOf(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1.00f,
        animationSpec = tween(150),
        label = "adasPanelScale",
    )

    fun removePanelFromAdas() {
        val newTop = topPanels.filter { it != panelId }
        val newBottom = bottomPanels.filter { it != panelId }

        when (panelId) {
            "media_player" -> LauncherAppConfigStore.setMediaMiniPlayerVisible(context, false)
            "drive_mode" -> LauncherAppConfigStore.setDriveModeBarVisible(context, false)
            "body_controls" -> LauncherAppConfigStore.setBodyControlsPanelVisible(context, false)
            "assist" -> LauncherAppConfigStore.setAssistPanelVisible(context, false)
            "car_color" -> LauncherAppConfigStore.setCarColorPanelVisible(context, false)
            "nav_speed" -> LauncherAppConfigStore.setNavSpeedVisible(context, false)
            "nav_traffic_light" -> LauncherAppConfigStore.setNavTrafficLightVisible(context, false)
        }

        LauncherAppConfigStore.setAdasPanelsLayout(context, newTop, newBottom)
        Toast.makeText(context, "Панель удалена из зоны ADAS", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 300f else 1f)
            .onGloballyPositioned { coordinates ->
                if (!isDragging) {
                    val cardWindowTopPx = coordinates.boundsInWindow().top
                    initialPanelTopPx = if (containerWindowTopPx > 0f) {
                        cardWindowTopPx - containerWindowTopPx
                    } else {
                        val parent = coordinates.parentLayoutCoordinates
                        if (parent != null) parent.localPositionOf(coordinates, Offset.Zero).y else 0f
                    }
                    panelHeightPx = coordinates.size.height.toFloat()
                }
            }
            .graphicsLayer {
                // Direct translationY via draw-phase reading -> ZERO recompositions & ZERO lag!
                translationY = dragOffsetY
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isDragging) 0.92f else 1.0f
                compositingStrategy = CompositingStrategy.Auto
            }
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, LauncherColors.AccentCyan, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .pointerInput(panelId, topPanels, bottomPanels, containerHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = SystemClock.uptimeMillis()
                    var dragActive = false
                    var accumulativeDragY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!pointer.pressed) {
                            if (dragActive) {
                                pointer.consume()
                                val releasedCenterY = initialPanelTopPx + panelHeightPx / 2f + accumulativeDragY
                                val newLayout = calculateNewPanelLayout(
                                    draggedPanelId = panelId,
                                    releasedCenterY = releasedCenterY,
                                    containerHeightPx = containerHeightPx,
                                    currentTop = topPanels,
                                    currentBottom = bottomPanels,
                                )
                                vad.dashing.tbox.DclLogRecorder.logAdasPanelDrag(
                                    panelId = panelId,
                                    initialTopPx = initialPanelTopPx,
                                    panelHeightPx = panelHeightPx,
                                    dragOffsetY = accumulativeDragY,
                                    releasedCenterY = releasedCenterY,
                                    topPanels = newLayout.first,
                                    bottomPanels = newLayout.second,
                                )
                                isDragging = false
                                dragOffsetY = 0f
                                onLayoutChanged(newLayout.first, newLayout.second)
                                onDragEnded()
                            }
                            break
                        }

                        val elapsed = SystemClock.uptimeMillis() - downTime

                        if (!dragActive) {
                            val totalDeltaY = kotlin.math.abs(pointer.position.y - down.position.y)
                            val totalDeltaX = kotlin.math.abs(pointer.position.x - down.position.x)
                            if (totalDeltaY > 12f && totalDeltaY > totalDeltaX * 1.2f) {
                                dragActive = true
                                isDragging = true
                                onDragStarted()
                                accumulativeDragY = pointer.position.y - down.position.y
                                dragOffsetY = accumulativeDragY
                                pointer.consume()
                            } else if (elapsed >= 350L) {
                                dragActive = true
                                isDragging = true
                                onDragStarted()
                                accumulativeDragY = 0f
                                dragOffsetY = accumulativeDragY
                                pointer.consume()
                            }
                        } else {
                            val frameDeltaY = pointer.position.y - pointer.previousPosition.y
                            accumulativeDragY += frameDeltaY
                            dragOffsetY = accumulativeDragY
                            pointer.consume()
                        }
                    }
                }
            },
    ) {
        content()

        if (showBadges || isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .zIndex(350f)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .clickable {
                        removePanelFromAdas()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "−",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

internal fun calculateNewPanelLayout(
    draggedPanelId: String,
    releasedCenterY: Float,
    containerHeightPx: Float,
    currentTop: List<String>,
    currentBottom: List<String>,
): Pair<List<String>, List<String>> {
    val newTop = currentTop.filter { it != draggedPanelId }.toMutableList()
    val newBottom = currentBottom.filter { it != draggedPanelId }.toMutableList()

    val midPoint = if (containerHeightPx > 0f) containerHeightPx / 2f else 300f
    val slotHeightPx = 130f

    val topSectionHeight = newTop.size * slotHeightPx
    val bottomSectionHeight = newBottom.size * slotHeightPx
    val bottomSectionTop = (containerHeightPx - bottomSectionHeight).coerceAtLeast(midPoint)

    if (releasedCenterY < midPoint) {
        // Drop into TOP section
        if (newTop.isEmpty()) {
            newTop.add(draggedPanelId)
        } else if (releasedCenterY <= topSectionHeight + 40f) {
            // Dropped over existing top panels -> find matching slot index and insert/swap
            val step = if (topSectionHeight > 0f) topSectionHeight / newTop.size else slotHeightPx
            var insertIndex = newTop.size
            for (i in newTop.indices) {
                if (releasedCenterY < (i + 0.8f) * step) {
                    insertIndex = i
                    break
                }
            }
            newTop.add(insertIndex.coerceIn(0, newTop.size), draggedPanelId)
        } else {
            // Dropped in empty space in top half -> snap/attach at bottom of top section
            newTop.add(draggedPanelId)
        }
    } else {
        // Drop into BOTTOM section
        if (newBottom.isEmpty()) {
            newBottom.add(draggedPanelId)
        } else if (releasedCenterY >= bottomSectionTop - 40f) {
            // Dropped over existing bottom panels -> find matching slot index and insert/swap
            val step = if (bottomSectionHeight > 0f) bottomSectionHeight / newBottom.size else slotHeightPx
            var insertIndex = newBottom.size
            for (i in newBottom.indices) {
                val slotThresholdY = bottomSectionTop + (i + 0.8f) * step
                if (releasedCenterY < slotThresholdY) {
                    insertIndex = i
                    break
                }
            }
            newBottom.add(insertIndex.coerceIn(0, newBottom.size), draggedPanelId)
        } else {
            // Dropped in empty space in bottom half -> snap/attach at top of bottom section
            newBottom.add(0, draggedPanelId)
        }
    }

    return Pair(newTop, newBottom)
}
