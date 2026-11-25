package vad.dashing.tbox.ui.launcher

import android.Manifest
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import vad.dashing.tbox.ui.clickableWithSound
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import vad.dashing.tbox.GearboxType
import vad.dashing.tbox.valueToString
import kotlin.math.roundToInt
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import kotlin.math.roundToInt

@Composable
fun ThemeSettingsContent() {
    val context = LocalContext.current
    val solarDaytime = rememberSolarDaytimeState()

    LaunchedEffect(solarDaytime, LauncherThemeState.autoThemeSolarEnabled) {
        if (LauncherThemeState.autoThemeSolarEnabled && solarDaytime != null) {
            LauncherThemeState.setDarkTheme(context, !solarDaytime)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_dark_theme),
            active = LauncherThemeState.darkTheme,
            onClick = { LauncherThemeState.setDarkTheme(context, !LauncherThemeState.darkTheme) },
            enabled = !LauncherThemeState.autoThemeSolarEnabled,
        )
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_auto_theme_system),
            active = LauncherThemeState.autoThemeSystemEnabled,
            onClick = {
                LauncherThemeState.setAutoThemeSystemEnabled(
                    context,
                    !LauncherThemeState.autoThemeSystemEnabled,
                )
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_auto_theme_system_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ -> }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_auto_theme_solar),
            active = LauncherThemeState.autoThemeSolarEnabled,
            onClick = {
                val next = !LauncherThemeState.autoThemeSolarEnabled
                if (next && !hasLocationPermission(context)) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
                LauncherThemeState.setAutoThemeSolarEnabled(context, next)
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_auto_theme_solar_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_theme_button_visible_title),
            active = LauncherThemeState.themeButtonVisible,
            onClick = {
                LauncherThemeState.setThemeButtonVisible(context, !LauncherThemeState.themeButtonVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_theme_button_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        AdasWeatherSettings()
        UiClickSoundsSettingToggle()
    }
}

@Composable
fun AdasWeatherSettings() {
    val context = LocalContext.current
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_winter_theme),
        active = LauncherThemeState.winterThemeEnabled,
        onClick = {
            LauncherThemeState.setWinterThemeEnabled(context, !LauncherThemeState.winterThemeEnabled)
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_winter_theme_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_winter_theme_temp),
        active = LauncherThemeState.winterThemeTempBelowZeroEnabled,
        onClick = {
            LauncherThemeState.setWinterThemeTempBelowZeroEnabled(context, !LauncherThemeState.winterThemeTempBelowZeroEnabled)
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_winter_theme_temp_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_rain_theme),
        active = LauncherThemeState.rainThemeEnabled,
        onClick = {
            LauncherThemeState.setRainThemeEnabled(context, !LauncherThemeState.rainThemeEnabled)
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_rain_theme_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    val isManualWeatherActive = LauncherThemeState.winterThemeEnabled ||
        LauncherThemeState.rainThemeEnabled ||
        LauncherThemeState.winterThemeTempBelowZeroEnabled
    val autoWeatherAvailable = !isManualWeatherActive

    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_auto_weather),
        active = LauncherThemeState.autoWeatherEnabled,
        enabled = autoWeatherAvailable,
        onClick = {
            LauncherThemeState.setAutoWeatherEnabled(context, !LauncherThemeState.autoWeatherEnabled)
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_auto_weather_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
}

@Composable
private fun UiClickSoundsSettingToggle() {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val uiClickSoundsEnabled by settingsManager.uiClickSoundsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_ui_click_sounds),
        active = uiClickSoundsEnabled,
        onClick = {
            scope.launch {
                settingsManager.saveUiClickSoundsEnabled(!uiClickSoundsEnabled)
            }
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_ui_click_sounds_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
}

@Composable
internal fun LauncherExpHomeMoveTaskToBackSettings() {
    val context = LocalContext.current
    val moveBackRevision by LauncherAppConfigStore.experimentalHomeMoveTaskToBackRevisionFlow
        .collectAsStateWithLifecycle()
    val isMoveBackEnabled = remember(context, moveBackRevision) {
        LauncherAppConfigStore.experimentalHomeMoveTaskToBack(context)
    }

    val windowingModeRevision by LauncherAppConfigStore.experimentalHomeWindowingModeRevisionFlow
        .collectAsStateWithLifecycle()
    val isWindowingModeEnabled = remember(context, windowingModeRevision) {
        LauncherAppConfigStore.experimentalHomeWindowingMode(context)
    }

    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_exp_home_move_task_to_back_title),
        active = isMoveBackEnabled,
        onClick = {
            val next = !isMoveBackEnabled
            LauncherAppConfigStore.setExperimentalHomeMoveTaskToBack(context, next)
            if (next) {
                startDclwmStorageWatcher(context.applicationContext)
            } else {
                stopDclwmStorageWatcherIfUnused(context.applicationContext)
            }
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_exp_home_move_task_to_back_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_exp_home_windowing_mode_title),
        active = isWindowingModeEnabled,
        onClick = {
            val next = !isWindowingModeEnabled
            LauncherAppConfigStore.setExperimentalHomeWindowingMode(context, next)
            if (next) {
                startDclwmStorageWatcher(context.applicationContext)
            } else {
                stopDclwmStorageWatcherIfUnused(context.applicationContext)
            }
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_exp_home_windowing_mode_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
}

@Composable
internal fun LauncherHomeSettingsContent() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val useNewSettingsViewRevision by LauncherAppConfigStore.useNewSettingsViewRevisionFlow
        .collectAsStateWithLifecycle()
    val isUseNewSettingsView = remember(context, useNewSettingsViewRevision) {
        LauncherAppConfigStore.useNewSettingsView(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_new_settings_view_title),
        active = isUseNewSettingsView,
        onClick = {
            LauncherAppConfigStore.setUseNewSettingsView(context, !isUseNewSettingsView)
        },
    )
    Text(
        text = stringResource(R.string.launcher_new_settings_view_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    val expHideAppsOnHomeRevision by LauncherAppConfigStore.experimentalHideAppsOnHomeRevisionFlow
        .collectAsStateWithLifecycle()
    val isExpHideAppsOnHome = remember(context, expHideAppsOnHomeRevision) {
        LauncherAppConfigStore.experimentalHideAppsOnHome(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_exp_hide_apps_on_home_title),
        active = isExpHideAppsOnHome,
        onClick = {
            val next = !isExpHideAppsOnHome
            LauncherAppConfigStore.setExperimentalHideAppsOnHome(context, next)
            if (next) {
                startDclwmStorageWatcher(context.applicationContext)
            } else {
                stopDclwmStorageWatcherIfUnused(context.applicationContext)
            }
        },
    )
    Text(
        text = stringResource(R.string.launcher_vs_exp_hide_apps_on_home_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    LauncherExpHomeMoveTaskToBackSettings()

    val miniPlayerRevision by LauncherAppConfigStore.mediaMiniPlayerRevisionFlow
        .collectAsStateWithLifecycle()
    val miniPlayerVisible = remember(context, miniPlayerRevision) {
        LauncherAppConfigStore.mediaMiniPlayerVisible(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_media_mini_player_title),
        active = miniPlayerVisible,
        onClick = {
            LauncherAppConfigStore.setMediaMiniPlayerVisible(context, !miniPlayerVisible)
        },
    )
    Text(
        text = stringResource(R.string.launcher_media_mini_player_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    val showPlayerNameRevision by LauncherAppConfigStore.showPlayerNameInWidgetRevisionFlow
        .collectAsStateWithLifecycle()
    val showPlayerName = remember(context, showPlayerNameRevision) {
        LauncherAppConfigStore.showPlayerNameInWidget(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_show_player_name_in_widget_title),
        active = showPlayerName,
        onClick = {
            LauncherAppConfigStore.setShowPlayerNameInWidget(context, !showPlayerName)
        },
    )
    Text(
        text = stringResource(R.string.launcher_show_player_name_in_widget_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )

    val disableCoverColorRevision by LauncherAppConfigStore.disableMediaCardCoverColorRevisionFlow
        .collectAsStateWithLifecycle()
    val disableCoverColor = remember(context, disableCoverColorRevision) {
        LauncherAppConfigStore.disableMediaCardCoverColor(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_disable_media_card_cover_color_title),
        active = disableCoverColor,
        onClick = {
            LauncherAppConfigStore.setDisableMediaCardCoverColor(context, !disableCoverColor)
        },
    )
    Text(
        text = stringResource(R.string.launcher_disable_media_card_cover_color_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
    MediaCardOpacitySlider()
    MediaCardHeightScaleSlider()
    CarModelScaleSlider()
    CarModelDriveScaleSlider()
    CameraSwitchModeSelector()
    TrunkLongPressToggle()
    AvmCameraActionSelector()
    PdcVisualModeSelector()
    SidebarWidthSlider()
    AdasPanelToggle()
    AdasCompassWidgetToggle()
    NavSpeedWidgetToggle()
    ClassicSpeedLimitWidgetToggle()
    SpeedLimitWarningSoundToggle()
    TsrSystemToggle()
    NavTrafficLightWidgetToggle()
    NavTrafficLightScaleSlider()
    AdasDistanceTextSizeSlider()
    NavButtonsToggle()
    FloatingHomeToggle()
    FloatingHomeSizeSlider()
    EdgeGesturesSettings()
    ClimateControlsToggle()
    ClimateCardBgToggle()
    ClimateTempColorIndicationToggle()
    ClimateControlsScaleSlider()
    DockSecondRowToggle()
    DockColumnsSlider()
    ResetBottomSlotsButton()
    TopBarHeightSlider()
    TopBarLeftIndicatorsToggle()
    IgnoreIdleFuelToggle()
    BottomBarHeightSlider()
    HiddenAppsSettings()
    LauncherSystemControlsContent()
}

@Composable
internal fun LauncherSystemControlsContent() {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val uiClickSoundsEnabled by settingsManager.uiClickSoundsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val clickSoundScope = rememberCoroutineScope()
    val systemSettings = rememberLauncherSystemSettings(context)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_ui_click_sounds),
            active = uiClickSoundsEnabled,
            onClick = {
                clickSoundScope.launch {
                    settingsManager.saveUiClickSoundsEnabled(!uiClickSoundsEnabled)
                }
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_ui_click_sounds_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_wifi) +
                (systemSettings.wifiSsid?.let { " · $it" } ?: ""),
            active = systemSettings.wifiEnabled,
            onClick = { systemSettings.applyWifiEnabled(!systemSettings.wifiEnabled) },
        )
        LauncherWifiApBlock()
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_bluetooth) +
                (systemSettings.bluetoothName?.let { " · $it" } ?: ""),
            active = systemSettings.bluetoothEnabled,
            onClick = { systemSettings.applyBluetoothEnabled(!systemSettings.bluetoothEnabled) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.launcher_vs_volume),
                color = LauncherColors.TextPrimary,
                fontSize = 14.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "−",
                    color = LauncherColors.AccentCyan,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { systemSettings.adjustMediaVolume(-1) },
                )
                Text(
                    "${systemSettings.mediaVolume}/${systemSettings.mediaVolumeMax}",
                    color = LauncherColors.TextSecondary,
                    fontSize = 14.sp,
                )
                Text(
                    "+",
                    color = LauncherColors.AccentCyan,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { systemSettings.adjustMediaVolume(1) },
                )
            }
        }
        Text(
            stringResource(R.string.launcher_vs_brightness),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
        )
        if (systemSettings.brightnessWritable) {
            Slider(
                value = systemSettings.brightnessNorm,
                onValueChange = { systemSettings.applyBrightnessNorm(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = LauncherColors.AccentCyan,
                    activeTrackColor = LauncherColors.AccentCyan,
                ),
            )
        } else {
            Text(
                stringResource(R.string.launcher_vs_brightness_locked),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun MediaCardOpacitySlider() {
    val context = LocalContext.current
    var alpha by remember {
        mutableFloatStateOf(LauncherAppConfigStore.mediaCardAlpha(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_media_card_opacity_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(alpha * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_media_card_opacity_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = alpha,
            onValueChange = { next ->
                alpha = next
                LauncherAppConfigStore.setMediaCardAlpha(context, next)
            },
            valueRange = MEDIA_CARD_ALPHA_MIN..MEDIA_CARD_ALPHA_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun MediaCardHeightScaleSlider() {
    val context = LocalContext.current
    var scale by remember {
        mutableFloatStateOf(LauncherAppConfigStore.mediaCardHeightScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_media_card_height_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scale * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_media_card_height_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                scale = next
                LauncherAppConfigStore.setMediaCardHeightScale(context, next)
            },
            valueRange = MEDIA_CARD_HEIGHT_SCALE_MIN..MEDIA_CARD_HEIGHT_SCALE_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun TopBarHeightSlider() {
    val context = LocalContext.current
    var heightDp by remember {
        mutableFloatStateOf(LauncherAppConfigStore.topBarHeightDp(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_top_bar_height_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${heightDp.roundToInt()} dp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_top_bar_height_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = heightDp,
            onValueChange = { next ->
                heightDp = next
                LauncherAppConfigStore.setTopBarHeightDp(context, next.roundToInt())
            },
            valueRange = TOP_BAR_HEIGHT_MIN.toFloat()..TOP_BAR_HEIGHT_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun TopBarLeftIndicatorsToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.topBarLeftIndicatorsRevisionFlow.collectAsStateWithLifecycle()
    var visible by remember(context, revision) {
        mutableStateOf(LauncherAppConfigStore.topBarLeftIndicatorsVisible(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_top_bar_left_indicators_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = visible,
                onCheckedChange = { enabled ->
                    visible = enabled
                    LauncherAppConfigStore.setTopBarLeftIndicatorsVisible(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_top_bar_left_indicators_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun IgnoreIdleFuelToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.ignoreIdleFuelRevisionFlow.collectAsStateWithLifecycle()
    var ignore by remember(context, revision) {
        mutableStateOf(LauncherAppConfigStore.ignoreIdleFuel(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_ignore_idle_fuel_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = ignore,
                onCheckedChange = { enabled ->
                    ignore = enabled
                    LauncherAppConfigStore.setIgnoreIdleFuel(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_ignore_idle_fuel_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BottomBarHeightSlider() {
    val context = LocalContext.current
    var heightDp by remember {
        mutableFloatStateOf(LauncherAppConfigStore.bottomBarHeightDp(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_bottom_bar_height_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${heightDp.roundToInt()} dp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_bottom_bar_height_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = heightDp,
            onValueChange = { next ->
                heightDp = next
                LauncherAppConfigStore.setBottomBarHeightDp(context, next.roundToInt())
            },
            valueRange = BOTTOM_BAR_HEIGHT_MIN.toFloat()..BOTTOM_BAR_HEIGHT_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun CarModelScaleSlider() {
    val context = LocalContext.current
    val scaleRevision by LauncherAppConfigStore.carModelScaleRevisionFlow.collectAsStateWithLifecycle()
    var scale by remember(context, scaleRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.carModelScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_car_model_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scale * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_car_model_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                scale = next
                LauncherAppConfigStore.setCarModelScale(context, next)
            },
            valueRange = CAR_MODEL_SCALE_MIN..CAR_MODEL_SCALE_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun CarModelDriveScaleSlider() {
    val context = LocalContext.current
    val driveScaleRevision by LauncherAppConfigStore.carModelDriveScaleRevisionFlow.collectAsStateWithLifecycle()
    var scale by remember(context, driveScaleRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.carModelDriveScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_car_model_drive_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scale * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_car_model_drive_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                val rounded = (next * 100f).roundToInt() / 100f
                scale = rounded
                LauncherAppConfigStore.setCarModelDriveScale(context, rounded)
            },
            valueRange = CAR_MODEL_DRIVE_SCALE_MIN..CAR_MODEL_DRIVE_SCALE_MAX,
            steps = 59,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
internal fun LauncherSettingsOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) LauncherColors.AccentCyan.copy(alpha = 0.22f)
                else LauncherColors.CardDarkElevated,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CameraSwitchModeSelector() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.cameraSwitchModeRevisionFlow
        .collectAsStateWithLifecycle()
    val mode = remember(context, revision) {
        LauncherAppConfigStore.cameraSwitchMode(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.launcher_camera_switch_mode_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_camera_switch_mode_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_camera_switch_mode_pas),
                selected = mode == CAMERA_SWITCH_MODE_PAS_AND_PARK,
                onClick = {
                    LauncherAppConfigStore.setCameraSwitchMode(
                        context,
                        CAMERA_SWITCH_MODE_PAS_AND_PARK,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_camera_switch_mode_drive),
                selected = mode == CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
                onClick = {
                    LauncherAppConfigStore.setCameraSwitchMode(
                        context,
                        CAMERA_SWITCH_MODE_DRIVE_AND_PARK,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AvmCameraActionSelector() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.avmCameraActionRevisionFlow
        .collectAsStateWithLifecycle()
    val action = remember(context, revision) {
        LauncherAppConfigStore.avmCameraAction(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.launcher_vs_avm_action_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_vs_avm_action_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_vs_avm_action_do_nothing),
                selected = action == AVM_CAMERA_ACTION_DO_NOTHING,
                onClick = {
                    LauncherAppConfigStore.setAvmCameraAction(
                        context,
                        AVM_CAMERA_ACTION_DO_NOTHING,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_vs_avm_action_close),
                selected = action == AVM_CAMERA_ACTION_CLOSE_APPS,
                onClick = {
                    LauncherAppConfigStore.setAvmCameraAction(
                        context,
                        AVM_CAMERA_ACTION_CLOSE_APPS,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_vs_avm_action_minimize),
                selected = action == AVM_CAMERA_ACTION_MINIMIZE_APPS,
                onClick = {
                    LauncherAppConfigStore.setAvmCameraAction(
                        context,
                        AVM_CAMERA_ACTION_MINIMIZE_APPS,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PdcVisualModeSelector() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.pdcVisualModeRevisionFlow
        .collectAsStateWithLifecycle()
    val mode = remember(context, revision) {
        LauncherAppConfigStore.pdcVisualMode(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.launcher_pdc_visual_mode_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_pdc_visual_mode_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_pdc_visual_mode_arcs),
                selected = mode == PDC_VISUAL_ARCS,
                onClick = {
                    LauncherAppConfigStore.setPdcVisualMode(context, PDC_VISUAL_ARCS)
                },
                modifier = Modifier.weight(1f),
            )
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_pdc_visual_mode_classic),
                selected = mode == PDC_VISUAL_CLASSIC,
                onClick = {
                    LauncherAppConfigStore.setPdcVisualMode(context, PDC_VISUAL_CLASSIC)
                },
                modifier = Modifier.weight(1f),
            )
            LauncherSettingsOptionButton(
                text = stringResource(R.string.launcher_pdc_visual_mode_off),
                selected = mode == PDC_VISUAL_OFF,
                onClick = {
                    LauncherAppConfigStore.setPdcVisualMode(context, PDC_VISUAL_OFF)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SidebarWidthSlider() {
    val context = LocalContext.current
    var widthDp by remember {
        mutableFloatStateOf(LauncherAppConfigStore.sidebarWidthDp(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_sidebar_width_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${widthDp.roundToInt()} dp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_sidebar_width_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = widthDp,
            onValueChange = { next ->
                widthDp = next
                LauncherAppConfigStore.setSidebarWidthDp(context, next.roundToInt())
            },
            valueRange = SIDEBAR_WIDTH_MIN.toFloat()..SIDEBAR_WIDTH_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun NavButtonsToggle() {
    val context = LocalContext.current
    val navButtonsRevision by LauncherAppConfigStore.navButtonsRevisionFlow.collectAsStateWithLifecycle()
    var navButtonsVisible by remember(context, navButtonsRevision) {
        mutableStateOf(LauncherAppConfigStore.navButtonsVisible(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_nav_buttons_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = navButtonsVisible,
                onCheckedChange = { enabled ->
                    navButtonsVisible = enabled
                    LauncherAppConfigStore.setNavButtonsVisible(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_nav_buttons_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClimateControlsToggle() {
    val context = LocalContext.current
    val climateRevision by LauncherAppConfigStore.climateControlsRevisionFlow.collectAsStateWithLifecycle()
    var climateVisible by remember(context, climateRevision) {
        mutableStateOf(LauncherAppConfigStore.climateControlsVisible(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_climate_visible_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = climateVisible,
                onCheckedChange = { enabled ->
                    climateVisible = enabled
                    LauncherAppConfigStore.setClimateControlsVisible(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_climate_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClimateControlsScaleSlider() {
    val context = LocalContext.current
    val climateRevision by LauncherAppConfigStore.climateControlsRevisionFlow.collectAsStateWithLifecycle()
    var scale by remember(context, climateRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.climateControlsScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_climate_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scale * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_climate_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                scale = next
                LauncherAppConfigStore.setClimateControlsScale(context, next)
            },
            valueRange = CLIMATE_SCALE_MIN..CLIMATE_SCALE_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun FloatingHomeToggle() {
    val context = LocalContext.current
    val sizeRevision by LauncherAppConfigStore.floatingHomeVisibleRevisionFlow.collectAsStateWithLifecycle()
    var homeVisible by remember(context, sizeRevision) {
        mutableStateOf(LauncherAppConfigStore.floatingHomeVisible(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_floating_home_visible_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = homeVisible,
                onCheckedChange = { enabled ->
                    homeVisible = enabled
                    LauncherAppConfigStore.setFloatingHomeVisible(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_floating_home_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClimateCardBgToggle() {
    val context = LocalContext.current
    val climateCardBgRevision by LauncherAppConfigStore.climateCardBgRevisionFlow.collectAsStateWithLifecycle()
    var climateCardBgVisible by remember(context, climateCardBgRevision) {
        mutableStateOf(LauncherAppConfigStore.climateCardBgVisible(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_climate_card_bg_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = climateCardBgVisible,
                onCheckedChange = { enabled ->
                    climateCardBgVisible = enabled
                    LauncherAppConfigStore.setClimateCardBgVisible(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_climate_card_bg_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClimateTempColorIndicationToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.climateTempColorIndicationRevisionFlow.collectAsStateWithLifecycle()
    var enabledState by remember(context, revision) {
        mutableStateOf(LauncherAppConfigStore.climateTempColorIndication(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_climate_temp_color_indication_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = enabledState,
                onCheckedChange = { enabled ->
                    enabledState = enabled
                    LauncherAppConfigStore.setClimateTempColorIndication(context, enabled)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_climate_temp_color_indication_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FloatingHomeSizeSlider() {
    val context = LocalContext.current
    val sizeRevision by LauncherAppConfigStore.floatingHomeSizeRevisionFlow.collectAsStateWithLifecycle()
    var sizeDp by remember(context, sizeRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.floatingHomeSizeDp(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_floating_home_size_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${sizeDp.roundToInt()} dp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_floating_home_size_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = sizeDp,
            onValueChange = { next ->
                sizeDp = next
                LauncherAppConfigStore.setFloatingHomeSizeDp(context, next.roundToInt())
            },
            valueRange = FLOATING_HOME_SIZE_MIN.toFloat()..FLOATING_HOME_SIZE_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun EdgeGesturesSettings() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.edgeGesturesRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) { LauncherAppConfigStore.edgeGesturesEnabled(context) }
    var widthDp by remember(context, revision) { mutableFloatStateOf(LauncherAppConfigStore.edgeGesturesWidthDp(context).toFloat()) }
    val leftAction = remember(context, revision) { LauncherAppConfigStore.edgeGestureActionLeft(context) }
    val rightAction = remember(context, revision) { LauncherAppConfigStore.edgeGestureActionRight(context) }
    val bottomAction = remember(context, revision) { LauncherAppConfigStore.edgeGestureActionBottom(context) }
    val topAction = remember(context, revision) { LauncherAppConfigStore.edgeGestureActionTop(context) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.launcher_edge_gestures_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.launcher_edge_gestures_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_edge_gestures_enabled),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = enabled,
                onCheckedChange = { next ->
                    LauncherAppConfigStore.setEdgeGesturesEnabled(context, next)
                    if (next) {
                        LauncherEdgeGestureOverlay.ensureShowing(context)
                    } else {
                        LauncherEdgeGestureOverlay.hide()
                    }
                },
                colors = launcherSwitchColors(),
            )
        }

        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.launcher_edge_gestures_width),
                        color = LauncherColors.TextPrimary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "${widthDp.roundToInt()} dp",
                        color = LauncherColors.TextSecondary,
                        fontSize = 16.sp,
                    )
                }
                Slider(
                    value = widthDp,
                    onValueChange = { widthDp = it },
                    onValueChangeFinished = {
                        LauncherAppConfigStore.setEdgeGesturesWidthDp(context, widthDp.roundToInt())
                    },
                    valueRange = 5f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                        inactiveTrackColor = LauncherColors.TextMuted,
                    ),
                )
            }

            EdgeGestureActionPicker(
                title = "Жест сверху (Свайп вниз)",
                currentAction = topAction,
                onSelectAction = { LauncherAppConfigStore.setEdgeGestureActionTop(context, it) },
            )

            EdgeGestureActionPicker(
                title = stringResource(R.string.launcher_edge_gesture_left),
                currentAction = leftAction,
                onSelectAction = { LauncherAppConfigStore.setEdgeGestureActionLeft(context, it) },
            )

            EdgeGestureActionPicker(
                title = stringResource(R.string.launcher_edge_gesture_right),
                currentAction = rightAction,
                onSelectAction = { LauncherAppConfigStore.setEdgeGestureActionRight(context, it) },
            )

            EdgeGestureActionPicker(
                title = stringResource(R.string.launcher_edge_gesture_bottom),
                currentAction = bottomAction,
                onSelectAction = { LauncherAppConfigStore.setEdgeGestureActionBottom(context, it) },
            )
        }
    }
}

@Composable
private fun EdgeGestureActionPicker(
    title: String,
    currentAction: String,
    onSelectAction: (String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val labelNone = stringResource(R.string.launcher_edge_action_none)
    val labelHome = stringResource(R.string.launcher_edge_action_home)
    val labelBack = stringResource(R.string.launcher_edge_action_back)
    val labelUndo = stringResource(R.string.launcher_edge_action_previous_app)
    val labelClose = stringResource(R.string.launcher_edge_action_close_all)

    val builtInActions = remember(labelNone, labelHome, labelBack, labelUndo, labelClose) {
        listOf(
            EDGE_GESTURE_ACTION_SHADE to "Панель управления (Шторка)",
            EDGE_GESTURE_ACTION_NONE to labelNone,
            EDGE_GESTURE_ACTION_HOME to labelHome,
            EDGE_GESTURE_ACTION_BACK to labelBack,
            EDGE_GESTURE_ACTION_PREVIOUS_APP to labelUndo,
            EDGE_GESTURE_ACTION_CLOSE_ALL to labelClose,
        )
    }

    val desktopItems = remember(context) { LauncherHomeStore.loadItems(context) }
    val desktopActions = remember(context, desktopItems) {
        val pm = context.packageManager
        desktopItems.mapNotNull { item ->
            val key = "shortcut:${item.key}"
            val label = when (item) {
                is LauncherHomeItem.App -> {
                    runCatching {
                        val info = pm.getApplicationInfo(item.packageName, 0)
                        pm.getApplicationLabel(info).toString()
                    }.getOrDefault(item.packageName)
                }
                is LauncherHomeItem.Split -> {
                    val preset = LauncherSplitPresetStore.loadPresets(context).firstOrNull { it.id == item.presetId }
                    preset?.name?.ifBlank { null } ?: "Сплит: ${item.presetId}"
                }
                is LauncherHomeItem.TbmAutomation -> {
                    item.automationName.ifBlank { "TBM: ${item.automationId}" }
                }
            }
            key to "Ярлык: $label"
        }
    }

    val allOptions = remember(builtInActions, desktopActions) { builtInActions + desktopActions }
    val currentLabel = remember(currentAction, allOptions, labelNone) {
        allOptions.firstOrNull { it.first == currentAction || currentAction.startsWith(it.first) }?.second
            ?: labelNone
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = LauncherColors.TextPrimary, fontSize = 14.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            LauncherSettingsOptionButton(
                text = currentLabel,
                selected = true,
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(LauncherColors.CardDark),
            ) {
                allOptions.forEach { (actionKey, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = LauncherColors.TextPrimary) },
                        onClick = {
                            expanded = false
                            onSelectAction(actionKey)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockSecondRowToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.dockSecondRowRevisionFlow.collectAsStateWithLifecycle()
    var enabled by remember(context, revision) {
        mutableStateOf(LauncherAppConfigStore.dockSecondRowEnabled(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_dock_second_row_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Switch(
                checked = enabled,
                onCheckedChange = { active ->
                    enabled = active
                    LauncherAppConfigStore.setDockSecondRowEnabled(context, active)
                },
                colors = launcherSwitchColors(),
            )
        }
        Text(
            text = stringResource(R.string.launcher_dock_second_row_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DockColumnsSlider() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.dockColumnsRevisionFlow.collectAsStateWithLifecycle()
    var columns by remember(context, revision) {
        mutableFloatStateOf(LauncherAppConfigStore.dockColumns(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_dock_columns_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${columns.roundToInt()}",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_dock_columns_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = columns,
            onValueChange = { next ->
                columns = next
                LauncherAppConfigStore.setDockColumns(context, next.roundToInt())
            },
            valueRange = DOCK_COLUMNS_MIN.toFloat()..DOCK_COLUMNS_MAX.toFloat(),
            steps = DOCK_COLUMNS_MAX - DOCK_COLUMNS_MIN - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun ResetBottomSlotsButton() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.launcher_reset_bottom_slots_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_reset_bottom_slots_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        TextButton(
            onClick = {
                LauncherAppConfigStore.resetBottomSlotsToDefault(context)
            },
        ) {
            Text(
                text = stringResource(R.string.launcher_reset_bottom_slots_button),
                color = LauncherColors.AccentCyan,
            )
        }
    }
}

@Composable
private fun AdasDistanceTextSizeSlider() {
    val context = LocalContext.current
    var sizeSp by remember {
        mutableFloatStateOf(LauncherAppConfigStore.adasDistanceTextSize(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_distance_text_size_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${sizeSp.roundToInt()} sp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_adas_distance_text_size_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = sizeSp,
            onValueChange = { next ->
                sizeSp = next
                LauncherAppConfigStore.setAdasDistanceTextSize(context, next.roundToInt())
            },
            valueRange = ADAS_DISTANCE_TEXT_SIZE_MIN.toFloat()..ADAS_DISTANCE_TEXT_SIZE_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun AdasIconScaleSlider() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasIconScaleRevisionFlow.collectAsStateWithLifecycle()
    var scalePercent by remember(context, revision) {
        mutableFloatStateOf(LauncherAppConfigStore.adasIconScale(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_icon_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${scalePercent.roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_adas_icon_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scalePercent,
            onValueChange = { next ->
                val snapped = ((next / 10f).roundToInt() * 10f).coerceIn(
                    ADAS_ICON_SCALE_MIN.toFloat(),
                    ADAS_ICON_SCALE_MAX.toFloat()
                )
                scalePercent = snapped
                LauncherAppConfigStore.setAdasIconScale(context, snapped.roundToInt())
            },
            valueRange = ADAS_ICON_SCALE_MIN.toFloat()..ADAS_ICON_SCALE_MAX.toFloat(),
            steps = ((ADAS_ICON_SCALE_MAX - ADAS_ICON_SCALE_MIN) / 10) - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun AdasAccBySpeedLimitToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasAccBySpeedLimitRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.adasAccBySpeedLimitEnabled(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_adas_acc_by_speed_limit_title),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setAdasAccBySpeedLimitEnabled(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_adas_acc_by_speed_limit_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AdasAccSpeedOffsetSlider() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasAccSpeedOffsetRevisionFlow.collectAsStateWithLifecycle()
    var offsetKm by remember(context, revision) {
        mutableFloatStateOf(LauncherAppConfigStore.adasAccSpeedOffset(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_acc_speed_offset_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "+${offsetKm.roundToInt()} km/h",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_adas_acc_speed_offset_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = offsetKm,
            onValueChange = { next ->
                val snapped = next.roundToInt().coerceIn(ADAS_ACC_SPEED_OFFSET_MIN, ADAS_ACC_SPEED_OFFSET_MAX)
                offsetKm = snapped.toFloat()
                LauncherAppConfigStore.setAdasAccSpeedOffset(context, snapped)
            },
            valueRange = ADAS_ACC_SPEED_OFFSET_MIN.toFloat()..ADAS_ACC_SPEED_OFFSET_MAX.toFloat(),
            steps = ADAS_ACC_SPEED_OFFSET_MAX - ADAS_ACC_SPEED_OFFSET_MIN - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}



@Composable
private fun AdasPanelToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasPanelVisibleRevisionFlow
        .collectAsStateWithLifecycle()
    val visible = remember(context, revision) {
        LauncherAppConfigStore.adasPanelVisible(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_adas_panel_title),
            active = visible,
            onClick = {
                LauncherAppConfigStore.setAdasPanelVisible(context, !visible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_adas_panel_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AdasCompassWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasCompassWidgetRevisionFlow
        .collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.adasCompassWidgetEnabled(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_compass_widget),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setAdasCompassWidgetEnabled(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_adas_compass_widget_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}



@Composable
private fun NavSpeedWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navSpeedRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navSpeedVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_speed_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavSpeedVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_speed_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClassicSpeedLimitWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.classicSpeedLimitRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.classicSpeedLimitVisible(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_classic_speed_limit_title),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setClassicSpeedLimitVisible(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_classic_speed_limit_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SpeedLimitWarningSoundToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.speedLimitWarningSoundRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.speedLimitWarningSoundEnabled(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_speed_limit_warning_sound_title),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setSpeedLimitWarningSoundEnabled(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_speed_limit_warning_sound_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TsrSystemToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.tsrSystemRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.tsrSystemEnabled(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_tsr_system_title),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setTsrSystemEnabled(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_tsr_system_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavTrafficLightWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navTrafficLightRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navTrafficLightVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_traffic_light_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavTrafficLightVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_traffic_light_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavTrafficLightScaleSlider() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navTrafficLightScaleRevisionFlow.collectAsStateWithLifecycle()
    var scaleRatio by remember(context, revision) {
        mutableFloatStateOf(LauncherAppConfigStore.navTrafficLightScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_nav_traffic_light_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scaleRatio * 100).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_nav_traffic_light_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scaleRatio,
            onValueChange = { next ->
                val snapped = ((next * 20f).roundToInt() / 20f).coerceIn(0.5f, 1.5f)
                scaleRatio = snapped
                LauncherAppConfigStore.setNavTrafficLightScale(context, snapped)
            },
            valueRange = 0.5f..1.5f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}







@Composable
fun CruiseControlSettingsContent() {
    val context = LocalContext.current
    val cruiseTypeRevision by LauncherAppConfigStore.cruiseTypeRevisionFlow
        .collectAsStateWithLifecycle()
    val isConventional = remember(context, cruiseTypeRevision) {
        LauncherAppConfigStore.isConventionalCruise(context)
    }

    val cruiseEngageActionRevision by LauncherAppConfigStore.cruiseEngageActionRevisionFlow
        .collectAsStateWithLifecycle()
    val cruiseEngageAction = remember(context, cruiseEngageActionRevision) {
        LauncherAppConfigStore.cruiseEngageAction(context)
    }

    val carModelVisibleRevision by LauncherAppConfigStore.carModelVisibleRevisionFlow
        .collectAsStateWithLifecycle()
    val carModelVisible = remember(context, carModelVisibleRevision) {
        LauncherAppConfigStore.carModelVisible(context)
    }

    val backflipAnimationRevision by LauncherAppConfigStore.backflipAnimationRevisionFlow
        .collectAsStateWithLifecycle()
    val backflipAnimationEnabled = remember(context, backflipAnimationRevision) {
        LauncherAppConfigStore.backflipAnimationEnabled(context)
    }

    val gearboxTypeRevision by LauncherAppConfigStore.gearboxTypeRevisionFlow
        .collectAsStateWithLifecycle()
    val gearboxType = remember(context, gearboxTypeRevision) {
        LauncherAppConfigStore.gearboxType(context)
    }

    val driveModeGlowRevision by LauncherAppConfigStore.driveModeGlowRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeGlowVisible = remember(context, driveModeGlowRevision) {
        LauncherAppConfigStore.driveModeGlowVisible(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.launcher_cruise_type_title),
                color = LauncherColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                text = stringResource(R.string.launcher_cruise_type_desc),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LauncherSettingsOptionButton(
                    text = stringResource(R.string.launcher_cruise_type_adaptive),
                    selected = !isConventional,
                    onClick = {
                        LauncherAppConfigStore.setCruiseType(context, CRUISE_TYPE_ADAPTIVE)
                    },
                    modifier = Modifier.weight(1f),
                )
                LauncherSettingsOptionButton(
                    text = stringResource(R.string.launcher_cruise_type_conventional),
                    selected = isConventional,
                    onClick = {
                        LauncherAppConfigStore.setCruiseType(context, CRUISE_TYPE_CONVENTIONAL)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.launcher_cruise_engage_action_title),
                color = LauncherColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                text = stringResource(R.string.launcher_cruise_engage_action_desc),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LauncherSettingsOptionButton(
                    text = stringResource(R.string.launcher_cruise_engage_action_none),
                    selected = cruiseEngageAction == CRUISE_ENGAGE_ACTION_NONE,
                    onClick = {
                        LauncherAppConfigStore.setCruiseEngageAction(context, CRUISE_ENGAGE_ACTION_NONE)
                    },
                    modifier = Modifier.weight(1f),
                )
                LauncherSettingsOptionButton(
                    text = stringResource(R.string.launcher_cruise_engage_action_presets),
                    selected = cruiseEngageAction == CRUISE_ENGAGE_ACTION_PRESETS,
                    onClick = {
                        LauncherAppConfigStore.setCruiseEngageAction(context, CRUISE_ENGAGE_ACTION_PRESETS)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!isConventional) {
                    LauncherSettingsOptionButton(
                        text = stringResource(R.string.launcher_cruise_engage_action_smart_acc),
                        selected = cruiseEngageAction == CRUISE_ENGAGE_ACTION_SMART_ACC,
                        onClick = {
                            LauncherAppConfigStore.setCruiseEngageAction(context, CRUISE_ENGAGE_ACTION_SMART_ACC)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_car_model_visible_title),
            active = carModelVisible,
            onClick = {
                LauncherAppConfigStore.setCarModelVisible(context, !carModelVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_car_model_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_backflip_animation_title),
            active = backflipAnimationEnabled,
            onClick = {
                LauncherAppConfigStore.setBackflipAnimationEnabled(context, !backflipAnimationEnabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_backflip_animation_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.launcher_gearbox_type_title),
                color = LauncherColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                text = stringResource(R.string.launcher_gearbox_type_desc),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LauncherSettingsOptionButton(
                    text = "7DCT",
                    selected = gearboxType == GearboxType.DCT_7,
                    onClick = {
                        LauncherAppConfigStore.setGearboxType(context, GearboxType.DCT_7)
                    },
                    modifier = Modifier.weight(1f),
                )
                LauncherSettingsOptionButton(
                    text = "6DCT",
                    selected = gearboxType == GearboxType.DCT_6,
                    onClick = {
                        LauncherAppConfigStore.setGearboxType(context, GearboxType.DCT_6)
                    },
                    modifier = Modifier.weight(1f),
                )
                LauncherSettingsOptionButton(
                    text = "Manual",
                    selected = gearboxType == GearboxType.MANUAL,
                    onClick = {
                        LauncherAppConfigStore.setGearboxType(context, GearboxType.MANUAL)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AdasPanelsSelectorSection()

        val launcherSettingsOnDoubleTapRevision by LauncherAppConfigStore.launcherSettingsOnDoubleTapRevisionFlow
            .collectAsStateWithLifecycle()
        val launcherSettingsOnDoubleTap = remember(context, launcherSettingsOnDoubleTapRevision) {
            LauncherAppConfigStore.launcherSettingsOnDoubleTap(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_settings_on_double_tap_title),
            active = launcherSettingsOnDoubleTap,
            onClick = {
                LauncherAppConfigStore.setLauncherSettingsOnDoubleTap(context, !launcherSettingsOnDoubleTap)
            },
        )
        Text(
            text = stringResource(R.string.launcher_settings_on_double_tap_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        AdasCompassWidgetToggle()

        val accPresetsInQuickSettingsRevision by LauncherAppConfigStore.accPresetsInQuickSettingsRevisionFlow
            .collectAsStateWithLifecycle()
        val accPresetsInQuickSettingsVisible = remember(context, accPresetsInQuickSettingsRevision) {
            LauncherAppConfigStore.accPresetsInQuickSettingsVisible(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_acc_presets_in_quick_settings_visible_title),
            active = accPresetsInQuickSettingsVisible,
            onClick = {
                LauncherAppConfigStore.setAccPresetsInQuickSettingsVisible(
                    context,
                    !accPresetsInQuickSettingsVisible,
                )
            },
        )
        Text(
            text = stringResource(R.string.launcher_acc_presets_in_quick_settings_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        val smartPanelRevision by LauncherAppConfigStore.smartPanelRevisionFlow
            .collectAsStateWithLifecycle()
        val smartPanelEnabled = remember(context, smartPanelRevision) {
            LauncherAppConfigStore.smartPanelEnabled(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_smart_panel_title),
            active = smartPanelEnabled,
            onClick = {
                LauncherAppConfigStore.setSmartPanelEnabled(
                    context,
                    !smartPanelEnabled,
                )
            },
        )
        Text(
            text = stringResource(R.string.launcher_smart_panel_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LauncherColors.LeftPanelCard)
                .clickable {
                    LauncherAppConfigStore.resetAdasPanelsLayout(context)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.launcher_reset_adas_panels_layout_title),
                color = LauncherColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_drive_mode_glow_visible_title),
            active = driveModeGlowVisible,
            onClick = {
                LauncherAppConfigStore.setDriveModeGlowVisible(context, !driveModeGlowVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_drive_mode_glow_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        val headlightBeamsRevision by LauncherAppConfigStore.headlightBeamsRevisionFlow
            .collectAsStateWithLifecycle()
        val headlightBeamsMode = remember(context, headlightBeamsRevision) {
            LauncherAppConfigStore.headlightBeamsMode(context)
        }
        val modeLabel = when (headlightBeamsMode) {
            HEADLIGHT_BEAMS_OFF -> stringResource(R.string.launcher_headlight_beams_mode_off)
            HEADLIGHT_BEAMS_DARK_THEME -> stringResource(R.string.launcher_headlight_beams_mode_dark_theme)
            HEADLIGHT_BEAMS_NIGHT_ONLY -> stringResource(R.string.launcher_headlight_beams_mode_night_only)
            else -> stringResource(R.string.launcher_headlight_beams_mode_off)
        }

        LauncherSettingsValueRow(
            label = stringResource(R.string.launcher_headlight_beams_visible_title),
            value = modeLabel,
            onClick = {
                val nextMode = (headlightBeamsMode + 1) % 3
                LauncherAppConfigStore.setHeadlightBeamsMode(context, nextMode)
            },
        )
        Text(
            text = stringResource(R.string.launcher_headlight_beams_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        AdasWeatherSettings()

        ClassicSpeedLimitWidgetToggle()
        SpeedLimitWarningSoundToggle()
        AdasAccBySpeedLimitToggle()
        AdasAccSpeedOffsetSlider()

        CruisePresetsSettings()
    }
}

private data class AdasPanelSelectorItem(
    val id: String,
    val title: String,
    val isComposite: Boolean,
    val content: @Composable () -> Unit,
)

@Composable
private fun MiniButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = if (LauncherThemeState.darkTheme) Color.White else LauncherColors.TextPrimary,
    bgColor: Color = if (LauncherThemeState.darkTheme) Color(0xFF263238) else Color(0xFFE2E8F0),
    fontSize: TextUnit = 8.sp,
    borderColor: Color = Color.Transparent,
) {
    val resolvedBorder = if (borderColor != Color.Transparent) {
        borderColor
    } else if (!LauncherThemeState.darkTheme) {
        Color(0xFFCBD5E1)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .then(
                if (resolvedBorder != Color.Transparent) {
                    Modifier.border(0.8.dp, resolvedBorder, RoundedCornerShape(3.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MiniatureAccPresets() {
    val green = if (LauncherThemeState.darkTheme) Color(0xFF10B981) else Color(0xFF059669)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("ACC", textColor = green, borderColor = green, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        MiniButton("60", fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        MiniButton("80", fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        MiniButton("100", fontSize = 7.sp, modifier = Modifier.weight(1f))
        MiniButton("120", fontSize = 7.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureNgpLkaLdw() {
    val green = if (LauncherThemeState.darkTheme) Color(0xFF10B981) else Color(0xFF059669)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("NGP", textColor = green, borderColor = green, fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("LKA", textColor = green, borderColor = green, fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("LDW", textColor = green, borderColor = green, fontSize = 8.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureLanes() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("LDW", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("LKA", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("FCW", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureRadars() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("BSD", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("DOW", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
        MiniButton("RAD", fontSize = 8.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureChassis() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("ESC", fontSize = 8.sp, modifier = Modifier.weight(1f))
        MiniButton("HDC", fontSize = 8.sp, modifier = Modifier.weight(1f))
        MiniButton("HOLD", fontSize = 7.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureLights() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("AUTO", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        MiniButton("LOW", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        MiniButton("POS", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        MiniButton("OFF", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        MiniButton("FOG", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureWipers() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("Сервис", fontSize = 8.sp, modifier = Modifier.weight(1f))
        MiniButton("Авто", fontSize = 8.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniatureCarColor() {
    val bg = if (LauncherThemeState.darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val border = if (LauncherThemeState.darkTheme) Color.Transparent else Color(0xFFCBD5E1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .then(
                if (border != Color.Transparent) {
                    Modifier.border(0.8.dp, border, RoundedCornerShape(3.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = listOf(Color.White, Color.Gray, Color.DarkGray, Color(0xFF1E3A8A), Color(0xFFB91C1C), Color(0xFF047857))
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(0.5.dp, if (c == Color.White) Color.LightGray else Color.Transparent, CircleShape),
            )
        }
    }
}

@Composable
private fun AdasAssistCompositeMiniature() {
    val green = if (LauncherThemeState.darkTheme) Color(0xFF10B981) else Color(0xFF059669)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("ACC", textColor = green, borderColor = green, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
            MiniButton("NGP", textColor = green, borderColor = green, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("60", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("80", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("100", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("120", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("DIST", fontSize = 7.sp, modifier = Modifier.weight(1f))
            MiniButton("Умный", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdasDriveModeCompositeMiniature() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("ECO", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("COMF", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("SPORT", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
            MiniButton("SP+", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("Снег", fontSize = 6.sp, modifier = Modifier.weight(1f))
            MiniButton("Грязь", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
            MiniButton("Песок", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
            MiniButton("Бездор", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdasBodyControlsCompositeMiniature() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("DRV1", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("DRV2", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("DRV3", fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("Багаж", fontSize = 6.sp, modifier = Modifier.weight(1f))
            MiniButton("Окна", fontSize = 6.sp, modifier = Modifier.weight(1f))
            MiniButton("Зерк", fontSize = 6.sp, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("Зеркала", fontSize = 6.sp, modifier = Modifier.weight(1f))
            MiniButton("Пасс", fontSize = 6.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdasCamdControlsCompositeMiniature() {
    val red = Color(0xFFEF4444)
    val blue = Color(0xFF3B82F6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("CAM ◄►", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
            MiniButton("CAM ▲▼", fontSize = 5.5.sp, modifier = Modifier.weight(1f))
            MiniButton("CAM 360", textColor = blue, borderColor = blue, fontSize = 5.5.sp, modifier = Modifier.weight(1.1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton("● REC", textColor = red, borderColor = red, fontSize = 6.5.sp, modifier = Modifier.weight(1f))
            MiniButton("SOS", textColor = red, fontSize = 6.5.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdasPanelsSelectorSection() {
    val context = LocalContext.current
    val enabledPanelsRevision by LauncherAppConfigStore.enabledAdasPanelsRevisionFlow.collectAsStateWithLifecycle()
    val enabledPanels = remember(context, enabledPanelsRevision) {
        LauncherAppConfigStore.enabledAdasPanels(context)
    }

    val panelItems = remember {
        listOf(
            // Row 1
            AdasPanelSelectorItem(
                id = "assist",
                title = "ACC + NGP + Пресеты",
                isComposite = true,
                content = { AdasAssistCompositeMiniature() },
            ),
            AdasPanelSelectorItem(
                id = "acc_presets",
                title = "ACC + Пресеты",
                isComposite = false,
                content = { MiniatureAccPresets() },
            ),
            AdasPanelSelectorItem(
                id = "ngp_lka_ldw",
                title = "NGP + LKA + LDW",
                isComposite = false,
                content = { MiniatureNgpLkaLdw() },
            ),
            AdasPanelSelectorItem(
                id = "lanes",
                title = "Полосы (LKA/LDW)",
                isComposite = false,
                content = { MiniatureLanes() },
            ),

            // Row 2
            AdasPanelSelectorItem(
                id = "drive_mode",
                title = "Режимы движения",
                isComposite = true,
                content = { AdasDriveModeCompositeMiniature() },
            ),
            AdasPanelSelectorItem(
                id = "chassis",
                title = "Шасси (ESC/HOLD)",
                isComposite = false,
                content = { MiniatureChassis() },
            ),
            AdasPanelSelectorItem(
                id = "radars",
                title = "Радары (BSD/RAD)",
                isComposite = false,
                content = { MiniatureRadars() },
            ),
            AdasPanelSelectorItem(
                id = "lights",
                title = "Свет",
                isComposite = false,
                content = { MiniatureLights() },
            ),

            // Row 3
            AdasPanelSelectorItem(
                id = "body_controls",
                title = "Кузов и сиденья",
                isComposite = true,
                content = { AdasBodyControlsCompositeMiniature() },
            ),
            AdasPanelSelectorItem(
                id = "wipers",
                title = "Дворники",
                isComposite = false,
                content = { MiniatureWipers() },
            ),
            AdasPanelSelectorItem(
                id = "car_color",
                title = "Цвет кузова",
                isComposite = false,
                content = { MiniatureCarColor() },
            ),
            AdasPanelSelectorItem(
                id = "camd_controls",
                title = "Камеры / Регистратор",
                isComposite = true,
                content = { AdasCamdControlsCompositeMiniature() },
            ),
        )
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Панели",
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            text = "Выберите панели для быстрого меню ADAS (синяя рамка — выбрана)",
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val rows = remember(panelItems) { panelItems.chunked(4) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        val isSelected = item.id in enabledPanels
                        val borderColor = if (isSelected) {
                            Color(0xFF3B82F6)
                        } else if (LauncherThemeState.darkTheme) {
                            Color(0xFF4B5563)
                        } else {
                            Color(0xFFCBD5E1)
                        }
                        val borderWidth = if (isSelected) 2.dp else 1.dp

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LauncherColors.LeftPanelCard)
                                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                                .clickableWithSound(onClickLabel = item.title) {
                                    LauncherAppConfigStore.setAdasPanelEnabled(context, item.id, !isSelected)
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(bottom = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.title,
                                    color = if (isSelected) {
                                        if (LauncherThemeState.darkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB)
                                    } else {
                                        LauncherColors.TextPrimary
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                contentAlignment = Alignment.Center,
                            ) {
                                item.content()
                            }
                        }
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun YandexNavSettingsContent() {
    val context = LocalContext.current
    var isStubInstalled by remember { mutableStateOf(MonjaroStubInstaller.checkInstalled(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            isStubInstalled = MonjaroStubInstaller.checkInstalled(context)
            delay(1500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // General settings
        NavHideWidgetsOnScreenToggle()

        // Section 1: Notification-based widgets (Official + MOD)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.launcher_yandex_nav_official_section_title),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.launcher_yandex_nav_official_section_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        NavBgManeuverWidgetToggle()
        NavBgEtaWidgetToggle()
        NavBgTrafficLightWidgetToggle()

        // Section 2: Broadcast-based widgets (MOD only)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.launcher_yandex_nav_mod_section_title),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.launcher_yandex_nav_mod_section_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        MonjaroStubInstallSection(
            isInstalled = isStubInstalled,
            onInstalledChanged = { installed ->
                isStubInstalled = installed
                if (installed) {
                    LauncherAppConfigStore.setNavBgLanesVisible(context, true)
                    LauncherAppConfigStore.setNavBgMinimapVisible(context, true)
                }
            }
        )

        NavSpeedWidgetToggle()
        NavTrafficLightWidgetToggle()
        NavTrafficLightScaleSlider()
        NavBgNextTurnWidgetToggle()
        NavBgLanesOrTurnWidgetToggle(isStubInstalled)
        NavBgLanesOrTurnScaleSlider()
        NavBgLanesWidgetToggle(isStubInstalled)
        NavBgMinimapWidgetToggle(isStubInstalled)
        NavBgMinimapTransparencySlider()
        NavBgMinimapHeightSlider()
        NavBgMinimap3dToggle()
        NavBgMinimapAutoZoomToggle()
    }
}

@Composable
private fun MonjaroStubInstallSection(
    isInstalled: Boolean,
    onInstalledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var isInstalling by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .border(
                1.dp,
                if (isInstalled) Color(0xFF10B981).copy(alpha = 0.5f) else LauncherColors.SettingsBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = stringResource(R.string.launcher_monjaro_stub_install_title),
                    color = LauncherColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.launcher_monjaro_stub_install_desc),
                    color = LauncherColors.TextMuted,
                    fontSize = 12.sp,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isInstalled -> Color(0xFF10B981).copy(alpha = 0.2f)
                            isInstalling -> LauncherColors.AccentCyan.copy(alpha = 0.3f)
                            else -> LauncherColors.AccentCyan
                        }
                    )
                    .clickable(enabled = !isInstalled && !isInstalling) {
                        isInstalling = true
                        MonjaroStubInstaller.install(context) { success ->
                            isInstalling = false
                            val installedNow = MonjaroStubInstaller.checkInstalled(context)
                            onInstalledChanged(installedNow)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        isInstalled -> "✓ " + stringResource(R.string.launcher_monjaro_stub_btn_installed)
                        isInstalling -> "Установка..."
                        else -> stringResource(R.string.launcher_monjaro_stub_btn_install)
                    },
                    color = when {
                        isInstalled -> Color(0xFF10B981)
                        isInstalling -> LauncherColors.TextMuted
                        else -> Color.Black
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NavBgManeuverWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgManeuverRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgManeuverVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_maneuver_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavBgManeuverVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_maneuver_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgEtaWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgEtaRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgEtaVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_eta_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavBgEtaVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_eta_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgLanesWidgetToggle(isStubInstalled: Boolean) {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgLanesRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgLanesVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_lanes_title),
            active = visible && isStubInstalled,
            onClick = {
                if (isStubInstalled) {
                    LauncherAppConfigStore.setNavBgLanesVisible(context, !visible)
                } else {
                    MonjaroStubInstaller.install(context)
                }
            },
        )
        Text(
            text = if (!isStubInstalled) {
                stringResource(R.string.launcher_monjaro_stub_required_hint) + " · " + stringResource(R.string.launcher_nav_bg_lanes_desc)
            } else {
                stringResource(R.string.launcher_nav_bg_lanes_desc)
            },
            color = if (!isStubInstalled) Color(0xFFF59E0B) else LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgMinimapWidgetToggle(isStubInstalled: Boolean) {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgMinimapRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgMinimapVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_minimap_title),
            active = visible && isStubInstalled,
            onClick = {
                if (isStubInstalled) {
                    val next = !visible
                    LauncherAppConfigStore.setNavBgMinimapVisible(context, next)
                    if (next) {
                        val density = context.resources.displayMetrics.density
                        val widthPx = (180 * density).toInt()
                        val heightPx = (LauncherAppConfigStore.navBgMinimapHeightDp(context) * density).toInt()
                        val is3d = LauncherAppConfigStore.navBgMinimap3dEnabled(context)
                        val autoZoom = LauncherAppConfigStore.navBgMinimapAutoZoomEnabled(context)
                        val speed = vad.dashing.tbox.CanDataRepository.carSpeed.value ?: 0f
                        val zoom = YandexNavRepository.resolveMinimapZoomForSpeed(speed, autoZoom)
                        YandexNavRepository.enableMinimap(
                            context = context,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            zoom = zoom,
                            viewMode = if (is3d) 1 else 0,
                        )
                    } else {
                        YandexNavRepository.disableMinimap(context)
                    }
                } else {
                    MonjaroStubInstaller.install(context)
                }
            },
        )
        Text(
            text = if (!isStubInstalled) {
                stringResource(R.string.launcher_monjaro_stub_required_hint) + " · " + stringResource(R.string.launcher_nav_bg_minimap_desc)
            } else {
                stringResource(R.string.launcher_nav_bg_minimap_desc)
            },
            color = if (!isStubInstalled) Color(0xFFF59E0B) else LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgMinimapTransparencySlider() {
    val context = LocalContext.current
    var transparency by remember {
        mutableFloatStateOf(LauncherAppConfigStore.navBgMinimapTransparency(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_nav_bg_minimap_transparency_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${transparency.roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_nav_bg_minimap_transparency_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = transparency,
            onValueChange = { next ->
                val snapped = next.roundToInt().coerceIn(
                    NAV_BG_MINIMAP_TRANSPARENCY_MIN,
                    NAV_BG_MINIMAP_TRANSPARENCY_MAX,
                )
                transparency = snapped.toFloat()
                LauncherAppConfigStore.setNavBgMinimapTransparency(context, snapped)
            },
            valueRange = NAV_BG_MINIMAP_TRANSPARENCY_MIN.toFloat()..
                NAV_BG_MINIMAP_TRANSPARENCY_MAX.toFloat(),
            steps = NAV_BG_MINIMAP_TRANSPARENCY_MAX - NAV_BG_MINIMAP_TRANSPARENCY_MIN - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun NavBgMinimapHeightSlider() {
    val context = LocalContext.current
    var heightDp by remember {
        mutableFloatStateOf(LauncherAppConfigStore.navBgMinimapHeightDp(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_nav_bg_minimap_height_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${heightDp.roundToInt()} dp",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_nav_bg_minimap_height_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = heightDp,
            onValueChange = { next ->
                val snapped = NAV_BG_MINIMAP_HEIGHT_MIN_DP +
                    ((next - NAV_BG_MINIMAP_HEIGHT_MIN_DP) / NAV_BG_MINIMAP_HEIGHT_STEP_DP).roundToInt() *
                    NAV_BG_MINIMAP_HEIGHT_STEP_DP
                heightDp = snapped.toFloat()
                LauncherAppConfigStore.setNavBgMinimapHeightDp(context, snapped)
            },
            valueRange = NAV_BG_MINIMAP_HEIGHT_MIN_DP.toFloat()..NAV_BG_MINIMAP_HEIGHT_MAX_DP.toFloat(),
            steps = (NAV_BG_MINIMAP_HEIGHT_MAX_DP - NAV_BG_MINIMAP_HEIGHT_MIN_DP) /
                NAV_BG_MINIMAP_HEIGHT_STEP_DP - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun NavBgMinimap3dToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgMinimapRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) { LauncherAppConfigStore.navBgMinimap3dEnabled(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_minimap_3d_title),
            active = enabled,
            onClick = { LauncherAppConfigStore.setNavBgMinimap3dEnabled(context, !enabled) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_minimap_3d_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgMinimapAutoZoomToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgMinimapRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) { LauncherAppConfigStore.navBgMinimapAutoZoomEnabled(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_minimap_auto_zoom_title),
            active = enabled,
            onClick = { LauncherAppConfigStore.setNavBgMinimapAutoZoomEnabled(context, !enabled) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_minimap_auto_zoom_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgNextTurnWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgNextTurnRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgNextTurnVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_next_turn_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavBgNextTurnVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_next_turn_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgLanesOrTurnWidgetToggle(isStubInstalled: Boolean) {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgLanesOrTurnRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgLanesOrTurnVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_lanes_or_turn_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavBgLanesOrTurnVisible(context, !visible) },
        )
        Text(
            text = if (!isStubInstalled) {
                stringResource(R.string.launcher_monjaro_stub_required_hint) + " · " + stringResource(R.string.launcher_nav_bg_lanes_or_turn_desc)
            } else {
                stringResource(R.string.launcher_nav_bg_lanes_or_turn_desc)
            },
            color = if (!isStubInstalled) Color(0xFFF59E0B) else LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgLanesOrTurnScaleSlider() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgLanesOrTurnRevisionFlow.collectAsStateWithLifecycle()
    var scalePercent by remember(context, revision) {
        mutableFloatStateOf(LauncherAppConfigStore.navBgLanesOrTurnScalePercent(context).toFloat())
    }
    val displayPercent = scalePercent.roundToInt()
    val valueText = if (displayPercent > 0) "+$displayPercent%" else "$displayPercent%"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_nav_bg_lanes_or_turn_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_nav_bg_lanes_or_turn_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scalePercent,
            onValueChange = { next ->
                val clamped = next.roundToInt().coerceIn(
                    NAV_BG_LANES_OR_TURN_SCALE_MIN_PERCENT,
                    NAV_BG_LANES_OR_TURN_SCALE_MAX_PERCENT,
                )
                val snapped = NAV_BG_LANES_OR_TURN_SCALE_MIN_PERCENT +
                    ((clamped - NAV_BG_LANES_OR_TURN_SCALE_MIN_PERCENT + NAV_BG_LANES_OR_TURN_SCALE_STEP_PERCENT / 2) /
                        NAV_BG_LANES_OR_TURN_SCALE_STEP_PERCENT) * NAV_BG_LANES_OR_TURN_SCALE_STEP_PERCENT
                scalePercent = snapped.toFloat()
                LauncherAppConfigStore.setNavBgLanesOrTurnScalePercent(context, snapped)
            },
            valueRange = NAV_BG_LANES_OR_TURN_SCALE_MIN_PERCENT.toFloat()..
                NAV_BG_LANES_OR_TURN_SCALE_MAX_PERCENT.toFloat(),
            steps = (NAV_BG_LANES_OR_TURN_SCALE_MAX_PERCENT - NAV_BG_LANES_OR_TURN_SCALE_MIN_PERCENT) /
                NAV_BG_LANES_OR_TURN_SCALE_STEP_PERCENT - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun NavHideWidgetsOnScreenToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navHideWidgetsOnScreenRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) { LauncherAppConfigStore.navHideWidgetsOnScreen(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_hide_widgets_on_screen_title),
            active = enabled,
            onClick = { LauncherAppConfigStore.setNavHideWidgetsOnScreen(context, !enabled) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_hide_widgets_on_screen_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NavBgTrafficLightWidgetToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.navBgTrafficLightRevisionFlow.collectAsStateWithLifecycle()
    val visible = remember(context, revision) { LauncherAppConfigStore.navBgTrafficLightVisible(context) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_nav_bg_traffic_light_title),
            active = visible,
            onClick = { LauncherAppConfigStore.setNavBgTrafficLightVisible(context, !visible) },
        )
        Text(
            text = stringResource(R.string.launcher_nav_bg_traffic_light_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CruisePresetsSettings() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.cruisePresetsRevisionFlow.collectAsStateWithLifecycle()
    val presets = remember(context, revision) {
        LauncherAppConfigStore.cruisePresetsKmh(context)
    }
    val incInterval = remember(context, revision) {
        LauncherAppConfigStore.cruiseIncreaseIntervalMs(context)
    }
    val decInterval = remember(context, revision) {
        LauncherAppConfigStore.cruiseDecreaseIntervalMs(context)
    }
    val fuelPrice = remember(context, revision) {
        LauncherAppConfigStore.fuelPriceRub(context)
    }
    val fuelTankCapacity = remember(context, revision) {
        LauncherAppConfigStore.fuelTankCapacityLiters(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.launcher_cruise_presets_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_cruise_presets_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        presets.forEachIndexed { index, kmh ->
            CruisePresetSliderRow(
                index = index,
                kmh = kmh,
                onChange = { next ->
                    LauncherAppConfigStore.setCruisePresetKmh(context, index, next)
                },
            )
        }
        CruiseIntervalSliderRow(
            title = "RES+ delay (+1 km/h)",
            valueMs = incInterval,
            onChange = { next ->
                LauncherAppConfigStore.setCruiseIncreaseIntervalMs(context, next)
            },
        )
        CruiseIntervalSliderRow(
            title = "SET- delay (-1 km/h)",
            valueMs = decInterval,
            onChange = { next ->
                LauncherAppConfigStore.setCruiseDecreaseIntervalMs(context, next)
            },
        )
        FuelPriceSliderRow(
            title = stringResource(R.string.launcher_fuel_price_title),
            price = fuelPrice,
            onChange = { next ->
                LauncherAppConfigStore.setFuelPriceRub(context, next)
            },
        )
        FuelTankCapacitySliderRow(
            title = stringResource(R.string.launcher_fuel_tank_capacity_title),
            capacity = fuelTankCapacity,
            onChange = { next ->
                LauncherAppConfigStore.setFuelTankCapacityLiters(context, next)
            },
        )
    }
}

@Composable
private fun CruiseIntervalSliderRow(
    title: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${valueMs}ms",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / 25f).roundToInt() * 25
                onChange(snapped.coerceIn(50, 1500).toLong())
            },
            valueRange = 50f..1500f,
            steps = ((1500 - 50) / 25) - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun FuelPriceSliderRow(
    title: String,
    price: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${valueToString(price, 1)} ${stringResource(R.string.unit_ruble)}",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = price,
            onValueChange = { raw ->
                val rounded = (raw * 10f).roundToInt() / 10f
                onChange(rounded.coerceIn(FUEL_PRICE_MIN, FUEL_PRICE_MAX))
            },
            valueRange = FUEL_PRICE_MIN..FUEL_PRICE_MAX,
            steps = ((FUEL_PRICE_MAX - FUEL_PRICE_MIN) / 0.1f).roundToInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun FuelTankCapacitySliderRow(
    title: String,
    capacity: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${capacity.roundToInt()} ${stringResource(R.string.unit_liter)}",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = capacity,
            onValueChange = { raw ->
                val rounded = raw.roundToInt().toFloat()
                onChange(rounded.coerceIn(FUEL_TANK_CAPACITY_MIN, FUEL_TANK_CAPACITY_MAX))
            },
            valueRange = FUEL_TANK_CAPACITY_MIN..FUEL_TANK_CAPACITY_MAX,
            steps = (FUEL_TANK_CAPACITY_MAX - FUEL_TANK_CAPACITY_MIN).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun CruisePresetSliderRow(
    index: Int,
    kmh: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_cruise_preset_n, index + 1),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = stringResource(R.string.launcher_cruise_preset_value, kmh),
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = kmh.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / CRUISE_PRESET_STEP_KMH).roundToInt() * CRUISE_PRESET_STEP_KMH
                onChange(snapped.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH))
            },
            valueRange = CRUISE_PRESET_MIN_KMH.toFloat()..CRUISE_PRESET_MAX_KMH.toFloat(),
            steps = ((CRUISE_PRESET_MAX_KMH - CRUISE_PRESET_MIN_KMH) / CRUISE_PRESET_STEP_KMH) - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun HiddenAppsSettings() {
    val context = LocalContext.current
    val hidden = remember(context) { LauncherAppConfigStore.hiddenPackages(context) }
    if (hidden.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Скрытые приложения (${hidden.size})",
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "Приложения, скрытые из рабочего стола и поиска",
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
            )
            TextButton(
                onClick = {
                    LauncherAppConfigStore.unhideAllPackages(context)
                },
            ) {
                Text(
                    text = "Показать все скрытые приложения",
                    color = LauncherColors.AccentCyan,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun TrunkLongPressToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.trunkLongPressRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.trunkLongPressEnabled(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_trunk_long_press_enabled_title),
            active = enabled,
            onClick = {
                LauncherAppConfigStore.setTrunkLongPressEnabled(context, !enabled)
            },
        )
        Text(
            text = stringResource(R.string.launcher_trunk_long_press_enabled_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}
