package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.R
import kotlin.math.roundToInt

private const val FLASH_DURATION_MS = 5_000L

/**
 * Telltale icons (seat belts, doors, ICM faults) — icon-only, only when active.
 * Icons flash for 5 seconds after activation/triggering and then remain steadily lit.
 */
@Composable
fun LauncherVehicleAlertsStrip(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val topBarRevision by LauncherAppConfigStore.topBarHeightRevisionFlow.collectAsStateWithLifecycle()
    val topBarHeightDp = remember(context, topBarRevision) {
        LauncherAppConfigStore.topBarHeightDp(context)
    }

    val scale = (topBarHeightDp / 40f).coerceIn(0.5f, 2.0f)
    val iconSizeDp = (18 * scale).roundToInt().coerceIn(9, 36)
    val paddingDp = (5 * scale).roundToInt().coerceIn(2, 12)
    val cornerRadiusDp = (8 * scale).roundToInt().coerceIn(4, 16)
    val spacingDp = (6 * scale).roundToInt().coerceIn(2, 12)

    val state by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    val showAll = LauncherDevVehicleState.showAllIndicators
    // Body open statuses are shown as badges on the car, not next to ADAS (unless showAll is active).
    val alerts = if (showAll) state.alerts else state.alerts.filterNot { it.id.isBodyOpenAlert }
    if (alerts.isEmpty()) return

    val activeAlertIds = remember(alerts) { alerts.map { it.id }.toSet() }
    val activationTimes = remember { mutableMapOf<LauncherAlertId, Long>() }

    SideEffect {
        activationTimes.keys.retainAll(activeAlertIds)
        val now = SystemClock.uptimeMillis()
        for (id in activeAlertIds) {
            if (!activationTimes.containsKey(id)) {
                activationTimes[id] = now
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        alerts.forEach { alert ->
            val activationTimeMs = activationTimes[alert.id] ?: SystemClock.uptimeMillis()
            LauncherVehicleAlertIcon(
                alert = alert,
                activationTimeMs = activationTimeMs,
                iconSizeDp = iconSizeDp,
                paddingDp = paddingDp,
                cornerRadiusDp = cornerRadiusDp,
            )
        }
    }
}

internal val LauncherAlertId.isBodyOpenAlert: Boolean
    get() = when (this) {
        LauncherAlertId.DoorDriver,
        LauncherAlertId.DoorPassenger,
        LauncherAlertId.DoorRearLeft,
        LauncherAlertId.DoorRearRight,
        LauncherAlertId.HoodOpen,
        LauncherAlertId.TrunkOpen,
        -> true
        else -> false
    }

@Composable
private fun LauncherVehicleAlertIcon(
    alert: LauncherVehicleAlert,
    activationTimeMs: Long,
    iconSizeDp: Int = 18,
    paddingDp: Int = 5,
    cornerRadiusDp: Int = 8,
) {
    var isFlashing by remember(alert.id, activationTimeMs) {
        mutableStateOf((SystemClock.uptimeMillis() - activationTimeMs) < FLASH_DURATION_MS)
    }

    LaunchedEffect(alert.id, activationTimeMs) {
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

    val alpha = if (isFlashing) {
        val infiniteTransition = rememberInfiniteTransition(label = "alert_flash_${alert.id}")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alert_alpha_${alert.id}",
        )
        animatedAlpha
    } else {
        1.0f
    }

    val tint = when (alert.severity) {
        LauncherAlertSeverity.Critical -> LauncherColors.WarningRed
        LauncherAlertSeverity.Warning -> LauncherColors.WarningAmber
    }
    val label = stringResource(alert.id.labelRes)
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(paddingDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(alert.id.iconRes),
            contentDescription = label,
            modifier = Modifier.size(iconSizeDp.dp),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
    }
}

internal val LauncherAlertId.labelRes: Int
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver -> R.string.launcher_alert_seatbelt_driver
        LauncherAlertId.SeatBeltPassenger -> R.string.launcher_alert_seatbelt_passenger
        LauncherAlertId.SeatBeltRearLeft -> R.string.launcher_alert_seatbelt_rl
        LauncherAlertId.SeatBeltRearMid -> R.string.launcher_alert_seatbelt_rm
        LauncherAlertId.SeatBeltRearRight -> R.string.launcher_alert_seatbelt_rr
        LauncherAlertId.DoorDriver -> R.string.launcher_alert_door_fl
        LauncherAlertId.DoorPassenger -> R.string.launcher_alert_door_fr
        LauncherAlertId.DoorRearLeft -> R.string.launcher_alert_door_rl
        LauncherAlertId.DoorRearRight -> R.string.launcher_alert_door_rr
        LauncherAlertId.HoodOpen -> R.string.launcher_alert_hood
        LauncherAlertId.TrunkOpen -> R.string.launcher_alert_trunk
        LauncherAlertId.TirePressure -> R.string.launcher_alert_tire
        LauncherAlertId.LowFuel -> R.string.launcher_alert_fuel
        LauncherAlertId.Speeding -> R.string.launcher_alert_speeding
        LauncherAlertId.HighTemperature -> R.string.launcher_alert_high_temp
        LauncherAlertId.PressBrake -> R.string.launcher_alert_press_brake
        LauncherAlertId.SysFault -> R.string.launcher_alert_sys_fault
        LauncherAlertId.BattFault -> R.string.launcher_alert_batt_fault
        LauncherAlertId.ChargeFault -> R.string.launcher_alert_charge_fault
        LauncherAlertId.HvFaultStop -> R.string.launcher_alert_hv_fault
        LauncherAlertId.PowerModeFail -> R.string.launcher_alert_power_mode
        LauncherAlertId.LowBatterySoc -> R.string.launcher_alert_low_soc
    }

private val LauncherAlertId.iconRes: Int
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver,
        LauncherAlertId.SeatBeltPassenger,
        LauncherAlertId.SeatBeltRearLeft,
        LauncherAlertId.SeatBeltRearMid,
        LauncherAlertId.SeatBeltRearRight,
        -> R.drawable.ic_launcher_seatbelt
        LauncherAlertId.DoorDriver,
        LauncherAlertId.DoorPassenger,
        LauncherAlertId.DoorRearLeft,
        LauncherAlertId.DoorRearRight,
        -> R.drawable.ic_launcher_door
        LauncherAlertId.HoodOpen -> R.drawable.ic_launcher_hood
        LauncherAlertId.TrunkOpen -> R.drawable.ic_launcher_trunk
        LauncherAlertId.TirePressure -> R.drawable.ic_refuel_price_warning
        LauncherAlertId.LowFuel -> R.drawable.ic_launcher_fuel
        LauncherAlertId.LowBatterySoc,
        LauncherAlertId.BattFault,
        -> R.drawable.ic_launcher_battery
        else -> R.drawable.ic_notification
    }
