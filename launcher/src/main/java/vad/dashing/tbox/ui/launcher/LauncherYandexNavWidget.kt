package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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

private val SPEED_LIMIT_UNIT_REGEX = Regex("(?i)\\s*(км/?ч|km/?h|км|km)\\s*")
private val DISTANCE_SPLIT_REGEX = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(.*)$")

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
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()

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
    if (!shouldShowYandexBroadcastWidget(isYandexWindowOpen = isYandexOpen, hasData = hasTrafficLightData)) return

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
        digitsOnly.ifEmpty { speedLimit.replace(SPEED_LIMIT_UNIT_REGEX, "").trim() }
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
                val primaryImage = remember(primaryBmp) { primaryBmp.asImageBitmap() }
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        bitmap = primaryImage,
                        contentDescription = null,
                        colorFilter = turnIconFilter,
                        modifier = Modifier.size(36.dp),
                    )
                    secondaryBmp?.let { sec ->
                        val secImage = remember(sec) { sec.asImageBitmap() }
                        Image(
                            bitmap = secImage,
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
    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
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
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                remDist?.let {
                    Text(
                        text = it,
                        color = primaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                remTime?.let {
                    Text(
                        text = it,
                        color = LauncherColors.AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                arrTime?.let {
                    Text(
                        text = it,
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            navState.etaProgressBitmap?.let { bar ->
                val barImage = remember(bar) { bar.asImageBitmap() }
                Image(
                    bitmap = barImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
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
 * Freely movable Overlay 7: Background Lanes block (from RemoteViews / Notification / Broadcast).
 */
@Composable
internal fun LauncherNavBgLanesOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()
    val isLiveNavigating = isYandexOpen && navState.isNavigating

    val laneDistText = if (isLiveNavigating) navState.lanesDistance else null
    val hasLanes = isLiveNavigating && (navState.lanesBitmaps.isNotEmpty() || navState.lanesBitmap != null)
    val hasData = hasLanes || !laneDistText.isNullOrBlank()

    if (!shouldShowYandexBroadcastWidget(isYandexWindowOpen = isYandexOpen, hasData = hasData)) return

    val isDark = LauncherThemeState.darkTheme
    val laneColorFilter = if (!isDark) ColorFilter.tint(Color(0xFF0F172A), BlendMode.SrcIn) else null
    val textColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)

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
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLiveNavigating && navState.lanesBitmaps.isNotEmpty()) {
                val laneImages = remember(navState.lanesBitmaps) { navState.lanesBitmaps.map { it.asImageBitmap() } }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    laneImages.forEach { laneImage ->
                        Image(
                            bitmap = laneImage,
                            contentDescription = null,
                            colorFilter = laneColorFilter,
                            modifier = Modifier.size(31.2.dp),
                        )
                    }
                }
            } else if (isLiveNavigating && navState.lanesBitmap != null) {
                val singleLaneBmp = navState.lanesBitmap!!
                val singleLaneImage = remember(singleLaneBmp) { singleLaneBmp.asImageBitmap() }
                Image(
                    bitmap = singleLaneImage,
                    contentDescription = null,
                    colorFilter = laneColorFilter,
                    modifier = Modifier.height(33.8.dp),
                )
            }
            if (!laneDistText.isNullOrBlank()) {
                val hasLanesAbove = navState.lanesBitmaps.isNotEmpty() ||
                    navState.lanesBitmap != null
                Text(
                    text = laneDistText,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = if (hasLanesAbove) Modifier.offset(y = (-3).dp) else Modifier,
                )
            }
        }
    }
}

internal fun shouldShowNavMinimap(
    isYandexWindowOpen: Boolean,
    hasBitmap: Boolean,
): Boolean = isYandexWindowOpen && hasBitmap

internal fun shouldSuppressNavMinimapForAcc(
    hideOnAccEnabled: Boolean,
    accActive: Boolean,
    accOverride: Boolean,
): Boolean = hideOnAccEnabled && (accActive || accOverride)

internal enum class LanesOrTurnTapAction {
    TOGGLE_MINIMAP,
    OPEN_YANDEX_NAV,
}

internal fun resolveLanesOrTurnTapAction(
    minimapSettingEnabled: Boolean,
): LanesOrTurnTapAction = if (minimapSettingEnabled) {
    LanesOrTurnTapAction.TOGGLE_MINIMAP
} else {
    LanesOrTurnTapAction.OPEN_YANDEX_NAV
}

internal fun isNavMinimapEffectivelyVisible(
    minimapSettingEnabled: Boolean,
    hideOnAccEnabled: Boolean,
    accActive: Boolean,
    accOverride: Boolean,
    manuallyHidden: Boolean,
    accOverrideVisible: Boolean = false,
): Boolean {
    if (!minimapSettingEnabled) return false
    val accSuppressed = shouldSuppressNavMinimapForAcc(
        hideOnAccEnabled = hideOnAccEnabled,
        accActive = accActive,
        accOverride = accOverride,
    )
    return if (accSuppressed) {
        accOverrideVisible
    } else {
        !manuallyHidden
    }
}

/**
 * Full-width Rectangular Minimap Block (from com.yandex.MINIMAP).
 * Spans full width of ADAS zone with configurable height, transparency, and edge gradients.
 */
@Composable
internal fun LauncherNavBgMinimapOverlay(
    containerWidthPx: Int = 0,
    heightPx: Int = 0,
    transparencyPercent: Int = NAV_BG_MINIMAP_TRANSPARENCY_DEFAULT,
    resolutionPercent: Int = NAV_BG_MINIMAP_RESOLUTION_DEFAULT_PERCENT,
    filterQuality: FilterQuality = FilterQuality.High,
    is3dEnabled: Boolean = false,
    isAutoZoomEnabled: Boolean = false,
    speedProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()
    if (!isYandexOpen) return

    val currentSpeedProvider by rememberUpdatedState(speedProvider)
    var appliedZoom by remember(isAutoZoomEnabled) {
        mutableFloatStateOf(
            YandexNavRepository.resolveMinimapZoomForSpeed(
                currentSpeedProvider(),
                isAutoZoomEnabled,
            ),
        )
    }
    LaunchedEffect(isAutoZoomEnabled) {
        if (!isAutoZoomEnabled) {
            appliedZoom = YandexNavRepository.resolveMinimapZoomForSpeed(0f, false)
            return@LaunchedEffect
        }
        snapshotFlow {
            val spd = currentSpeedProvider()
            val desired = YandexNavRepository.resolveMinimapZoomForSpeed(spd, true)
            val current = appliedZoom
            if (desired == current) {
                current
            } else {
                // Apply 3 km/h hysteresis around thresholds (20/50/80/110 km/h)
                val withinHysteresis = when {
                    current == 17.0f && desired == 16.5f -> spd < 23f
                    current == 16.5f && desired == 17.0f -> spd > 17f
                    current == 16.5f && desired == 16.0f -> spd < 53f
                    current == 16.0f && desired == 16.5f -> spd > 47f
                    current == 16.0f && desired == 15.2f -> spd < 83f
                    current == 15.2f && desired == 16.0f -> spd > 77f
                    current == 15.2f && desired == 14.5f -> spd < 113f
                    current == 14.5f && desired == 15.2f -> spd > 107f
                    else -> false
                }
                if (withinHysteresis) current else desired
            }
        }
            .distinctUntilChanged()
            .collectLatest { targetZoom ->
                if (targetZoom != appliedZoom) {
                    delay(1_500L)
                    appliedZoom = targetZoom
                }
            }
    }
    val viewMode = if (is3dEnabled) 1 else 0

    // Keep offscreen minimap active in Yandex Navi with 5s heartbeat while widget is active,
    // and immediately push updated resolution / zoom / 3D view mode when changed.
    LaunchedEffect(containerWidthPx, heightPx, resolutionPercent, appliedZoom, viewMode) {
        val density = context.resources.displayMetrics.density
        val rawWidth = if (containerWidthPx > 0) containerWidthPx else (180 * density).toInt()
        val rawHeight = if (heightPx > 0) heightPx else (NAV_BG_MINIMAP_HEIGHT_DEFAULT_DP * density).toInt()
        val widthPx = YandexNavRepository.scaleMinimapDimensionPx(rawWidth, resolutionPercent)
        val heightPxScaled = YandexNavRepository.scaleMinimapDimensionPx(rawHeight, resolutionPercent)
        while (isActive) {
            YandexNavRepository.enableMinimap(
                context = context,
                widthPx = widthPx,
                heightPx = heightPxScaled,
                zoom = appliedZoom,
                viewMode = viewMode,
            )
            delay(5000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            YandexNavRepository.disableMinimap(context)
        }
    }

    LauncherNavBgMinimapFrameContent(
        transparencyPercent = transparencyPercent,
        is3dEnabled = is3dEnabled,
        filterQuality = filterQuality,
        modifier = modifier,
    )
}

@Composable
private fun LauncherNavBgMinimapFrameContent(
    transparencyPercent: Int,
    is3dEnabled: Boolean,
    filterQuality: FilterQuality,
    modifier: Modifier = Modifier,
) {
    val minimapFrame by YandexNavRepository.minimapFrame.collectAsStateWithLifecycle()
    val lightBuffer = remember { MinimapLightThemeBuffer() }
    DisposableEffect(Unit) {
        onDispose {
            lightBuffer.release()
        }
    }

    val bmp = minimapFrame.bitmap ?: return
    val frameSeq = minimapFrame.frameSeq
    val isDarkTheme = LauncherThemeState.darkTheme
    var lightFrame by remember { mutableStateOf<Pair<Long, ImageBitmap>?>(null) }

    LaunchedEffect(bmp, frameSeq, isDarkTheme) {
        if (!isDarkTheme && lightFrame?.first != frameSeq) {
            val converted = withContext(LauncherCpuAffinity.minimapLightConvertDispatcher) {
                lightBuffer.convert(bmp)
            }
            if (converted != null) {
                lightFrame = frameSeq to converted
            }
        }
    }

    val displayBitmap: ImageBitmap = if (isDarkTheme) {
        remember(bmp, frameSeq) { bmp.asImageBitmap() }
    } else {
        lightFrame?.second ?: return
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = (100 - transparencyPercent.coerceIn(0, 100)) / 100f
                if (is3dEnabled) {
                    rotationX = 36f
                    scaleX = 1.12f
                    scaleY = 1.16f
                    cameraDistance = 14f * density
                }
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val horizontalFade = Brush.horizontalGradient(
                    0.00f to Color.Transparent,
                    0.08f to Color.Black,
                    0.92f to Color.Black,
                    1.00f to Color.Transparent,
                )
                val verticalFade = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.14f to Color.Black,
                    0.86f to Color.Black,
                    1.00f to Color.Transparent,
                )
                onDrawWithContent {
                    drawContent()
                    // Edge gradients fading smoothly into full transparency on all 4 sides
                    drawRect(
                        brush = horizontalFade,
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = verticalFade,
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = displayBitmap,
            contentDescription = "Minimap",
            contentScale = ContentScale.Crop,
            filterQuality = filterQuality,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun LauncherNavBgMinimapInteractionOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasMinimapBitmap by YandexNavRepository.hasMinimapBitmap.collectAsStateWithLifecycle()
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()
    if (!shouldShowNavMinimap(isYandexOpen, hasMinimapBitmap)) return

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
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") },
    )
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
                val tlImage = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = tlImage,
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

/**
 * Freely movable Overlay 11: Compact Next Turn & Distance widget from Navigator broadcast.
 * Matches the visual style and dimensions of [LauncherNavBgLanesOverlay].
 */
@Composable
internal fun LauncherNavBgNextTurnOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()
    val isLiveNavigating = isYandexOpen && navState.isNavigating

    val turnBmp = if (isLiveNavigating) {
        navState.broadcastManeuverBitmap ?: navState.maneuverBitmap ?: navState.maneuverPrimaryIcon
    } else null
    val distText = if (isLiveNavigating) {
        navState.broadcastManeuverDistance
            ?: navState.nextText
            ?: navState.maneuverDistance
    } else null

    val hasData = turnBmp != null || !distText.isNullOrBlank()
    if (!shouldShowYandexBroadcastWidget(isYandexWindowOpen = isYandexOpen, hasData = hasData)) return

    val isDark = LauncherThemeState.darkTheme
    val arrowColorFilter = if (!isDark) ColorFilter.tint(Color(0xFF0F172A), BlendMode.SrcIn) else null
    val textColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)

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
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (turnBmp != null) {
                val turnImage = remember(turnBmp) { turnBmp.asImageBitmap() }
                Image(
                    bitmap = turnImage,
                    contentDescription = null,
                    colorFilter = arrowColorFilter,
                    modifier = Modifier.size(31.2.dp),
                )
            }
            if (!distText.isNullOrBlank()) {
                val hasArrowAbove = turnBmp != null
                Text(
                    text = distText,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = if (hasArrowAbove) Modifier.offset(y = (-3).dp) else Modifier,
                )
            }
        }
    }
}

/**
 * Splits a formatted distance string (e.g. "200 м", "1.3 км") into its primary number ("200", "1.3")
 * and unit suffix ("м", "км") so the number can be rendered larger than the unit like Yandex's blue sign.
 */
internal fun splitDistanceNumberAndUnit(rawDistance: String): Pair<String, String> {
    val trimmed = rawDistance.trim()
    val match = DISTANCE_SPLIT_REGEX.find(trimmed)
    return if (match != null) {
        match.groupValues[1] to match.groupValues[2].trim()
    } else {
        trimmed to ""
    }
}

/**
 * Crops out transparent top/bottom/left/right margins from an arrow or lanes [android.graphics.Bitmap]
 * so that both lane arrows and turn maneuver arrows fill 100% of their rendered height and match
 * visually in size.
 */
internal fun trimBitmapTransparentBounds(
    source: android.graphics.Bitmap,
    alphaThreshold: Int = 20,
): android.graphics.Bitmap {
    if (source.isRecycled || source.width <= 1 || source.height <= 1) return source
    return runCatching {
        val isHardwareCopy = source.config == android.graphics.Bitmap.Config.HARDWARE
        val readable = if (isHardwareCopy) {
            source.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return source
        } else {
            source
        }
        try {
            val w = readable.width
            val h = readable.height
            val pixels = IntArray(w * h)
            readable.getPixels(pixels, 0, w, 0, 0, w, h)

            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1

            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    val alpha = pixels[rowOffset + x] ushr 24
                    if (alpha >= alphaThreshold) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (maxX < minX || maxY < minY) return source
            val cropW = (maxX - minX + 1).coerceAtLeast(1)
            val cropH = (maxY - minY + 1).coerceAtLeast(1)
            if (cropW == w && cropH == h) return source
            android.graphics.Bitmap.createBitmap(readable, minX, minY, cropW, cropH)
        } finally {
            if (isHardwareCopy && !readable.isRecycled) {
                readable.recycle()
            }
        }
    }.getOrDefault(source)
}

/**
 * Crops a list of individual lane bitmaps using a shared vertical bounding box (`[globalMinY..globalMaxY]`)
 * and per-lane horizontal bounds so all lane arrows remain aligned vertically with one another while
 * filling 100% of the rendered arrow height (matching the turn indicator arrow size).
 */
internal fun trimLaneBitmapsCommonVerticalBounds(
    bitmaps: List<android.graphics.Bitmap>,
    alphaThreshold: Int = 20,
): List<android.graphics.Bitmap> {
    if (bitmaps.isEmpty()) return bitmaps
    return runCatching {
        data class LaneScan(
            val source: android.graphics.Bitmap,
            val readable: android.graphics.Bitmap,
            val isHardwareCopy: Boolean,
            val minX: Int,
            val maxX: Int,
            val minY: Int,
            val maxY: Int,
        )

        val scans = bitmaps.map { src ->
            if (src.isRecycled || src.width <= 1 || src.height <= 1) {
                return@map LaneScan(src, src, false, 0, src.width - 1, 0, src.height - 1)
            }
            val isHardwareCopy = src.config == android.graphics.Bitmap.Config.HARDWARE
            val readable = if (isHardwareCopy) {
                src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return@map LaneScan(src, src, false, 0, src.width - 1, 0, src.height - 1)
            } else {
                src
            }
            val w = readable.width
            val h = readable.height
            val pixels = IntArray(w * h)
            readable.getPixels(pixels, 0, w, 0, 0, w, h)

            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    val alpha = pixels[rowOffset + x] ushr 24
                    if (alpha >= alphaThreshold) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX || maxY < minY) {
                LaneScan(src, readable, isHardwareCopy, 0, w - 1, 0, h - 1)
            } else {
                LaneScan(src, readable, isHardwareCopy, minX, maxX, minY, maxY)
            }
        }

        try {
            val globalMinY = scans.minOf { it.minY }.coerceAtLeast(0)
            val globalMaxY = scans.maxOf { it.maxY }

            scans.map { scan ->
                val w = scan.readable.width
                val h = scan.readable.height
                val top = globalMinY.coerceIn(0, h - 1)
                val bottom = globalMaxY.coerceIn(top, h - 1)
                val left = scan.minX.coerceIn(0, w - 1)
                val right = scan.maxX.coerceIn(left, w - 1)
                val cropW = (right - left + 1).coerceAtLeast(1)
                val cropH = (bottom - top + 1).coerceAtLeast(1)
                if (cropW == w && cropH == h) {
                    scan.source
                } else {
                    android.graphics.Bitmap.createBitmap(scan.readable, left, top, cropW, cropH)
                }
            }
        } finally {
            scans.forEach { scan ->
                if (scan.isHardwareCopy && !scan.readable.isRecycled) {
                    scan.readable.recycle()
                }
            }
        }
    }.getOrDefault(bitmaps)
}

/**
 * Freely movable Overlay 12: Combined Lanes or Next Turn Widget (2-in-1) in Yandex blue road-sign style.
 * - Base height is 105.dp (scaled by [scale] from 0.5x to 2.0x), overall transparency is 30% (alpha = 0.70f).
 * - Arrows (traffic lanes if available, otherwise next turn) are placed on top, distance text below.
 * - Both arrows and distance text are horizontally centered across the widget width.
 * - Base arrow height is 45.dp (scaled by [scale]), and transparent padding is trimmed from both lane
 *   and turn bitmaps so lane arrows and turn indicator arrows have the exact same visual size.
 */
@Composable
internal fun LauncherNavBgLanesOrTurnOverlay(
    scale: Float = 1f,
    onClick: (() -> Unit)? = null,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isYandexOpen by YandexNavWindowTracker.isWindowOpen.collectAsStateWithLifecycle()
    val isLiveNavigating = isYandexOpen && navState.isNavigating

    val hasLanes = isLiveNavigating && (navState.lanesBitmaps.isNotEmpty() || navState.lanesBitmap != null)
    val turnBmp = if (isLiveNavigating) {
        navState.broadcastManeuverBitmap ?: navState.maneuverBitmap ?: navState.maneuverPrimaryIcon
    } else null
    val distText = if (isLiveNavigating) {
        if (hasLanes) {
            navState.lanesDistance
                ?: navState.broadcastManeuverDistance
                ?: navState.nextText
                ?: navState.maneuverDistance
        } else {
            navState.broadcastManeuverDistance
                ?: navState.nextText
                ?: navState.maneuverDistance
                ?: navState.lanesDistance
        }
    } else null

    val hasTurn = turnBmp != null || !distText.isNullOrBlank()
    val hasData = isLiveNavigating && (hasLanes || hasTurn)
    if (!shouldShowYandexBroadcastWidget(isYandexWindowOpen = isYandexOpen, hasData = hasData)) return

    val s = scale.coerceIn(0.5f, 2.0f)
    val signBlue = Color(0xFF0066FF)
    val whiteIconFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)
    val arrowHeight = (45f * s).dp

    val trimmedLanesBitmaps by produceState<List<android.graphics.Bitmap>>(
        initialValue = if (hasLanes) navState.lanesBitmaps else emptyList(),
        key1 = hasLanes,
        key2 = navState.lanesBitmaps,
    ) {
        value = if (hasLanes && navState.lanesBitmaps.isNotEmpty()) {
            withContext(LauncherCpuAffinity.minimapLightConvertDispatcher) {
                trimLaneBitmapsCommonVerticalBounds(navState.lanesBitmaps)
            }
        } else {
            emptyList()
        }
    }
    val needSingleLaneBitmap = hasLanes && navState.lanesBitmaps.isEmpty() && navState.lanesBitmap != null
    val trimmedLanesBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = if (needSingleLaneBitmap) navState.lanesBitmap else null,
        key1 = needSingleLaneBitmap,
        key2 = navState.lanesBitmap,
    ) {
        val src = navState.lanesBitmap
        value = if (needSingleLaneBitmap && src != null) {
            withContext(LauncherCpuAffinity.minimapLightConvertDispatcher) {
                trimBitmapTransparentBounds(src)
            }
        } else {
            null
        }
    }
    val needTurnBitmap = !hasLanes && turnBmp != null
    val trimmedTurnBmp by produceState<android.graphics.Bitmap?>(
        initialValue = if (needTurnBitmap) turnBmp else null,
        key1 = needTurnBitmap,
        key2 = turnBmp,
    ) {
        value = if (needTurnBitmap && turnBmp != null) {
            withContext(LauncherCpuAffinity.minimapLightConvertDispatcher) {
                trimBitmapTransparentBounds(turnBmp)
            }
        } else {
            null
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .height((105f * s).dp)
            .defaultMinSize(minWidth = (108f * s).dp)
            .graphicsLayer {
                alpha = 0.70f
                compositingStrategy = CompositingStrategy.Offscreen
            }
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
            .clickable {
                val clickHandler = currentOnClick
                if (clickHandler != null) {
                    clickHandler()
                } else {
                    launchLauncherApp(context, "ru.yandex.yandexnavi")
                }
            }
            .clip(RoundedCornerShape((16f * s).dp))
            .background(signBlue)
            .padding(horizontal = (16f * s).dp, vertical = (8f * s).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((6f * s).dp, Alignment.CenterVertically),
        ) {
            if (hasLanes) {
                if (trimmedLanesBitmaps.isNotEmpty()) {
                    val trimmedLaneImages = remember(trimmedLanesBitmaps) {
                        trimmedLanesBitmaps.map { bmp ->
                            val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                                .coerceIn(0.2f, 8f)
                            bmp.asImageBitmap() to aspect
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((8f * s).dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        trimmedLaneImages.forEach { (laneImg, aspect) ->
                            Image(
                                bitmap = laneImg,
                                contentDescription = null,
                                colorFilter = whiteIconFilter,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .height(arrowHeight)
                                    .aspectRatio(aspect),
                            )
                        }
                    }
                } else {
                    trimmedLanesBitmap?.let { laneBmp ->
                        val laneImg = remember(laneBmp) { laneBmp.asImageBitmap() }
                        val aspect = (
                            laneBmp.width.toFloat() /
                                laneBmp.height.coerceAtLeast(1).toFloat()
                            ).coerceIn(0.2f, 16f)
                        Image(
                            bitmap = laneImg,
                            contentDescription = null,
                            colorFilter = whiteIconFilter,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .height(arrowHeight)
                                .aspectRatio(aspect),
                        )
                    }
                }
            } else {
                trimmedTurnBmp?.let { tBmp ->
                    val turnImg = remember(tBmp) { tBmp.asImageBitmap() }
                    val aspect = (
                        tBmp.width.toFloat() /
                            tBmp.height.coerceAtLeast(1).toFloat()
                        ).coerceIn(0.2f, 8f)
                    Image(
                        bitmap = turnImg,
                        contentDescription = null,
                        colorFilter = whiteIconFilter,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .height(arrowHeight)
                            .aspectRatio(aspect),
                    )
                }
            }

            if (!distText.isNullOrBlank()) {
                val (numPart, unitPart) = remember(distText) {
                    splitDistanceNumberAndUnit(distText)
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy((3f * s).dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = numPart,
                        color = Color.White,
                        fontSize = (26f * s).sp,
                        lineHeight = (26f * s).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                    if (unitPart.isNotEmpty()) {
                        Text(
                            text = unitPart,
                            color = Color.White,
                            fontSize = (18f * s).sp,
                            lineHeight = (18f * s).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            modifier = Modifier.padding(bottom = (2f * s).dp),
                        )
                    }
                }
            }
        }
    }
}

