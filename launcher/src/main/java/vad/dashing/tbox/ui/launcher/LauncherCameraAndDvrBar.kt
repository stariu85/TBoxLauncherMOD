package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdConnectionStatus
import vad.dashing.tbox.camd.CamdWidgetCameraMode

private val AdasPanelButtonHeight = 60.dp

/**
 * 2-page ADAS panel:
 * Page 0 (Первая панель): 3 camera buttons (Left/Right, Rear/Front, 360).
 * Page 1 (Вторая панель): 2 DVR buttons (REC, SOS).
 * Swipe left switches from Cameras to DVR.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCameraAndDvrBar(
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    reportSubPanel: Boolean = true,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { 2 }

    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, reportSubPanel) {
        if (reportSubPanel) {
            val visibleSub = if (pagerState.currentPage == 1) "camd_dvr" else "camd_cameras"
            LauncherAppConfigStore.setVisibleSubPanel("camd_controls", visibleSub)
            LauncherAppConfigStore.setVisibleSubPanel("cameras", visibleSub)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(AdasPanelButtonHeight),
    ) { page ->
        when (page) {
            0 -> LauncherCamdCamerasPage(modifier = Modifier.fillMaxSize())
            1 -> LauncherCamdDvrPage(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Первая панель: 3 кнопки камер (как в виджете камеры в доке): лево/право, зад/перед, 360.
 * Значки увеличены на 50% и отцентрированы без подписей.
 */
@Composable
fun LauncherCamdCamerasPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activeMode by CamdCameraRepository.activeWidgetCameraMode.collectAsStateWithLifecycle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AdasPanelButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Button 1: Left / Right
        CamdControlButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_camd_widget_left_right,
            contentDescription = "Камеры Право / Лево",
            isActive = activeMode == CamdWidgetCameraMode.LEFT_RIGHT,
            onClick = {
                CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.LEFT_RIGHT)
            },
        )

        // Button 2: Rear / Front (Up / Down)
        CamdControlButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_camd_widget_up_down,
            contentDescription = "Камеры Верх / Низ",
            isActive = activeMode == CamdWidgetCameraMode.UP_DOWN,
            onClick = {
                CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.UP_DOWN)
            },
        )

        // Button 3: 360
        CamdControlButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_camd_widget_360,
            contentDescription = "Камеры 360",
            isActive = activeMode == CamdWidgetCameraMode.ALL_360,
            onClick = {
                CamdCameraRepository.toggleWidgetCameras(context, CamdWidgetCameraMode.ALL_360)
            },
        )
    }
}

/**
 * Вторая панель: панель видеорегистратора (REC и SOS).
 * Значки слева от надписей, всё отцентрировано, размер надписей согласован с другими панелями ADAS (19.sp).
 */
@Composable
fun LauncherCamdDvrPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(context) {
        CamdCameraRepository.startStatusPolling(context)
    }
    val status by CamdCameraRepository.status.collectAsStateWithLifecycle()
    val connected = status as? CamdConnectionStatus.Connected
    val recState = status.resolveRecIndicatorState()
    val isRecorderOn = recState != vad.dashing.tbox.camd.CamdRecIndicatorState.OFF
    val isSosOngoing = connected?.recSos == true

    var localSosSaving by remember { mutableStateOf(false) }
    val showSosActive = isSosOngoing || localSosSaving

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AdasPanelButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Button 1: REC
        val recAccentColor = when (recState) {
            vad.dashing.tbox.camd.CamdRecIndicatorState.ACTIVE -> Color(0xFFEF4444)
            vad.dashing.tbox.camd.CamdRecIndicatorState.STANDBY -> LauncherColors.AccentBlue
            vad.dashing.tbox.camd.CamdRecIndicatorState.OFF -> Color.Transparent
        }
        val recBgColor by animateColorAsState(
            targetValue = if (isRecorderOn) recAccentColor.copy(alpha = 0.22f) else LauncherColors.LeftPanelCard,
            label = "rec_bg",
        )
        val recBorderColor by animateColorAsState(
            targetValue = recAccentColor,
            label = "rec_border",
        )
        val recIconRes = when (recState) {
            vad.dashing.tbox.camd.CamdRecIndicatorState.ACTIVE -> R.drawable.ic_camd_rec_active
            vad.dashing.tbox.camd.CamdRecIndicatorState.STANDBY -> R.drawable.ic_camd_rec_standby
            vad.dashing.tbox.camd.CamdRecIndicatorState.OFF -> R.drawable.ic_camd_rec_idle
        }
        val recTextColor = when (recState) {
            vad.dashing.tbox.camd.CamdRecIndicatorState.ACTIVE -> Color(0xFFEF4444)
            vad.dashing.tbox.camd.CamdRecIndicatorState.STANDBY -> LauncherColors.AccentBlue
            vad.dashing.tbox.camd.CamdRecIndicatorState.OFF -> LauncherColors.LeftTextPrimary
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(AdasPanelButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(recBgColor)
                .border(
                    width = if (isRecorderOn) 1.5.dp else 0.dp,
                    color = recBorderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        scope.launch {
                            CamdCameraRepository.setRecorderEnabled(context, !isRecorderOn)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(recIconRes),
                    contentDescription = "REC",
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REC",
                    color = recTextColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Button 2: SOS
        val sosBgColor by animateColorAsState(
            targetValue = if (showSosActive) Color(0xFFEF4444).copy(alpha = 0.22f) else LauncherColors.LeftPanelCard,
            label = "sos_bg",
        )
        val sosBorderColor by animateColorAsState(
            targetValue = if (showSosActive) Color(0xFFEF4444) else Color.Transparent,
            label = "sos_border",
        )
        val sosIconTint by animateColorAsState(
            targetValue = if (showSosActive) Color(0xFFEF4444) else Color(0xFFE2E8F0),
            label = "sos_icon_tint",
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(AdasPanelButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(sosBgColor)
                .border(
                    width = if (showSosActive) 1.5.dp else 0.dp,
                    color = sosBorderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !showSosActive,
                    onClick = {
                        scope.launch {
                            localSosSaving = true
                            CamdCameraRepository.triggerRecorderSos(context)
                            delay(2000L)
                            localSosSaving = false
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_camd_sos),
                    contentDescription = "SOS",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(sosIconTint),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showSosActive) "SOS..." else "SOS",
                    color = if (showSosActive) Color(0xFFEF4444) else LauncherColors.LeftTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CamdControlButton(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF0284C7).copy(alpha = 0.28f) else LauncherColors.LeftPanelCard,
        label = "cam_btn_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF38BDF8) else Color.Transparent,
        label = "cam_btn_border",
    )
    val tintColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF38BDF8) else LauncherColors.LeftTextPrimary,
        label = "cam_btn_tint",
    )

    Box(
        modifier = modifier
            .height(AdasPanelButtonHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(tintColor),
        )
    }
}
