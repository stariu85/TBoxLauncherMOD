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

    val status by CamdCameraRepository.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        CamdCameraRepository.refreshStatus(context)
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
                val presetsDetail = if (s.presets.isNotEmpty()) " • Пресетов: ${s.presets.size}" else ""
                "${stringResource(R.string.camd_status_connected)}$detail$avmDetail$presetsDetail" to Color(0xFF10B981)
            }
            is CamdConnectionStatus.NoConnection -> {
                stringResource(R.string.camd_status_no_connection, s.errorMessage) to Color(0xFFEF4444)
            }
        }

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
        }

        val isInstalled = status !is CamdConnectionStatus.NotInstalled

        // Option 1: "Выключить штатную систему 360"
        LauncherSettingsToggleRow(
            label = stringResource(R.string.camd_disable_stock_avm_title),
            active = disableStockAvm,
            enabled = isInstalled,
            onClick = if (isInstalled) {
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
            color = LauncherColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

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
