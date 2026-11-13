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
                "${stringResource(R.string.camd_status_connected)}$detail$avmDetail" to Color(0xFF10B981)
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
        val canEnableTurnSignals = isInstalled && disableStockAvm
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
    }
}
