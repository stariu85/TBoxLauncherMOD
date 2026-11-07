package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import vad.dashing.tbox.ui.clickableWithSound
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.sendSetMbCanProperty
import vad.dashing.tbox.ui.sendToggleLaneMode
import vad.dashing.tbox.ui.sendToggleParkingRadar
import vad.dashing.tbox.ui.sendToggleRearFogLight
import vad.dashing.tbox.ui.theme.tboxCaption

private val ButtonHeight = 60.dp

internal enum class AssistChipState {
    Off,
    Standby,
    Active,
    Unavailable,
}

/**
 * Main Central Page: ACC and NGP (2 large buttons)
 */
@Composable
fun LauncherAssistControlBar(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val tjaIcaState by UniversalCanRepository.tjaIcaState.collectAsStateWithLifecycle()

    // 1. ACC (Cruise Control) State
    val accDisplaySpeed = adas.accSetSpeedKmh ?: tboxCruise?.toInt()?.takeIf { it > 0 }
    val accState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasCruiseActive) AssistChipState.Active else AssistChipState.Off
        }
        adas.accActive -> AssistChipState.Active
        adas.accStandby -> AssistChipState.Standby
        (tboxCruise ?: 0u) > 0u -> AssistChipState.Active
        else -> AssistChipState.Off
    }

    // 2. NGP State
    val ngpState = when {
        LauncherDevVehicleState.simulateEnabled -> {
            if (LauncherDevVehicleState.adasLanesActive) AssistChipState.Active else AssistChipState.Off
        }
        tjaIcaState is MbCanBinaryState.Unavailable -> AssistChipState.Unavailable
        adas.tja == LauncherAdasAssistIcon.Active -> AssistChipState.Active
        tjaIcaState is MbCanBinaryState.On || adas.tja == LauncherAdasAssistIcon.Dark -> AssistChipState.Standby
        else -> AssistChipState.Off
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: ACC / Cruise Button
        val accLabel = if (accState != AssistChipState.Off && accDisplaySpeed != null && accDisplaySpeed > 0) {
            accDisplaySpeed.toString()
        } else {
            "ACC"
        }

        AssistButtonChip(
            label = accLabel,
            iconRes = R.drawable.ic_launcher_cruise,
            state = accState,
            onClick = {
                LauncherCruisePresetController.launchStatusSingleTap(context)
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: NGP Button
        AssistButtonChip(
            label = stringResource(R.string.launcher_cruise_ngp_title),
            iconRes = R.drawable.ic_adas_steering_wheel,
            state = ngpState,
            onClick = {
                if (ngpState == AssistChipState.Unavailable) return@AssistButtonChip
                LauncherCruisePresetController.launchNgpWithSetMinus(context)
            },
            iconSize = 22.dp,
            fontSize = 19.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

/**
 * Safety Assist Panel: LDW, LKA, and FCW (3 buttons).
 */
@Composable
fun LauncherLanesControlBar(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val currentLasMode = vehicleControls.lasMode ?: adas.lasMode
    val currentLasSensitivity = vehicleControls.lasSensitivity

    val currentFcwSwitch = vehicleControls.fcw ?: adas.fcwActive
    val currentFcwOption = vehicleControls.fcwOption

    var ldwTapCount by remember { mutableIntStateOf(0) }
    var lkaTapCount by remember { mutableIntStateOf(0) }
    var sensitivityFallbackJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(currentLasMode) {
        if (currentLasMode != 1) ldwTapCount = 0
        if (currentLasMode != 2) lkaTapCount = 0
    }

    // 1. LDW Button Logic
    val isLdwActive = currentLasMode == 1
    val ldwColor = when {
        !isLdwActive -> null // OFF
        currentLasSensitivity == 1 -> Color(0xFFF97316) // Level 1 (High) = Orange
        else -> Color(0xFF10B981) // Level 0 (Min / Low) = Green
    }

    val onLdwClick: () -> Unit = {
        if (!isLdwActive) {
            val wasBothOff = currentLasMode != 2
            // 1st tap: Turn ON LDW (Property 17 = 1, Property 80 = 2). Button color reflects current sensitivity from CAN.
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 1)
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 2)
            ldwTapCount = 1
            lkaTapCount = 0

            if (wasBothOff) {
                sensitivityFallbackJob?.cancel()
                sensitivityFallbackJob = scope.launch {
                    delay(1000L)
                    if (vehicleControls.lasSensitivity == null) {
                        sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, 0)
                    }
                }
            }
        } else {
            sensitivityFallbackJob?.cancel()
            // LDW is active. Cycle sensitivity 1 time, on 3rd tap turn OFF (Property 17 = 3, Property 80 = 1).
            val nextTap = ldwTapCount + 1
            if (nextTap >= 3) {
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 3)
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 1)
                ldwTapCount = 0
            } else {
                val currentSens = currentLasSensitivity ?: 0
                val nextSens = if (currentSens >= 1) 0 else 1
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, nextSens)
                ldwTapCount = nextTap
            }
        }
    }

    // 2. LKA Button Logic
    val isLkaActive = currentLasMode == 2
    val lkaColor = when {
        !isLkaActive -> null // OFF
        currentLasSensitivity == 1 -> Color(0xFFF97316) // Level 1 (High) = Orange
        else -> Color(0xFF10B981) // Level 0 (Min / Low) = Green
    }

    val onLkaClick: () -> Unit = {
        if (!isLkaActive) {
            val wasBothOff = currentLasMode != 1
            // 1st tap: Turn ON LKA (Property 17 = 2, Property 80 = 2). Button color reflects current sensitivity from CAN.
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 2)
            sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 2)
            lkaTapCount = 1
            ldwTapCount = 0

            if (wasBothOff) {
                sensitivityFallbackJob?.cancel()
                sensitivityFallbackJob = scope.launch {
                    delay(1000L)
                    if (vehicleControls.lasSensitivity == null) {
                        sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, 0)
                    }
                }
            }
        } else {
            sensitivityFallbackJob?.cancel()
            // LKA is active. Cycle sensitivity 1 time, on 3rd tap turn OFF (Property 17 = 3, Property 80 = 1).
            val nextTap = lkaTapCount + 1
            if (nextTap >= 3) {
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, 3)
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LDW_SWITCH, 1)
                lkaTapCount = 0
            } else {
                val currentSens = currentLasSensitivity ?: 0
                val nextSens = if (currentSens >= 1) 0 else 1
                sendSetMbCanProperty(context, MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, nextSens)
                lkaTapCount = nextTap
            }
        }
    }

    // 3. FCW Button Logic
    val isFcwActive = currentFcwSwitch
    val fcwColor = when {
        !isFcwActive -> null // State 0: OFF (серый по умолчанию)
        currentFcwOption == 1 -> Color(0xFF10B981) // State 1: Близко (зеленый)
        currentFcwOption == 2 -> Color(0xFF3B82F6) // State 2: Средний (синий)
        currentFcwOption == 3 -> Color(0xFFEF4444) // State 3: Дальний (красный)
        else -> Color(0xFF10B981) // Fallback в активном состоянии: зеленый (близко)
    }

    val onFcwClick: () -> Unit = {
        vad.dashing.tbox.ui.sendCycleFcw(context, isFcwActive, currentFcwOption)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: LDW Button
        AssistColoredChip(
            label = "LDW",
            iconRes = R.drawable.ic_adas_lka,
            activeColor = ldwColor,
            onClick = onLdwClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: LKA Button
        AssistColoredChip(
            label = "LKA",
            iconRes = R.drawable.ic_adas_lka,
            activeColor = lkaColor,
            onClick = onLkaClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: FCW Button (Three Arcs Icon)
        AssistColoredChip(
            label = "FCW",
            iconRes = R.drawable.ic_fcw_arcs,
            activeColor = fcwColor,
            onClick = onFcwClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

/**
 * Radar Panel: BSD, DOW, and PARK (Parktronic) (3 buttons).
 */
@Composable
fun LauncherRadarControlBar(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val activeGreen = Color(0xFF10B981)

    // Local / simulated state fallbacks
    var simBsd by remember { mutableStateOf(false) }
    var simDow by remember { mutableStateOf(false) }

    // 1. BSD State & Click
    val bsdActive = vehicleControls.bsd ?: simBsd
    val bsdColor = if (bsdActive) activeGreen else null
    val onBsdClick: () -> Unit = {
        val next = !bsdActive
        simBsd = next
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.BSD_SWITCH,
            if (next) 2 else 1,
        )
    }

    // 2. DOW State & Click
    val dowActive = vehicleControls.dow ?: simDow
    val dowColor = if (dowActive) activeGreen else null
    val onDowClick: () -> Unit = {
        val next = !dowActive
        simDow = next
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.DOW_SWITCH,
            if (next) 2 else 1,
        )
    }

    // 3. PARK (Parktronic) State & Click
    val parkActive = vehicleControls.parkingRadar == true
    val parkColor = if (parkActive) activeGreen else null
    val onParkClick: () -> Unit = {
        sendToggleParkingRadar(context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: BSD Button
        AssistColoredChip(
            label = "BSD",
            iconRes = R.drawable.ic_bsd_blindspot,
            activeColor = bsdColor,
            onClick = onBsdClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: DOW Button
        AssistColoredChip(
            label = "DOW",
            iconRes = R.drawable.ic_dow_door,
            activeColor = dowColor,
            onClick = onDowClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: RAD (Parktronic) Button
        AssistColoredChip(
            label = "RAD",
            iconRes = R.drawable.ic_widget_parking_radar,
            activeColor = parkColor,
            onClick = onParkClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

/**
 * Chassis / Brake & Stability Panel: ESC (ESP), HDC, and HOLD (3 buttons).
 */
@Composable
fun LauncherChassisControlBar(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val activeGreen = Color(0xFF10B981)
    val disabledRed = Color(0xFFEF4444)

    // Local / simulated state fallbacks
    var simEscOff by remember { mutableStateOf(false) }
    var simHdc by remember { mutableStateOf(false) }
    var simHold by remember { mutableStateOf(false) }

    // 1. ESC / ESP Button Logic:
    // Green when ON (escOff == false), Red when OFF (escOff == true).
    val isEscOff = vehicleControls.escOff ?: simEscOff
    val escColor = if (isEscOff) disabledRed else activeGreen
    val onEscClick: () -> Unit = {
        val nextEscOff = !isEscOff
        simEscOff = nextEscOff
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH,
            if (nextEscOff) 2 else 1, // 2 = ESC Off (Red), 1 = ESC On (Green)
        )
    }

    // 2. HDC Button Logic:
    // Green when ON, Grey when OFF.
    val isHdcActive = vehicleControls.hdc ?: simHdc
    val hdcColor = if (isHdcActive) activeGreen else null
    val onHdcClick: () -> Unit = {
        val nextHdc = !isHdcActive
        simHdc = nextHdc
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.HDC_SWITCH,
            if (nextHdc) 2 else 1,
        )
    }

    // 3. HOLD (Auto Hold) Button Logic:
    // Green when ON, Grey when OFF.
    val isHoldActive = vehicleControls.avh ?: vehicleControls.accAutobrake ?: simHold
    val holdColor = if (isHoldActive) activeGreen else null
    val onHoldClick: () -> Unit = {
        val nextHold = !isHoldActive
        simHold = nextHold
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.AVH_SWITCH,
            if (nextHold) 2 else 1,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: ESC / ESP Button
        AssistColoredChip(
            label = "ESC",
            iconRes = R.drawable.ic_esp_icon,
            activeColor = escColor,
            onClick = onEscClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: HDC Button
        AssistColoredChip(
            label = "HDC",
            iconRes = R.drawable.ic_launcher_hdc,
            activeColor = hdcColor,
            onClick = onHdcClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: HLD Button
        AssistColoredChip(
            label = "HLD",
            iconRes = R.drawable.ic_hold_brake,
            activeColor = holdColor,
            onClick = onHoldClick,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

/**
 * Lights Panel: Headlights Mode, Auto High Beam (HMA), and Rear Fog Light (3 buttons).
 */
@Composable
fun LauncherLightsControlBar(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)

    val activeGreen = Color(0xFF10B981)
    val activeTurquoise = Color(0xFF06B6D4)
    val activeBlue = Color(0xFF3B82F6)
    val activeOrange = Color(0xFFF97316)

    // Local / simulated state fallbacks
    var simLightRaw by remember { mutableIntStateOf(LIGHT_CONTROL_AUTO) }
    var simHma by remember { mutableStateOf<Boolean?>(null) }
    var simRearFog by remember { mutableStateOf(false) }

    LaunchedEffect(vehicleControls.hmaOn) {
        if (vehicleControls.hmaOn != null) {
            simHma = vehicleControls.hmaOn
        }
    }

    // 1. Headlights Button Logic:
    // AUTO (Green) -> DRL/POS (Turquoise) -> LOW BEAM (Blue) -> OFF (Grey)
    val currentLightRaw = vehicleControls.lightControlRaw ?: simLightRaw
    val (lightIcon, lightColor) = when (currentLightRaw) {
        LIGHT_CONTROL_AUTO -> R.drawable.ic_headlights_auto to activeGreen
        LIGHT_CONTROL_POSITION -> R.drawable.ic_headlights_position to activeTurquoise
        LIGHT_CONTROL_LOW_BEAM -> R.drawable.ic_headlights_low_beam to activeBlue
        else -> R.drawable.ic_headlights_off to null
    }

    val onLightClick: () -> Unit = {
        val nextRaw = when (currentLightRaw) {
            LIGHT_CONTROL_AUTO -> LIGHT_CONTROL_POSITION
            LIGHT_CONTROL_POSITION -> LIGHT_CONTROL_LOW_BEAM
            LIGHT_CONTROL_LOW_BEAM -> LIGHT_CONTROL_OFF
            else -> LIGHT_CONTROL_AUTO
        }
        simLightRaw = nextRaw
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.LIGHT_CONTROL,
            nextRaw,
        )
    }

    // 2. Auto High Beam (HMA) Button Logic:
    // OFF (Grey), ON (Green)
    val isHmaActive = simHma ?: vehicleControls.hmaOn ?: (adas.hma == LauncherAdasAssistIcon.Active)
    val hmaColor = if (isHmaActive) activeGreen else null
    val onHmaClick: () -> Unit = {
        val nextHma = !isHmaActive
        simHma = nextHma
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.HMA_SWITCH,
            if (nextHma) 2 else 1,
        )
    }

    // 3. Rear Fog Light Button Logic:
    // OFF (Grey), ON (Orange)
    val isRearFogActive = vehicleControls.rearFogLight ?: simRearFog
    val rearFogColor = if (isRearFogActive) activeOrange else null
    val onRearFogClick: () -> Unit = {
        val nextFog = !isRearFogActive
        simRearFog = nextFog
        sendToggleRearFogLight(context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: Headlights Control (No text, 40dp icon centered)
        AssistColoredChip(
            label = "",
            iconRes = lightIcon,
            activeColor = lightColor,
            onClick = onLightClick,
            iconSize = 40.dp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 2: Auto High Beam (IHBC) (No text, 40dp icon centered)
        AssistColoredChip(
            label = "",
            iconRes = R.drawable.ic_high_beam_auto,
            activeColor = hmaColor,
            onClick = onHmaClick,
            iconSize = 40.dp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Slot 3: Rear Fog Light (ПТФ) (No text, 40dp icon centered)
        AssistColoredChip(
            label = "",
            iconRes = R.drawable.ic_launcher_rear_fog,
            activeColor = rearFogColor,
            onClick = onRearFogClick,
            iconSize = 40.dp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}

@Composable
private fun AssistColoredChip(
    label: String,
    activeColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    iconSize: Dp = 22.dp,
    fontSize: TextUnit = 19.sp,
) {
    val isOff = activeColor == null
    val accentColor = activeColor ?: LauncherColors.LeftTextPrimary
    val border = activeColor ?: Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.LeftPanelCard)
            .then(
                if (!isOff) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickableWithSound(onClickLabel = label.ifBlank { "Chip" }) {
                DclLogRecorder.logUiClick("AssistColoredChip", label = label.ifBlank { iconRes?.toString() ?: "chip" })
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label.isBlank()) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = ColorFilter.tint(accentColor),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        colorFilter = ColorFilter.tint(accentColor),
                    )
                }
                Text(
                    text = label,
                    color = accentColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AssistButtonChip(
    label: String,
    state: AssistChipState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    iconSize: Dp = 22.dp,
    fontSize: TextUnit = 19.sp,
) {
    val activeGreen = Color(0xFF10B981)
    val unavailableRed = Color(0xFFEF4444)
    val standbyTextColor = if (LauncherThemeState.darkTheme) Color.White else Color(0xFF374151)

    val (textColor, bg, border) = when (state) {
        AssistChipState.Active -> Triple(
            activeGreen,
            LauncherColors.LeftPanelCard, // Standard card background
            activeGreen,                  // Green border!
        )
        AssistChipState.Standby -> Triple(
            standbyTextColor,             // Graphite in light theme, White in dark theme!
            LauncherColors.LeftPanelCard, // Same card background as Active!
            activeGreen,                  // Same green border as Active!
        )
        AssistChipState.Unavailable -> Triple(
            unavailableRed,
            LauncherColors.LeftPanelCard,
            unavailableRed,                         // Red border when system is unavailable!
        )
        AssistChipState.Off -> Triple(
            LauncherColors.LeftTextPrimary,
            LauncherColors.LeftPanelCard,
            Color.Transparent,
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (state != AssistChipState.Off) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickableWithSound(onClickLabel = label) {
                DclLogRecorder.logUiClick("AssistButtonChip", label = label)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = ColorFilter.tint(textColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.tboxCaption,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Wipers Panel: Service Mode & Auto Sensitivity (2 buttons).
 */
@Composable
fun LauncherWiperControlBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)
    val wiperMaintenance by UniversalCanRepository.wiperMaintenanceState.collectAsStateWithLifecycle()

    val isMaintenanceActive = wiperMaintenance is MbCanBinaryState.On
    val maintenanceColor = if (isMaintenanceActive) LauncherColors.AccentCyan else null

    val onMaintenanceClick: () -> Unit = {
        vad.dashing.tbox.ui.sendToggleWiperMaintenance(context)
    }

    var simSensitivity by remember { mutableIntStateOf(1) }

    LaunchedEffect(vehicleControls.wiperSensitivity) {
        if (vehicleControls.wiperSensitivity != null) {
            simSensitivity = vehicleControls.wiperSensitivity
        }
    }

    val currentSens = vehicleControls.wiperSensitivity ?: simSensitivity
    val sensitivityColor = when (currentSens) {
        1 -> Color(0xFFEF4444) // Red
        2 -> Color(0xFFEAB308) // Yellow
        3 -> Color(0xFF3B82F6) // Blue
        4 -> Color(0xFF22C55E) // Green
        else -> Color(0xFFEF4444)
    }

    val onSensitivityClick: () -> Unit = {
        val nextSens = if (currentSens >= 4) 1 else currentSens + 1
        simSensitivity = nextSens
        sendSetMbCanProperty(
            context,
            MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
            nextSens,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Button 1: Service Mode
        AssistColoredChip(
            label = "Сервис",
            iconRes = R.drawable.ic_wiper,
            activeColor = maintenanceColor,
            onClick = onMaintenanceClick,
            iconSize = 22.dp,
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )

        // Button 2: Auto Sensitivity
        AssistColoredChip(
            label = "Авто $currentSens",
            iconRes = R.drawable.ic_windshield,
            activeColor = sensitivityColor,
            onClick = onSensitivityClick,
            iconSize = 22.dp,
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight),
        )
    }
}
