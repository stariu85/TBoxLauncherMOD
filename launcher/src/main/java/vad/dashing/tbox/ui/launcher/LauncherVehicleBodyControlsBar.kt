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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.ui.sendOpenCloseTrunk

private val ButtonHeight = 60.dp

/**
 * Vehicle Body Controls Panel at the bottom of the ADAS zone:
 * 1. Trunk Button (long-click opens/closes trunk if gear is 'P', else warns "Переключите передачу в P")
 * 2. Windows Button (hold to open gradually, single tap to close all, double tap to open all)
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
    var isOpeningWindows by remember { mutableStateOf(false) }

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

        // Slot 2: Windows Button (Hold to open, single tap close all, double tap open all)
        val activeGreen = Color(0xFF10B981)
        val windowsBg = if (isOpeningWindows) activeGreen.copy(alpha = 0.25f) else LauncherColors.LeftPanelCard
        val windowsBorder = if (isOpeningWindows) activeGreen else Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(windowsBg)
                .then(
                    if (isOpeningWindows) {
                        Modifier.border(1.5.dp, windowsBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Single tap -> Close all windows (0%)
                            setAllWindowsPosition(0)
                        },
                        onDoubleTap = {
                            // Double tap -> Open all windows fully (100%)
                            setAllWindowsPosition(100)
                        },
                        onPress = {
                            // Press and hold -> Gradually open windows
                            isOpeningWindows = true
                            val job = scope.launch {
                                var current = readAverageWindowPosition()
                                while (isActive) {
                                    current = (current + 6).coerceAtMost(100)
                                    setAllWindowsPosition(current)
                                    delay(110L)
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                job.cancel()
                                isOpeningWindows = false
                            }
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
                    if (isOpeningWindows) activeGreen else LauncherColors.LeftTextPrimary,
                ),
            )
        }
    }
}

private fun setAllWindowsPosition(percent: Int) {
    val target = percent.coerceIn(0, 100)
    for (i in 0..3) {
        MbCanEngineFacade.setWindowPosition(i, target)
    }
}

private fun readAverageWindowPosition(): Int {
    var sum = 0
    var count = 0
    for (i in 0..3) {
        val pos = MbCanEngineFacade.readWindowPosition(i)
        if (pos != null) {
            sum += pos
            count++
        }
    }
    return if (count > 0) sum / count else 0
}
