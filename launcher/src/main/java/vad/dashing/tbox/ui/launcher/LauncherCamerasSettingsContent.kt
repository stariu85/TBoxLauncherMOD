package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.camd.CamdCameraRepository
import vad.dashing.tbox.camd.CamdConnectionStatus
import vad.dashing.tbox.camd.CamdSocketClient
import vad.dashing.tbox.mbcan.TurnSignalSide

@Composable
fun LauncherCamerasSettingsContent(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    val camdConfigRevision by LauncherAppConfigStore.camdConfigRevisionFlow.collectAsStateWithLifecycle()
    val disableStockAvm = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdDisableStockAvm(context)
    }
    val stockAvmMinSpeed = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdStockAvmSideCamerasMinSpeed(context)
    }
    val reverseCameras = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdReverseCameras(context)
    }
    val pdcCameras = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdPdcCameras(context)
    }
    val turnSignalCameras = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdTurnSignalCameras(context)
    }
    val shapeCircle = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdSideCamerasShapeCircle(context)
    }
    val rectCorners = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdSideCamerasRectCorners(context)
    }
    val leftPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdTurnSignalLeftPreset(context)
    }
    val rightPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdTurnSignalRightPreset(context)
    }
    val sizeAdjustment = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdSideCamerasSizeAdjustment(context)
    }
    val autoDisableStockAvm = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdAutoDisableStockAvm(context)
    }
    val autoAvmOnSpeed = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdAutoAvmOnSpeed(context)
    }
    val autoAvmOffSpeed = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdAutoAvmOffSpeed(context)
    }
    val lowSpeedSideCameras = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedSideCameras(context)
    }
    val lowSpeedThreshold = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedThreshold(context)
    }
    val lowSpeedTrigger = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedSideTrigger(context)
    }
    val lowSpeedWidthPercent = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedSideWidthPercent(context)
    }
    val lowSpeedLeftPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedLeftPreset(context)
    }
    val lowSpeedRightPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedRightPreset(context)
    }
    val lowSpeedSteerOffAngle = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedSteerOffAngle(context)
    }
    val lowSpeedSteerOnAngle = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdLowSpeedSteerOnAngle(context)
    }
    val widgetLrLeftPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdWidgetLrLeftPreset(context)
    }
    val widgetLrRightPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdWidgetLrRightPreset(context)
    }
    val widgetUdTopPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdWidgetUdTopPreset(context)
    }
    val widgetUdBottomPreset = remember(context, camdConfigRevision) {
        LauncherAppConfigStore.camdWidgetUdBottomPreset(context)
    }

    val status by CamdCameraRepository.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        CamdCameraRepository.refreshStatus(context)
        CamdCameraRepository.startSideCamerasMonitor(context)
    }

    LaunchedEffect(status) {
        val s = status
        if (s is CamdConnectionStatus.Connected) {
            if (s.keepAvm != disableStockAvm) {
                LauncherAppConfigStore.setCamdDisableStockAvm(context, s.keepAvm)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Camd Service Status Block
        val statusBg = LauncherColors.CardDarkElevated
        val statusTitle = stringResource(R.string.camd_status_title)

        val (statusText, statusColor) = when (val s = status) {
            is CamdConnectionStatus.NotInstalled -> {
                stringResource(R.string.camd_status_not_installed) to Color(0xFFF59E0B)
            }
            is CamdConnectionStatus.Connected -> {
                val detail = if (s.openWindows.isNotEmpty()) {
                    " (окна: ${s.openWindows.joinToString()})"
                } else {
                    ""
                }
                val avmDetail = if (s.keepAvm) " • Штатная AVM выключена" else " • Штатная AVM активна"
                val recDetail = when {
                    s.recSos -> " • Регистратор: SOS!"
                    s.recActive -> " • Регистратор: запись (${s.recSegSeconds ?: 180}с)"
                    s.recEnabled -> " • Регистратор: включен"
                    else -> ""
                }
                val presetsDetail = if (s.presets.isNotEmpty()) " • Пресетов: ${s.presets.size}" else ""
                "${stringResource(R.string.camd_status_connected)}$detail$avmDetail$recDetail$presetsDetail" to Color(0xFF10B981)
            }
            is CamdConnectionStatus.NoConnection -> {
                stringResource(R.string.camd_status_no_connection, s.errorMessage) to Color(0xFFEF4444)
            }
        }

        var isSosTriggering by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(statusBg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusTitle,
                    color = LauncherColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = LauncherColors.AccentCyan,
                        strokeWidth = 2.dp,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isChecking = true
                                CamdSocketClient.wakeUpService(context)
                                CamdCameraRepository.refreshStatus(context)
                                isChecking = false
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.camd_status_check_button),
                            fontSize = 13.sp,
                            color = LauncherColors.AccentCyan,
                        )
                    }
                }
            }

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )

            val connectedStatus = status as? CamdConnectionStatus.Connected
            if (connectedStatus != null && (connectedStatus.recActive || connectedStatus.recEnabled)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSosTriggering = true
                                CamdCameraRepository.triggerRecorderSos(context)
                                delay(1500L)
                                isSosTriggering = false
                            }
                        },
                        enabled = !isSosTriggering && !connectedStatus.recSos,
                    ) {
                        Text(
                            text = if (connectedStatus.recSos) "SOS выполняется..." else "REC SOS (Аварийное сохранение)",
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444),
                        )
                    }
                }
            }
        }

        val isInstalled = status !is CamdConnectionStatus.NotInstalled

        // Option 1: "Выключить штатную систему 360"
        val canToggleManualAvm = isInstalled && !autoDisableStockAvm
        LauncherSettingsToggleRow(
            label = stringResource(R.string.camd_disable_stock_avm_title),
            active = disableStockAvm,
            enabled = canToggleManualAvm,
            onClick = if (canToggleManualAvm) {
                {
                    val next = !disableStockAvm
                    LauncherAppConfigStore.setCamdDisableStockAvm(context, next)
                    scope.launch {
                        CamdCameraRepository.setHoldStockAvm(context, next)
                    }
                }
            } else null,
        )

        Text(
            text = stringResource(R.string.camd_disable_stock_avm_desc),
            color = if (canToggleManualAvm) LauncherColors.TextSecondary else LauncherColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        val isStockAvmEnabled = isInstalled && !disableStockAvm

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.camd_stock_avm_min_speed_title),
                    color = if (isStockAvmEnabled) LauncherColors.TextPrimary else LauncherColors.TextMuted,
                    fontSize = 15.sp,
                )
                Text(
                    text = "$stockAvmMinSpeed км/ч",
                    color = if (isStockAvmEnabled) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.camd_stock_avm_min_speed_desc),
                color = if (isStockAvmEnabled) LauncherColors.TextSecondary else LauncherColors.TextMuted,
                fontSize = 12.sp,
            )
            Slider(
                value = stockAvmMinSpeed.toFloat(),
                onValueChange = { next ->
                    LauncherAppConfigStore.setCamdStockAvmSideCamerasMinSpeed(context, next.roundToInt())
                },
                valueRange = 1f..30f,
                steps = 28,
                enabled = isStockAvmEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = LauncherColors.AccentCyan,
                    activeTrackColor = LauncherColors.AccentCyan,
                    inactiveTrackColor = LauncherColors.TextMuted,
                    disabledThumbColor = LauncherColors.TextMuted,
                    disabledActiveTrackColor = LauncherColors.TextMuted,
                    disabledInactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.3f),
                ),
            )
        }

        if (isInstalled && disableStockAvm) {
            Spacer(modifier = Modifier.height(4.dp))

            LauncherSettingsToggleRow(
                label = stringResource(R.string.camd_reverse_cameras_title),
                active = reverseCameras,
                enabled = true,
                onClick = {
                    val next = !reverseCameras
                    LauncherAppConfigStore.setCamdReverseCameras(context, next)
                },
            )

            Text(
                text = stringResource(R.string.camd_reverse_cameras_desc),
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            LauncherSettingsToggleRow(
                label = stringResource(R.string.camd_pdc_cameras_title),
                active = pdcCameras,
                enabled = true,
                onClick = {
                    val next = !pdcCameras
                    LauncherAppConfigStore.setCamdPdcCameras(context, next)
                },
            )

            Text(
                text = stringResource(R.string.camd_pdc_cameras_desc),
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Option 1.1: "Автоматическое выключение штатной AVM"
        LauncherSettingsToggleRow(
            label = stringResource(R.string.camd_auto_disable_stock_avm_title),
            active = autoDisableStockAvm,
            enabled = isInstalled,
            onClick = if (isInstalled) {
                {
                    val next = !autoDisableStockAvm
                    LauncherAppConfigStore.setCamdAutoDisableStockAvm(context, next)
                }
            } else null,
        )

        Text(
            text = stringResource(R.string.camd_auto_disable_stock_avm_desc),
            color = if (isInstalled) LauncherColors.TextSecondary else LauncherColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        // Dual-thumb scale for auto AVM speed thresholds (activation & deactivation)
        val isAutoAvmActive = isInstalled && autoDisableStockAvm
        Spacer(modifier = Modifier.height(2.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val onSpeedLabel = if (autoAvmOnSpeed == 0) {
                    stringResource(R.string.camd_auto_avm_on_speed_reverse_label)
                } else {
                    stringResource(R.string.camd_auto_avm_on_speed_label, autoAvmOnSpeed)
                }
                Text(
                    text = onSpeedLabel,
                    color = if (isAutoAvmActive) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.camd_auto_avm_off_speed_label, autoAvmOffSpeed),
                    color = if (isAutoAvmActive) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.camd_auto_avm_speeds_desc),
                color = if (isAutoAvmActive) LauncherColors.TextSecondary else LauncherColors.TextMuted,
                fontSize = 12.sp,
            )
            RangeSlider(
                value = autoAvmOnSpeed.toFloat()..autoAvmOffSpeed.toFloat(),
                onValueChange = { range ->
                    var newOn = range.start.roundToInt().coerceIn(0, 29)
                    var newOff = range.endInclusive.roundToInt().coerceIn(1, 30)
                    if (newOff - newOn < 1) {
                        if (newOn != autoAvmOnSpeed) {
                            newOn = (newOff - 1).coerceAtLeast(0)
                        } else {
                            newOff = (newOn + 1).coerceAtMost(30)
                        }
                    }
                    if (newOff <= newOn) {
                        if (newOn < 30) newOff = newOn + 1 else newOn = newOff - 1
                    }
                    LauncherAppConfigStore.setCamdAutoAvmSpeeds(context, newOn, newOff)
                },
                valueRange = 0f..30f,
                steps = 29,
                enabled = isAutoAvmActive,
                colors = SliderDefaults.colors(
                    thumbColor = LauncherColors.AccentCyan,
                    activeTrackColor = LauncherColors.AccentCyan,
                    inactiveTrackColor = LauncherColors.TextMuted,
                    disabledThumbColor = LauncherColors.TextMuted,
                    disabledActiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                    disabledInactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.2f),
                ),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Option 2: "Включение боковых камер по поворотникам"
        val canEnableTurnSignals = isInstalled
        LauncherSettingsToggleRow(
            label = stringResource(R.string.camd_turn_signals_title),
            active = turnSignalCameras,
            enabled = canEnableTurnSignals,
            onClick = if (canEnableTurnSignals) {
                {
                    val next = !turnSignalCameras
                    LauncherAppConfigStore.setCamdTurnSignalCameras(context, next)
                }
            } else null,
        )

        Text(
            text = stringResource(R.string.camd_turn_signals_desc),
            color = if (canEnableTurnSignals) LauncherColors.TextSecondary else LauncherColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        // Sub-options for turn signal side cameras (shape & presets)
        if (canEnableTurnSignals && turnSignalCameras) {
            Spacer(modifier = Modifier.height(4.dp))

            // Option 2.1: Circular shape for side cameras (shape=circle)
            LauncherSettingsToggleRow(
                label = stringResource(R.string.camd_side_cameras_shape_circle_title),
                active = shapeCircle,
                enabled = true,
                onClick = {
                    val next = !shapeCircle
                    LauncherAppConfigStore.setCamdSideCamerasShapeCircle(context, next)
                },
            )

            Text(
                text = stringResource(R.string.camd_side_cameras_shape_circle_desc),
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Option 2.2: Rectangular video in corners (3x4, 4 - width)
            LauncherSettingsToggleRow(
                label = stringResource(R.string.camd_side_cameras_rect_corners_title),
                active = rectCorners,
                enabled = true,
                onClick = {
                    val next = !rectCorners
                    LauncherAppConfigStore.setCamdSideCamerasRectCorners(context, next)
                },
            )

            Text(
                text = stringResource(R.string.camd_side_cameras_rect_corners_desc),
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            if (shapeCircle || rectCorners) {
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.camd_side_cameras_size_adjustment_title),
                            color = LauncherColors.TextPrimary,
                            fontSize = 15.sp,
                        )
                        val formattedPercent = if (sizeAdjustment > 0) "+$sizeAdjustment%" else "$sizeAdjustment%"
                        Text(
                            text = formattedPercent,
                            color = LauncherColors.AccentCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.camd_side_cameras_size_adjustment_desc),
                        color = LauncherColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = sizeAdjustment.toFloat(),
                        onValueChange = { next ->
                            LauncherAppConfigStore.setCamdSideCamerasSizeAdjustment(context, next.roundToInt())
                        },
                        valueRange = -50f..100f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = LauncherColors.AccentCyan,
                            activeTrackColor = LauncherColors.AccentCyan,
                            inactiveTrackColor = LauncherColors.TextMuted,
                        ),
                    )
                }
            }

            // Option 2.3: Preset selection for Left Camera & Right Camera (all broadcast presets)
            val connectedStatus = status as? CamdConnectionStatus.Connected
            val defaultPresetLabel = stringResource(R.string.camd_preset_default)

            val allPresets = (listOf("") + (connectedStatus?.presets?.map { it.name } ?: emptyList()) + listOfNotNull(leftPreset.ifBlank { null }, rightPreset.ifBlank { null })).distinct()
            if (allPresets.size > 1) {
                val formatPresetLabel: (String) -> String = { presetName ->
                    if (presetName.isBlank() || presetName !in allPresets) {
                        defaultPresetLabel
                    } else {
                        val p = connectedStatus?.presets?.firstOrNull { it.name == presetName }
                        if (p != null) {
                            "${p.name} (#${p.cameraId})"
                        } else {
                            presetName
                        }
                    }
                }

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_turn_signal_preset_left_title),
                    value = formatPresetLabel(leftPreset),
                    onClick = {
                        val currentIdx = allPresets.indexOf(leftPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allPresets.size
                        LauncherAppConfigStore.setCamdTurnSignalLeftPreset(context, allPresets[nextIdx])
                    },
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_turn_signal_preset_right_title),
                    value = formatPresetLabel(rightPreset),
                    onClick = {
                        val currentIdx = allPresets.indexOf(rightPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allPresets.size
                        LauncherAppConfigStore.setCamdTurnSignalRightPreset(context, allPresets[nextIdx])
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Option 3: "Боковые камеры на малой скорости при поворотах"
        LauncherSettingsToggleRow(
            label = stringResource(R.string.camd_low_speed_side_cameras_title),
            active = lowSpeedSideCameras,
            enabled = isInstalled,
            onClick = if (isInstalled) {
                {
                    val next = !lowSpeedSideCameras
                    LauncherAppConfigStore.setCamdLowSpeedSideCameras(context, next)
                }
            } else null,
        )

        Text(
            text = stringResource(R.string.camd_low_speed_side_cameras_desc),
            color = if (isInstalled) LauncherColors.TextSecondary else LauncherColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (isInstalled && lowSpeedSideCameras) {
            Spacer(modifier = Modifier.height(4.dp))

            // Sub-option 3.1: Trigger condition (turn signals, steering 30 deg, both)
            val triggerOptions = listOf(
                CAMD_LOW_SPEED_TRIGGER_TURN_SIGNALS to stringResource(R.string.camd_low_speed_trigger_turn_signals),
                CAMD_LOW_SPEED_TRIGGER_STEERING to stringResource(R.string.camd_low_speed_trigger_steering),
                CAMD_LOW_SPEED_TRIGGER_BOTH to stringResource(R.string.camd_low_speed_trigger_both),
            )
            val currentTriggerLabel = triggerOptions.firstOrNull { it.first == lowSpeedTrigger }?.second
                ?: stringResource(R.string.camd_low_speed_trigger_both)

            LauncherSettingsValueRow(
                label = stringResource(R.string.camd_low_speed_trigger_title),
                value = currentTriggerLabel,
                onClick = {
                    val currentIdx = triggerOptions.indexOfFirst { it.first == lowSpeedTrigger }.takeIf { it >= 0 } ?: 0
                    val nextIdx = (currentIdx + 1) % triggerOptions.size
                    LauncherAppConfigStore.setCamdLowSpeedSideTrigger(context, triggerOptions[nextIdx].first)
                },
            )

            // Steering wheel activation/deactivation angle dual-thumb slider
            val isSteerTriggerActive = isInstalled && lowSpeedSideCameras &&
                (lowSpeedTrigger == CAMD_LOW_SPEED_TRIGGER_STEERING || lowSpeedTrigger == CAMD_LOW_SPEED_TRIGGER_BOTH)

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.camd_low_speed_steer_off_angle_label, lowSpeedSteerOffAngle),
                        color = if (isSteerTriggerActive) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.camd_low_speed_steer_on_angle_label, lowSpeedSteerOnAngle),
                        color = if (isSteerTriggerActive) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(R.string.camd_low_speed_steer_angles_desc),
                    color = if (isSteerTriggerActive) LauncherColors.TextSecondary else LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
                RangeSlider(
                    value = lowSpeedSteerOffAngle.toFloat()..lowSpeedSteerOnAngle.toFloat(),
                    onValueChange = { range ->
                        val rawOff = (range.start / 5f).roundToInt() * 5
                        val rawOn = (range.endInclusive / 5f).roundToInt() * 5
                        var newOff = rawOff.coerceIn(5, 85)
                        var newOn = rawOn.coerceIn(10, 90)

                        if (newOn - newOff < 5) {
                            if (newOff != lowSpeedSteerOffAngle) {
                                newOff = (newOn - 5).coerceAtLeast(5)
                            } else {
                                newOn = (newOff + 5).coerceAtMost(90)
                            }
                        }
                        if (newOn <= newOff) {
                            if (newOff <= 85) newOn = newOff + 5 else newOff = newOn - 5
                        }
                        LauncherAppConfigStore.setCamdLowSpeedSteerAngles(context, newOff, newOn)
                    },
                    valueRange = 5f..90f,
                    steps = 16,
                    enabled = isSteerTriggerActive,
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                        inactiveTrackColor = LauncherColors.TextMuted,
                        disabledThumbColor = LauncherColors.TextMuted,
                        disabledActiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.4f),
                        disabledInactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.2f),
                    ),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-option 3.2: Speed threshold slider (1..30 km/h)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.camd_low_speed_threshold_title),
                        color = LauncherColors.TextPrimary,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "$lowSpeedThreshold км/ч",
                        color = LauncherColors.AccentCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(R.string.camd_low_speed_threshold_desc),
                    color = LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
                Slider(
                    value = lowSpeedThreshold.toFloat(),
                    onValueChange = { next ->
                        LauncherAppConfigStore.setCamdLowSpeedThreshold(context, next.roundToInt())
                    },
                    valueRange = 1f..30f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                        inactiveTrackColor = LauncherColors.TextMuted,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-option 3.3: Width adjustment slider (10%..50%, step 5%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.camd_low_speed_width_title),
                        color = LauncherColors.TextPrimary,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "$lowSpeedWidthPercent%",
                        color = LauncherColors.AccentCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(R.string.camd_low_speed_width_desc),
                    color = LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
                Slider(
                    value = lowSpeedWidthPercent.toFloat(),
                    onValueChange = { next ->
                        val rounded5 = (next / 5f).roundToInt() * 5
                        LauncherAppConfigStore.setCamdLowSpeedSideWidthPercent(context, rounded5)
                    },
                    valueRange = 10f..50f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                        inactiveTrackColor = LauncherColors.TextMuted,
                    ),
                )
            }

            // Sub-option 3.4: Preset selection for Left and Right low-speed cameras
            val connectedStatus = status as? CamdConnectionStatus.Connected
            val defaultPresetLabel = stringResource(R.string.camd_preset_default)
            val allLowSpeedPresets = (listOf("") + (connectedStatus?.presets?.map { it.name } ?: emptyList()) + listOfNotNull(lowSpeedLeftPreset.ifBlank { null }, lowSpeedRightPreset.ifBlank { null })).distinct()

            if (allLowSpeedPresets.size > 1) {
                val formatLowSpeedPresetLabel: (String) -> String = { presetName ->
                    if (presetName.isBlank() || presetName !in allLowSpeedPresets) {
                        defaultPresetLabel
                    } else {
                        val p = connectedStatus?.presets?.firstOrNull { it.name == presetName }
                        if (p != null) {
                            "${p.name} (#${p.cameraId})"
                        } else {
                            presetName
                        }
                    }
                }

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_low_speed_preset_left_title),
                    value = formatLowSpeedPresetLabel(lowSpeedLeftPreset),
                    onClick = {
                        val currentIdx = allLowSpeedPresets.indexOf(lowSpeedLeftPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allLowSpeedPresets.size
                        LauncherAppConfigStore.setCamdLowSpeedLeftPreset(context, allLowSpeedPresets[nextIdx])
                    },
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_low_speed_preset_right_title),
                    value = formatLowSpeedPresetLabel(lowSpeedRightPreset),
                    onClick = {
                        val currentIdx = allLowSpeedPresets.indexOf(lowSpeedRightPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allLowSpeedPresets.size
                        LauncherAppConfigStore.setCamdLowSpeedRightPreset(context, allLowSpeedPresets[nextIdx])
                    },
                )
            }
        }

        if (isInstalled) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.camd_widget_title),
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            val connectedStatus = status as? CamdConnectionStatus.Connected
            val defaultPresetLabel = stringResource(R.string.camd_preset_default)

            val allWidgetPresets = (listOf("") + (connectedStatus?.presets?.map { it.name } ?: emptyList()) +
                listOfNotNull(
                    widgetLrLeftPreset.ifBlank { null },
                    widgetLrRightPreset.ifBlank { null },
                    widgetUdTopPreset.ifBlank { null },
                    widgetUdBottomPreset.ifBlank { null }
                )).distinct()

            val formatWidgetPresetLabel: (String) -> String = { presetName ->
                if (presetName.isBlank() || presetName !in allWidgetPresets) {
                    defaultPresetLabel
                } else {
                    val p = connectedStatus?.presets?.firstOrNull { it.name == presetName }
                    if (p != null) {
                        "${p.name} (#${p.cameraId})"
                    } else {
                        presetName
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Section: Кнопка «Право / Лево»
                Text(
                    text = stringResource(R.string.camd_widget_lr_section_title),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_widget_lr_left_preset_title),
                    value = formatWidgetPresetLabel(widgetLrLeftPreset),
                    onClick = {
                        val currentIdx = allWidgetPresets.indexOf(widgetLrLeftPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allWidgetPresets.size
                        LauncherAppConfigStore.setCamdWidgetLrLeftPreset(context, allWidgetPresets[nextIdx])
                    },
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_widget_lr_right_preset_title),
                    value = formatWidgetPresetLabel(widgetLrRightPreset),
                    onClick = {
                        val currentIdx = allWidgetPresets.indexOf(widgetLrRightPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allWidgetPresets.size
                        LauncherAppConfigStore.setCamdWidgetLrRightPreset(context, allWidgetPresets[nextIdx])
                    },
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Section: Кнопка «Верх / Низ»
                Text(
                    text = stringResource(R.string.camd_widget_ud_section_title),
                    color = LauncherColors.AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_widget_ud_top_preset_title),
                    value = formatWidgetPresetLabel(widgetUdTopPreset),
                    onClick = {
                        val currentIdx = allWidgetPresets.indexOf(widgetUdTopPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allWidgetPresets.size
                        LauncherAppConfigStore.setCamdWidgetUdTopPreset(context, allWidgetPresets[nextIdx])
                    },
                )

                LauncherSettingsValueRow(
                    label = stringResource(R.string.camd_widget_ud_bottom_preset_title),
                    value = formatWidgetPresetLabel(widgetUdBottomPreset),
                    onClick = {
                        val currentIdx = allWidgetPresets.indexOf(widgetUdBottomPreset).takeIf { it >= 0 } ?: 0
                        val nextIdx = (currentIdx + 1) % allWidgetPresets.size
                        LauncherAppConfigStore.setCamdWidgetUdBottomPreset(context, allWidgetPresets[nextIdx])
                    },
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.camd_widget_360_info),
                    color = LauncherColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Test action buttons
        val activeCamera by CamdCameraRepository.activeTurnSignalCamera.collectAsStateWithLifecycle()
        val activeSide by CamdCameraRepository.activeTurnSignalSideFlow.collectAsStateWithLifecycle()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    CamdCameraRepository.toggleDesktopCameras(context)
                },
                enabled = isInstalled,
            ) {
                Text(
                    text = stringResource(R.string.camd_test_desktop_cameras_btn),
                    fontSize = 13.sp,
                    color = if (isInstalled) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                )
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        CamdCameraRepository.closeAll(context)
                    }
                },
                enabled = isInstalled,
            ) {
                Text(
                    text = stringResource(R.string.camd_close_all_cameras_btn),
                    fontSize = 13.sp,
                    color = if (isInstalled) LauncherColors.TextPrimary else LauncherColors.TextMuted,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val isLeftActive = activeSide == TurnSignalSide.Left || activeCamera == CamdCameraRepository.CAMERA_ID_LEFT
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    CamdCameraRepository.testSideCamera(context, CamdCameraRepository.CAMERA_ID_LEFT)
                },
                enabled = isInstalled,
            ) {
                Text(
                    text = stringResource(R.string.camd_test_side_left_btn),
                    fontSize = 13.sp,
                    color = if (isLeftActive) Color(0xFF10B981) else if (isInstalled) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                )
            }

            val isRightActive = activeSide == TurnSignalSide.Right || activeCamera == CamdCameraRepository.CAMERA_ID_RIGHT
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    CamdCameraRepository.testSideCamera(context, CamdCameraRepository.CAMERA_ID_RIGHT)
                },
                enabled = isInstalled,
            ) {
                Text(
                    text = stringResource(R.string.camd_test_side_right_btn),
                    fontSize = 13.sp,
                    color = if (isRightActive) Color(0xFF10B981) else if (isInstalled) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                )
            }
        }
    }
}
