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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * Combined Yandex Navigator ADAS widget (single panel height 60.dp).
 */
@Composable
internal fun LauncherYandexNavWidget(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val adasState by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val carSpeedState by CanDataRepository.carSpeed.collectAsStateWithLifecycle()
    val currentSpeedKmh = (carSpeedState ?: 0f).roundToInt().coerceAtLeast(0)

    val isDark = LauncherThemeState.darkTheme
    val darkGraphite = Color(0xFF1E293B)
    val primaryTextColor = if (isDark) Color.White else darkGraphite
    val secondaryTextColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF475569)

    val now = System.currentTimeMillis()
    val isNavStale = (now - navState.lastUpdateMs) > 10000L

    val yandexLimit = navState.speedLimit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    val cameraLimit = (adasState.speedLimitKmh ?: adasState.tsr.speedLimitKmh.takeIf { adasState.tsr.valid })?.toString()
    val effectiveSpeedLimit = if (!isNavStale && yandexLimit != null) yandexLimit else cameraLimit

    val streetName = (navState.nextStreet ?: navState.nextText ?: "").trim()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.LeftPanelCard)
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (streetName.isNotEmpty()) {
                Text(
                    text = streetName,
                    color = primaryTextColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ManeuverIcon(
                        bitmap = navState.maneuverBitmap,
                        isDark = isDark,
                        darkGraphite = darkGraphite,
                    )
                    val distText = navState.maneuverDistance ?: ""
                    if (distText.isNotEmpty()) {
                        Text(
                            text = distText,
                            color = primaryTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CurrentSpeedBadge(speedKmh = currentSpeedKmh)
                    effectiveSpeedLimit?.let { limit ->
                        SpeedLimitBadge(speedLimit = limit)
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val finishDist = navState.remainingDistance
                    val arrival = navState.arrivalTime
                    val time = navState.remainingTime

                    val timeSub = when {
                        arrival != null && time != null -> "$time ($arrival)"
                        time != null -> time
                        arrival != null -> arrival
                        else -> null
                    }

                    if (finishDist != null) {
                        Text(
                            text = finishDist,
                            color = primaryTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    if (timeSub != null) {
                        Text(
                            text = timeSub,
                            color = if (finishDist != null) secondaryTextColor else primaryTextColor,
                            fontSize = 9.sp,
                            fontWeight = if (finishDist != null) FontWeight.Medium else FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Freely movable Overlay 1: Turn Maneuver & Distance.
 */
@Composable
internal fun LauncherNavManeuverOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isDark = LauncherThemeState.darkTheme
    val darkGraphite = Color(0xFF1E293B)
    val primaryTextColor = if (isDark) Color.White else darkGraphite
    val cardBg = if (isDark) Color(0xDD1E293B) else Color(0xDDF8FAFC)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ManeuverIcon(
                bitmap = navState.maneuverBitmap,
                isDark = isDark,
                darkGraphite = darkGraphite,
            )

            val distText = navState.maneuverDistance.takeIf { !it.isNullOrBlank() && it != "null" }
                ?: navState.remainingDistance.takeIf { !it.isNullOrBlank() && it != "null" }
                ?: navState.remainingTime.takeIf { !it.isNullOrBlank() && it != "null" }
                ?: if (navState.isNavigating) "В пути" else "—"
            Text(
                text = distText,
                color = primaryTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Freely movable Overlay 2: Target Street Name.
 */
@Composable
internal fun LauncherNavStreetOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isDark = LauncherThemeState.darkTheme
    val darkGraphite = Color(0xFF1E293B)
    val primaryTextColor = if (isDark) Color.White else darkGraphite
    val cardBg = if (isDark) Color(0xDD1E293B) else Color(0xDDF8FAFC)

    val streetRaw = (navState.nextStreet ?: navState.nextText ?: "").trim()
    val isIgnored = streetRaw.lowercase().let { lower ->
        lower.isBlank() || lower == "null" || lower.startsWith("navigator is running") ||
                lower.startsWith("яндекс.навигатор") || lower.startsWith("яндекс навигатор") ||
                lower.startsWith("yandex navigator") || lower.contains("is running")
    }
    val streetName = when {
        !isIgnored -> streetRaw
        navState.arrivalTime != null -> "Прибытие: ${navState.arrivalTime}"
        navState.isNavigating -> "Маршрут активен"
        else -> "—"
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = streetName.ifEmpty { "—" },
            color = primaryTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Freely movable Overlay 5: Full Yandex Navigator Notification Text.
 * Displays "Через 250 м поверните направо на Ленинский проспект".
 */
@Composable
internal fun LauncherNavTextOverlay(
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val isDark = LauncherThemeState.darkTheme
    val primaryTextColor = if (isDark) Color.White else Color(0xFF1E293B)
    val cardBg = if (isDark) Color(0xDD1E293B) else Color(0xDDF8FAFC)

    val fullText = (navState.fullNotificationText
        ?: navState.nextStreet?.let { "Маршрут по $it" }
        ?: navState.nextText
        ?: "—").trim()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fullText,
            color = primaryTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

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

    val now = System.currentTimeMillis()
    val isNavStale = (now - navState.lastUpdateMs) > 10000L

    val yandexLimit = navState.speedLimit?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    val cameraLimit = (adas.speedLimitKmh ?: adas.tsr.speedLimitKmh.takeIf { adas.tsr.valid })?.toString()
    val effectiveSpeedLimit = if (!isNavStale && yandexLimit != null) yandexLimit else cameraLimit

    val isDark = LauncherThemeState.darkTheme
    val cardBg = if (isDark) Color(0xDD1E293B) else Color(0xDDF8FAFC)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { launchLauncherApp(context, "ru.yandex.yandexnavi") }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CurrentSpeedBadge(speedKmh = currentSpeedKmh)
            effectiveSpeedLimit?.let { limit ->
                SpeedLimitBadge(speedLimit = limit)
            }
        }
    }
}

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
    val isDark = LauncherThemeState.darkTheme
    val primaryTextColor = if (isDark) Color.White else Color(0xFF1E293B)

    val scaleRevision by LauncherAppConfigStore.navTrafficLightScaleRevisionFlow.collectAsStateWithLifecycle()
    val scale = remember(context, scaleRevision) {
        LauncherAppConfigStore.navTrafficLightScale(context)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
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
            )

            navState.trafficLightDistanceMeters?.let { dist ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape((6 * scale).dp))
                        .background(Color(0xCC111827))
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
) {
    val lampSize = (22 * scale).dp
    val paddingHorizontal = (4 * scale).dp
    val paddingVertical = (5 * scale).dp
    val spacing = (3 * scale).dp
    val cornerRadius = (8 * scale).dp

    val colorLower = colorStr?.lowercase()
    val isGreen = colorLower == "green"
    val isYellow = colorLower == "yellow" || colorLower == "orange"
    val isRed = colorLower == "red" || colorLower == "dark_red"

    val greenColor = if (isGreen) Color(0xFF22C55E) else Color(0xFF374151)
    val yellowColor = if (isYellow) Color(0xFFF59E0B) else Color(0xFF374151)
    val redColor = if (isRed) Color(0xFFEF4444) else Color(0xFF374151)

    val formattedArrow = when (arrowStr?.lowercase()?.trim()) {
        "left" -> "←"
        "right" -> "→"
        "straight", "up" -> "↑"
        "u_turn" -> "↰"
        else -> if (arrowStr != null && arrowStr.length <= 3 && arrowStr.all { it.isDigit() }) arrowStr else ""
    }

    val timerText = countdownSeconds?.toString() ?: formattedArrow

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF111827))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(cornerRadius))
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TOP CIRCLE: GREEN SIGNAL
        Box(
            modifier = Modifier
                .size(lampSize)
                .clip(CircleShape)
                .background(greenColor),
        )

        // MIDDLE CIRCLE: TIMER & YELLOW SIGNAL
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

        // BOTTOM CIRCLE: RED SIGNAL
        Box(
            modifier = Modifier
                .size(lampSize)
                .clip(CircleShape)
                .background(redColor),
        )
    }
}

@Composable
private fun ManeuverIcon(
    bitmap: Bitmap?,
    isDark: Boolean,
    darkGraphite: Color,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Turn Maneuver",
                modifier = Modifier.size(24.dp),
                colorFilter = if (isDark) null else ColorFilter.tint(darkGraphite, blendMode = BlendMode.SrcIn),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.player_yandex_navigator),
                contentDescription = "Yandex Navigator",
                modifier = Modifier.size(22.dp),
                colorFilter = if (isDark) null else ColorFilter.tint(darkGraphite, blendMode = BlendMode.SrcIn),
            )
        }
    }
}

@Composable
private fun CurrentSpeedBadge(
    speedKmh: Int,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = speedKmh.toString(),
            color = Color.Black,
            fontSize = 12.sp,
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
) {
    val cleanText = remember(speedLimit) {
        val digitsOnly = speedLimit.filter { it.isDigit() }
        digitsOnly.ifEmpty { speedLimit.replace(Regex("(?i)\\s*(км/?ч|km/?h|км|km)\\s*"), "").trim() }
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color(0xFFEF4444), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = cleanText,
            color = Color.Black,
            fontSize = 12.sp,
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
