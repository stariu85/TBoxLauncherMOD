package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.R
import kotlin.math.roundToInt

internal object LauncherVehicleStatusDialogState {
    var isVisible by mutableStateOf(false)
        private set

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}

internal enum class LauncherVehicleHealthStatus {
    Ok,       // Зелёный значок OK в кругу
    Warning,  // Жёлтый значок "восклицательный знак в треугольнике"
    Error,    // Красный значок Warning! в прямоугольной рамке
}

@Composable
internal fun rememberVehicleHealthStatus(): LauncherVehicleHealthStatus {
    val alertsState by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    val adasState by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tiresState by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val wheelsPressure by CanDataRepository.wheelsPressure.collectAsStateWithLifecycle()
    val effectiveTires = remember(tiresState, wheelsPressure) {
        mergeTireStateWithCanData(tiresState, wheelsPressure)
    }
    val simShowAll = LauncherDevVehicleState.showAllIndicators

    val alerts = if (simShowAll) alertsState.alerts else alertsState.alerts.filterNot { it.id.isBodyOpenAlert }
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasState

    val hasCritical = simShowAll ||
            alerts.any { it.severity == LauncherAlertSeverity.Critical } ||
            effectiveTires.hasAttention ||
            adas.srrSystem == LauncherSrrSystemState.Fault

    if (hasCritical) return LauncherVehicleHealthStatus.Error

    val hasWarning = alerts.any { it.severity == LauncherAlertSeverity.Warning }

    if (hasWarning) return LauncherVehicleHealthStatus.Warning

    return LauncherVehicleHealthStatus.Ok
}

internal data class LauncherVehicleStatusDetailItem(
    val title: String,
    val description: String,
    val severity: LauncherAlertSeverity,
)

private val LauncherAlertId.detailDescription: String
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver -> "Водитель не пристёгнут. Зафиксируйте ремень безопасности перед началом движения."
        LauncherAlertId.SeatBeltPassenger -> "Передний пассажир не пристёгнут. Зафиксируйте ремень безопасности."
        LauncherAlertId.SeatBeltRearLeft -> "Пассажир на заднем левом сиденье не пристёгнут."
        LauncherAlertId.SeatBeltRearMid -> "Пассажир на заднем среднем сиденье не пристёгнут."
        LauncherAlertId.SeatBeltRearRight -> "Пассажир на заднем правом сиденье не пристёгнут."
        LauncherAlertId.DoorDriver -> "Водительская дверь не закрыта полностью."
        LauncherAlertId.DoorPassenger -> "Передняя пассажирская дверь не закрыта полностью."
        LauncherAlertId.DoorRearLeft -> "Задняя левая дверь не закрыта полностью."
        LauncherAlertId.DoorRearRight -> "Задняя правая дверь не закрыта полностью."
        LauncherAlertId.HoodOpen -> "Крышка капота неплотно закрыта. Проверьте фиксацию защёлки."
        LauncherAlertId.TrunkOpen -> "Дверь багажного отделения открыта."
        LauncherAlertId.TirePressure -> "Давление в колёсах ниже рекомендуемого порога (2.0 bar)."
        LauncherAlertId.LowFuel -> "Минимальный остаток топлива в баке. Пополните запас топлива."
        LauncherAlertId.Speeding -> "Текущая скорость превышает установленное ограничение."
        LauncherAlertId.HighTemperature -> "Высокая температура охлаждающей жидкости. Остановите движение и дайте двигателю остыть."
        LauncherAlertId.PressBrake -> "Для продолжения или смены режима нажмите педаль тормоза."
        LauncherAlertId.SysFault -> "Зафиксирована ошибка системы управления ICM или блоков CAN."
        LauncherAlertId.BattFault -> "Неисправность аккумулятора 12V или системы контроля батареи."
        LauncherAlertId.ChargeFault -> "Ошибка процесса зарядки АКБ или генератора."
        LauncherAlertId.HvFaultStop -> "Критический сбой высоковольтной системы HV. Требуется остановка и сервисная диагностика."
        LauncherAlertId.PowerModeFail -> "Ошибка переключения режимов питания Power Mode."
        LauncherAlertId.LowBatterySoc -> "Низкий уровень заряда аккумулятора (Low SOC)."
        LauncherAlertId.KeyBatteryLow -> "Низкий уровень заряда батарейки смарт-ключа (CR2032). Требуется замена."
    }

@Composable
internal fun rememberVehicleStatusDetailItems(): List<LauncherVehicleStatusDetailItem> {
    val alertsState by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    val adasState by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tiresState by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val wheelsPressure by CanDataRepository.wheelsPressure.collectAsStateWithLifecycle()
    val effectiveTires = remember(tiresState, wheelsPressure) {
        mergeTireStateWithCanData(tiresState, wheelsPressure)
    }
    val simShowAll = LauncherDevVehicleState.showAllIndicators

    val alerts = if (simShowAll) alertsState.alerts else alertsState.alerts.filterNot { it.id.isBodyOpenAlert }
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasState

    return buildList {
        alerts.forEach { alert ->
            add(
                LauncherVehicleStatusDetailItem(
                    title = stringResource(alert.id.labelRes),
                    description = alert.id.detailDescription,
                    severity = alert.severity,
                )
            )
        }

        if (effectiveTires.hasAttention) {
            val lowCorners = listOf(
                "Переднее левое" to effectiveTires.fl,
                "Переднее правое" to effectiveTires.fr,
                "Заднее левое" to effectiveTires.rl,
                "Заднее правое" to effectiveTires.rr,
            ).filter { it.second.needsAttention }

            lowCorners.forEach { (cornerName, wheel) ->
                val press = wheel.pressureBar?.let { "${(it * 10).roundToInt() / 10f} bar" } ?: "—"
                add(
                    LauncherVehicleStatusDetailItem(
                        title = "Низкое давление: $cornerName",
                        description = "Текущее давление $press ниже норматива (2.0 bar). Проверьте колесо.",
                        severity = LauncherAlertSeverity.Critical,
                    )
                )
            }
        }

        if (adas.srrSystem == LauncherSrrSystemState.Fault) {
            add(
                LauncherVehicleStatusDetailItem(
                    title = "Ошибка боковых радаров (SRR)",
                    description = "Неисправность или перекрытие датчиков слепых зон.",
                    severity = LauncherAlertSeverity.Critical,
                )
            )
        }
    }
}

@Composable
internal fun LauncherVehicleStatusDialog(
    onDismissRequest: () -> Unit,
) {
    val items = rememberVehicleStatusDetailItems()
    val status = rememberVehicleHealthStatus()

    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Состояние систем авто",
                    color = LauncherColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                when (status) {
                    LauncherVehicleHealthStatus.Ok -> {
                        Text(
                            text = "Good",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LauncherVehicleHealthStatus.Warning -> {
                        Text(
                            text = "Warning",
                            color = Color(0xFFF59E0B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LauncherVehicleHealthStatus.Error -> {
                        Text(
                            text = "Error",
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF22C55E),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "Тут нечего смотреть, у Дашки всё отлично!",
                                color = LauncherColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items.forEach { item ->
                        val tint = when (item.severity) {
                            LauncherAlertSeverity.Critical -> Color(0xFFEF4444)
                            LauncherAlertSeverity.Warning -> Color(0xFFF59E0B)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LauncherColors.SurfaceDark)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = tint,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    color = tint,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = item.description,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_close), color = LauncherColors.AccentCyan)
            }
        },
    )
}
