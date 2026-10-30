package vad.dashing.tbox.ui.launcher

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
        UiClickSoundsSettingToggle()
    }
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
internal fun LauncherHomeSettingsContent() {
    val context = LocalContext.current
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
    val closePlayerAfterPlaybackRevision by LauncherAppConfigStore.closePlayerAfterPlaybackRevisionFlow
        .collectAsStateWithLifecycle()
    val closePlayerAfterPlayback = remember(context, closePlayerAfterPlaybackRevision) {
        LauncherAppConfigStore.closePlayerAfterPlayback(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_close_player_after_playback_title),
        active = closePlayerAfterPlayback,
        onClick = {
            LauncherAppConfigStore.setClosePlayerAfterPlayback(context, !closePlayerAfterPlayback)
        },
    )
    Text(
        text = stringResource(R.string.launcher_close_player_after_playback_desc),
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
    CameraSwitchModeSelector()
    TrunkLongPressToggle()
    AvmCameraActionSelector()
    PdcVisualModeSelector()
    SidebarWidthSlider()
    AdasPanelToggle()
    AdasDistanceTextSizeSlider()
    NavButtonsToggle()
    FloatingHomeToggle()
    FloatingHomeSizeSlider()
    EdgeGesturesSettings()
    ClimateControlsToggle()
    ClimateCardBgToggle()
    ClimateControlsScaleSlider()
    ResetBottomSlotsButton()
    TopBarHeightSlider()
    TopBarLeftIndicatorsToggle()
    BottomBarHeightSlider()
    HiddenAppsSettings()
    TbmApiSettingsContent(settingsManager = remember(context) { SettingsManager(context) })
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
    var scale by remember {
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
                    valueRange = 15f..35f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = LauncherColors.AccentCyan,
                        activeTrackColor = LauncherColors.AccentCyan,
                        inactiveTrackColor = LauncherColors.TextMuted,
                    ),
                )
            }

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
fun CruiseControlSettingsContent() {
    val context = LocalContext.current
    val driveModeBarRevision by LauncherAppConfigStore.driveModeBarRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeBarVisible = remember(context, driveModeBarRevision) {
        LauncherAppConfigStore.driveModeBarVisible(context)
    }
    val driveModeGlowRevision by LauncherAppConfigStore.driveModeGlowRevisionFlow
        .collectAsStateWithLifecycle()
    val driveModeGlowVisible = remember(context, driveModeGlowRevision) {
        LauncherAppConfigStore.driveModeGlowVisible(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_drive_mode_bar_visible_title),
            active = driveModeBarVisible,
            onClick = {
                LauncherAppConfigStore.setDriveModeBarVisible(context, !driveModeBarVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_drive_mode_bar_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        val assistPanelRevision by LauncherAppConfigStore.assistPanelRevisionFlow
            .collectAsStateWithLifecycle()
        val assistPanelVisible = remember(context, assistPanelRevision) {
            LauncherAppConfigStore.assistPanelVisible(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_assist_panel_visible_title),
            active = assistPanelVisible,
            onClick = {
                LauncherAppConfigStore.setAssistPanelVisible(context, !assistPanelVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_assist_panel_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        val bodyControlsRevision by LauncherAppConfigStore.bodyControlsPanelRevisionFlow
            .collectAsStateWithLifecycle()
        val bodyControlsVisible = remember(context, bodyControlsRevision) {
            LauncherAppConfigStore.bodyControlsPanelVisible(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_body_controls_panel_visible_title),
            active = bodyControlsVisible,
            onClick = {
                LauncherAppConfigStore.setBodyControlsPanelVisible(context, !bodyControlsVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_body_controls_panel_visible_desc),
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
        val headlightBeamsVisible = remember(context, headlightBeamsRevision) {
            LauncherAppConfigStore.headlightBeamsVisible(context)
        }

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_headlight_beams_visible_title),
            active = headlightBeamsVisible,
            onClick = {
                LauncherAppConfigStore.setHeadlightBeamsVisible(context, !headlightBeamsVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_headlight_beams_visible_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )

        CruisePresetsSettings()
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
