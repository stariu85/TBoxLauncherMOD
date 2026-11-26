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
import androidx.compose.ui.input.pointer.PointerEventPass
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


private const val ADAS_PANEL_DRAG_HOLD_TIMEOUT_MS = 1000L

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
        fun canonicalPanelId(id: String): String = when (id) {
            "body_controls_trunk", "body_controls_seats", "body_controls_seat_memory" -> "body_controls"
            "drive_mode_offroad" -> "drive_mode"
            "presets", "smart_control" -> "assist"
            "cameras", "camd_cameras", "camd_dvr" -> "camd_controls"
            else -> id
        }

        val basePanelId = canonicalPanelId(panelId)
        val newTop = topPanels.filter { canonicalPanelId(it) != basePanelId }
        val newBottom = bottomPanels.filter { canonicalPanelId(it) != basePanelId }

        when (basePanelId) {
            "media_player" -> LauncherAppConfigStore.setMediaMiniPlayerVisible(context, false)
            "drive_mode" -> {
                LauncherAppConfigStore.setDriveModeBarVisible(context, false)
                val quickTop = LauncherAppConfigStore.quickSettingsTopPanels(context).toMutableList()
                val quickBottom = LauncherAppConfigStore.quickSettingsBottomPanels(context).toMutableList()
                listOf("drive_mode", "drive_mode_offroad").forEach { sub ->
                    if (sub !in quickTop && sub !in quickBottom) {
                        quickBottom.add(sub)
                    }
                }
                LauncherAppConfigStore.setQuickSettingsPanelsLayout(context, quickTop, quickBottom)
            }
            "body_controls" -> {
                LauncherAppConfigStore.setBodyControlsPanelVisible(context, false)
                val quickTop = LauncherAppConfigStore.quickSettingsTopPanels(context).toMutableList()
                val quickBottom = LauncherAppConfigStore.quickSettingsBottomPanels(context).toMutableList()
                listOf("body_controls_seat_memory", "body_controls_trunk", "body_controls_seats").forEach { sub ->
                    if (sub !in quickTop && sub !in quickBottom) {
                        quickBottom.add(sub)
                    }
                }
                LauncherAppConfigStore.setQuickSettingsPanelsLayout(context, quickTop, quickBottom)
            }
            "assist" -> LauncherAppConfigStore.setAssistPanelVisible(context, false)
            "car_color" -> LauncherAppConfigStore.setCarColorPanelVisible(context, false)
            "nav_speed" -> LauncherAppConfigStore.setNavSpeedVisible(context, false)
            "nav_traffic_light" -> LauncherAppConfigStore.setNavTrafficLightVisible(context, false)
            "smart_panel" -> LauncherAppConfigStore.setSmartPanelEnabled(context, false)
            "camd_controls" -> {
                LauncherAppConfigStore.setCamdControlsPanelVisible(context, false)
                val quickTop = LauncherAppConfigStore.quickSettingsTopPanels(context).toMutableList()
                val quickBottom = LauncherAppConfigStore.quickSettingsBottomPanels(context).toMutableList()
                if ("camd_cameras" !in quickTop && "camd_cameras" !in quickBottom) {
                    quickBottom.add("camd_cameras")
                }
                if ("camd_dvr" !in quickTop && "camd_dvr" !in quickBottom) {
                    quickBottom.add("camd_dvr")
                }
                LauncherAppConfigStore.setQuickSettingsPanelsLayout(context, quickTop, quickBottom)
            }
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
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val downTime = SystemClock.uptimeMillis()
                    val touchSlop = viewConfiguration.touchSlop
                    var accumulativeDragY = 0f

                    while (true) {
                        val remainingMs = ADAS_PANEL_DRAG_HOLD_TIMEOUT_MS - (SystemClock.uptimeMillis() - downTime)
                        if (remainingMs <= 0L) {
                            break
                        }
                        val event = withTimeoutOrNull(remainingMs) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        } ?: break
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!pointer.pressed) {
                            return@awaitEachGesture
                        }
                        if ((pointer.position - down.position).getDistance() > touchSlop) {
                            return@awaitEachGesture
                        }
                    }

                    isDragging = true
                    onDragStarted()
                    dragOffsetY = 0f

                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!pointer.pressed) {
                                pointer.consume()
                                finished = true
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
                                break
                            }

                            val frameDeltaY = pointer.position.y - pointer.previousPosition.y
                            accumulativeDragY += frameDeltaY
                            dragOffsetY = accumulativeDragY
                            pointer.consume()
                        }
                    } finally {
                        if (!finished) {
                            isDragging = false
                            dragOffsetY = 0f
                            onDragEnded()
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
