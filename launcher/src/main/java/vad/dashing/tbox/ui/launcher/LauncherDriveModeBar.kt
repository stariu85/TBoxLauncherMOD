package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeDisplayLabel
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 60.dp

@Composable
fun LauncherDriveModeBar(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
    initialPage: Int? = null,
    reportSubPanel: Boolean = true,
    onModeSelected: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    // Gear states
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()

    val simGearOverride = LauncherDevVehicleState.gearSlotOverride
    val parsedGear = simGearOverride ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    var latchedGear by remember { mutableStateOf<Char?>(null) }
    SideEffect {
        if (parsedGear != null) latchedGear = parsedGear
    }
    val activeGear = parsedGear ?: latchedGear

    // Drive mode states
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveModeWetRaw by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()

    val resolvedLabel = remember(driveModeRaw, driveModeWetRaw, gearBoxDriveMode) {
        resolveDriveModeDisplayLabel(
            driveModeRaw = driveModeRaw,
            driveModeWetRaw = driveModeWetRaw,
            gearBoxDriveMode = gearBoxDriveMode,
        ).uppercase()
    }

    var pendingMode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resolvedLabel) {
        if (pendingMode == resolvedLabel) {
            pendingMode = null
        }
    }
    LaunchedEffect(pendingMode) {
        if (pendingMode != null) {
            kotlinx.coroutines.delay(3500L)
            pendingMode = null
        }
    }
    val activeMode = pendingMode ?: resolvedLabel

    val isEco = activeMode == "ECO"
    val isNor = activeMode == "NOR" || activeMode == "NORMAL" || activeMode == "COMFORT"
    val isSpt = activeMode == "SPT" || activeMode == "SPORT"
    val isSnow = activeMode == "SNOW" || activeMode == "СНЕГ"
    val isMud = activeMode == "MUD" || activeMode == "ГРЯЗЬ"
    val isSand = activeMode == "SAND" || activeMode == "ПЕСОК"

    val startPage = initialPage ?: (if (isSnow || isMud || isSand) 1 else 0)
    val pagerState = rememberPagerState(initialPage = startPage) { 2 }
    LaunchedEffect(pagerState.currentPage, reportSubPanel) {
        if (reportSubPanel) {
            val visibleSub = if (pagerState.currentPage == 1) "drive_mode_offroad" else "drive_mode"
            LauncherAppConfigStore.setVisibleSubPanel("drive_mode", visibleSub)
        }
    }

    LaunchedEffect(isSnow, isMud, isSand) {
        if (initialPage == null && (isSnow || isMud || isSand)) {
            pagerState.animateScrollToPage(1)
        }
    }

    val onSelectMode: (String) -> Unit = { mode ->
        pendingMode = mode
        onModeSelected?.invoke(mode)
        scope.launch {
            val (stdValue, wetValue) = when (mode) {
                "ECO" -> 2 to 1
                "NOR" -> 0 to 2
                "SPT" -> 1 to 0
                "SNOW" -> 3 to 2
                "MUD" -> 4 to 2
                "SAND" -> 5 to 2
                else -> 0 to 2
            }
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE, stdValue)
            )
            UniversalCanRepository.execute(
                MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET, wetValue)
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
    ) { page ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Slot 1: Active Gear Indicator
            GearIndicatorChip(
                activeGear = activeGear,
                currentGearNum = gearBoxCurrentGear,
                modifier = Modifier
                    .weight(1f)
                    .height(ButtonHeight),
            )

            if (page == 0) {
                // Slot 2: ECO Button
                DriveModeChip(
                    label = "ECO",
                    selected = isEco,
                    activeColor = Color(0xFF16A34A), // Green
                    onClick = { onSelectMode("ECO") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )

                // Slot 3: NOR Button
                DriveModeChip(
                    label = "NOR",
                    selected = isNor,
                    activeColor = Color(0xFF3B82F6), // Blue
                    onClick = { onSelectMode("NOR") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )

                // Slot 4: SPT Button
                DriveModeChip(
                    label = "SPT",
                    selected = isSpt,
                    activeColor = Color(0xFFEF4444), // Red
                    onClick = { onSelectMode("SPT") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )
            } else {
                // Slot 2: SNOW Button
                DriveModeChip(
                    label = "SNOW",
                    selected = isSnow,
                    activeColor = Color(0xFFFFFFFF), // White background
                    borderColor = Color(0xFF64748B), // Dark grey border
                    textColorOverride = Color(0xFF334155), // Dark grey text
                    onClick = { onSelectMode("SNOW") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )

                // Slot 3: MUD Button
                DriveModeChip(
                    label = "MUD",
                    selected = isMud,
                    activeColor = Color(0xFFF97316), // Orange
                    onClick = { onSelectMode("MUD") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )

                // Slot 4: SAND Button
                DriveModeChip(
                    label = "SAND",
                    selected = isSand,
                    activeColor = Color(0xFFEAB308), // Yellow
                    onClick = { onSelectMode("SAND") },
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonHeight),
                )
            }
        }
    }
}

@Composable
private fun GearIndicatorChip(
    activeGear: Char?,
    currentGearNum: Int?,
    modifier: Modifier = Modifier,
) {
    val gearText = remember(activeGear, currentGearNum) {
        if (activeGear == 'D') {
            if (currentGearNum != null && currentGearNum > 0) {
                "D $currentGearNum"
            } else {
                "D"
            }
        } else {
            activeGear?.toString() ?: "-"
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.LeftPanelCard),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = gearText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.LeftTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DriveModeChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    textColorOverride: Color? = null,
    onClick: () -> Unit,
) {
    val effectiveBorderColor = borderColor ?: activeColor.copy(alpha = 0.85f)
    val textColor = if (selected) (textColorOverride ?: activeColor) else LauncherColors.LeftTextPrimary

    val backgroundModifier = if (selected) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    activeColor.copy(alpha = 0.35f),
                    activeColor.copy(alpha = 0.15f),
                )
            )
        )
    } else {
        Modifier.background(LauncherColors.LeftPanelCard)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(backgroundModifier)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, effectiveBorderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
