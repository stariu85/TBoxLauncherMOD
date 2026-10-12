package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.tbm.TbmConnectionState
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.rememberWrappedOnClick
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun TbmApiSettingsContent(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tbmRepository = remember(context) { TbmRepositoryProvider.get(context) }

    val connectionState by tbmRepository.connectionState.collectAsStateWithLifecycle()
    val savedToken by settingsManager.tbmApiTokenFlow.collectAsStateWithLifecycle(initialValue = null)
    val savedPort by settingsManager.tbmApiPortFlow.collectAsStateWithLifecycle(initialValue = 8765)

    var inputPortText by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var inputTokenText by remember(savedToken) { mutableStateOf(savedToken.orEmpty()) }
    var pairStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPairingInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tbmRepository.checkHealth()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Status Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LauncherColors.CardDarkElevated.copy(alpha = 0.5f))
                .padding(12.dp),
        ) {
            val statusText = when (val state = connectionState) {
                is TbmConnectionState.Connected -> stringResource(
                    R.string.settings_tbm_api_status_connected,
                    state.health.appVersion.ifEmpty { "1.0" },
                    state.health.apiVersion,
                )
                TbmConnectionState.Connecting -> stringResource(R.string.settings_tbm_api_status_connecting)
                TbmConnectionState.PairingRequired -> stringResource(R.string.settings_tbm_api_status_pairing_required)
                is TbmConnectionState.Error -> stringResource(R.string.settings_tbm_api_status_error, state.message)
                TbmConnectionState.Disconnected -> stringResource(R.string.settings_tbm_api_status_pairing_required)
            }

            val statusColor = when (connectionState) {
                is TbmConnectionState.Connected -> LauncherColors.AccentCyan
                TbmConnectionState.Connecting -> LauncherColors.WarningAmber
                else -> MaterialTheme.colorScheme.error
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.tboxBody,
                color = LauncherColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    scope.launch { tbmRepository.checkHealth() }
                },
            ) {
                Text(
                    text = stringResource(R.string.settings_tbm_api_check_health_button),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        // Pairing Button & Hint
        val approvedMsg = stringResource(R.string.settings_tbm_api_pair_approved)
        val pendingMsg = stringResource(R.string.settings_tbm_api_pair_pending)
        val failedMsg = stringResource(R.string.settings_tbm_api_pair_failed, "")

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LauncherColors.CardDarkElevated.copy(alpha = 0.3f))
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_tbm_api_pair_hint),
                style = MaterialTheme.typography.tboxBody,
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = rememberWrappedOnClick {
                        isPairingInProgress = true
                        pairStatusMessage = pendingMsg
                        scope.launch {
                            val res = tbmRepository.requestPairing()
                            isPairingInProgress = false
                            val pairResp = res.getOrNull()
                            if (pairResp?.status == "approved") {
                                pairStatusMessage = approvedMsg
                            } else if (pairResp?.status == "pending") {
                                pairStatusMessage = pendingMsg
                            } else {
                                pairStatusMessage = res.exceptionOrNull()?.message ?: pairResp?.status ?: failedMsg
                            }
                        }
                    },
                    enabled = !isPairingInProgress,
                ) {
                    if (isPairingInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LauncherColors.TextPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_tbm_api_pair_button),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }

            if (!pairStatusMessage.isNullOrEmpty()) {
                Text(
                    text = pairStatusMessage.orEmpty(),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.AccentCyan,
                    fontSize = 13.sp,
                )
            }
        }

        // Port Setting
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = inputPortText,
                onValueChange = { inputPortText = it },
                label = { Text(stringResource(R.string.settings_tbm_api_port_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = rememberWrappedOnClick {
                    val p = inputPortText.toIntOrNull() ?: 8765
                    scope.launch {
                        settingsManager.saveTbmApiPort(p)
                        tbmRepository.checkHealth()
                    }
                },
            ) {
                Text(stringResource(R.string.settings_tbm_api_save_port_button))
            }
        }

        // Token Setting
        @Suppress("DEPRECATION")
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = inputTokenText,
                onValueChange = { inputTokenText = it },
                label = { Text(stringResource(R.string.settings_tbm_api_token_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                    if (clip.isNotEmpty()) {
                        inputTokenText = clip
                        scope.launch {
                            settingsManager.saveTbmApiToken(clip)
                            tbmRepository.checkHealth()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.settings_tbm_api_paste_token_button))
            }
            Button(
                onClick = rememberWrappedOnClick {
                    scope.launch {
                        settingsManager.saveTbmApiToken(inputTokenText.trim().ifEmpty { null })
                        tbmRepository.checkHealth()
                    }
                },
            ) {
                Text(stringResource(R.string.settings_tbm_api_save_token_button))
            }
            if (!savedToken.isNullOrEmpty()) {
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        scope.launch {
                            settingsManager.saveTbmApiToken(null)
                            inputTokenText = ""
                            tbmRepository.checkHealth()
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_tbm_api_clear_token_button))
                }
            }
        }
    }
}
