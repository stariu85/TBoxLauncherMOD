package vad.dashing.tbox.ui.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.R
import kotlin.math.roundToInt

/**
 * Transparent 2-panel-height ADAS widget displaying:
 * - Top-left: Turn maneuver icon & distance (dark graphite in light theme)
 * - Bottom-left: Next traffic light & countdown timer (under maneuver)
 * - Top-right: Current car speed (black border circle) flush against Speed Limit sign (red border circle)
 * - Center: Lane markings (dark graphite in light theme)
 * - Bottom-right: Arrival ETA and remaining distance
 */
@Composable
internal fun LauncherYandexNavWidget(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navState by YandexNavRepository.state.collectAsStateWithLifecycle()
    val carSpeedState by CanDataRepository.carSpeed.collectAsStateWithLifecycle()
    val currentSpeedKmh = (carSpeedState ?: 0f).roundToInt().coerceAtLeast(0)

    val isDark = LauncherThemeState.darkTheme
    val darkGraphite = Color(0xFF1E293B)
    val primaryTextColor = if (isDark) Color.White else darkGraphite
    val secondaryTextColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF475569)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Transparent)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                launchLauncherApp(context, "ru.yandex.yandexnavi")
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // --- 1. TOP RIGHT: CURRENT SPEED + SPEED LIMIT (FLUSH TOGETHER) ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Current Speed Badge (Black Border)
            CurrentSpeedBadge(speedKmh = currentSpeedKmh)

            // Speed Limit Badge (Red Border)
            navState.speedLimit?.let { limit ->
                Spacer(modifier = Modifier.width(2.dp))
                SpeedLimitBadge(speedLimit = limit)
            }
        }

        // --- 2. TOP LEFT: MANEUVER ARROW & DISTANCE TO TURN ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            ManeuverIcon(
                bitmap = navState.maneuverBitmap,
                isDark = isDark,
                darkGraphite = darkGraphite,
            )

            Spacer(modifier = Modifier.height(3.dp))

            val distText = navState.maneuverDistance ?: ""
            if (distText.isNotEmpty()) {
                Text(
                    text = distText,
                    color = primaryTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // --- 3. CENTER: LANE MARKINGS (РАЗМЕТКА ПОЛОС) ---
        if (navState.lanesBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = navState.lanesBitmap!!.asImageBitmap(),
                    contentDescription = "Lane Markings",
                    modifier = Modifier.height(28.dp),
                    colorFilter = if (isDark) null else ColorFilter.tint(darkGraphite, blendMode = BlendMode.SrcIn),
                )
            }
        }

        // --- 4. BOTTOM LEFT: TRAFFIC LIGHT & COUNTDOWN TIMER (UNDER MANEUVER) ---
        if (navState.trafficLightColor != null || navState.trafficLightCountdownSeconds != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 2.dp),
            ) {
                TrafficLightBadge(
                    colorStr = navState.trafficLightColor,
                    countdownSeconds = navState.trafficLightCountdownSeconds,
                    arrowStr = navState.trafficLightArrow,
                )
            }
        }

        // --- 5. BOTTOM RIGHT: DISTANCE TO FINISH (ABOVE) & ARRIVAL TIME / TIME REMAINING (BELOW) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val finishDist = navState.remainingDistance
            val arrival = navState.arrivalTime
            val time = navState.remainingTime

            val timeSub = when {
                arrival != null && time != null -> "$arrival • $time"
                arrival != null -> arrival
                time != null -> time
                else -> null
            }

            if (finishDist != null) {
                Text(
                    text = finishDist,
                    color = primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (timeSub != null) {
                Text(
                    text = timeSub,
                    color = if (finishDist != null) secondaryTextColor else primaryTextColor,
                    fontSize = if (finishDist != null) 11.sp else 13.sp,
                    fontWeight = if (finishDist != null) FontWeight.Medium else FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ManeuverIcon(
    bitmap: Bitmap?,
    isDark: Boolean,
    darkGraphite: Color,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Turn Maneuver",
                modifier = Modifier.size(36.dp),
                colorFilter = if (isDark) null else ColorFilter.tint(darkGraphite, blendMode = BlendMode.SrcIn),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.player_yandex_navigator),
                contentDescription = "Yandex Navigator",
                modifier = Modifier.size(30.dp),
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
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.5.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = speedKmh.toString(),
            color = Color.Black,
            fontSize = 13.sp,
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
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.5.dp, Color(0xFFEF4444), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = cleanText,
            color = Color.Black,
            fontSize = 13.sp,
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
private fun TrafficLightBadge(
    colorStr: String?,
    countdownSeconds: Int?,
    arrowStr: String?,
) {
    val lightBg = when (colorStr?.lowercase()) {
        "red", "dark_red" -> Color(0xFFEF4444)
        "yellow", "orange" -> Color(0xFFF59E0B)
        "green" -> Color(0xFF22C55E)
        else -> if (countdownSeconds != null) Color(0xFF3B82F6) else return
    }

    val timerText = countdownSeconds?.toString() ?: arrowStr ?: ""

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(lightBg),
        contentAlignment = Alignment.Center,
    ) {
        if (timerText.isNotEmpty()) {
            @Suppress("DEPRECATION")
            Text(
                text = timerText,
                color = Color.White,
                fontSize = 13.sp,
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
}
