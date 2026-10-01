package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.roundToInt
import vad.dashing.tbox.R

@Composable
internal fun LauncherHomeSettingsContent() {
    val context = LocalContext.current
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_dark_theme),
        active = LauncherThemeState.darkTheme,
        onClick = { LauncherThemeState.setDarkTheme(context, !LauncherThemeState.darkTheme) },
    )
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
    AdasDistanceOffsetSlider()
    NavButtonsToggle()
    TopBarHeightSlider()
    BottomBarHeightSlider()
    CruisePresetsSettings()
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
private fun AdasDistanceOffsetSlider() {
    val context = LocalContext.current
    var offsetRatio by remember {
        mutableFloatStateOf(LauncherAppConfigStore.adasDistanceLabelOffset(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_distance_offset_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(offsetRatio * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_adas_distance_offset_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = offsetRatio,
            onValueChange = { next ->
                offsetRatio = next
                LauncherAppConfigStore.setAdasDistanceLabelOffset(context, next)
            },
            valueRange = ADAS_DISTANCE_LABEL_OFFSET_MIN..ADAS_DISTANCE_LABEL_OFFSET_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
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
