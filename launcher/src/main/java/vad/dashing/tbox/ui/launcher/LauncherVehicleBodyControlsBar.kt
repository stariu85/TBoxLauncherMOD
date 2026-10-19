package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.ui.sendOpenCloseTrunk

private val ButtonHeight = 60.dp

/**
 * Vehicle Body Controls Panel at the bottom of the ADAS zone:
 * 1. Trunk Button (long-click opens/closes trunk if gear is 'P', else warns "Переключите передачу в P")
 * 2. Windows Button (single tap to close all, double tap to open all, sent sequentially with 1s delay)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherVehicleBodyControlsBar(
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
                            // Single tap -> Close all windows (0%)
                            triggerWindowsCommand(0)
                        },
                        onDoubleTap = {
                            // Double tap -> Open all windows fully (100%)
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
