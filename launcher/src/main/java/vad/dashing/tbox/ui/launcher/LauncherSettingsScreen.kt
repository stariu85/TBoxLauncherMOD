package vad.dashing.tbox.ui.launcher

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.DonationLinks
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.ui.rememberWrappedOnClick
import vad.dashing.tbox.ui.requestHeadUnitReboot
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.update.UpdateChannel
import vad.dashing.tbox.update.UpdateReleaseInfo
import vad.dashing.tbox.update.UpdateUiState
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.update.formatApkSizeMegabytes
import vad.dashing.tbox.update.formatDownloadEta
import vad.dashing.tbox.update.formatDownloadSpeed

/**
 * About + OTA screen opened by the launcher «Настроить» button ([MainActivity]).
 */
@Composable
fun LauncherSettingsScreen(
    updateViewModel: UpdateViewModel,
    settingsManager: SettingsManager,
    onOpenInstallPermissionSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val uiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val updateChannel by updateViewModel.updateChannel.collectAsStateWithLifecycle()
    val updateCheckEnabled by updateViewModel.updateCheckEnabled.collectAsStateWithLifecycle()
    val pendingUpdateInfo = updateViewModel.peekUpdateInfo()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var canInstall by remember { mutableStateOf(updateViewModel.canInstallPackages()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = updateViewModel.canInstallPackages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdateOnStartupIfEnabled()
    }

    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val currentVersionName = remember { packageInfo.versionName.orEmpty() }
    val currentVersionCode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherColors.SettingsBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_standalone_about_title),
                style = MaterialTheme.typography.tboxHeadline,
                color = LauncherColors.TextPrimary,
            )
            OutlinedButton(onClick = rememberWrappedOnClick(onClose)) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        Text(
            text = stringResource(R.string.launcher_standalone_app_name),
            style = MaterialTheme.typography.tboxTitle,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = stringResource(
                R.string.update_current_version,
                currentVersionName,
                currentVersionCode,
            ),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )
        Text(
            text = context.packageName,
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextMuted,
        )
        Text(
            text = stringResource(
                R.string.launcher_standalone_about_body,
                BuildConfig.VERSION_NAME,
            ),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )

        val rebootAction = rememberHuRebootActionEnabled()
        val rebootScope = rememberCoroutineScope()
        LauncherHuRebootButton(
            enabled = rebootAction.enabled,
            onClick = {
                rebootAction.onUsed()
                rebootScope.launch {
                    settingsManager.markHuRebootedAfterUpdate(currentVersionCode)
                    requestHeadUnitReboot(context)
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherColors.SettingsBorder),
        )

        Text(
            text = stringResource(R.string.update_tab_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = LauncherColors.TextPrimary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_update_check_enabled_title),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_update_check_enabled_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextMuted,
                )
            }
            Switch(
                checked = updateCheckEnabled,
                onCheckedChange = { enabled ->
                    updateViewModel.saveUpdateCheckEnabled(enabled)
                },
                colors = launcherSwitchColors(),
            )
        }

        Text(
            text = stringResource(R.string.settings_update_channel_title),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.settings_update_channel_desc),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = updateChannel == UpdateChannel.RELEASE,
                onClick = rememberWrappedOnClick {
                    updateViewModel.saveUpdateChannel(UpdateChannel.RELEASE)
                },
                label = {
                    Text(stringResource(R.string.update_channel_release))
                },
            )
            FilterChip(
                selected = updateChannel == UpdateChannel.DEVELOPMENT,
                onClick = rememberWrappedOnClick {
                    updateViewModel.saveUpdateChannel(UpdateChannel.DEVELOPMENT)
                },
                label = {
                    Text(stringResource(R.string.update_channel_development))
                },
            )
        }

        Button(
            onClick = rememberWrappedOnClick { updateViewModel.checkForUpdate(force = true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.settings_update_check_button),
                style = MaterialTheme.typography.tboxButton,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherColors.SettingsBorder),
        )

        when (val state = uiState) {
            UpdateUiState.Idle -> {
                Text(
                    text = stringResource(
                        if (updateCheckEnabled) {
                            R.string.update_idle_hint
                        } else {
                            R.string.update_idle_hint_manual_only
                        },
                    ),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextSecondary,
                )
            }
            UpdateUiState.Checking -> {
                CircularProgressIndicator(color = LauncherColors.AccentCyan)
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextPrimary,
                )
            }
            UpdateUiState.UpToDate -> {
                Text(
                    text = stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.AccentCyan,
                )
            }
            is UpdateUiState.Available -> {
                UpdateReleaseDetails(state.info)
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.downloadAndVerify() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_download),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            is UpdateUiState.Downloading -> {
                pendingUpdateInfo?.let { UpdateReleaseDetails(it) }
                val progress = state.progress
                val percent = progress.percent
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.update_downloading, percent),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextPrimary,
                    )
                } else {
                    CircularProgressIndicator(color = LauncherColors.AccentCyan)
                    Text(
                        text = stringResource(R.string.update_downloading_unknown),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextPrimary,
                    )
                }
                progress.speedBytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                    Text(
                        text = stringResource(
                            R.string.update_download_speed,
                            formatDownloadSpeed(speed),
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextSecondary,
                    )
                }
                progress.remainingSeconds?.let { seconds ->
                    Text(
                        text = stringResource(
                            R.string.update_download_eta,
                            formatDownloadEta(seconds),
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextSecondary,
                    )
                }
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.cancelDownload() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_cancel_download),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            UpdateUiState.Verifying -> {
                pendingUpdateInfo?.let { UpdateReleaseDetails(it) }
                CircularProgressIndicator(color = LauncherColors.AccentCyan)
                Text(
                    text = stringResource(R.string.update_verifying),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextPrimary,
                )
            }
            is UpdateUiState.ReadyToInstall -> {
                UpdateReleaseDetails(state.info)
                if (!canInstall) {
                    Text(
                        text = stringResource(R.string.launcher_update_install_permission_hint),
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextSecondary,
                    )
                    Button(
                        onClick = rememberWrappedOnClick(onOpenInstallPermissionSettings),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_grant_install_permission),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.installPreparedApk() },
                    enabled = canInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_install),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            is UpdateUiState.Error -> {
                Text(
                    text = resolveUpdateErrorMessage(state.message),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.error,
                )
                state.cachedInfo?.let { UpdateReleaseDetails(it) }
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.checkForUpdate(force = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_retry_check),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                if (state.cachedInfo != null) {
                    Button(
                        onClick = rememberWrappedOnClick { updateViewModel.downloadAndVerify() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_download),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LauncherDonationsSection(
                entries = remember { DonationLinks.entries },
                modifier = Modifier.weight(1f),
            )
            LauncherDonationsSection(
                entries = remember { DonationLinks.feedbackEntries },
                modifier = Modifier.weight(1f),
                titleRes = R.string.launcher_feedback_title,
            )
        }
    }
}

@Composable
private fun UpdateReleaseDetails(info: UpdateReleaseInfo) {
    Text(
        text = stringResource(
            R.string.update_new_version,
            info.versionName,
            info.versionCode,
        ),
        style = MaterialTheme.typography.tboxTitle,
        color = LauncherColors.TextPrimary,
    )
    if (info.apkSizeBytes != null && info.apkSizeBytes > 0L) {
        Text(
            text = stringResource(
                R.string.update_apk_size_mb,
                formatApkSizeMegabytes(info.apkSizeBytes),
            ),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )
    }
    if (info.publishedAt.isNotBlank()) {
        Text(
            text = stringResource(R.string.update_published_at, info.publishedAt),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )
    }
    if (info.changelog.isNotBlank()) {
        Text(
            text = stringResource(R.string.update_changelog),
            style = MaterialTheme.typography.tboxTitle,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = info.changelog,
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )
    }
}

@Composable
private fun resolveUpdateErrorMessage(raw: String): String = when (raw) {
    "network_unavailable" -> stringResource(R.string.update_error_network)
    "Update source URL is not configured" -> stringResource(R.string.update_error_not_configured)
    "APK checksum mismatch" -> stringResource(R.string.update_error_verify_checksum)
    "APK package name mismatch" -> stringResource(R.string.update_error_verify_package)
    "APK signing certificate mismatch" -> stringResource(R.string.update_error_verify_signature)
    else -> stringResource(R.string.update_error_generic, raw)
}
