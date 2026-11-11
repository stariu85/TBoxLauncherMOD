package vad.dashing.tbox.ui.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.R
import kotlin.math.roundToInt

/**
 * Freely movable Overlay 3: Current Speed & Speed Limit Sign (with Camera TSR fallback).
 */
@Composable
internal fun LauncherNavSpeedOverlay(
    adas: LauncherAdasState,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val carSpeedState by CanDataRepository.carSpeed.collectAsStateWithLifecycle()
    val currentSpeedKmh = (carSpeedState ?: 0f).roundToInt().coerceAtLeast(0)

    val yandexLimit = navState.speedLimit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }

    val classicSpeedLimitRevision by LauncherAppConfigStore.classicSpeedLimitRevisionFlow.collectAsStateWithLifecycle()
    val classicSpeedLimitVisible = remember(context, classicSpeedLimitRevision) {
        LauncherAppConfigStore.classicSpeedLimitVisible(context)
    }
    val tsrSystemRevision by LauncherAppConfigStore.tsrSystemRevisionFlow.collectAsStateWithLifecycle()
    val tsrSystemEnabled = remember(context, tsrSystemRevision) {
        LauncherAppConfigStore.tsrSystemEnabled(context)
    }

    val rawCameraLimit = (adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid })?.toString()
    val cameraLimit = if (tsrSystemEnabled && !classicSpeedLimitVisible && yandexLimit == null) rawCameraLimit else null
    val effectiveSpeedLimit = yandexLimit ?: cameraLimit

    val offsetRevision by LauncherAppConfigStore.adasAccSpeedOffsetRevisionFlow.collectAsStateWithLifecycle()
    val speedOffset = remember(context, offsetRevision) {
        LauncherAppConfigStore.adasAccSpeedOffset(context)
    }

    val numericLimit = effectiveSpeedLimit?.filter { it.isDigit() }?.toIntOrNull()
    val isExceeded = numericLimit != null && currentSpeedKmh > (numericLimit + speedOffset)

    var isBlinkingRed by remember { mutableStateOf(false) }

    LaunchedEffect(isExceeded) {
        if (isExceeded) {
            while (isActive) {
                repeat(5) {
                    isBlinkingRed = true
                    delay(200L)
                    isBlinkingRed = false
                    delay(200L)
                }
                delay(1000L)
            }
        } else {
            isBlinkingRed = false
        }
    }

    val isDark = LauncherThemeState.darkTheme
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                )
            }
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (effectiveSpeedLimit != null) Arrangement.spacedBy((-6).dp) else Arrangement.Center,
        ) {
            CurrentSpeedBadge(speedKmh = currentSpeedKmh, isDark = isDark)
            effectiveSpeedLimit?.let { limit ->
                SpeedLimitBadge(speedLimit = limit, isDark = isDark, isBlinkingRed = isBlinkingRed)
            }
        }
    }
}

private const val TRAFFIC_LIGHT_STALE_TIMEOUT_MS = 180_000L // 180s backup timeout

/**
 * Freely movable Overlay 4: Next Traffic Light Signal & Distance.
 * Scalable via navTrafficLightScale setting (50% - 150%).
 * Top circle = Green signal, Middle circle = Timer & Yellow, Bottom circle = Red signal.
 */
@Composable
internal fun LauncherNavTrafficLightOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(navState.trafficLightLastUpdateMs) {
        if (navState.trafficLightLastUpdateMs > 0L) {
            val elapsed = System.currentTimeMillis() - navState.trafficLightLastUpdateMs
            val remaining = TRAFFIC_LIGHT_STALE_TIMEOUT_MS - elapsed
            if (remaining > 0) {
                delay(remaining + 100L)
            }
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val isTrafficLightStale = navState.trafficLightLastUpdateMs == 0L ||
            (currentTimeMs - navState.trafficLightLastUpdateMs) > TRAFFIC_LIGHT_STALE_TIMEOUT_MS
    val hasTrafficLightData = !isTrafficLightStale &&
            (!navState.trafficLightColor.isNullOrBlank() ||
             navState.trafficLightCountdownSeconds != null ||
             (navState.trafficLightDistanceMeters != null && navState.trafficLightDistanceMeters!! > 0))
    if (!hasTrafficLightData) return

    val isDark = LauncherThemeState.darkTheme
    val primaryTextColor = if (isDark) Color.White else Color(0xFF1E293B)

    val scaleRevision by LauncherAppConfigStore.navTrafficLightScaleRevisionFlow.collectAsStateWithLifecycle()
    val scale = remember(context, scaleRevision) {
        LauncherAppConfigStore.navTrafficLightScale(context)
    }

    val currentTrafficOnDrag by rememberUpdatedState(onDrag)
    val currentTrafficOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentTrafficOnDrag(dragAmount)
                    },
                    onDragEnd = { currentTrafficOnDragEnd() },
                    onDragCancel = { currentTrafficOnDragEnd() },
                )
            }
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
        ) {
            TrafficLightHousing(
                colorStr = navState.trafficLightColor,
                countdownSeconds = navState.trafficLightCountdownSeconds,
                arrowStr = navState.trafficLightArrow,
                scale = scale,
                isDark = isDark,
            )

            navState.trafficLightDistanceMeters?.let { dist ->
                val distBg = if (isDark) Color(0xCC111827) else Color(0xFFE2E8F0)
                val distBorder = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape((6 * scale).dp))
                        .background(distBg)
                        .border(1.dp, distBorder, RoundedCornerShape((6 * scale).dp))
                        .padding(horizontal = (6 * scale).dp, vertical = (3 * scale).dp),
                ) {
                    Text(
                        text = "$dist м",
                        color = primaryTextColor,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficLightHousing(
    colorStr: String?,
    countdownSeconds: Int?,
    arrowStr: String?,
    scale: Float = 1.0f,
    isDark: Boolean = LauncherThemeState.darkTheme,
) {
    val lampSize = (22 * scale).dp
    val paddingHorizontal = (5 * scale).dp
    val paddingVertical = (4 * scale).dp
    val spacing = (3 * scale).dp
    val cornerRadius = (8 * scale).dp

    val colorLower = colorStr?.lowercase()
    val isGreen = colorLower == "green"
    val isYellow = colorLower == "orange" || colorLower == "yellow"
    val isRed = colorLower == "red" || colorLower == "dark_red"

    val inactiveLampColor = if (isDark) Color(0xFF374151) else Color(0xFFCBD5E1)
    val greenColor = if (isGreen) Color(0xFF22C55E) else inactiveLampColor
    val yellowColor = if (isYellow) Color(0xFFF59E0B) else inactiveLampColor
    val redColor = if (isRed) Color(0xFFEF4444) else inactiveLampColor

    val housingBg = if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0)
    val housingBorder = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF94A3B8).copy(alpha = 0.4f)

    val formattedArrow = when (arrowStr?.lowercase()?.trim()) {
        "left" -> "←"
        "right" -> "→"
        "straight", "up" -> "↑"
        "u_turn" -> "↰"
        else -> if (!arrowStr.isNullOrBlank() && arrowStr.length <= 3 && !arrowStr.all { it.isDigit() }) arrowStr else ""
    }

    val timerText = countdownSeconds?.toString() ?: ""

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(housingBg)
            .border(1.dp, housingBorder, RoundedCornerShape(cornerRadius))
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEFT CIRCLE: GREEN SIGNAL (Show arrow if green active)
        Box(
            modifier = Modifier
                .size(lampSize)
                .clip(CircleShape)
                .background(greenColor),
            contentAlignment = Alignment.Center,
        ) {
            val greenArrow = if (isGreen) formattedArrow else ""
            if (greenArrow.isNotEmpty()) {
                @Suppress("DEPRECATION")
                Text(
                    text = greenArrow,
                    color = Color.White,
                    fontSize = (11 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-1.5 * scale).dp),
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

        // MIDDLE CIRCLE: TIMER & YELLOW SIGNAL (ONLY TIMER)
        Box(
            modifier = Modifier
                .size(lampSize)
                .clip(CircleShape)
                .background(yellowColor),
            contentAlignment = Alignment.Center,
        ) {
            if (timerText.isNotEmpty()) {
                @Suppress("DEPRECATION")
                Text(
                    text = timerText,
                    color = Color.White,
                    fontSize = (10 * scale).sp,
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
        }

        // RIGHT CIRCLE: RED SIGNAL (Show arrow if red active)
        Box(
            modifier = Modifier
                .size(lampSize)
                .clip(CircleShape)
                .background(redColor),
            contentAlignment = Alignment.Center,
        ) {
            val redArrow = if (isRed) formattedArrow else ""
            if (redArrow.isNotEmpty()) {
                @Suppress("DEPRECATION")
                Text(
                    text = redArrow,
                    color = Color.White,
                    fontSize = (11 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-1.5 * scale).dp),
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
    }
}


@Composable
private fun CurrentSpeedBadge(
    speedKmh: Int,
    isDark: Boolean = LauncherThemeState.darkTheme,
) {
    val text = speedKmh.toString()
    val bgColor = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val fontSize = if (text.length >= 3) 16.sp else 22.sp

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
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
}

@Composable
private fun SpeedLimitBadge(
    speedLimit: String,
    isDark: Boolean = LauncherThemeState.darkTheme,
    isBlinkingRed: Boolean = false,
) {
    val cleanText = remember(speedLimit) {
        val digitsOnly = speedLimit.filter { it.isDigit() }
        digitsOnly.ifEmpty { speedLimit.replace(Regex("(?i)\\s*(км/?ч|km/?h|км|km)\\s*"), "").trim() }
    }

    val bgColor = when {
        isBlinkingRed -> Color(0xFFEF4444)
        isDark -> Color(0xFF1E293B)
        else -> Color.White
    }
    val textColor = when {
        isBlinkingRed -> Color.White
        isDark -> Color.White
        else -> Color.Black
    }
    val borderColor = if (isBlinkingRed) Color.White else Color(0xFFEF4444)
    val fontSize = if (cleanText.length >= 3) 16.sp else 22.sp

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(6.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = cleanText,
            color = textColor,
            fontSize = fontSize,
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
}
