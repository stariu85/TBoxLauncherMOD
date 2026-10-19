package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private const val LONG_PRESS_TIMEOUT_MS = 3000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DraggableAdasPanel(
    panelId: String,
    containerHeightPx: Float,
    topPanels: List<String>,
    bottomPanels: List<String>,
    onLayoutChanged: (topPanels: List<String>, bottomPanels: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialPanelTopPx by remember { mutableFloatStateOf(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1.00f,
        animationSpec = tween(150),
        label = "adasPanelScale",
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 300f else 1f)
            .onGloballyPositioned { coordinates ->
                if (!isDragging) {
                    val parent = coordinates.parentLayoutCoordinates
                    if (parent != null) {
                        val positionInParent = parent.localPositionOf(coordinates, Offset.Zero)
                        initialPanelTopPx = positionInParent.y
                        panelHeightPx = coordinates.size.height.toFloat()
                    }
                }
            }
            .graphicsLayer {
                // Direct translationY via draw-phase reading -> ZERO recompositions & ZERO lag!
                translationY = dragOffsetY
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (isDragging) 0.92f else 1.0f
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
                            }
                            break
                        }

                        val elapsed = SystemClock.uptimeMillis() - downTime

                        if (!dragActive) {
                            if (elapsed >= LONG_PRESS_TIMEOUT_MS) {
                                dragActive = true
                                isDragging = true
                                accumulativeDragY = 0f
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
    }
}

private fun calculateNewPanelLayout(
    draggedPanelId: String,
    releasedCenterY: Float,
    containerHeightPx: Float,
    currentTop: List<String>,
    currentBottom: List<String>,
): Pair<List<String>, List<String>> {
    val newTop = currentTop.filter { it != draggedPanelId }.toMutableList()
    val newBottom = currentBottom.filter { it != draggedPanelId }.toMutableList()

    val midPoint = if (containerHeightPx > 0f) containerHeightPx / 2f else 300f

    if (releasedCenterY < midPoint) {
        // Drop into TOP section
        if (newTop.isEmpty()) {
            newTop.add(draggedPanelId)
        } else if (newTop.size == 1) {
            if (releasedCenterY < midPoint * 0.5f) {
                newTop.add(0, draggedPanelId)
            } else {
                newTop.add(draggedPanelId)
            }
        } else {
            val step = midPoint / newTop.size
            var insertIndex = newTop.size
            for (i in newTop.indices) {
                if (releasedCenterY < step * (i + 1)) {
                    insertIndex = i
                    break
                }
            }
            newTop.add(insertIndex.coerceIn(0, newTop.size), draggedPanelId)
        }
    } else {
        // Drop into BOTTOM section
        if (newBottom.isEmpty()) {
            newBottom.add(draggedPanelId)
        } else if (newBottom.size == 1) {
            if (releasedCenterY < midPoint + (midPoint * 0.5f)) {
                newBottom.add(0, draggedPanelId)
            } else {
                newBottom.add(draggedPanelId)
            }
        } else {
            val bottomHeight = containerHeightPx - midPoint
            val step = bottomHeight / newBottom.size
            var insertIndex = newBottom.size
            for (i in newBottom.indices) {
                if (releasedCenterY < midPoint + step * (i + 1)) {
                    insertIndex = i
                    break
                }
            }
            newBottom.add(insertIndex.coerceIn(0, newBottom.size), draggedPanelId)
        }
    }

    return Pair(newTop, newBottom)
}
