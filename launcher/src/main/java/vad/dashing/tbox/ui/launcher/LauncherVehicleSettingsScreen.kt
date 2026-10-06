package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.ui.theme.tboxCaption

/**
 * Opens vehicle settings as a system overlay above freeform windows when possible,
 * otherwise as an in-launcher full-screen overlay (no reveal animation).
 */
internal fun launchVehicleSettingsWindow(context: Context) {
    LauncherVehicleSettingsWindow.hide()
    LauncherVehicleSettingsActivity.finishIfOpen()
    val shownAsSystemOverlay = LauncherVehicleSettingsOverlayWindow.show(context)
    // Always mark open so the home 3D SceneView is torn down — two Filament
    // views on the HU make the settings model hitch.
    LauncherVehicleSettingsUiState.markOpen()
    if (!shownAsSystemOverlay) {
        LauncherVehicleSettingsUiState.revealProgress = 1f
        LauncherOverlayElevator.forceRecoverToFront(context)
        LauncherOverlayElevator.setHoldSource("vehicle_settings", true)
    }
    LauncherVehicleBodyRepository.ensurePolling()
}

internal fun closeVehicleSettingsOverlay() {
    LauncherVehicleSettingsOverlayWindow.hide()
    LauncherVehicleSettingsWindow.hide()
    LauncherVehicleSettingsActivity.finishIfOpen()
    LauncherVehicleSettingsUiState.markClosed()
    LauncherOverlayElevator.setHoldSource("vehicle_settings", false)
}

/**
 * Full-screen vehicle settings: controls on the left, compact 3D car on the right.
 */
@Composable
fun LauncherVehicleSettingsScreen(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    onClose: () -> Unit,
    @Suppress("UNUSED_PARAMETER") revealProgress: Float = 1f,
) {
    val context = LocalContext.current
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    var paintId by remember(context) { mutableStateOf(LauncherAppConfigStore.carPaintId(context)) }
    var paintRevision by remember { mutableStateOf(0) }
    var expandedSection by remember { mutableStateOf<VehicleSettingsSection?>(VehicleSettingsSection.Status) }
    val settingsOrbit = remember { LauncherSettingsOrbitState() }
    var wheelAnchors by remember {
        mutableStateOf<Map<LauncherWheelCorner, Offset>>(emptyMap())
    }
    var doorAnchors by remember {
        mutableStateOf<Map<LauncherWheelCorner, Offset>>(emptyMap())
    }
    var windowFeedback by remember { mutableStateOf<WindowGestureFeedback?>(null) }
    var bodyRigAvailable by remember { mutableStateOf(false) }
    var simUnlockTapCount by remember { mutableStateOf(0) }
    var simUnlocked by remember { mutableStateOf(LauncherDevVehicleState.simulateEnabled) }

    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val tiresReal by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val simulate = LauncherDevVehicleState.simulateEnabled
    val effectiveBody = if (simulate) LauncherDevVehicleState.bodyState() else vehicleBody
    val effectiveTires = LauncherDevVehicleState.tireStateOrNull() ?: tiresReal
    val motion = rememberLauncherVehicleMotion(tboxConnected, canViewModel)
    val steer = rememberLauncherVisualSteer(motion)
    val rig = LauncherCarRigState(
        doorFlOpen = effectiveBody.doorFlOpen,
        doorFrOpen = effectiveBody.doorFrOpen,
        doorRlOpen = effectiveBody.doorRlOpen,
        doorRrOpen = effectiveBody.doorRrOpen,
        tailgateOpen = effectiveBody.tailgateOpen,
        speedKmh = motion.speedKmh,
        steeringDeg = steer,
    )
    val setWindowPosition: (LauncherWheelCorner, Int) -> Unit = { corner, position ->
        context.applicationContext.startService(
            Intent(context.applicationContext, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_MBCAN_COMMAND
                putExtra(
                    BackgroundService.EXTRA_MBCAN_COMMAND_TYPE,
                    BackgroundService.MBCAN_COMMAND_SET_WINDOW,
                )
                putExtra(BackgroundService.EXTRA_MBCAN_PROPERTY_ID, corner.windowIndex())
                putExtra(BackgroundService.EXTRA_MBCAN_VALUE, position)
            },
        )
    }
    LaunchedEffect(windowFeedback) {
        val feedback = windowFeedback ?: return@LaunchedEffect
        if (!feedback.committed) return@LaunchedEffect
        delay(1_500L)
        if (windowFeedback == feedback) windowFeedback = null
    }
    val modelGestureModifier = Modifier
        .fillMaxSize()
        .pointerInput(settingsOrbit) {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                settingsOrbit.beginInteraction()
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)
                val selectedWindow = doorAnchors.minByOrNull { (_, anchor) ->
                    val dx = anchor.x - firstDown.position.x
                    val dy = anchor.y - firstDown.position.y
                    dx * dx + dy * dy
                }?.takeIf { (_, anchor) ->
                    val dx = anchor.x - firstDown.position.x
                    val dy = anchor.y - firstDown.position.y
                    dx * dx + dy * dy <= 140f * 140f
                }?.key
                val startWindowPercent = selectedWindow?.let { corner ->
                    MbCanEngineFacade.readWindowPosition(corner.windowIndex())
                }
                var multiTouchSeen = false
                var totalPan = Offset.Zero
                var windowSwipe = false
                var rotationSwipe = selectedWindow == null
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedPointers = event.changes.count { it.pressed }
                        when {
                            pressedPointers >= 2 -> {
                                multiTouchSeen = true
                                windowSwipe = false
                                rotationSwipe = false
                                windowFeedback = null
                                val zoom = event.calculateZoom()
                                settingsOrbit.multiplyScale(zoom)
                            }

                            pressedPointers == 1 && !multiTouchSeen -> {
                                val pan = event.calculatePan()
                                totalPan += pan
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        velocityTracker.addPosition(
                                            change.uptimeMillis,
                                            change.position,
                                        )
                                    }
                                }
                                if (selectedWindow != null && !windowSwipe && !rotationSwipe) {
                                    if (abs(totalPan.y) > 12f && abs(totalPan.y) > abs(totalPan.x) * 1.2f) {
                                        windowSwipe = true
                                    } else if (abs(totalPan.x) > 12f && abs(totalPan.x) > abs(totalPan.y) * 1.2f) {
                                        rotationSwipe = true
                                        windowFeedback = null
                                    }
                                }
                                if (selectedWindow != null && windowSwipe) {
                                    val start = startWindowPercent
                                        ?: if (totalPan.y >= 0f) 0 else 100
                                    val target = (
                                        start + totalPan.y / WINDOW_SWIPE_FULL_RANGE_PX * 100f
                                        ).roundToInt().coerceIn(0, 100)
                                    windowFeedback = WindowGestureFeedback(
                                        corner = selectedWindow,
                                        percent = target,
                                        committed = false,
                                    )
                                }
                                if (rotationSwipe) {
                                    settingsOrbit.addYaw(pan.x / SETTINGS_YAW_PX_PER_DEG)
                                }
                            }
                        }

                        event.changes.forEach { change ->
                            if (change.positionChanged()) change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    settingsOrbit.endInteraction()
                    if (rotationSwipe && !multiTouchSeen && !windowSwipe) {
                        settingsOrbit.applyFlingFromPxVelocity(velocityTracker.calculateVelocity().x)
                    }
                }
                if (
                    selectedWindow != null &&
                    windowSwipe &&
                    !multiTouchSeen &&
                    abs(totalPan.y) >= 25f
                ) {
                    val target = windowFeedback?.percent ?: startWindowPercent
                    if (target != null) {
                        setWindowPosition(
                            selectedWindow,
                            target,
                        )
                        windowFeedback = WindowGestureFeedback(
                            corner = selectedWindow,
                            percent = target,
                            committed = true,
                        )
                    }
                } else if (windowFeedback?.committed != true) {
                    windowFeedback = null
                }
            }
        }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherColors.SettingsBackground)
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 82.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .padding(end = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.launcher_vehicle_settings_title),
                            style = MaterialTheme.typography.tboxCaption,
                            color = LauncherColors.TextPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "v${vad.dashing.tbox.BuildConfig.VERSION_NAME.substringBefore('-')}",
                            color = LauncherColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        IconButton(
                            onClick = { launchTBoxSettings(context) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.action_configure),
                                tint = LauncherColors.AccentCyan,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.launcher_vs_overlay_hint),
                        color = LauncherColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = LauncherColors.TextSecondary,
                    )
                }
            }
            LauncherVehicleSettingsContent(
                canViewModel = canViewModel,
                tboxViewModel = tboxViewModel,
                paintId = paintId,
                onPaintChanged = { id ->
                    paintId = id
                    paintRevision++
                    LauncherAppConfigStore.setCarPaintId(context, id)
                },
                expandedSection = expandedSection,
                onSectionToggle = { section ->
                    expandedSection = if (expandedSection == section) null else section
                },
                simulationUnlocked = simUnlocked,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LauncherColors.SurfaceDark)
                    .padding(12.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LauncherColors.SettingsModelCenter,
                            LauncherColors.SettingsModelEdge,
                        ),
                        radius = 900f,
                    ),
                )
                .border(
                    width = 1.dp,
                    color = LauncherColors.SettingsBorder,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            LauncherCar3DModel(
                rigState = rig,
                speedKmh = motion.speedKmh,
                steeringDeg = steer,
                steerPreview = motion.steerPreviewActive,
                modelRevision = 0,
                paintRevision = paintRevision,
                paintId = paintId,
                showRoad = false,
                settingsView = true,
                settingsProgress = 1f,
                settingsOrbit = settingsOrbit,
                onWheelAnchorsChanged = { wheelAnchors = it },
                onDoorAnchorsChanged = { doorAnchors = it },
                onBodyRigAvailabilityChanged = { bodyRigAvailable = it },
                // Inline compositing keeps the tire badges above the 3D model here.
                textureSurface = true,
                modifier = Modifier.fillMaxSize(),
            )
            LauncherTireBadges(
                state = effectiveTires,
                wheelAnchorsPx = wheelAnchors,
                showAll = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (!bodyRigAvailable) {
                LauncherDoorBadges(
                    body = effectiveBody,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            windowFeedback?.let { feedback ->
                LauncherWindowGestureFeedback(
                    feedback = feedback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                )
            }
            // A dedicated topmost touch layer keeps SceneView from intercepting pinch gestures.
            Box(modifier = modelGestureModifier)
            // Hidden simulation unlock: 8 taps in the top-right corner of the 3D model.
            // (Bottom corners are covered by the dock overlay, taps would never reach here.)
            if (!simUnlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(96.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            simUnlockTapCount++
                            if (simUnlockTapCount >= 8) {
                                simUnlocked = true
                                LauncherDevVehicleState.simulateEnabled = true
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.launcher_vs_sim_unlocked,
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                )
            }
        }
    }
}

private const val WINDOW_SWIPE_FULL_RANGE_PX = 220f

private data class WindowGestureFeedback(
    val corner: LauncherWheelCorner,
    val percent: Int,
    val committed: Boolean,
)

private fun LauncherWheelCorner.windowIndex(): Int = when (this) {
    LauncherWheelCorner.FL -> 0
    LauncherWheelCorner.FR -> 1
    LauncherWheelCorner.RL -> 2
    LauncherWheelCorner.RR -> 3
}

@Composable
private fun LauncherWindowGestureFeedback(
    feedback: WindowGestureFeedback,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.94f))
            .border(1.dp, LauncherColors.AccentCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (feedback.corner) {
                    LauncherWheelCorner.FL -> "Переднее левое"
                    LauncherWheelCorner.FR -> "Переднее правое"
                    LauncherWheelCorner.RL -> "Заднее левое"
                    LauncherWheelCorner.RR -> "Заднее правое"
                },
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${feedback.percent}%",
                color = LauncherColors.AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(LauncherColors.SettingsBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(feedback.percent / 100f)
                    .height(5.dp)
                    .background(LauncherColors.AccentCyan),
            )
        }
    }
}
