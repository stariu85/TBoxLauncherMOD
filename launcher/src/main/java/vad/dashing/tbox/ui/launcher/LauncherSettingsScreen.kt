package vad.dashing.tbox.ui.launcher

import android.app.Application
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import vad.dashing.tbox.update.InstallPermissionHelper
import vad.dashing.tbox.update.UpdateChannel
import vad.dashing.tbox.update.UpdateReleaseInfo
import vad.dashing.tbox.update.UpdateUiState
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.update.UpdateViewModelFactory
import vad.dashing.tbox.update.formatApkSizeMegabytes
import vad.dashing.tbox.update.formatDownloadEta
import vad.dashing.tbox.update.formatDownloadSpeed

/**
 * About + System Settings screen.
 */
@Composable
fun LauncherSettingsScreen(
    updateViewModel: UpdateViewModel,
    settingsManager: SettingsManager,
    onOpenInstallPermissionSettings: () -> Unit,
    onClose: () -> Unit,
) {
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
                text = stringResource(R.string.launcher_vs_section_system),
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

        LauncherSystemAndAboutSettingsContent(
            updateViewModel = updateViewModel,
            settingsManager = settingsManager,
            onOpenInstallPermissionSettings = onOpenInstallPermissionSettings,
        )
    }
}

@Composable
fun LauncherSystemAndAboutSettingsContent(
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager? = null,
    updateViewModel: UpdateViewModel? = null,
    onOpenInstallPermissionSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val manager = settingsManager ?: remember(context) { SettingsManager(context) }
    val vm: UpdateViewModel = updateViewModel ?: viewModel(
        factory = UpdateViewModelFactory(
            context.applicationContext as Application,
            manager,
        ),
    )
    val onOpenPermissionSettings: () -> Unit = onOpenInstallPermissionSettings ?: {
        launchSystemSettingsInFreeform(
            context,
            InstallPermissionHelper.createUnknownSourcesSettingsIntent(context),
        )
    }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val updateChannel by vm.updateChannel.collectAsStateWithLifecycle()
    val updateCheckEnabled by vm.updateCheckEnabled.collectAsStateWithLifecycle()
    val pendingUpdateInfo = vm.peekUpdateInfo()
    val activity = LocalActivity.current
    var canInstall by remember { mutableStateOf(vm.canInstallPackages()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = vm.canInstallPackages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.checkForUpdateOnStartupIfEnabled()
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                    manager.markHuRebootedAfterUpdate(currentVersionCode)
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

        var showRestoredRebootDialog by remember { mutableStateOf(false) }

        LauncherBackupSection(
            settingsManager = manager,
            onRestoreSuccess = {
                showRestoredRebootDialog = true
            },
        )

        if (showRestoredRebootDialog) {
            RestoredRebootDialog(
                onDismiss = { showRestoredRebootDialog = false },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherColors.SettingsBorder),
        )

        SystemWallpaperSection()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherColors.SettingsBorder),
        )

        // OTA Update section
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
                    vm.saveUpdateCheckEnabled(enabled)
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
                    vm.saveUpdateChannel(UpdateChannel.RELEASE)
                },
                label = {
                    Text(stringResource(R.string.update_channel_release))
                },
            )
            FilterChip(
                selected = updateChannel == UpdateChannel.DEVELOPMENT,
                onClick = rememberWrappedOnClick {
                    vm.saveUpdateChannel(UpdateChannel.DEVELOPMENT)
                },
                label = {
                    Text(stringResource(R.string.update_channel_development))
                },
            )
        }

        Button(
            onClick = rememberWrappedOnClick { vm.checkForUpdate(force = true) },
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
                    onClick = rememberWrappedOnClick { vm.downloadAndVerify() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_download),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            is UpdateUiState.WaitingForServer -> {
                UpdateReleaseDetails(state.info)
                CircularProgressIndicator(color = LauncherColors.AccentCyan)
                Text(
                    text = stringResource(R.string.update_waiting_for_server),
                    style = MaterialTheme.typography.tboxBody,
                    color = LauncherColors.TextPrimary,
                )
                Button(
                    onClick = rememberWrappedOnClick { vm.cancelDownload() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_cancel_download),
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
                    onClick = rememberWrappedOnClick { vm.cancelDownload() },
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
                        onClick = rememberWrappedOnClick(onOpenPermissionSettings),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_grant_install_permission),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                Button(
                    onClick = rememberWrappedOnClick { vm.installPreparedApk() },
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
                    onClick = rememberWrappedOnClick { vm.checkForUpdate(force = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_retry_check),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                if (state.cachedInfo != null) {
                    Button(
                        onClick = rememberWrappedOnClick { vm.downloadAndVerify() },
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
private fun resolveUpdateErrorMessage(raw: String): String = when {
    raw == "network_unavailable" ->
        "Ошибка сети: нет подключения к интернету на устройстве"

    raw == "Update source URL is not configured" ->
        "Ошибка конфигурации: URL источника обновлений не настроен"

    raw == "APK checksum mismatch" ->
        stringResource(R.string.update_error_verify_checksum)

    raw == "APK package name mismatch" ->
        stringResource(R.string.update_error_verify_package)

    raw == "APK signing certificate mismatch" ->
        stringResource(R.string.update_error_verify_signature)

    raw.startsWith("No update entry for flavor", ignoreCase = true) ->
        "На сервере нет данных для версии: ${raw.substringAfter("flavor ").trim()}"

    raw.contains("UnknownHostException", ignoreCase = true) ||
        raw.contains("Unable to resolve host", ignoreCase = true) ->
        "Ошибка подключения: не удалось разрешить имя сервера (${raw.substringAfter(": ").ifEmpty { raw }})"

    raw.contains("SocketTimeoutException", ignoreCase = true) ||
        raw.contains("timeout", ignoreCase = true) ->
        "Ошибка подключения: превышено время ожидания сервера ($raw)"

    raw.contains("ConnectException", ignoreCase = true) ||
        raw.contains("Failed to connect", ignoreCase = true) ->
        "Ошибка подключения: не удалось соединиться с сервером ($raw)"

    raw.contains("HTTP 404", ignoreCase = true) ->
        "Ошибка сервера: файл не найден (HTTP 404). Проверьте наличие version.json в Облаке Mail.ru"

    raw.contains("HTTP 403", ignoreCase = true) ->
        "Ошибка сервера: доступ к папке запрещён (HTTP 403)"

    raw.contains("HTTP", ignoreCase = true) ->
        "Ошибка сервера: $raw"

    else ->
        stringResource(R.string.update_error_generic, raw)
}

@Composable
private fun SystemWallpaperSection() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.launcher_system_wallpaper_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = LauncherColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.launcher_system_wallpaper_desc),
            style = MaterialTheme.typography.tboxBody,
            color = LauncherColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = rememberWrappedOnClick {
                    BlankWallpaperService.setSystemBlackWallpaper(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.launcher_system_wallpaper_set_button),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    BlankWallpaperService.openSystemWallpaperSettings(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.launcher_system_wallpaper_settings_button),
                    style = MaterialTheme.typography.tboxButton,
                    color = LauncherColors.AccentCyan,
                )
            }
        }
    }
}
