package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import kotlin.math.roundToInt

@Composable
fun ThemeSettingsContent() {
    val context = LocalContext.current
    val headlightBeams = rememberHeadlightBeams()
    val solarDaytime = rememberSolarDaytimeState()

    LaunchedEffect(headlightBeams.any, LauncherThemeState.autoThemeHeadlightsEnabled) {
        if (LauncherThemeState.autoThemeHeadlightsEnabled) {
            LauncherThemeState.setDarkTheme(context, headlightBeams.any)
        }
    }

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
            enabled = !LauncherThemeState.autoThemeHeadlightsEnabled && !LauncherThemeState.autoThemeSolarEnabled,
        )
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_auto_theme_headlights),
            active = LauncherThemeState.autoThemeHeadlightsEnabled,
            onClick = {
                LauncherThemeState.setAutoThemeHeadlightsEnabled(
                    context,
                    !LauncherThemeState.autoThemeHeadlightsEnabled,
                )
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_auto_theme_headlights_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_vs_auto_theme_solar),
            active = LauncherThemeState.autoThemeSolarEnabled,
            onClick = {
                LauncherThemeState.setAutoThemeSolarEnabled(
                    context,
                    !LauncherThemeState.autoThemeSolarEnabled,
                )
            },
        )
        Text(
            text = stringResource(R.string.launcher_vs_auto_theme_solar_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
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
    MediaCardOpacitySlider()
    CarModelScaleSlider()
    SidebarWidthSlider()
    AdasDistanceTextSizeSlider()
    AdasAlertsPlacementToggle()
    NavButtonsToggle()
    FloatingHomeToggle()
    FloatingHomeSizeSlider()
    ClimateControlsToggle()
    ClimateCardBgToggle()
    ClimateControlsScaleSlider()
    ResetBottomSlotsButton()
    TopBarHeightSlider()
    TopBarLeftIndicatorsToggle()
    BottomBarHeightSlider()
    GridColumnsSlider()
    GridRowsSlider()
    HomeIconScaleSlider()
    HiddenAppsSettings()
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
private fun AdasAlertsPlacementToggle() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasAlertsPlacementRevisionFlow
        .collectAsStateWithLifecycle()
    val inLeftPanel = remember(context, revision) {
        LauncherAppConfigStore.adasAlertsInLeftPanel(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_adas_alerts_placement_title),
            active = inLeftPanel,
            onClick = {
                LauncherAppConfigStore.setAdasAlertsInLeftPanel(context, !inLeftPanel)
            },
        )
        Text(
            text = stringResource(R.string.launcher_adas_alerts_placement_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}



@Composable
fun CruiseControlSettingsContent() {
    val context = LocalContext.current
    val cruisePanelRevision by LauncherAppConfigStore.cruisePanelRevisionFlow
        .collectAsStateWithLifecycle()
    val cruisePanelVisible = remember(context, cruisePanelRevision) {
        LauncherAppConfigStore.cruisePanelVisible(context)
    }
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

        LauncherSettingsToggleRow(
            label = stringResource(R.string.launcher_cruise_panel_visible_title),
            active = cruisePanelVisible,
            onClick = {
                LauncherAppConfigStore.setCruisePanelVisible(context, !cruisePanelVisible)
            },
        )
        Text(
            text = stringResource(R.string.launcher_cruise_panel_visible_desc),
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
private fun GridColumnsSlider() {
    val context = LocalContext.current
    val colsRevision by LauncherAppConfigStore.gridColumnsRevisionFlow.collectAsStateWithLifecycle()
    var cols by remember(context, colsRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.gridColumns(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_grid_columns_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${cols.roundToInt()}",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.settings_grid_columns_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = cols,
            onValueChange = { next ->
                cols = next
                LauncherAppConfigStore.setGridColumns(context, next.roundToInt())
            },
            valueRange = GRID_COLUMNS_MIN.toFloat()..GRID_COLUMNS_MAX.toFloat(),
            steps = GRID_COLUMNS_MAX - GRID_COLUMNS_MIN - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun GridRowsSlider() {
    val context = LocalContext.current
    val rowsRevision by LauncherAppConfigStore.gridRowsRevisionFlow.collectAsStateWithLifecycle()
    var rows by remember(context, rowsRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.gridRows(context).toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_grid_rows_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${rows.roundToInt()}",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.settings_grid_rows_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = rows,
            onValueChange = { next ->
                rows = next
                LauncherAppConfigStore.setGridRows(context, next.roundToInt())
            },
            valueRange = GRID_ROWS_MIN.toFloat()..GRID_ROWS_MAX.toFloat(),
            steps = GRID_ROWS_MAX - GRID_ROWS_MIN - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun HomeIconScaleSlider() {
    val context = LocalContext.current
    val scaleRevision by LauncherAppConfigStore.homeIconScaleRevisionFlow.collectAsStateWithLifecycle()
    var scale by remember(context, scaleRevision) {
        mutableFloatStateOf(LauncherAppConfigStore.homeIconScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_home_icon_scale_title),
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
            text = stringResource(R.string.settings_home_icon_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                scale = next
                LauncherAppConfigStore.setHomeIconScale(context, next)
            },
            valueRange = HOME_ICON_SCALE_MIN..HOME_ICON_SCALE_MAX,
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
