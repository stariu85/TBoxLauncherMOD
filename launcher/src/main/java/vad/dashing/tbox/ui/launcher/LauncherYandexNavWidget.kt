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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()

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
    val effectiveSpeedLimit = resolveEffectiveNavSpeedLimit(
        isYandexWindowOpen = isYandexOpen,
        yandexLimit = yandexLimit,
        cameraLimit = cameraLimit,
    )

    val offsetRevision by LauncherAppConfigStore.adasAccSpeedOffsetRevisionFlow.collectAsStateWithLifecycle()
    val speedOffset = remember(context, offsetRevision) {
        LauncherAppConfigStore.adasAccSpeedOffset(context)
    }

    val numericLimit = effectiveSpeedLimit?.filter { it.isDigit() }?.toIntOrNull()
    val isExceeded = numericLimit != null && currentSpeedKmh > (numericLimit + speedOffset)

    val warningSoundRevision by LauncherAppConfigStore.speedLimitWarningSoundRevisionFlow.collectAsStateWithLifecycle()
    val warningSoundEnabled = remember(context, warningSoundRevision) {
        LauncherAppConfigStore.speedLimitWarningSoundEnabled(context)
    }

    LaunchedEffect(Unit) {
        SpeedWarningAudioPlayer.init(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            SpeedWarningAudioPlayer.stop()
        }
    }

    var isBlinkingRed by remember { mutableStateOf(false) }

    LaunchedEffect(isExceeded, warningSoundEnabled) {
        if (isExceeded) {
            launch {
                while (isActive) {
                    repeat(5) {
                        isBlinkingRed = true
                        delay(200L)
                        isBlinkingRed = false
                        delay(200L)
                    }
                    delay(1000L)
                }
            }
            if (warningSoundEnabled) {
                launch {
                    while (isActive) {
                        SpeedWarningAudioPlayer.play(context)
                        delay(4000L)
                        delay(8000L)
                    }
                }
            }
        } else {
            isBlinkingRed = false
            SpeedWarningAudioPlayer.stop()
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
private const val TRAFFIC_LIGHT_TIMER_STALE_TIMEOUT_MS = 3_000L // 3s backup timeout for timer

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

    LaunchedEffect(navState.trafficLightCountdownLastUpdateMs) {
        if (navState.trafficLightCountdownLastUpdateMs > 0L) {
            val elapsed = System.currentTimeMillis() - navState.trafficLightCountdownLastUpdateMs
            val remaining = TRAFFIC_LIGHT_TIMER_STALE_TIMEOUT_MS - elapsed
            if (remaining > 0) {
                delay(remaining + 50L)
            }
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val isTrafficLightStale = navState.trafficLightLastUpdateMs == 0L ||
            (currentTimeMs - navState.trafficLightLastUpdateMs) > TRAFFIC_LIGHT_STALE_TIMEOUT_MS

    val effectiveCountdownSeconds = resolveEffectiveTrafficLightTimer(
        countdownSeconds = navState.trafficLightCountdownSeconds,
        lastUpdateMs = navState.trafficLightCountdownLastUpdateMs,
        currentTimeMs = currentTimeMs,
        timeoutMs = TRAFFIC_LIGHT_TIMER_STALE_TIMEOUT_MS,
    )

    val hasTrafficLightData = !isTrafficLightStale &&
            (!navState.trafficLightColor.isNullOrBlank() ||
             effectiveCountdownSeconds != null ||
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
                countdownSeconds = effectiveCountdownSeconds,
                arrowStr = navState.trafficLightArrow,
                scale = scale,
                isDark = isDark,
            )

            navState.trafficLightDistanceMeters?.let { dist ->
                val distBg = (if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0)).copy(alpha = 0.7f)
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

internal fun resolveEffectiveNavSpeedLimit(
    isYandexWindowOpen: Boolean,
    yandexLimit: String?,
    cameraLimit: String?,
): String? {
    if (!isYandexWindowOpen) return null
    return yandexLimit ?: cameraLimit
}

internal fun resolveEffectiveTrafficLightTimer(
    countdownSeconds: Int?,
    lastUpdateMs: Long,
    currentTimeMs: Long,
    timeoutMs: Long = TRAFFIC_LIGHT_TIMER_STALE_TIMEOUT_MS,
): Int? {
    if (countdownSeconds == null || lastUpdateMs <= 0L) return null
    if (currentTimeMs >= lastUpdateMs && (currentTimeMs - lastUpdateMs) > timeoutMs) return null
    return countdownSeconds.takeIf { it >= 0 }
}

internal fun resolveTrafficLightTimerFontSizeSp(
    timerText: String,
    scale: Float = 1.0f,
): Float {
    return if (timerText.length >= 3) 7f * scale else 10f * scale
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

    val housingBg = (if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0)).copy(alpha = 0.7f)
    val housingBorder = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF94A3B8).copy(alpha = 0.4f)

    val formattedArrow = when (arrowStr?.lowercase()?.trim()) {
        "left" -> "←"
        "right" -> "→"
        "straight", "up" -> "↑"
        "u_turn" -> "↰"
        else -> if (!arrowStr.isNullOrBlank() && arrowStr.length <= 3 && !arrowStr.all { it.isDigit() }) arrowStr else ""
    }

    val timerText = countdownSeconds?.takeIf { it >= 0 }?.toString() ?: ""

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
                val timerFontSize = resolveTrafficLightTimerFontSizeSp(timerText, scale).sp
                @Suppress("DEPRECATION")
                Text(
                    text = timerText,
                    color = Color.White,
                    fontSize = timerFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
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
    val bgColor = (if (isDark) Color(0xFF1E293B) else Color.White).copy(alpha = 0.7f)
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
        isBlinkingRed -> Color(0xFFEF4444).copy(alpha = 0.7f)
        isDark -> Color(0xFF1E293B).copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.7f)
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

/**
 * Freely movable Overlay 5: Background Turn Maneuver block (from RemoteViews / Notification).
 */
@Composable
internal fun LauncherNavBgManeuverOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isSimulate = LauncherDevVehicleState.simulateEnabled

    val primaryBmp = navState.maneuverPrimaryIcon ?: navState.maneuverBitmap
    val secondaryBmp = navState.maneuverSecondaryIcon
    val distance = navState.maneuverDistance ?: if (isSimulate && !navState.isNavigating) "400 м" else null
    val street = navState.nextStreet ?: if (isSimulate && !navState.isNavigating) "Ленинградский просп." else null
    val topDesc = navState.maneuverTopDescription ?: if (isSimulate && !navState.isNavigating) "Съезд 2" else null

    val isVisible = (navState.isNavigating && (distance != null || primaryBmp != null || street != null)) ||
            (isSimulate && distance != null)

    if (!isVisible) return

    val isDark = LauncherThemeState.darkTheme
    val bgCard = if (isDark) Color(0xDD111827) else Color(0xEEFFFFFF)
    val borderCard = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

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
            .clip(RoundedCornerShape(12.dp))
            .background(bgCard)
            .border(1.dp, borderCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val turnIconFilter = if (isDark) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null

            if (primaryBmp != null) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        bitmap = primaryBmp.asImageBitmap(),
                        contentDescription = null,
                        colorFilter = turnIconFilter,
                        modifier = Modifier.size(36.dp),
                    )
                    secondaryBmp?.let { sec ->
                        Image(
                            bitmap = sec.asImageBitmap(),
                            contentDescription = null,
                            colorFilter = turnIconFilter,
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = 2.dp, y = 2.dp),
                        )
                    }
                }
            } else {
                val fallbackTurnBg = if (isDark) Color.White.copy(alpha = 0.15f) else LauncherColors.AccentCyan.copy(alpha = 0.2f)
                val fallbackTurnColor = if (isDark) Color.White else LauncherColors.AccentCyan
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(fallbackTurnBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↱", color = fallbackTurnColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                topDesc?.let {
                    Text(
                        text = it,
                        color = LauncherColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                distance?.let {
                    Text(
                        text = it,
                        color = primaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                street?.let {
                    Text(
                        text = it,
                        color = secondaryTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Freely movable Overlay 6: Background ETA & Distance block (from RemoteViews / Notification).
 */
@Composable
internal fun LauncherNavBgEtaOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isSimulate = LauncherDevVehicleState.simulateEnabled

    val remDist = navState.remainingDistance ?: if (isSimulate && !navState.isNavigating) "8.4 км" else null
    val remTime = navState.remainingTime ?: if (isSimulate && !navState.isNavigating) "18 мин" else null
    val arrTime = navState.arrivalTime ?: if (isSimulate && !navState.isNavigating) "18:45" else null

    val isVisible = (navState.isNavigating && (remDist != null || remTime != null || arrTime != null)) ||
            (isSimulate && remDist != null)

    if (!isVisible) return

    val isDark = LauncherThemeState.darkTheme
    val bgCard = if (isDark) Color(0xDD111827) else Color(0xEEFFFFFF)
    val borderCard = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

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
            .clip(RoundedCornerShape(12.dp))
            .background(bgCard)
            .border(1.dp, borderCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                remDist?.let {
                    Text(
                        text = it,
                        color = primaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (remDist != null && remTime != null) {
                    Text("·", color = LauncherColors.TextMuted, fontSize = 13.sp)
                }
                remTime?.let {
                    Text(
                        text = it,
                        color = LauncherColors.AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (arrTime != null) {
                    Text("·", color = LauncherColors.TextMuted, fontSize = 13.sp)
                    Text(
                        text = arrTime,
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            navState.etaProgressBitmap?.let { bar ->
                Image(
                    bitmap = bar.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Freely movable Overlay 7: Background Lanes block (from RemoteViews / Notification).
 */
@Composable
internal fun LauncherNavBgLanesOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isSimulate = LauncherDevVehicleState.simulateEnabled

    val hasLanes = navState.lanesBitmaps.isNotEmpty() || navState.lanesBitmap != null
    val isVisible = (navState.isNavigating && hasLanes) || isSimulate

    if (!isVisible) return

    val isDark = LauncherThemeState.darkTheme
    val bgCard = if (isDark) Color(0xDD111827) else Color(0xEEFFFFFF)
    val borderCard = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
    val laneColorFilter = if (!isDark) ColorFilter.tint(Color(0xFF0F172A), BlendMode.SrcIn) else null

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
            .clip(RoundedCornerShape(8.dp))
            .background(bgCard)
            .border(1.dp, borderCard, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (navState.lanesBitmaps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                navState.lanesBitmaps.forEach { lane ->
                    Image(
                        bitmap = lane.asImageBitmap(),
                        contentDescription = null,
                        colorFilter = laneColorFilter,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else if (navState.lanesBitmap != null) {
            Image(
                bitmap = navState.lanesBitmap!!.asImageBitmap(),
                contentDescription = null,
                colorFilter = laneColorFilter,
                modifier = Modifier.height(26.dp),
            )
        } else {
            // Simulation fallback sample lanes
            val activeLaneColor = if (isDark) Color.White else Color(0xFF0F172A)
            val inactiveLaneColor = activeLaneColor.copy(alpha = 0.35f)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↑", color = inactiveLaneColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("↑", color = activeLaneColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("↗", color = inactiveLaneColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Freely movable Overlay 8: Background Route Traffic Light (from RemoteViews / Notification).
 */
@Composable
internal fun LauncherNavBgTrafficLightOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isSimulate = LauncherDevVehicleState.simulateEnabled

    val tlText = navState.notificationTrafficLightText ?: if (isSimulate && !navState.isNavigating) "15" else null
    val tlBmp = navState.notificationTrafficLightBitmap

    val isVisible = (navState.isNavigating && (tlText != null || tlBmp != null)) ||
            (isSimulate && tlText != null)

    if (!isVisible) return

    val isDark = LauncherThemeState.darkTheme
    val bgCard = (if (isDark) Color(0xFF111827) else Color.White).copy(alpha = 0.7f)
    val borderCard = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)

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
            .clip(RoundedCornerShape(10.dp))
            .background(bgCard)
            .border(1.dp, borderCard, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tlBmp?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            } ?: Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E)),
            )

            tlText?.let { timer ->
                Text(
                    text = timer,
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

