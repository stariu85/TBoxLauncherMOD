package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel

/**
 * ADAS sub-panel (accessible by swiping left from Speed Presets) containing:
 * 1. "DIST" button: cycles distance/time gap (1->2->3) with 3 stacked V-chevrons icon.
 * 2. "Умный ACC" button: sets ACC cruise speed to (speed limit + offset). Automatically adapts on speed limit change.
 *    Hidden if "ACC speed by speed limit" setting is disabled.
 */
@Composable
internal fun LauncherSmartAccAndDistControl(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val enabledRevision by LauncherAppConfigStore.adasAccBySpeedLimitRevisionFlow.collectAsStateWithLifecycle()
    val offsetRevision by LauncherAppConfigStore.adasAccSpeedOffsetRevisionFlow.collectAsStateWithLifecycle()
    val adasAccBySpeedLimitEnabled = remember(context, enabledRevision) {
        LauncherAppConfigStore.adasAccBySpeedLimitEnabled(context)
    }
    val offset = remember(context, offsetRevision) {
        LauncherAppConfigStore.adasAccSpeedOffset(context)
    }

    val rawGap = adas.timeGapLevel ?: LauncherDevVehicleState.adasTimeGapLevel
    val displayChevronLevel = when (rawGap) {
        0 -> 1 // 0 -> Level 1 (Min distance / 1 chevron)
        1 -> 2 // 1 -> Level 2 (Mid distance / 2 chevrons)
        2 -> 3 // 2 -> Level 3 (Max distance / 3 chevrons)
        else -> (rawGap + 1).coerceIn(1, 3)
    }

    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val yandexLimit = navState.speedLimit?.filter { it.isDigit() }?.toIntOrNull()
    val cameraLimit = adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid }
    val effectiveSpeedLimit = yandexLimit ?: cameraLimit

    val isSmartActive by LauncherCruisePresetController.isSmartSpeedLimitActive.collectAsStateWithLifecycle()
    val cruiseState = LauncherCruisePresetController.currentAccCruiseState()

    // Auto-adapt ACC speed on speed limit change when Smart ACC is active
    var lastAppliedLimit by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(effectiveSpeedLimit, offset, isSmartActive, cruiseState) {
        if (adasAccBySpeedLimitEnabled && cruiseState == AccCruiseState.Active && isSmartActive && effectiveSpeedLimit != null) {
            if (effectiveSpeedLimit != lastAppliedLimit) {
                lastAppliedLimit = effectiveSpeedLimit
                val targetSpeed = effectiveSpeedLimit + offset
                LauncherCruisePresetController.applySpeedLimitPreset(context, targetSpeed)
            }
        } else if (!isSmartActive || cruiseState != AccCruiseState.Active) {
            lastAppliedLimit = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // --- BUTTON 1: DIST (Time Gap / Distance) ---
            SmartPanelButton(
                label = "DIST",
                icon = {
                    ThreeChevronsIcon(
                        level = displayChevronLevel,
                        activeColor = LauncherColors.AccentCyan,
                    )
                },
                onClick = {
                    val nextRaw = when (rawGap) {
                        0 -> 1
                        1 -> 2
                        2 -> 0
                        else -> 0
                    }
                    LauncherCruisePresetController.setTimeGap(context, nextRaw)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            )

            // --- BUTTON 2: SMART ACC (Only if enabled in settings) ---
            if (adasAccBySpeedLimitEnabled) {
                val hasLimit = effectiveSpeedLimit != null
                val offsetStr = if (offset >= 0) "+$offset" else "$offset"
                val buttonText = "Limit $offsetStr"
                val isActive = isSmartActive && cruiseState == AccCruiseState.Active

                SmartPanelButton(
                    label = buttonText,
                    enabled = hasLimit,
                    isActive = isActive,
                    icon = {
                        if (hasLimit) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, Color(0xFFEF4444), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                @Suppress("DEPRECATION")
                                Text(
                                    text = effectiveSpeedLimit.toString(),
                                    color = Color.Black,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x20FFFFFF))
                                    .border(1.5.dp, Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                @Suppress("DEPRECATION")
                                Text(
                                    text = "--",
                                    color = Color.Gray,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                )
                            }
                        }
                    },
                    onClick = {
                        if (effectiveSpeedLimit != null) {
                            val targetSpeed = effectiveSpeedLimit + offset
                            LauncherCruisePresetController.applySpeedLimitPreset(context, targetSpeed)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun SmartPanelButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
) {
    val activeGreen = Color(0xFF10B981)
    val bg = when {
        isActive -> LauncherColors.LeftPanelCard
        enabled -> LauncherColors.LeftPanelCard
        else -> LauncherColors.LeftPanelCard.copy(alpha = 0.5f)
    }
    val border = if (isActive) activeGreen else Color.Transparent
    val textTint = when {
        isActive -> activeGreen
        enabled -> LauncherColors.LeftTextPrimary
        else -> Color.Gray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(
                if (isActive) {
                    Modifier.border(1.5.dp, border, RoundedCornerShape(24.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = textTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * 3 stacked V-chevrons icon indicating distance/time gap level (1, 2, or 3).
 * Level 1 = bottom chevron active
 * Level 2 = bottom 2 chevrons active
 * Level 3 = all 3 chevrons active
 */
@Composable
private fun ThreeChevronsIcon(
    level: Int,
    activeColor: Color = LauncherColors.AccentCyan,
    inactiveColor: Color = Color(0x35FFFFFF),
    modifier: Modifier = Modifier,
) {
    val activeLevel = level.coerceIn(1, 3)
    Canvas(modifier = modifier.size(20.dp, 22.dp)) {
        val w = size.width
        val h = size.height
        val stepY = h / 3.2f

        for (i in 1..3) {
            // i = 1 is bottom, i = 2 is middle, i = 3 is top
            val isLit = i <= activeLevel
            val c = if (isLit) activeColor else inactiveColor
            val cy = h - (i - 0.45f) * stepY

            val path = Path().apply {
                moveTo(1.5f, cy + 2.5f)
                lineTo(w / 2f, cy - 2.5f)
                lineTo(w - 1.5f, cy + 2.5f)
            }
            drawPath(
                path = path,
                color = c,
                style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
