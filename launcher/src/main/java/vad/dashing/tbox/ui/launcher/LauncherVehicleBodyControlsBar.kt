package vad.dashing.tbox.ui.launcher

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MirrorFoldLastCommandStore
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendToggleMirrorFold

private val ButtonHeight = 60.dp

/**
 * Vehicle Body Controls Panel at the bottom of the ADAS zone:
 * Page 0:
 *   1. Trunk Button (long-click opens/closes trunk if gear is 'P', else warns "Переключите передачу в P")
 *   2. Windows Button (single tap to close all, double tap to open all, sent sequentially with 1s delay)
 * Page 1 (Swipe Left):
 *   1. Driver Seat Memory Button (opens SeatAdjustmentDialogActivity)
 *   2. Rearview Mirror Adjustment Button (opens RearviewMirrorAngleAdjustmentDialogActivity)
 *   3. Passenger Seat Adjustment Button (opens PassengerSeatAdjustDialog)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherVehicleBodyControlsBar(
    activeGear: Char?,
    onShowTrunkParkWarning: () -> Unit,
    modifier: Modifier = Modifier,
    reportSubPanel: Boolean = true,
    initialPage: Int = 1,
) {
    val context = LocalContext.current

    val pagerState = rememberPagerState(initialPage = initialPage) { 3 }
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }
    LaunchedEffect(pagerState.currentPage, reportSubPanel) {
        if (reportSubPanel) {
            val visibleSub = when (pagerState.currentPage) {
                0 -> "body_controls_trunk"
                1 -> "body_controls_seat_memory"
                2 -> "body_controls_seats"
                else -> "body_controls_seat_memory"
            }
            LauncherAppConfigStore.setVisibleSubPanel("body_controls", visibleSub)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
    ) { page ->
        when (page) {
            0 -> {
                // Page 0 (Swipe Right from Central): Trunk & Windows buttons
                LauncherTrunkWindowsMirrorFoldBar(
                    activeGear = activeGear,
                    onShowTrunkParkWarning = onShowTrunkParkWarning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            1 -> {
                // Page 1 (Central): Driver seat memory presets
                LauncherSeatMemoryPresetsBar(modifier = Modifier.fillMaxWidth())
            }
            else -> {
                // Page 2 (Swipe Left from Central): Seat & Mirror adjustment buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Combined Button (Weight 2f): Sliders + Driver Seat + Mirror -> Opens Mirror Adjustment Settings
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(ButtonHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LauncherColors.LeftPanelCard)
                            .clickable { launchMirrorAdjustSettingsEmbedded(context) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_settings_sliders),
                                contentDescription = "Настройки",
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_seat_memory_btn),
                                contentDescription = "Регулировка водительского сиденья",
                                modifier = Modifier.size(30.dp),
                                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_rearview_mirror),
                                contentDescription = "Регулировка зеркал",
                                modifier = Modifier.size(36.dp),
                                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                            )
                        }
                    }

                    // Button 2 (Weight 1f): Passenger Seat Adjustment Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LauncherColors.LeftPanelCard)
                            .clickable { PassengerSeatOverlayWindow.show(context) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_seat_memory_btn),
                            contentDescription = "Регулировка пассажирского сиденья",
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { scaleX = -1f },
                            colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LauncherSeatMemoryPresetsBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeSlot by remember { mutableStateOf<Int?>(null) }
    var resetJob by remember { mutableStateOf<Job?>(null) }

    fun recallSlot(slot: Int) {
        activeSlot = slot
        resetJob?.cancel()
        Toast.makeText(context, "Применение памяти: Слот $slot", Toast.LENGTH_SHORT).show()
        resetJob = scope.launch(Dispatchers.IO) {
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, slot)
            )
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_CALLOUTREQ, slot)
            )
            delay(1000L)
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION, 0)
            )
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SEAT_POSITION_CALLOUTREQ, 0)
            )
            delay(1500L)
            activeSlot = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeGreen = Color(0xFF10B981)
        for (slot in 1..3) {
            val isActive = activeSlot == slot
            val bg = if (isActive) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
            val border = if (isActive) activeGreen else Color.Transparent

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ButtonHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .then(
                        if (isActive) {
                            Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable {
                        recallSlot(slot)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val iconRes = when (slot) {
                        1 -> R.drawable.ic_seat_memory_drv1
                        2 -> R.drawable.ic_seat_memory_drv2
                        else -> R.drawable.ic_seat_memory_drv3
                    }
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = "DRV$slot",
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(
                            if (isActive) activeGreen else LauncherColors.LeftTextPrimary
                        ),
                    )
                    Text(
                        text = "DRV$slot",
                        color = if (isActive) activeGreen else LauncherColors.LeftTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherTrunkWindowsMirrorFoldBar(
    activeGear: Char?,
    onShowTrunkParkWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var windowJob by remember { mutableStateOf<Job?>(null) }
    var isOperatingWindows by remember { mutableStateOf(false) }

    fun triggerWindowsCommand(targetPercent: Int) {
        windowJob?.cancel()
        windowJob = scope.launch {
            isOperatingWindows = true
            try {
                sendSequentialWindowCommands(targetPercent)
            } finally {
                isOperatingWindows = false
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: Trunk Button (Long press to open/close trunk)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.LeftPanelCard)
                .combinedClickable(
                    onClick = {
                        if (activeGear != 'P') {
                            onShowTrunkParkWarning()
                        }
                    },
                    onLongClick = {
                        if (activeGear == 'P') {
                            sendOpenCloseTrunk(context)
                        } else {
                            onShowTrunkParkWarning()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_trunk),
                contentDescription = "Багажник",
                modifier = Modifier.size(40.dp),
                colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
            )
        }

        // Slot 2: Windows Button (Single tap to close all, double tap to open all)
        val activeGreen = Color(0xFF10B981)
        val windowsBg = if (isOperatingWindows) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
        val windowsBorder = if (isOperatingWindows) activeGreen else Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(windowsBg)
                .then(
                    if (isOperatingWindows) {
                        Modifier.border(1.5.dp, windowsBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            triggerWindowsCommand(0)
                        },
                        onDoubleTap = {
                            triggerWindowsCommand(100)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_widget_side_window),
                contentDescription = "Окна",
                modifier = Modifier.size(40.dp),
                colorFilter = ColorFilter.tint(
                    if (isOperatingWindows) activeGreen else LauncherColors.LeftTextPrimary,
                ),
            )
        }

        // Slot 3: Mirror Folding Button
        val mirrorFoldVal = MirrorFoldLastCommandStore.readLastValue()
        val isMirrorFolded = mirrorFoldVal == MIRROR_FOLD_SWITCH_VALUE_FOLD
        val mirrorBg = if (isMirrorFolded) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
        val mirrorBorder = if (isMirrorFolded) activeGreen else Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(mirrorBg)
                .then(
                    if (isMirrorFolded) {
                        Modifier.border(1.5.dp, mirrorBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable {
                    sendToggleMirrorFold(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_mirror),
                contentDescription = "Складывание зеркал",
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(
                    if (isMirrorFolded) activeGreen else LauncherColors.LeftTextPrimary,
                ),
            )
        }
    }
}

private enum class SmartPanelView {
    SeatPresets,   // Driver Seat Memory Presets (DRV1, DRV2, DRV3)
    CruiseControl, // ACC Speed Presets
    TrunkWindows,  // Trunk, Windows & Mirror Fold
}

@Composable
fun LauncherSmartPanel(
    activeGear: Char?,
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    onShowTrunkParkWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var wasInDrive by remember { mutableStateOf(activeGear == 'D') }
    var currentView by remember {
        mutableStateOf(
            if (activeGear == 'D') SmartPanelView.CruiseControl else SmartPanelView.SeatPresets
        )
    }

    LaunchedEffect(activeGear) {
        if (activeGear == 'D') {
            wasInDrive = true
            currentView = SmartPanelView.CruiseControl
        } else if (activeGear == 'P') {
            if (wasInDrive) {
                currentView = SmartPanelView.TrunkWindows
            }
            wasInDrive = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = currentView,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "smart_panel_transition",
        ) { view ->
            when (view) {
                SmartPanelView.SeatPresets -> {
                    LauncherVehicleBodyControlsBar(
                        activeGear = activeGear,
                        onShowTrunkParkWarning = onShowTrunkParkWarning,
                        initialPage = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SmartPanelView.CruiseControl -> {
                    LauncherAssistAndPresetsPanel(
                        canViewModel = canViewModel,
                        adas = adas,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SmartPanelView.TrunkWindows -> {
                    LauncherVehicleBodyControlsBar(
                        activeGear = activeGear,
                        onShowTrunkParkWarning = onShowTrunkParkWarning,
                        initialPage = 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private suspend fun sendSequentialWindowCommands(percent: Int) {
    val target = percent.coerceIn(0, 100)
    val activeTargets = mutableMapOf<Int, Int>()
    val windowIndices = intArrayOf(0, 1, 2, 3) // 0: Driver, 1: Passenger, 2: Rear Left, 3: Rear Right
    for (i in windowIndices.indices) {
        if (i > 0) {
            delay(1000L)
        }
        val windowIdx = windowIndices[i]
        activeTargets[windowIdx] = target
        MbCanEngineFacade.setWindowPositions(activeTargets)
    }
    delay(2500L)
}
