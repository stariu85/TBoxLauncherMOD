package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxCaption
import kotlin.math.roundToInt

private const val FLASH_DURATION_MS = 5_000L

@Composable
private fun rememberAdasFlashingAlpha(key: String, activationTimeMs: Long): Float {
    var isFlashing by remember(key, activationTimeMs) {
        mutableStateOf((SystemClock.uptimeMillis() - activationTimeMs) < FLASH_DURATION_MS)
    }

    LaunchedEffect(key, activationTimeMs) {
        val elapsed = SystemClock.uptimeMillis() - activationTimeMs
        val remainingMs = FLASH_DURATION_MS - elapsed
        if (remainingMs > 0) {
            isFlashing = true
            delay(remainingMs)
            isFlashing = false
        } else {
            isFlashing = false
        }
    }

    return if (isFlashing) {
        val infiniteTransition = rememberInfiniteTransition(label = "adas_flash_$key")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "adas_alpha_$key",
        )
        animatedAlpha
    } else {
        1.0f
    }
}

/** Compact ADAS status chips aligned with launcher palette. */
@Composable
fun LauncherAdasStrip(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
) {
    val context = LocalContext.current
    val topBarRevision by LauncherAppConfigStore.topBarHeightRevisionFlow.collectAsStateWithLifecycle()
    val topBarHeightDp = remember(context, topBarRevision) {
        LauncherAppConfigStore.topBarHeightDp(context)
    }

    val scale = (topBarHeightDp / 40f).coerceIn(0.5f, 2.0f)
    val sizeMultiplier = if (isVertical) 1.25f else 1.0f
    val iconBoxSizeDp = ((28 * scale) * sizeMultiplier).roundToInt().coerceIn(14, 70)
    val innerIconSizeDp = ((18 * scale) * sizeMultiplier).roundToInt().coerceIn(9, 45)
    val speedFontSizeSp = ((11 * scale) * sizeMultiplier).roundToInt().coerceIn(7, 28)
    val chipFontSizeSp = ((10 * scale) * sizeMultiplier).roundToInt().coerceIn(7, 25)
    val alertIconSizeDp = ((12 * scale) * sizeMultiplier).roundToInt().coerceIn(8, 30)
    val paddingHDp = ((8 * scale) * sizeMultiplier).roundToInt().coerceIn(3, 20)
    val paddingVDp = ((4 * scale) * sizeMultiplier).roundToInt().coerceIn(2, 12)
    val spacingDp = ((6 * scale) * sizeMultiplier).roundToInt().coerceIn(2, 15)

    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
    val showAll = LauncherDevVehicleState.showAllIndicators

    val pasOn = (parkingRadar is MbCanBinaryState.On) || showAll
    val cruiseActive = (cruiseSpeed ?: 0u) > 0u || showAll
    val showAcc = adas.accActive || adas.accStandby || showAll
    val showAlerts = adas.fcwActive || adas.distanceWarning || adas.aebHint ||
        adas.accTakeOver || adas.adasTakeOver || adas.accOverride || adas.speedLimitWarning || showAll
    val showLanes = adas.laneAssistEngaged || showAll
    val showSla = adas.speedLimitKmh != null || showAll
    val showHma = adas.hma != LauncherAdasAssistIcon.Hidden || showAll
    val showTja = adas.tja != LauncherAdasAssistIcon.Hidden || showAll
    val showSrr = adas.srrSystem != LauncherSrrSystemState.Hidden || showAll
    if (!pasOn && !cruiseActive && !showAcc && !showAlerts &&
        !showLanes && !showSla && !showHma && !showTja && !showSrr
    ) {
        return
    }

    val activeKeys = buildSet {
        if (pasOn) add("pas")
        if (showHma) add("hma")
        if (showTja) add("tja")
        if (adas.srrSystem == LauncherSrrSystemState.Fault || showAll) add("srr_fault")
        if (showAcc || cruiseActive) add("acc")
        if (adas.speedLimitKmh != null) add("speed_limit")
        if (adas.fcwActive || adas.distanceWarning || showAll) add("fcw")
        if (adas.aebHint || showAll) add("aeb")
        if (adas.accTakeOver || adas.adasTakeOver || showAll) add("takeover")
        if (adas.laneDepartureLeft || adas.laneDepartureRight || showLanes || showAll) add("lka")
        if (adas.rearThreats.rctaLeft != LauncherRearThreatLevel.Off || showAll) add("rcta_l")
        if (adas.rearThreats.rctaRight != LauncherRearThreatLevel.Off || showAll) add("rcta_r")
        if (adas.rearThreats.dowLeft != LauncherRearThreatLevel.Off || showAll) add("dow_l")
        if (adas.rearThreats.dowRight != LauncherRearThreatLevel.Off || showAll) add("dow_r")
        if (adas.rearThreats.rcw != LauncherRearThreatLevel.Off || showAll) add("rcw")
    }

    val activationTimes = remember { mutableMapOf<String, Long>() }
    SideEffect {
        activationTimes.keys.retainAll(activeKeys)
        val now = SystemClock.uptimeMillis()
        for (key in activeKeys) {
            if (!activationTimes.containsKey(key)) {
                activationTimes[key] = now
            }
        }
    }

    val itemsContent: @Composable () -> Unit = {
        if (pasOn) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_pas_on),
                tint = LauncherColors.AccentBlue,
                iconRes = R.drawable.ic_widget_parking_radar,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "pas",
                activationTimeMs = activationTimes["pas"] ?: 0L,
            )
        }
        if (showHma) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_hma),
                tint = assistTint(adas.hma),
                iconRes = R.drawable.ic_adas_hma,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "hma",
                activationTimeMs = activationTimes["hma"] ?: 0L,
            )
        }
        if (showTja) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_tja),
                tint = assistTint(adas.tja),
                iconRes = R.drawable.ic_adas_tja,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "tja",
                activationTimeMs = activationTimes["tja"] ?: 0L,
            )
        }
        if (adas.srrSystem == LauncherSrrSystemState.Fault || showAll) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_srr_fault),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_fcw,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "srr_fault",
                activationTimeMs = activationTimes["srr_fault"] ?: 0L,
            )
        }
        if (showAcc) {
            val accTint = when {
                adas.accOverride || adas.accTakeOver -> Color(0xFFF59E0B)
                adas.accStandby -> Color(0xFFEAB308)
                adas.accMode == LauncherAdasAccMode.ActiveBlue -> LauncherColors.AccentCyan
                else -> LauncherColors.AccentBlue
            }
            val accTime = activationTimes["acc"] ?: 0L
            adas.accSetSpeedKmh?.let { speed ->
                LauncherAdasSpeedBadge(
                    speed = speed.toString(),
                    tint = accTint,
                    fontSizeSp = speedFontSizeSp,
                    paddingHDp = paddingHDp,
                    paddingVDp = paddingVDp,
                    activationKey = "acc",
                    activationTimeMs = accTime,
                )
            } ?: LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_acc_standby),
                tint = accTint,
                iconRes = R.drawable.ic_launcher_cruise,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "acc",
                activationTimeMs = accTime,
            )
        } else if (cruiseActive) {
            LauncherAdasSpeedBadge(
                speed = cruiseSpeed.toString(),
                fontSizeSp = speedFontSizeSp,
                paddingHDp = paddingHDp,
                paddingVDp = paddingVDp,
                activationKey = "acc",
                activationTimeMs = activationTimes["acc"] ?: 0L,
            )
        }
        adas.speedLimitKmh?.let { limit ->
            LauncherAdasSpeedBadge(
                speed = limit.toString(),
                tint = if (adas.speedLimitWarning) Color(0xFFEF4444) else Color(0xFFE11D48),
                fontSizeSp = speedFontSizeSp,
                paddingHDp = paddingHDp,
                paddingVDp = paddingVDp,
                activationKey = "speed_limit",
                activationTimeMs = activationTimes["speed_limit"] ?: 0L,
            )
        }
        if (adas.fcwActive || adas.distanceWarning || showAll) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_fcw),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_fcw,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "fcw",
                activationTimeMs = activationTimes["fcw"] ?: 0L,
            )
        }
        if (adas.aebHint || showAll) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_aeb),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_aeb,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "aeb",
                activationTimeMs = activationTimes["aeb"] ?: 0L,
            )
        }
        if (adas.accTakeOver || adas.adasTakeOver || showAll) {
            LauncherAdasAlertChip(
                text = stringResource(R.string.launcher_adas_takeover),
                fontSizeSp = chipFontSizeSp,
                iconSizeDp = alertIconSizeDp,
                paddingHDp = paddingHDp,
                paddingVDp = paddingVDp,
                activationKey = "takeover",
                activationTimeMs = activationTimes["takeover"] ?: 0L,
            )
        }
        val ldwOrLkaActive = adas.leftLane != LauncherAdasLaneVisualization.Hidden ||
            adas.rightLane != LauncherAdasLaneVisualization.Hidden ||
            adas.lkaStatusCode != 0
        if (ldwOrLkaActive || showLanes || showAll) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_lka),
                tint = when {
                    adas.laneDepartureLeft || adas.laneDepartureRight -> Color(0xFFF59E0B)
                    adas.lkaStatusCode != 0 -> LauncherColors.AccentBlue
                    else -> Color(0xFF22C55E)
                },
                iconRes = R.drawable.ic_adas_lka,
                iconBoxSizeDp = iconBoxSizeDp,
                innerIconSizeDp = innerIconSizeDp,
                activationKey = "lka",
                activationTimeMs = activationTimes["lka"] ?: 0L,
            )
        }
        val hasOtherRearThreats = adas.rearThreats.rctaLeft != LauncherRearThreatLevel.Off ||
            adas.rearThreats.rctaRight != LauncherRearThreatLevel.Off ||
            adas.rearThreats.dowLeft != LauncherRearThreatLevel.Off ||
            adas.rearThreats.dowRight != LauncherRearThreatLevel.Off ||
            adas.rearThreats.rcw != LauncherRearThreatLevel.Off
        if (hasOtherRearThreats || showAll) {
            LauncherRearThreatChips(
                threats = adas.rearThreats,
                activationTimes = activationTimes,
                fontSizeSp = chipFontSizeSp,
                alertIconSizeDp = alertIconSizeDp,
                paddingHDp = paddingHDp,
                paddingVDp = paddingVDp,
            )
        }
    }

    if (isVertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacingDp.dp),
            horizontalAlignment = Alignment.End,
        ) {
            itemsContent()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsContent()
        }
    }
}

@Composable
private fun LauncherRearThreatChips(
    threats: LauncherRearThreats,
    activationTimes: Map<String, Long>,
    fontSizeSp: Int = 10,
    alertIconSizeDp: Int = 12,
    paddingHDp: Int = 8,
    paddingVDp: Int = 4,
) {
    fun tint(level: LauncherRearThreatLevel): Color = when (level) {
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444)
        LauncherRearThreatLevel.Caution -> Color(0xFFF59E0B)
        LauncherRearThreatLevel.Off -> LauncherColors.TextSecondary
    }
    if (threats.rctaLeft != LauncherRearThreatLevel.Off) {
        LauncherAdasAlertChip(stringResource(R.string.launcher_adas_rcta_left), fontSizeSp, alertIconSizeDp, paddingHDp, paddingVDp, "rcta_l", activationTimes["rcta_l"] ?: 0L)
    }
    if (threats.rctaRight != LauncherRearThreatLevel.Off) {
        LauncherAdasAlertChip(stringResource(R.string.launcher_adas_rcta_right), fontSizeSp, alertIconSizeDp, paddingHDp, paddingVDp, "rcta_r", activationTimes["rcta_r"] ?: 0L)
    }
    if (threats.dowLeft != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_dow_left), tint(threats.dowLeft), fontSizeSp, paddingHDp, paddingVDp, "dow_l", activationTimes["dow_l"] ?: 0L)
    }
    if (threats.dowRight != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_dow_right), tint(threats.dowRight), fontSizeSp, paddingHDp, paddingVDp, "dow_r", activationTimes["dow_r"] ?: 0L)
    }
    if (threats.rcw != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_rcw), tint(threats.rcw), fontSizeSp, paddingHDp, paddingVDp, "rcw", activationTimes["rcw"] ?: 0L)
    }
}

private fun assistTint(icon: LauncherAdasAssistIcon): Color = when (icon) {
    LauncherAdasAssistIcon.Warning -> Color(0xFFF59E0B)
    LauncherAdasAssistIcon.Active -> LauncherColors.AccentCyan
    LauncherAdasAssistIcon.Dark -> LauncherColors.AccentBlue
    LauncherAdasAssistIcon.Hidden -> LauncherColors.TextSecondary
}

@Composable
private fun LauncherAdasIcon(
    contentDescription: String,
    tint: Color,
    iconRes: Int,
    iconBoxSizeDp: Int = 28,
    innerIconSizeDp: Int = 18,
    activationKey: String? = null,
    activationTimeMs: Long = 0L,
) {
    val alpha = if (activationKey != null) rememberAdasFlashingAlpha(activationKey, activationTimeMs) else 1.0f
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .size(iconBoxSizeDp.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(innerIconSizeDp.dp),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun LauncherAdasSpeedBadge(
    speed: String,
    tint: Color = LauncherColors.AccentCyan,
    fontSizeSp: Int = 11,
    paddingHDp: Int = 8,
    paddingVDp: Int = 4,
    activationKey: String? = null,
    activationTimeMs: Long = 0L,
) {
    val alpha = if (activationKey != null) rememberAdasFlashingAlpha(activationKey, activationTimeMs) else 1.0f
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape((paddingVDp * 2).dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = paddingHDp.dp, vertical = paddingVDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speed,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LauncherAdasChip(
    text: String,
    tint: Color,
    fontSizeSp: Int = 10,
    paddingHDp: Int = 8,
    paddingVDp: Int = 4,
    activationKey: String? = null,
    activationTimeMs: Long = 0L,
) {
    val alpha = if (activationKey != null) rememberAdasFlashingAlpha(activationKey, activationTimeMs) else 1.0f
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape((paddingVDp * 2).dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = paddingHDp.dp, vertical = paddingVDp.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Transient ACC following-distance indicator centered over the left panel car area
 * (like stock cluster): only while the user is changing it, auto-hides after ~5 s.
 */
@Composable
fun BoxScope.LauncherAdasTimeGapFlash(
    timeGapLevel: Int?,
    timeGapFlashUntilMs: Long,
    tint: Color = LauncherColors.AccentCyan,
) {
    val level = timeGapLevel ?: return
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(timeGapFlashUntilMs) {
        while (SystemClock.uptimeMillis() < timeGapFlashUntilMs) {
            delay(160)
            now = SystemClock.uptimeMillis()
        }
        now = SystemClock.uptimeMillis()
    }
    if (timeGapFlashUntilMs <= now) return
    val bars = (level + 1).coerceIn(1, 3)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.90f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .width(132.dp)
                .height(52.dp),
        ) {
            drawTimeGapSchema(bars = bars, tint = tint)
        }
    }
}

private fun DrawScope.drawTimeGapSchema(
    bars: Int,
    tint: Color,
) {
    val w = size.width
    val h = size.height
    val carW = w * 0.20f
    val carH = h * 0.42f
    fun car(cx: Float, cy: Float) {
        val path = Path().apply {
            moveTo(cx - carW * 0.38f, cy + carH * 0.42f)
            lineTo(cx - carW * 0.42f, cy - carH * 0.05f)
            lineTo(cx - carW * 0.22f, cy - carH * 0.48f)
            lineTo(cx + carW * 0.22f, cy - carH * 0.48f)
            lineTo(cx + carW * 0.42f, cy - carH * 0.05f)
            lineTo(cx + carW * 0.38f, cy + carH * 0.42f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.92f), style = Stroke(width = 2.4f))
    }
    car(w * 0.16f, h * 0.58f)
    car(w * 0.84f, h * 0.58f)
    val gapLeft = w * 0.30f
    val gapRight = w * 0.70f
    val step = (gapRight - gapLeft) / 4f
    repeat(3) { i ->
        val x = gapLeft + step * (i + 1)
        val on = i < bars
        val color = tint.copy(alpha = if (on) 0.95f else 0.22f)
        drawLine(
            color = color,
            start = Offset(x, h * 0.34f),
            end = Offset(x, h * 0.78f),
            strokeWidth = if (on) 5.5f else 3.5f,
            cap = StrokeCap.Round,
        )
    }
}

/** Visual ACC following-distance like stock ADAS (1–3 chevrons). */
@Composable
private fun LauncherAdasTimeGapChip(
    level: Int,
    tint: Color,
) {
    val bars = (level + 1).coerceIn(1, 3)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_time_gap_label),
                style = MaterialTheme.typography.tboxCaption,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            repeat(3) { index ->
                Text(
                    text = "˄",
                    color = if (index < bars) tint else tint.copy(alpha = 0.28f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LauncherAdasAlertChip(
    text: String,
    fontSizeSp: Int = 10,
    iconSizeDp: Int = 12,
    paddingHDp: Int = 8,
    paddingVDp: Int = 4,
    activationKey: String? = null,
    activationTimeMs: Long = 0L,
) {
    val alpha = if (activationKey != null) rememberAdasFlashingAlpha(activationKey, activationTimeMs) else 1.0f
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape((paddingVDp * 2).dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
            .padding(horizontal = paddingHDp.dp, vertical = paddingVDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((paddingHDp / 2).coerceAtLeast(2).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(iconSizeDp.dp),
                tint = Color(0xFFEF4444),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.tboxCaption,
                color = Color(0xFFEF4444),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
