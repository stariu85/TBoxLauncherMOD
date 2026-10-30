package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val PREFS = "tbox_launcher_app_config"
private const val KEY_HIDDEN = "hidden_packages"
private const val KEY_GRID = "grid_packages"
private const val KEY_DOCK = "dock_packages"
private const val KEY_CAR_PAINT = "car_paint_id"
private const val KEY_DEFAULT_MEDIA = "default_media_package"
private const val KEY_MEDIA_CARD_ALPHA = "media_card_alpha"
private const val KEY_MEDIA_CARD_HEIGHT_SCALE = "media_card_height_scale"
private const val KEY_MEDIA_MINI_PLAYER_VISIBLE = "media_mini_player_visible"
private const val KEY_CLOSE_PLAYER_AFTER_PLAYBACK = "close_player_after_playback"
private const val KEY_DISABLE_MEDIA_CARD_COVER_COLOR = "disable_media_card_cover_color"
private const val KEY_DOCK_ICON_SCALE = "dock_icon_scale"
private const val KEY_TOP_BAR_HEIGHT = "top_bar_height_dp"
private const val KEY_TOP_BAR_LEFT_INDICATORS_VISIBLE = "top_bar_left_indicators_visible"
private const val KEY_TOP_BAR_ITEMS = "top_bar_items"
private val DEFAULT_TOP_BAR_ITEMS = listOf("temp", "fuel", "voltage", "cpu", "ram")
private const val KEY_BOTTOM_BAR_HEIGHT = "bottom_bar_height_dp"
private const val KEY_CAR_MODEL_SCALE = "car_model_scale"
private const val KEY_SIDEBAR_WIDTH = "sidebar_width_dp"
private const val KEY_CAMERA_SWITCH_MODE = "camera_switch_mode"
private const val KEY_CAMERA_ANIMATION_ENABLED = "camera_animation_enabled"
private const val KEY_TRUNK_LONG_PRESS_ENABLED = "trunk_long_press_enabled"
private const val KEY_AVM_CAMERA_ACTION = "avm_camera_action"
private const val KEY_PDC_VISUAL_MODE = "pdc_visual_mode"
private const val KEY_EDGE_GESTURES_ENABLED = "edge_gestures_enabled"
private const val KEY_EDGE_GESTURES_WIDTH = "edge_gestures_width_dp"
private const val KEY_EDGE_GESTURE_ACTION_LEFT = "edge_gesture_action_left"
private const val KEY_EDGE_GESTURE_ACTION_RIGHT = "edge_gesture_action_right"
private const val KEY_EDGE_GESTURE_ACTION_BOTTOM = "edge_gesture_action_bottom"

internal const val EDGE_GESTURE_ACTION_NONE = "none"
internal const val EDGE_GESTURE_ACTION_HOME = "home"
internal const val EDGE_GESTURE_ACTION_BACK = "back"
internal const val EDGE_GESTURE_ACTION_PREVIOUS_APP = "undo"
internal const val EDGE_GESTURE_ACTION_CLOSE_ALL = "close_all"

const val PDC_VISUAL_ARCS = 0
const val PDC_VISUAL_CLASSIC = 1
const val PDC_VISUAL_OFF = 2
private const val KEY_NAV_BUTTONS_VISIBLE = "nav_buttons_visible"
private const val KEY_CLIMATE_VISIBLE = "climate_controls_visible"
private const val KEY_CLIMATE_SCALE = "climate_controls_scale"
private const val KEY_CLIMATE_CARD_BG_VISIBLE = "climate_card_bg_visible"
private const val KEY_FLOATING_HOME_VISIBLE = "floating_home_visible"
private const val KEY_FLOATING_HOME_SIZE = "floating_home_size_dp"
private const val KEY_ADAS_DISTANCE_TEXT_SIZE = "adas_distance_text_size"
private const val KEY_ADAS_DISTANCE_LABEL_OFFSET = "adas_distance_label_offset"
private const val KEY_ADAS_PANEL_VISIBLE = "adas_panel_visible"
private const val KEY_ADAS_ALERTS_IN_LEFT_PANEL = "adas_alerts_in_left_panel"
private const val KEY_ADAS_BSD_ALERT_OVERLAY = "adas_bsd_alert_overlay_enabled"
private const val KEY_SPEED_LIMIT_X_RATIO = "speed_limit_x_ratio"
private const val KEY_SPEED_LIMIT_Y_RATIO = "speed_limit_y_ratio"
private const val KEY_FUEL_SHOWS_RANGE = "fuel_shows_range"
private const val KEY_FULLSCREEN = "fullscreen_packages"
private const val KEY_FULL_WIDTH = "full_width_packages"
private const val KEY_BOTTOM_SLOTS_UNIFIED = "bottom_slots_unified"
private const val KEY_ASSIST_PRESETS_ACTIVE_TAB = "assist_presets_active_tab"
private const val KEY_GRID_COLUMNS = "home_grid_columns"
private const val KEY_GRID_ROWS = "home_grid_rows"
private const val KEY_GRID_ROW_SPACING = "home_grid_row_spacing"
private const val KEY_HOME_ICON_SCALE = "home_icon_scale"
private const val KEY_WALLPAPER_DARK_URI = "wallpaper_dark_uri"
private const val KEY_WALLPAPER_LIGHT_URI = "wallpaper_light_uri"
private const val KEY_WALLPAPER_SCALE_MODE = "wallpaper_scale_mode"

internal const val GRID_SLOTS_TOTAL_COUNT = 20

internal val DEFAULT_BOTTOM_SLOTS_UNIFIED: List<String?> = listOf(
    null,          // Slot 0 (1st cell left): Reserved for floating Home button
    "all_apps",    // Slot 1 (2nd cell left): "Все приложения" by default
    "seat_heat_left",
    "seat_vent_left",
    "temp_driver", // Occupies slots 4 and 5 (span = 2)
    null,          // Slot 5 absorbed by temp_driver
    "hvac_auto",
    "temp_pass",   // Occupies slots 7 and 8 (span = 2)
    null,          // Slot 8 absorbed by temp_pass
    "seat_heat_right",
    "seat_vent_right",
    "recirc",
    "steering_heat",
    "windscreen_heat",
    "front_defrost",
    "rear_defrost",
    null,
    null,
    null,
    null,
)

enum class LauncherAppLaunchMode(val code: String) {
    EMBEDDED("embedded"),
    FULL_WIDTH("full_width"),
    FULLSCREEN("fullscreen");

    companion object {
        fun fromCode(code: String?): LauncherAppLaunchMode = when (code) {
            FULL_WIDTH.code -> FULL_WIDTH
            FULLSCREEN.code -> FULLSCREEN
            else -> EMBEDDED
        }
    }
}

enum class WallpaperScaleMode(val code: Int) {
    FILL_WIDTH(1),
    FILL_HEIGHT(2),
    FILL_BOUNDS(0);

    companion object {
        fun fromCode(code: Int): WallpaperScaleMode = when (code) {
            0 -> FILL_BOUNDS
            2 -> FILL_HEIGHT
            else -> FILL_WIDTH
        }
    }
}

enum class LauncherTireBadgeShape(val code: String) {
    RECTANGLE("rectangle"),
    ROUND("round");

    companion object {
        fun fromCode(code: String?): LauncherTireBadgeShape = when (code) {
            ROUND.code -> ROUND
            else -> RECTANGLE
        }
    }
}
internal const val MEDIA_CARD_ALPHA_DEFAULT = 0.88f
internal const val MEDIA_CARD_ALPHA_MIN = 0.40f
internal const val MEDIA_CARD_ALPHA_MAX = 1.00f
internal const val MEDIA_CARD_HEIGHT_SCALE_DEFAULT = 1.00f
internal const val MEDIA_CARD_HEIGHT_SCALE_MIN = 0.50f
internal const val MEDIA_CARD_HEIGHT_SCALE_MAX = 1.00f
internal const val DOCK_ICON_SCALE_DEFAULT = 1.00f
internal const val DOCK_ICON_SCALE_MIN = 1.00f
internal const val DOCK_ICON_SCALE_MAX = 2.00f
internal const val TOP_BAR_HEIGHT_DEFAULT = 32
internal const val TOP_BAR_HEIGHT_MIN = 28
internal const val TOP_BAR_HEIGHT_MAX = 72
internal const val BOTTOM_BAR_HEIGHT_DEFAULT = 70
internal const val BOTTOM_BAR_HEIGHT_MIN = 48
internal const val BOTTOM_BAR_HEIGHT_MAX = 110
internal const val FLOATING_HOME_SIZE_DEFAULT = 55
internal const val FLOATING_HOME_SIZE_MIN = 32
internal const val FLOATING_HOME_SIZE_MAX = 150
internal const val CAR_MODEL_SCALE_DEFAULT = 1.00f
internal const val CAR_MODEL_SCALE_MIN = 1.00f
internal const val CAR_MODEL_SCALE_MAX = 2.50f
internal const val SIDEBAR_WIDTH_DEFAULT = 300
internal const val SIDEBAR_WIDTH_MIN = 240
internal const val SIDEBAR_WIDTH_MAX = 400
internal const val CAMERA_SWITCH_MODE_PAS_AND_PARK = 0
internal const val CAMERA_SWITCH_MODE_DRIVE_AND_PARK = 1
internal const val CAMERA_SWITCH_MODE_DEFAULT = CAMERA_SWITCH_MODE_DRIVE_AND_PARK
internal const val AVM_CAMERA_ACTION_DO_NOTHING = 0
internal const val AVM_CAMERA_ACTION_CLOSE_APPS = 1
internal const val AVM_CAMERA_ACTION_MINIMIZE_APPS = 2
internal const val AVM_CAMERA_ACTION_DEFAULT = AVM_CAMERA_ACTION_MINIMIZE_APPS
internal const val ADAS_DISTANCE_TEXT_SIZE_DEFAULT = 28
internal const val ADAS_DISTANCE_TEXT_SIZE_MIN = 20
internal const val ADAS_DISTANCE_TEXT_SIZE_MAX = 40
internal const val ADAS_DISTANCE_LABEL_OFFSET_DEFAULT = 0.50f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MIN = 0.00f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MAX = 1.00f
internal const val CLIMATE_SCALE_DEFAULT = 1.50f
internal const val CLIMATE_SCALE_MIN = 1.05f
internal const val CLIMATE_SCALE_MAX = 1.70f
internal const val GRID_COLUMNS_DEFAULT = 12
internal const val GRID_COLUMNS_MIN = 5
internal const val GRID_COLUMNS_MAX = 20
internal const val GRID_ROWS_DEFAULT = 7
internal const val GRID_ROWS_MIN = 1
internal const val GRID_ROWS_MAX = 10
internal const val GRID_ROW_SPACING_DEFAULT = 4
internal const val GRID_ROW_SPACING_MIN = 0
internal const val GRID_ROW_SPACING_MAX = 32
internal const val HOME_ICON_SCALE_DEFAULT = 1.35f
internal const val HOME_ICON_SCALE_MIN = 0.60f
internal const val HOME_ICON_SCALE_MAX = 1.80f
internal val CRUISE_PRESET_DEFAULTS_KMH = listOf(110, 90, 80, 60)
internal const val CRUISE_PRESET_MIN_KMH = 30
internal const val CRUISE_PRESET_MAX_KMH = 160
internal const val CRUISE_PRESET_STEP_KMH = 1
private const val KEY_CRUISE_PRESETS = "cruise_presets_kmh"
private const val KEY_LAST_CRUISE_SPEED = "last_cruise_speed_kmh"
private const val KEY_CRUISE_PANEL_VISIBLE = "cruise_panel_visible"
private const val KEY_CRUISE_PANEL_POSITION = "cruise_panel_position"
private const val KEY_SWAP_TOP_BOTTOM_PANELS = "swap_top_bottom_panels"
private const val KEY_DRIVE_MODE_BAR_VISIBLE = "drive_mode_bar_visible"
private const val KEY_ASSIST_PANEL_VISIBLE = "assist_panel_visible"
private const val KEY_BODY_CONTROLS_PANEL_VISIBLE = "body_controls_panel_visible"
private const val KEY_ADAS_TOP_PANELS = "adas_top_panels"
private const val KEY_ADAS_BOTTOM_PANELS = "adas_bottom_panels"
private const val KEY_DRIVE_MODE_GLOW_VISIBLE = "drive_mode_glow_visible"
private const val KEY_HEADLIGHT_BEAMS_VISIBLE = "headlight_beams_visible"
private const val KEY_NGP_ENABLED = "ngp_enabled"
private const val KEY_TIME_GAP_LEVEL = "time_gap_level"
private const val KEY_CRUISE_INCREASE_INTERVAL = "cruise_increase_interval"
private const val KEY_CRUISE_DECREASE_INTERVAL = "cruise_decrease_interval"
private const val KEY_FUEL_PRICE = "fuel_price_rub"
internal const val FUEL_PRICE_DEFAULT = 75.0f
internal const val FUEL_PRICE_MIN = 60.0f
internal const val FUEL_PRICE_MAX = 150.0f
internal const val FUEL_PRICE_STEP = 0.1f
private const val KEY_TIRE_PRESSURE_ON_CAR_ENABLED = "tire_pressure_on_car_enabled"
private const val KEY_TIRE_PRESSURE_ONLY_LOW = "tire_pressure_only_low"
private const val KEY_TIRE_PRESSURE_ONLY_TOP_VIEW = "tire_pressure_only_top_view"
private const val KEY_TIRE_PRESSURE_LOW_THRESHOLD = "tire_pressure_low_threshold"
private const val KEY_TIRE_PRESSURE_ALWAYS_IN_SETTINGS = "tire_pressure_always_in_settings"
private const val KEY_TIRE_PRESSURE_INERTIA_ANIMATION_ENABLED = "tire_pressure_inertia_animation_enabled"
private const val KEY_TIRE_PRESSURE_BADGE_SHAPE = "tire_pressure_badge_shape"
private const val KEY_TIRE_PRESSURE_PERSIST_ENABLED = "tire_pressure_persist_enabled"
private const val KEY_SAVED_TIRE_PRESSURE_FL = "saved_tire_pressure_fl"
private const val KEY_SAVED_TIRE_PRESSURE_FR = "saved_tire_pressure_fr"
private const val KEY_SAVED_TIRE_PRESSURE_RL = "saved_tire_pressure_rl"
private const val KEY_SAVED_TIRE_PRESSURE_RR = "saved_tire_pressure_rr"
private const val KEY_SAVED_TIRE_TEMP_FL = "saved_tire_temp_fl"
private const val KEY_SAVED_TIRE_TEMP_FR = "saved_tire_temp_fr"
private const val KEY_SAVED_TIRE_TEMP_RL = "saved_tire_temp_rl"
private const val KEY_SAVED_TIRE_TEMP_RR = "saved_tire_temp_rr"
internal const val TIRE_PRESSURE_LOW_THRESHOLD_DEFAULT = 2.0f
internal const val TIRE_PRESSURE_LOW_THRESHOLD_MIN = 1.8f
internal const val TIRE_PRESSURE_LOW_THRESHOLD_MAX = 2.4f
internal const val GRID_SLOT_COUNT = 9
private const val DOCK_SLOT_COUNT = 4

private val DEFAULT_DOCK = listOf(
    "com.autopai.car.dialer",
    "com.wt.multimedia.platform3",
    "com.tencent.wecarnavi",
    "com.autopai.system.settings",
)

internal object LauncherAppConfigStore {

    private val wallpaperRevision = MutableStateFlow(0)
    internal val wallpaperRevisionFlow: StateFlow<Int> = wallpaperRevision

    fun wallpaperDarkUri(context: Context): String =
        prefs(context).getString(KEY_WALLPAPER_DARK_URI, "").orEmpty()

    fun setWallpaperDarkUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_WALLPAPER_DARK_URI, uri).apply()
        wallpaperRevision.value++
    }

    fun wallpaperLightUri(context: Context): String =
        prefs(context).getString(KEY_WALLPAPER_LIGHT_URI, "").orEmpty()

    fun setWallpaperLightUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_WALLPAPER_LIGHT_URI, uri).apply()
        wallpaperRevision.value++
    }

    fun wallpaperScaleMode(context: Context): WallpaperScaleMode =
        WallpaperScaleMode.fromCode(prefs(context).getInt(KEY_WALLPAPER_SCALE_MODE, 1))

    fun setWallpaperScaleMode(context: Context, mode: WallpaperScaleMode) {
        prefs(context).edit().putInt(KEY_WALLPAPER_SCALE_MODE, mode.code).apply()
        wallpaperRevision.value++
    }

    private val mediaCardAlphaRevision = MutableStateFlow(0)
    internal val mediaCardAlphaRevisionFlow: StateFlow<Int> = mediaCardAlphaRevision
    private val mediaCardHeightScaleRevision = MutableStateFlow(0)
    internal val mediaCardHeightScaleRevisionFlow: StateFlow<Int> = mediaCardHeightScaleRevision
    private val mediaMiniPlayerRevision = MutableStateFlow(0)
    internal val mediaMiniPlayerRevisionFlow: StateFlow<Int> = mediaMiniPlayerRevision
    private val closePlayerAfterPlaybackRevision = MutableStateFlow(0)
    internal val closePlayerAfterPlaybackRevisionFlow: StateFlow<Int> = closePlayerAfterPlaybackRevision
    private val disableMediaCardCoverColorRevision = MutableStateFlow(0)
    internal val disableMediaCardCoverColorRevisionFlow: StateFlow<Int> = disableMediaCardCoverColorRevision
    private val dockIconScaleRevision = MutableStateFlow(0)
    internal val dockIconScaleRevisionFlow: StateFlow<Int> = dockIconScaleRevision
    private val topBarHeightRevision = MutableStateFlow(0)
    internal val topBarHeightRevisionFlow: StateFlow<Int> = topBarHeightRevision
    private val topBarLeftIndicatorsRevision = MutableStateFlow(0)
    internal val topBarLeftIndicatorsRevisionFlow: StateFlow<Int> = topBarLeftIndicatorsRevision
    private val topBarItemsRevision = MutableStateFlow(0)
    internal val topBarItemsRevisionFlow: StateFlow<Int> = topBarItemsRevision
    private val topBarIndicatorsBorderRevision = MutableStateFlow(0)
    internal val topBarIndicatorsBorderRevisionFlow: StateFlow<Int> = topBarIndicatorsBorderRevision
    private val bottomBarHeightRevision = MutableStateFlow(0)
    internal val bottomBarHeightRevisionFlow: StateFlow<Int> = bottomBarHeightRevision
    private val floatingHomeSizeRevision = MutableStateFlow(0)
    internal val floatingHomeSizeRevisionFlow: StateFlow<Int> = floatingHomeSizeRevision
    private val floatingHomeVisibleRevision = MutableStateFlow(0)
    internal val floatingHomeVisibleRevisionFlow: StateFlow<Int> = floatingHomeVisibleRevision
    private val carModelScaleRevision = MutableStateFlow(0)
    internal val carModelScaleRevisionFlow: StateFlow<Int> = carModelScaleRevision
    private val sidebarWidthRevision = MutableStateFlow(0)
    internal val sidebarWidthRevisionFlow: StateFlow<Int> = sidebarWidthRevision
    private val cameraSwitchModeRevision = MutableStateFlow(0)
    internal val cameraSwitchModeRevisionFlow: StateFlow<Int> = cameraSwitchModeRevision
    private val cameraAnimationRevision = MutableStateFlow(0)
    internal val cameraAnimationRevisionFlow: StateFlow<Int> = cameraAnimationRevision
    private val trunkLongPressRevision = MutableStateFlow(0)
    internal val trunkLongPressRevisionFlow: StateFlow<Int> = trunkLongPressRevision
    private val avmCameraActionRevision = MutableStateFlow(0)
    internal val avmCameraActionRevisionFlow: StateFlow<Int> = avmCameraActionRevision
    private val pdcVisualModeRevision = MutableStateFlow(0)
    internal val pdcVisualModeRevisionFlow: StateFlow<Int> = pdcVisualModeRevision
    private val navButtonsRevision = MutableStateFlow(0)
    internal val navButtonsRevisionFlow: StateFlow<Int> = navButtonsRevision
    private val adasDistanceTextSizeRevision = MutableStateFlow(0)
    internal val adasDistanceTextSizeRevisionFlow: StateFlow<Int> = adasDistanceTextSizeRevision
    private val adasDistanceLabelOffsetRevision = MutableStateFlow(0)
    internal val adasDistanceLabelOffsetRevisionFlow: StateFlow<Int> = adasDistanceLabelOffsetRevision
    private val adasPanelVisibleRevision = MutableStateFlow(0)
    internal val adasPanelVisibleRevisionFlow: StateFlow<Int> = adasPanelVisibleRevision
    private val adasAlertsPlacementRevision = MutableStateFlow(0)
    internal val adasAlertsPlacementRevisionFlow: StateFlow<Int> = adasAlertsPlacementRevision
    private val adasBsdAlertOverlayRevision = MutableStateFlow(0)
    internal val adasBsdAlertOverlayRevisionFlow: StateFlow<Int> = adasBsdAlertOverlayRevision
    private val speedLimitPositionRevision = MutableStateFlow(0)
    internal val speedLimitPositionRevisionFlow: StateFlow<Int> = speedLimitPositionRevision
    private val climateControlsRevision = MutableStateFlow(0)
    internal val climateControlsRevisionFlow: StateFlow<Int> = climateControlsRevision
    private val climateCardBgRevision = MutableStateFlow(0)
    internal val climateCardBgRevisionFlow: StateFlow<Int> = climateCardBgRevision
    private val bottomSlotsRevision = MutableStateFlow(0)
    internal val bottomSlotsRevisionFlow: StateFlow<Int> = bottomSlotsRevision
    private val tirePressureConfigRevision = MutableStateFlow(0)
    internal val tirePressureConfigRevisionFlow: StateFlow<Int> = tirePressureConfigRevision
    private val cruisePresetsRevision = MutableStateFlow(0)
    internal val cruisePresetsRevisionFlow: StateFlow<Int> = cruisePresetsRevision
    private val cruisePanelRevision = MutableStateFlow(0)
    internal val cruisePanelRevisionFlow: StateFlow<Int> = cruisePanelRevision
    private val cruisePanelPositionRevision = MutableStateFlow(0)
    internal val cruisePanelPositionRevisionFlow: StateFlow<Int> = cruisePanelPositionRevision
    private val swapTopBottomPanelsRevision = MutableStateFlow(0)
    internal val swapTopBottomPanelsRevisionFlow: StateFlow<Int> = swapTopBottomPanelsRevision
    private val driveModeBarRevision = MutableStateFlow(0)
    internal val driveModeBarRevisionFlow: StateFlow<Int> = driveModeBarRevision
    private val assistPanelRevision = MutableStateFlow(0)
    internal val assistPanelRevisionFlow: StateFlow<Int> = assistPanelRevision
    private val bodyControlsPanelRevision = MutableStateFlow(0)
    internal val bodyControlsPanelRevisionFlow: StateFlow<Int> = bodyControlsPanelRevision
    private val assistPresetsTabRevision = MutableStateFlow(0)
    internal val assistPresetsTabRevisionFlow: StateFlow<Int> = assistPresetsTabRevision
    private val adasPanelsRevision = MutableStateFlow(0)
    internal val adasPanelsRevisionFlow: StateFlow<Int> = adasPanelsRevision
    private val driveModeGlowRevision = MutableStateFlow(0)
    internal val driveModeGlowRevisionFlow: StateFlow<Int> = driveModeGlowRevision
    private val headlightBeamsRevision = MutableStateFlow(0)
    internal val headlightBeamsRevisionFlow: StateFlow<Int> = headlightBeamsRevision
    private val ngpRevision = MutableStateFlow(0)
    internal val ngpRevisionFlow: StateFlow<Int> = ngpRevision
    private val gridColumnsRevision = MutableStateFlow(0)
    internal val gridColumnsRevisionFlow: StateFlow<Int> = gridColumnsRevision
    private val gridRowsRevision = MutableStateFlow(0)
    internal val gridRowsRevisionFlow: StateFlow<Int> = gridRowsRevision
    private val gridRowSpacingRevision = MutableStateFlow(0)
    internal val gridRowSpacingRevisionFlow: StateFlow<Int> = gridRowSpacingRevision
    private val homeIconScaleRevision = MutableStateFlow(0)
    internal val homeIconScaleRevisionFlow: StateFlow<Int> = homeIconScaleRevision
    private val edgeGesturesRevision = MutableStateFlow(0)
    internal val edgeGesturesRevisionFlow: StateFlow<Int> = edgeGesturesRevision

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun edgeGesturesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EDGE_GESTURES_ENABLED, true)

    fun setEdgeGesturesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EDGE_GESTURES_ENABLED, enabled).apply()
        edgeGesturesRevision.value++
    }

    fun edgeGesturesWidthDp(context: Context): Int =
        prefs(context).getInt(KEY_EDGE_GESTURES_WIDTH, 20).coerceIn(15, 35)

    fun setEdgeGesturesWidthDp(context: Context, widthDp: Int) {
        prefs(context).edit().putInt(KEY_EDGE_GESTURES_WIDTH, widthDp.coerceIn(15, 35)).apply()
        edgeGesturesRevision.value++
    }

    fun edgeGestureActionLeft(context: Context): String =
        prefs(context).getString(KEY_EDGE_GESTURE_ACTION_LEFT, EDGE_GESTURE_ACTION_BACK) ?: EDGE_GESTURE_ACTION_BACK

    fun setEdgeGestureActionLeft(context: Context, action: String) {
        prefs(context).edit().putString(KEY_EDGE_GESTURE_ACTION_LEFT, action).apply()
        edgeGesturesRevision.value++
    }

    fun edgeGestureActionRight(context: Context): String =
        prefs(context).getString(KEY_EDGE_GESTURE_ACTION_RIGHT, EDGE_GESTURE_ACTION_HOME) ?: EDGE_GESTURE_ACTION_HOME

    fun setEdgeGestureActionRight(context: Context, action: String) {
        prefs(context).edit().putString(KEY_EDGE_GESTURE_ACTION_RIGHT, action).apply()
        edgeGesturesRevision.value++
    }

    fun edgeGestureActionBottom(context: Context): String =
        prefs(context).getString(KEY_EDGE_GESTURE_ACTION_BOTTOM, EDGE_GESTURE_ACTION_PREVIOUS_APP) ?: EDGE_GESTURE_ACTION_PREVIOUS_APP

    fun setEdgeGestureActionBottom(context: Context, action: String) {
        prefs(context).edit().putString(KEY_EDGE_GESTURE_ACTION_BOTTOM, action).apply()
        edgeGesturesRevision.value++
    }

    fun carPaintId(context: Context): String {
        val raw = prefs(context).getString(KEY_CAR_PAINT, LauncherCarPaint.defaultId)
        return LauncherCarPaint.options.firstOrNull { it.id == raw }?.id ?: LauncherCarPaint.defaultId
    }

    fun setCarPaintId(context: Context, paintId: String) {
        prefs(context).edit().putString(KEY_CAR_PAINT, paintId).apply()
    }

    fun defaultMediaPackage(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_MEDIA, null)?.takeIf { it.isNotBlank() }

    fun setDefaultMediaPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_DEFAULT_MEDIA, packageName).apply()
    }

    fun mediaCardAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_MEDIA_CARD_ALPHA, MEDIA_CARD_ALPHA_DEFAULT)
            .coerceIn(MEDIA_CARD_ALPHA_MIN, MEDIA_CARD_ALPHA_MAX)

    fun setMediaCardAlpha(context: Context, alpha: Float) {
        val next = alpha.coerceIn(MEDIA_CARD_ALPHA_MIN, MEDIA_CARD_ALPHA_MAX)
        prefs(context).edit().putFloat(KEY_MEDIA_CARD_ALPHA, next).apply()
        mediaCardAlphaRevision.value++
    }

    fun mediaCardHeightScale(context: Context): Float =
        prefs(context).getFloat(KEY_MEDIA_CARD_HEIGHT_SCALE, MEDIA_CARD_HEIGHT_SCALE_DEFAULT)
            .coerceIn(MEDIA_CARD_HEIGHT_SCALE_MIN, MEDIA_CARD_HEIGHT_SCALE_MAX)

    fun setMediaCardHeightScale(context: Context, scale: Float) {
        val next = scale.coerceIn(MEDIA_CARD_HEIGHT_SCALE_MIN, MEDIA_CARD_HEIGHT_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_MEDIA_CARD_HEIGHT_SCALE, next).apply()
        mediaCardHeightScaleRevision.value++
    }

    fun mediaMiniPlayerVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, true)

    fun setMediaMiniPlayerVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, visible).apply()
        mediaMiniPlayerRevision.value++
    }

    fun closePlayerAfterPlayback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOSE_PLAYER_AFTER_PLAYBACK, false)

    fun setClosePlayerAfterPlayback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOSE_PLAYER_AFTER_PLAYBACK, enabled).apply()
        closePlayerAfterPlaybackRevision.value++
    }

    fun disableMediaCardCoverColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISABLE_MEDIA_CARD_COVER_COLOR, false)

    fun setDisableMediaCardCoverColor(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISABLE_MEDIA_CARD_COVER_COLOR, disabled).apply()
        disableMediaCardCoverColorRevision.value++
    }

    fun dockIconScale(context: Context): Float =
        prefs(context).getFloat(KEY_DOCK_ICON_SCALE, DOCK_ICON_SCALE_DEFAULT)
            .coerceIn(DOCK_ICON_SCALE_MIN, DOCK_ICON_SCALE_MAX)

    fun setDockIconScale(context: Context, scale: Float) {
        val next = scale.coerceIn(DOCK_ICON_SCALE_MIN, DOCK_ICON_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_DOCK_ICON_SCALE, next).apply()
        dockIconScaleRevision.value++
    }

    fun topBarHeightDp(context: Context): Int =
        prefs(context).getInt(KEY_TOP_BAR_HEIGHT, TOP_BAR_HEIGHT_DEFAULT)
            .coerceIn(TOP_BAR_HEIGHT_MIN, TOP_BAR_HEIGHT_MAX)

    fun setTopBarHeightDp(context: Context, heightDp: Int) {
        val next = heightDp.coerceIn(TOP_BAR_HEIGHT_MIN, TOP_BAR_HEIGHT_MAX)
        prefs(context).edit().putInt(KEY_TOP_BAR_HEIGHT, next).apply()
        topBarHeightRevision.value++
    }

    fun topBarLeftIndicatorsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOP_BAR_LEFT_INDICATORS_VISIBLE, true)

    fun setTopBarLeftIndicatorsVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOP_BAR_LEFT_INDICATORS_VISIBLE, visible).apply()
        topBarLeftIndicatorsRevision.value++
    }

    fun topBarIndicatorsBorderEnabled(context: Context): Boolean =
        prefs(context).getBoolean("top_bar_indicators_border_enabled", true)

    fun setTopBarIndicatorsBorderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("top_bar_indicators_border_enabled", enabled).apply()
        topBarIndicatorsBorderRevision.value++
    }

    fun topBarItems(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_TOP_BAR_ITEMS, null) ?: return DEFAULT_TOP_BAR_ITEMS
        if (raw.isBlank()) return emptyList()
        return raw.split('|').filter { it.isNotBlank() }
    }

    fun setTopBarItems(context: Context, items: List<String>) {
        val encoded = items.joinToString("|")
        prefs(context).edit().putString(KEY_TOP_BAR_ITEMS, encoded).apply()
        topBarItemsRevision.value++
    }

    fun addTopBarItem(context: Context, itemId: String) {
        val current = topBarItems(context).toMutableList()
        val finalId = if (itemId == "spacer" || itemId.startsWith("spacer")) {
            "spacer_${System.currentTimeMillis()}"
        } else {
            if (current.contains(itemId)) return
            itemId
        }
        current.add(finalId)
        setTopBarItems(context, current)
    }

    fun removeTopBarItem(context: Context, itemId: String) {
        val current = topBarItems(context).toMutableList()
        if (current.remove(itemId)) {
            setTopBarItems(context, current)
        }
    }

    fun reorderTopBarItem(context: Context, fromIndex: Int, toIndex: Int) {
        val current = topBarItems(context).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        setTopBarItems(context, current)
    }

    fun bottomBarHeightDp(context: Context): Int =
        prefs(context).getInt(KEY_BOTTOM_BAR_HEIGHT, BOTTOM_BAR_HEIGHT_DEFAULT)
            .coerceIn(BOTTOM_BAR_HEIGHT_MIN, BOTTOM_BAR_HEIGHT_MAX)

    fun setBottomBarHeightDp(context: Context, heightDp: Int) {
        val next = heightDp.coerceIn(BOTTOM_BAR_HEIGHT_MIN, BOTTOM_BAR_HEIGHT_MAX)
        prefs(context).edit().putInt(KEY_BOTTOM_BAR_HEIGHT, next).apply()
        bottomBarHeightRevision.value++
    }

    fun floatingHomeSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_FLOATING_HOME_SIZE, FLOATING_HOME_SIZE_DEFAULT)
            .coerceIn(FLOATING_HOME_SIZE_MIN, FLOATING_HOME_SIZE_MAX)

    fun setFloatingHomeSizeDp(context: Context, sizeDp: Int) {
        val next = sizeDp.coerceIn(FLOATING_HOME_SIZE_MIN, FLOATING_HOME_SIZE_MAX)
        prefs(context).edit().putInt(KEY_FLOATING_HOME_SIZE, next).apply()
        floatingHomeSizeRevision.value++
    }

    fun gridColumns(context: Context): Int =
        prefs(context).getInt(KEY_GRID_COLUMNS, GRID_COLUMNS_DEFAULT)
            .coerceIn(GRID_COLUMNS_MIN, GRID_COLUMNS_MAX)

    fun setGridColumns(context: Context, cols: Int) {
        val next = cols.coerceIn(GRID_COLUMNS_MIN, GRID_COLUMNS_MAX)
        prefs(context).edit().putInt(KEY_GRID_COLUMNS, next).apply()
        gridColumnsRevision.value++
    }

    fun gridRows(context: Context): Int =
        prefs(context).getInt(KEY_GRID_ROWS, GRID_ROWS_DEFAULT)
            .coerceIn(GRID_ROWS_MIN, GRID_ROWS_MAX)

    fun setGridRows(context: Context, rows: Int) {
        val next = rows.coerceIn(GRID_ROWS_MIN, GRID_ROWS_MAX)
        prefs(context).edit().putInt(KEY_GRID_ROWS, next).apply()
        gridRowsRevision.value++
    }

    fun gridRowSpacingDp(context: Context): Int =
        prefs(context).getInt(KEY_GRID_ROW_SPACING, GRID_ROW_SPACING_DEFAULT)
            .coerceIn(GRID_ROW_SPACING_MIN, GRID_ROW_SPACING_MAX)

    fun setGridRowSpacingDp(context: Context, spacingDp: Int) {
        val next = spacingDp.coerceIn(GRID_ROW_SPACING_MIN, GRID_ROW_SPACING_MAX)
        prefs(context).edit().putInt(KEY_GRID_ROW_SPACING, next).apply()
        gridRowSpacingRevision.value++
    }

    fun homeIconScale(context: Context): Float =
        prefs(context).getFloat(KEY_HOME_ICON_SCALE, HOME_ICON_SCALE_DEFAULT)
            .coerceIn(HOME_ICON_SCALE_MIN, HOME_ICON_SCALE_MAX)

    fun setHomeIconScale(context: Context, scale: Float) {
        val next = scale.coerceIn(HOME_ICON_SCALE_MIN, HOME_ICON_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_HOME_ICON_SCALE, next).apply()
        homeIconScaleRevision.value++
    }

    fun floatingHomeVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOATING_HOME_VISIBLE, true)

    fun setFloatingHomeVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOATING_HOME_VISIBLE, visible).apply()
        if (visible) {
            LauncherOverlayBar.show(context)
            // Ensure slot 0 is cleared if occupied
            val slots = bottomSlotsUnified(context).toMutableList()
            if (slots[0] != null) {
                val occupied = slots[0]
                slots[0] = null
                val freeIdx = (1 until GRID_SLOTS_TOTAL_COUNT).firstOrNull { slots[it] == null }
                if (freeIdx != null) slots[freeIdx] = occupied
                setBottomSlotsUnified(context, slots)
            }
        } else {
            LauncherOverlayBar.hide()
        }
        floatingHomeVisibleRevision.value++
    }

    fun carModelScale(context: Context): Float =
        prefs(context).getFloat(KEY_CAR_MODEL_SCALE, CAR_MODEL_SCALE_DEFAULT)
            .coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)

    fun setCarModelScale(context: Context, scale: Float) {
        val next = scale.coerceIn(CAR_MODEL_SCALE_MIN, CAR_MODEL_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_CAR_MODEL_SCALE, next).apply()
        carModelScaleRevision.value++
    }

    fun navButtonsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NAV_BUTTONS_VISIBLE, false)

    fun setNavButtonsVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_NAV_BUTTONS_VISIBLE, visible).apply()
        val slots = bottomSlotsUnified(context).toMutableList()
        if (visible) {
            if ("home_nav" !in slots) {
                val firstEmpty = (1 until GRID_SLOTS_TOTAL_COUNT).firstOrNull { slots[it] == null }
                if (firstEmpty != null) slots[firstEmpty] = "home_nav"
            }
            if ("back_nav" !in slots) {
                val secondEmpty = (1 until GRID_SLOTS_TOTAL_COUNT).firstOrNull { slots[it] == null }
                if (secondEmpty != null) slots[secondEmpty] = "back_nav"
            }
        } else {
            for (i in slots.indices) {
                if (slots[i] == "home_nav" || slots[i] == "back_nav") {
                    slots[i] = null
                }
            }
        }
        setBottomSlotsUnified(context, slots)
        navButtonsRevision.value++
    }

    fun adasDistanceTextSize(context: Context): Int =
        prefs(context).getInt(KEY_ADAS_DISTANCE_TEXT_SIZE, ADAS_DISTANCE_TEXT_SIZE_DEFAULT)
            .coerceIn(ADAS_DISTANCE_TEXT_SIZE_MIN, ADAS_DISTANCE_TEXT_SIZE_MAX)

    fun setAdasDistanceTextSize(context: Context, sizeSp: Int) {
        val next = sizeSp.coerceIn(ADAS_DISTANCE_TEXT_SIZE_MIN, ADAS_DISTANCE_TEXT_SIZE_MAX)
        prefs(context).edit().putInt(KEY_ADAS_DISTANCE_TEXT_SIZE, next).apply()
        adasDistanceTextSizeRevision.value++
    }

    fun adasDistanceLabelOffset(context: Context): Float =
        prefs(context).getFloat(KEY_ADAS_DISTANCE_LABEL_OFFSET, ADAS_DISTANCE_LABEL_OFFSET_DEFAULT)
            .coerceIn(ADAS_DISTANCE_LABEL_OFFSET_MIN, ADAS_DISTANCE_LABEL_OFFSET_MAX)

    fun setAdasDistanceLabelOffset(context: Context, offsetRatio: Float) {
        val next = offsetRatio.coerceIn(ADAS_DISTANCE_LABEL_OFFSET_MIN, ADAS_DISTANCE_LABEL_OFFSET_MAX)
        prefs(context).edit().putFloat(KEY_ADAS_DISTANCE_LABEL_OFFSET, next).apply()
        adasDistanceLabelOffsetRevision.value++
    }

    fun adasPanelVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADAS_PANEL_VISIBLE, true)

    fun setAdasPanelVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAS_PANEL_VISIBLE, visible).apply()
        adasPanelVisibleRevision.value++
    }

    fun adasAlertsInLeftPanel(context: Context): Boolean = true

    fun setAdasAlertsInLeftPanel(context: Context, inLeftPanel: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAS_ALERTS_IN_LEFT_PANEL, true).apply()
        adasAlertsPlacementRevision.value++
    }

    fun adasBsdAlertOverlayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADAS_BSD_ALERT_OVERLAY, true)

    fun setAdasBsdAlertOverlayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAS_BSD_ALERT_OVERLAY, enabled).apply()
        adasBsdAlertOverlayRevision.value++
    }

    fun tirePressureOnCarEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_ON_CAR_ENABLED, true)

    fun setTirePressureOnCarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_ON_CAR_ENABLED, enabled).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureOnlyLow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_ONLY_LOW, true)

    fun setTirePressureOnlyLow(context: Context, onlyLow: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_ONLY_LOW, onlyLow).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureOnlyTopView(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_ONLY_TOP_VIEW, false)

    fun setTirePressureOnlyTopView(context: Context, onlyTopView: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_ONLY_TOP_VIEW, onlyTopView).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureLowThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_TIRE_PRESSURE_LOW_THRESHOLD, TIRE_PRESSURE_LOW_THRESHOLD_DEFAULT)
            .coerceIn(TIRE_PRESSURE_LOW_THRESHOLD_MIN, TIRE_PRESSURE_LOW_THRESHOLD_MAX)

    fun setTirePressureLowThreshold(context: Context, threshold: Float) {
        val next = threshold.coerceIn(TIRE_PRESSURE_LOW_THRESHOLD_MIN, TIRE_PRESSURE_LOW_THRESHOLD_MAX)
        prefs(context).edit().putFloat(KEY_TIRE_PRESSURE_LOW_THRESHOLD, next).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureAlwaysInSettings(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_ALWAYS_IN_SETTINGS, false)

    fun setTirePressureAlwaysInSettings(context: Context, always: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_ALWAYS_IN_SETTINGS, always).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureInertiaAnimationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_INERTIA_ANIMATION_ENABLED, true)

    fun setTirePressureInertiaAnimationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_INERTIA_ANIMATION_ENABLED, enabled).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressureBadgeShape(context: Context): LauncherTireBadgeShape =
        LauncherTireBadgeShape.fromCode(prefs(context).getString(KEY_TIRE_PRESSURE_BADGE_SHAPE, null))

    fun setTirePressureBadgeShape(context: Context, shape: LauncherTireBadgeShape) {
        prefs(context).edit().putString(KEY_TIRE_PRESSURE_BADGE_SHAPE, shape.code).apply()
        tirePressureConfigRevision.value++
    }

    fun tirePressurePersistEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIRE_PRESSURE_PERSIST_ENABLED, true)

    fun setTirePressurePersistEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TIRE_PRESSURE_PERSIST_ENABLED, enabled).apply()
        tirePressureConfigRevision.value++
    }

    fun saveTirePressures(context: Context, state: LauncherTireState) {
        if (!tirePressurePersistEnabled(context)) return
        val edit = prefs(context).edit()
        state.fl.pressureBar?.let { edit.putFloat(KEY_SAVED_TIRE_PRESSURE_FL, it) }
        state.fr.pressureBar?.let { edit.putFloat(KEY_SAVED_TIRE_PRESSURE_FR, it) }
        state.rl.pressureBar?.let { edit.putFloat(KEY_SAVED_TIRE_PRESSURE_RL, it) }
        state.rr.pressureBar?.let { edit.putFloat(KEY_SAVED_TIRE_PRESSURE_RR, it) }
        state.fl.temperatureC?.let { edit.putInt(KEY_SAVED_TIRE_TEMP_FL, it) }
        state.fr.temperatureC?.let { edit.putInt(KEY_SAVED_TIRE_TEMP_FR, it) }
        state.rl.temperatureC?.let { edit.putInt(KEY_SAVED_TIRE_TEMP_RL, it) }
        state.rr.temperatureC?.let { edit.putInt(KEY_SAVED_TIRE_TEMP_RR, it) }
        edit.apply()
    }

    fun loadSavedTirePressures(context: Context): LauncherTireState? {
        if (!tirePressurePersistEnabled(context)) return null
        val p = prefs(context)
        fun sanitizeBar(bar: Float?): Float? {
            if (bar == null || !bar.isFinite() || bar <= 0f) return null
            return if (bar > 10f) bar / 36f else bar
        }
        val flP = sanitizeBar(if (p.contains(KEY_SAVED_TIRE_PRESSURE_FL)) p.getFloat(KEY_SAVED_TIRE_PRESSURE_FL, -1f) else null)
        val frP = sanitizeBar(if (p.contains(KEY_SAVED_TIRE_PRESSURE_FR)) p.getFloat(KEY_SAVED_TIRE_PRESSURE_FR, -1f) else null)
        val rlP = sanitizeBar(if (p.contains(KEY_SAVED_TIRE_PRESSURE_RL)) p.getFloat(KEY_SAVED_TIRE_PRESSURE_RL, -1f) else null)
        val rrP = sanitizeBar(if (p.contains(KEY_SAVED_TIRE_PRESSURE_RR)) p.getFloat(KEY_SAVED_TIRE_PRESSURE_RR, -1f) else null)

        val flT = if (p.contains(KEY_SAVED_TIRE_TEMP_FL)) p.getInt(KEY_SAVED_TIRE_TEMP_FL, -100).takeIf { it != -100 } else null
        val frT = if (p.contains(KEY_SAVED_TIRE_TEMP_FR)) p.getInt(KEY_SAVED_TIRE_TEMP_FR, -100).takeIf { it != -100 } else null
        val rlT = if (p.contains(KEY_SAVED_TIRE_TEMP_RL)) p.getInt(KEY_SAVED_TIRE_TEMP_RL, -100).takeIf { it != -100 } else null
        val rrT = if (p.contains(KEY_SAVED_TIRE_TEMP_RR)) p.getInt(KEY_SAVED_TIRE_TEMP_RR, -100).takeIf { it != -100 } else null

        if (flP == null && frP == null && rlP == null && rrP == null) return null

        return LauncherTireState(
            fl = LauncherTireWheelState(pressureBar = flP, temperatureC = flT, available = true),
            fr = LauncherTireWheelState(pressureBar = frP, temperatureC = frT, available = true),
            rl = LauncherTireWheelState(pressureBar = rlP, temperatureC = rlT, available = true),
            rr = LauncherTireWheelState(pressureBar = rrP, temperatureC = rrT, available = true),
        )
    }

    fun speedLimitXRatio(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED_LIMIT_X_RATIO, 0.5f).coerceIn(0f, 1f)

    fun speedLimitYRatio(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED_LIMIT_Y_RATIO, 0.14f).coerceIn(0f, 1f)

    fun setSpeedLimitPosition(context: Context, xRatio: Float, yRatio: Float) {
        prefs(context).edit()
            .putFloat(KEY_SPEED_LIMIT_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_SPEED_LIMIT_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .apply()
        speedLimitPositionRevision.value++
    }

    fun climateControlsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLIMATE_VISIBLE, true)

    fun setClimateControlsVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLIMATE_VISIBLE, visible).apply()
        climateControlsRevision.value++
    }

    fun climateControlsScale(context: Context): Float =
        prefs(context).getFloat(KEY_CLIMATE_SCALE, CLIMATE_SCALE_DEFAULT)
            .coerceIn(CLIMATE_SCALE_MIN, CLIMATE_SCALE_MAX)

    fun setClimateControlsScale(context: Context, scale: Float) {
        val next = scale.coerceIn(CLIMATE_SCALE_MIN, CLIMATE_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_CLIMATE_SCALE, next).apply()
        climateControlsRevision.value++
    }

    fun climateCardBgVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLIMATE_CARD_BG_VISIBLE, false)

    fun setClimateCardBgVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLIMATE_CARD_BG_VISIBLE, visible).apply()
        climateCardBgRevision.value++
    }

    fun bottomSlotsUnified(context: Context): List<String?> {
        val raw = prefs(context).getString(KEY_BOTTOM_SLOTS_UNIFIED, null)
        if (raw.isNullOrBlank()) return DEFAULT_BOTTOM_SLOTS_UNIFIED
        var list = raw.split(',').map { if (it.trim() == "null" || it.isBlank()) null else it.trim() }.toMutableList()
        if (list.size < GRID_SLOTS_TOTAL_COUNT) {
            while (list.size < GRID_SLOTS_TOTAL_COUNT) list.add(null)
        } else if (list.size > GRID_SLOTS_TOTAL_COUNT) {
            list = list.take(GRID_SLOTS_TOTAL_COUNT).toMutableList()
        }
        if (floatingHomeVisible(context)) {
            list[0] = null // Slot 0 blocked when floating Home button is ON
        }
        return list
    }

    fun setBottomSlotsUnified(context: Context, slots: List<String?>) {
        val raw = slots.take(GRID_SLOTS_TOTAL_COUNT).joinToString(",") { it ?: "null" }
        prefs(context).edit().putString(KEY_BOTTOM_SLOTS_UNIFIED, raw).apply()
        bottomSlotsRevision.value++
    }

    fun saveBottomSlotsPreset(context: Context, presetKey: String) {
        val currentSlots = bottomSlotsUnified(context)
        val raw = currentSlots.take(GRID_SLOTS_TOTAL_COUNT).joinToString(",") { it ?: "null" }
        prefs(context).edit().putString("bottom_slots_preset_$presetKey", raw).apply()
        bottomSlotsRevision.value++
    }

    fun hasBottomSlotsPreset(context: Context, presetKey: String): Boolean {
        val raw = prefs(context).getString("bottom_slots_preset_$presetKey", null)
        return !raw.isNullOrBlank()
    }

    fun applyBottomSlotsPreset(context: Context, presetKey: String): Boolean {
        val raw = prefs(context).getString("bottom_slots_preset_$presetKey", null)
        if (raw.isNullOrBlank()) return false
        var list = raw.split(',').map { if (it.trim() == "null" || it.isBlank()) null else it.trim() }.toMutableList()
        if (list.size < GRID_SLOTS_TOTAL_COUNT) {
            while (list.size < GRID_SLOTS_TOTAL_COUNT) list.add(null)
        } else if (list.size > GRID_SLOTS_TOTAL_COUNT) {
            list = list.take(GRID_SLOTS_TOTAL_COUNT).toMutableList()
        }
        setBottomSlotsUnified(context, list)
        return true
    }

    fun setButtonInUnifiedSlot(context: Context, slotIndex: Int, buttonId: String?) {
        val slots = bottomSlotsUnified(context).toMutableList()
        val minSlot = if (floatingHomeVisible(context)) 1 else 0
        if (slotIndex in minSlot until GRID_SLOTS_TOTAL_COUNT) {
            slots[slotIndex] = buttonId
            val span = if (buttonId == "temp_driver" || buttonId == "temp_pass") 2 else 1
            if (span > 1 && slotIndex + 1 < GRID_SLOTS_TOTAL_COUNT) {
                slots[slotIndex + 1] = null
            }
            setBottomSlotsUnified(context, slots)
        }
    }

    fun reorderUnifiedSlot(
        context: Context,
        itemId: String,
        currentIndex: Int,
        targetIndex: Int,
    ) {
        val slots = bottomSlotsUnified(context).toMutableList()
        val floatingHomeOn = floatingHomeVisible(context)
        val minSlot = if (floatingHomeOn) 1 else 0
        val itemSpan = if (itemId == "temp_driver" || itemId == "temp_pass" || itemId == "audio_volume") 2 else 1
        val maxTarget = if (itemSpan > 1) GRID_SLOTS_TOTAL_COUNT - 2 else GRID_SLOTS_TOTAL_COUNT - 1
        val validTarget = targetIndex.coerceIn(minSlot, maxTarget)
        if (validTarget == currentIndex) return

        val targetOld = slots[validTarget]
        slots[currentIndex] = targetOld
        slots[validTarget] = itemId

        if (itemSpan > 1 && validTarget + 1 < GRID_SLOTS_TOTAL_COUNT) {
            val absorbedOld = slots[validTarget + 1]
            slots[validTarget + 1] = null
            if (absorbedOld != null && slots[currentIndex] == null) {
                slots[currentIndex] = absorbedOld
            }
        }

        if (floatingHomeOn) {
            slots[0] = null
        }

        setBottomSlotsUnified(context, slots)
    }

    fun resetBottomSlotsToDefault(context: Context) {
        prefs(context).edit()
            .remove(KEY_BOTTOM_SLOTS_UNIFIED)
            .remove("bottom_slots_left")
            .remove("bottom_slots_right")
            .apply()
        bottomSlotsRevision.value++
    }

    fun sidebarWidthDp(context: Context): Int =
        prefs(context).getInt(KEY_SIDEBAR_WIDTH, SIDEBAR_WIDTH_DEFAULT)
            .coerceIn(SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX)

    fun setSidebarWidthDp(context: Context, widthDp: Int) {
        val next = widthDp.coerceIn(SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX)
        prefs(context).edit().putInt(KEY_SIDEBAR_WIDTH, next).apply()
        sidebarWidthRevision.value++
    }

    fun cameraSwitchMode(context: Context): Int =
        prefs(context).getInt(KEY_CAMERA_SWITCH_MODE, CAMERA_SWITCH_MODE_DEFAULT)
            .coerceIn(0, 1)

    fun setCameraSwitchMode(context: Context, mode: Int) {
        val next = mode.coerceIn(0, 1)
        prefs(context).edit().putInt(KEY_CAMERA_SWITCH_MODE, next).apply()
        cameraSwitchModeRevision.value++
    }

    @Suppress("UNUSED_PARAMETER")
    fun cameraAnimationEnabled(context: Context): Boolean = true

    fun setCameraAnimationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CAMERA_ANIMATION_ENABLED, enabled).apply()
        cameraAnimationRevision.value++
    }

    fun trunkLongPressEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRUNK_LONG_PRESS_ENABLED, false)

    fun setTrunkLongPressEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRUNK_LONG_PRESS_ENABLED, enabled).apply()
        trunkLongPressRevision.value++
    }

    fun avmCameraAction(context: Context): Int =
        prefs(context).getInt(KEY_AVM_CAMERA_ACTION, AVM_CAMERA_ACTION_DEFAULT)
            .coerceIn(0, 2)

    fun setAvmCameraAction(context: Context, action: Int) {
        val next = action.coerceIn(0, 2)
        prefs(context).edit().putInt(KEY_AVM_CAMERA_ACTION, next).apply()
        avmCameraActionRevision.value++
    }

    fun pdcVisualMode(context: Context): Int =
        prefs(context).getInt(KEY_PDC_VISUAL_MODE, PDC_VISUAL_ARCS)
            .coerceIn(PDC_VISUAL_ARCS, PDC_VISUAL_OFF)

    fun setPdcVisualMode(context: Context, mode: Int) {
        val next = mode.coerceIn(PDC_VISUAL_ARCS, PDC_VISUAL_OFF)
        prefs(context).edit().putInt(KEY_PDC_VISUAL_MODE, next).apply()
        pdcVisualModeRevision.value++
    }

    fun fuelShowsRange(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FUEL_SHOWS_RANGE, false)

    fun setFuelShowsRange(context: Context, showRange: Boolean) {
        prefs(context).edit().putBoolean(KEY_FUEL_SHOWS_RANGE, showRange).apply()
    }

    fun cruisePresetsKmh(context: Context): List<Int> {
        val parsed = prefs(context).getString(KEY_CRUISE_PRESETS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull()?.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH) }
        return when {
            parsed != null && parsed.size == 4 -> parsed
            parsed != null && parsed.size == 3 -> parsed + listOf(60)
            else -> CRUISE_PRESET_DEFAULTS_KMH
        }
    }

    fun setCruisePresetKmh(context: Context, index: Int, kmh: Int) {
        val next = cruisePresetsKmh(context).toMutableList()
        if (index !in next.indices) return
        next[index] = kmh.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH)
        prefs(context).edit().putString(KEY_CRUISE_PRESETS, next.joinToString(",")).apply()
        cruisePresetsRevision.value++
    }

    fun lastCruiseSpeedKmh(context: Context): Int {
        val saved = prefs(context).getInt(KEY_LAST_CRUISE_SPEED, 0)
        if (saved in CRUISE_PRESET_MIN_KMH..CRUISE_PRESET_MAX_KMH) {
            return saved
        }
        return cruisePresetsKmh(context).firstOrNull() ?: 110
    }

    fun setLastCruiseSpeedKmh(context: Context, kmh: Int) {
        val validKmh = kmh.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH)
        prefs(context).edit().putInt(KEY_LAST_CRUISE_SPEED, validKmh).apply()
        cruisePresetsRevision.value++
    }

    fun cruisePanelVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CRUISE_PANEL_VISIBLE, false)

    fun setCruisePanelVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_CRUISE_PANEL_VISIBLE, visible).apply()
        cruisePanelRevision.value++
    }

    fun cruisePanelPosition(context: Context): String =
        prefs(context).getString(KEY_CRUISE_PANEL_POSITION, "bottom") ?: "bottom"

    fun setCruisePanelPosition(context: Context, position: String) {
        prefs(context).edit().putString(KEY_CRUISE_PANEL_POSITION, position).apply()
        cruisePanelPositionRevision.value++
    }

    fun swapTopBottomPanels(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SWAP_TOP_BOTTOM_PANELS, false)

    fun setSwapTopBottomPanels(context: Context, swap: Boolean) {
        prefs(context).edit().putBoolean(KEY_SWAP_TOP_BOTTOM_PANELS, swap).apply()
        swapTopBottomPanelsRevision.value++
    }

    fun driveModeBarVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DRIVE_MODE_BAR_VISIBLE, true)

    fun setDriveModeBarVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_DRIVE_MODE_BAR_VISIBLE, visible).apply()
        driveModeBarRevision.value++
    }

    fun assistPanelVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASSIST_PANEL_VISIBLE, true)

    fun setAssistPanelVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASSIST_PANEL_VISIBLE, visible).apply()
        assistPanelRevision.value++
    }

    fun bodyControlsPanelVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BODY_CONTROLS_PANEL_VISIBLE, true)

    fun setBodyControlsPanelVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_BODY_CONTROLS_PANEL_VISIBLE, visible).apply()
        bodyControlsPanelRevision.value++
    }

    fun assistPresetsActiveTab(context: Context): String =
        prefs(context).getString(KEY_ASSIST_PRESETS_ACTIVE_TAB, "assist") ?: "assist"

    fun setAssistPresetsActiveTab(context: Context, tab: String) {
        prefs(context).edit().putString(KEY_ASSIST_PRESETS_ACTIVE_TAB, tab).apply()
        assistPresetsTabRevision.value++
    }

    fun adasTopPanels(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ADAS_TOP_PANELS, null)
        if (raw != null) {
            val list = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
            return list.map { if (it == "presets") "assist" else it }.distinct()
        }
        return listOf("drive_mode")
    }

    fun adasBottomPanels(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ADAS_BOTTOM_PANELS, null)
        val list = if (raw != null) {
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("assist", "body_controls")
        }
        val mapped = list.map { if (it == "presets") "assist" else it }.distinct().toMutableList()
        val top = adasTopPanels(context)
        if (bodyControlsPanelVisible(context) && "body_controls" !in top && "body_controls" !in mapped) {
            mapped.add("body_controls")
        }
        return mapped.filter { it !in top }
    }

    fun setAdasPanelsLayout(context: Context, top: List<String>, bottom: List<String>) {
        val cleanTop = top.map { if (it == "presets") "assist" else it }.distinct()
        val cleanBottom = bottom.map { if (it == "presets") "assist" else it }.distinct().filter { it !in cleanTop }
        prefs(context).edit()
            .putString(KEY_ADAS_TOP_PANELS, cleanTop.joinToString(","))
            .putString(KEY_ADAS_BOTTOM_PANELS, cleanBottom.joinToString(","))
            .apply()
        adasPanelsRevision.value++
    }

    fun resetAdasPanelsLayout(context: Context) {
        prefs(context).edit()
            .remove(KEY_ADAS_TOP_PANELS)
            .remove(KEY_ADAS_BOTTOM_PANELS)
            .apply()
        adasPanelsRevision.value++
    }

    fun driveModeGlowVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DRIVE_MODE_GLOW_VISIBLE, true)

    fun setDriveModeGlowVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_DRIVE_MODE_GLOW_VISIBLE, visible).apply()
        driveModeGlowRevision.value++
    }

    fun headlightBeamsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HEADLIGHT_BEAMS_VISIBLE, true)

    fun setHeadlightBeamsVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_HEADLIGHT_BEAMS_VISIBLE, visible).apply()
        headlightBeamsRevision.value++
    }

    fun ngpEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NGP_ENABLED, true)

    fun setNgpEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NGP_ENABLED, enabled).apply()
        ngpRevision.value++
    }

    fun timeGapLevel(context: Context): Int =
        prefs(context).getInt(KEY_TIME_GAP_LEVEL, 2).coerceIn(1, 3)

    fun setTimeGapLevel(context: Context, level: Int) {
        val valid = level.coerceIn(1, 3)
        prefs(context).edit().putInt(KEY_TIME_GAP_LEVEL, valid).apply()
        cruisePresetsRevision.value++
    }

    fun cruiseIncreaseIntervalMs(context: Context): Long =
        prefs(context).getLong(KEY_CRUISE_INCREASE_INTERVAL, 150L).coerceIn(50L, 1500L)

    fun setCruiseIncreaseIntervalMs(context: Context, intervalMs: Long) {
        val valid = intervalMs.coerceIn(50L, 1500L)
        prefs(context).edit().putLong(KEY_CRUISE_INCREASE_INTERVAL, valid).apply()
        cruisePresetsRevision.value++
    }

    fun cruiseDecreaseIntervalMs(context: Context): Long =
        prefs(context).getLong(KEY_CRUISE_DECREASE_INTERVAL, 150L).coerceIn(50L, 1500L)

    fun setCruiseDecreaseIntervalMs(context: Context, intervalMs: Long) {
        val valid = intervalMs.coerceIn(50L, 1500L)
        prefs(context).edit().putLong(KEY_CRUISE_DECREASE_INTERVAL, valid).apply()
        cruisePresetsRevision.value++
    }

    fun fuelPriceRub(context: Context): Float =
        prefs(context).getFloat(KEY_FUEL_PRICE, FUEL_PRICE_DEFAULT).coerceIn(FUEL_PRICE_MIN, FUEL_PRICE_MAX)

    fun setFuelPriceRub(context: Context, price: Float) {
        val rounded = kotlin.math.round(price * 10f) / 10f
        val clamped = rounded.coerceIn(FUEL_PRICE_MIN, FUEL_PRICE_MAX)
        prefs(context).edit().putFloat(KEY_FUEL_PRICE, clamped).apply()
        cruisePresetsRevision.value++
    }

    fun isFullscreenLaunch(context: Context, packageName: String): Boolean =
        appLaunchMode(context, packageName) == LauncherAppLaunchMode.FULLSCREEN

    fun fullscreenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FULLSCREEN, emptySet()).orEmpty()

    fun fullWidthPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FULL_WIDTH, emptySet()).orEmpty()

    fun appLaunchMode(context: Context, packageName: String): LauncherAppLaunchMode {
        if (packageName.isBlank()) return LauncherAppLaunchMode.EMBEDDED
        if (packageName in fullscreenPackages(context)) return LauncherAppLaunchMode.FULLSCREEN
        if (packageName in fullWidthPackages(context)) return LauncherAppLaunchMode.FULL_WIDTH
        return LauncherAppLaunchMode.EMBEDDED
    }

    fun setAppLaunchMode(context: Context, packageName: String, mode: LauncherAppLaunchMode) {
        if (packageName.isBlank()) return
        val full = fullscreenPackages(context).toMutableSet()
        val fw = fullWidthPackages(context).toMutableSet()
        when (mode) {
            LauncherAppLaunchMode.FULLSCREEN -> {
                full.add(packageName)
                fw.remove(packageName)
            }
            LauncherAppLaunchMode.FULL_WIDTH -> {
                fw.add(packageName)
                full.remove(packageName)
            }
            LauncherAppLaunchMode.EMBEDDED -> {
                full.remove(packageName)
                fw.remove(packageName)
            }
        }
        prefs(context).edit()
            .putStringSet(KEY_FULLSCREEN, full)
            .putStringSet(KEY_FULL_WIDTH, fw)
            .apply()
    }

    fun setFullscreenLaunch(context: Context, packageName: String, enabled: Boolean) {
        setAppLaunchMode(
            context,
            packageName,
            if (enabled) LauncherAppLaunchMode.FULLSCREEN else LauncherAppLaunchMode.EMBEDDED,
        )
    }

    fun hiddenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    fun hidePackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.add(packageName)
        setHiddenPackages(context, next)
    }

    fun showPackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.remove(packageName)
        setHiddenPackages(context, next)
    }

    fun unhideAllPackages(context: Context) {
        setHiddenPackages(context, emptySet())
    }

    fun gridPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_GRID, null), GRID_SLOT_COUNT)

    fun dockPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_DOCK, null), DOCK_SLOT_COUNT)
            .mapIndexed { index, pkg ->
                pkg.ifBlank { DEFAULT_DOCK.getOrElse(index) { "" } }
            }

    fun setGridSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until GRID_SLOT_COUNT) return
        val slots = gridPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_GRID, encodeSlots(slots)).apply()
    }

    fun setDockSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until DOCK_SLOT_COUNT) return
        val slots = dockPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_DOCK, encodeSlots(slots)).apply()
    }

    fun clearGridSlot(context: Context, slotIndex: Int) {
        setGridSlot(context, slotIndex, "")
    }

    fun gridSlotEntries(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
    ): List<LaunchableAppEntry?> {
        val byPackage = allVisible.associateBy { it.packageName }
        return pinned.map { pkg ->
            if (pkg.isBlank()) null else byPackage[pkg]
        }
    }

    fun filterVisible(
        entries: List<LaunchableAppEntry>,
        hidden: Set<String>,
    ): List<LaunchableAppEntry> = entries.filter { it.packageName !in hidden }

    fun resolveGridApps(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
        priority: List<String>,
    ): List<LaunchableAppEntry> {
        val byPackage = allVisible.associateBy { it.packageName }
        val result = mutableListOf<LaunchableAppEntry>()
        val used = mutableSetOf<String>()

        for (pkg in pinned) {
            if (pkg.isBlank()) continue
            byPackage[pkg]?.let {
                result.add(it)
                used.add(pkg)
            }
        }

        val fillOrder = LauncherOemAppSort.sortEntries(allVisible, priority)
        for (entry in fillOrder) {
            if (result.size >= GRID_SLOT_COUNT) break
            if (entry.packageName in used) continue
            result.add(entry)
            used.add(entry.packageName)
        }
        return result.take(GRID_SLOT_COUNT)
    }

    private fun decodeSlots(raw: String?, count: Int): List<String> {
        val parsed = raw?.split('|').orEmpty()
        return List(count) { index -> parsed.getOrElse(index) { "" } }
    }

    private fun encodeSlots(slots: List<String>): String =
        slots.joinToString("|")
}
