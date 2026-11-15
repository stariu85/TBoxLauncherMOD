package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log

/**
 * Ensures that all default settings (from backup/factory defaults) are physically
 * written into SharedPreferences, even for parameters that were never touched by the user.
 *
 * Runs on every application startup. If a parameter is already present, it is left untouched.
 * If a parameter is missing, its default value is explicitly saved into SharedPreferences.
 */
object LauncherFirstInstallDefaults {
    private const val TAG = "FirstInstallDefaults"

    fun applyIfNeeded(context: Context) {
        try {
            seedMissingDefaults(context)
            Log.i(TAG, "All default settings ensured in SharedPreferences.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply default settings", e)
        }
    }

    fun seedMissingDefaults(context: Context) {
        // 1. tbox_launcher_app_config
        val appConfig = context.getSharedPreferences("tbox_launcher_app_config", Context.MODE_PRIVATE)
        val appEditor = appConfig.edit()
        var appChanged = false

        fun putIfMissing(key: String, value: Boolean) {
            if (!appConfig.contains(key)) {
                appEditor.putBoolean(key, value)
                appChanged = true
            }
        }
        fun putIfMissing(key: String, value: Int) {
            if (!appConfig.contains(key)) {
                appEditor.putInt(key, value)
                appChanged = true
            }
        }
        fun putIfMissing(key: String, value: Long) {
            if (!appConfig.contains(key)) {
                appEditor.putLong(key, value)
                appChanged = true
            }
        }
        fun putIfMissing(key: String, value: Float) {
            if (!appConfig.contains(key)) {
                appEditor.putFloat(key, value)
                appChanged = true
            }
        }
        fun putIfMissing(key: String, value: String) {
            if (!appConfig.contains(key)) {
                appEditor.putString(key, value)
                appChanged = true
            }
        }
        fun putIfMissing(key: String, value: Set<String>) {
            if (!appConfig.contains(key)) {
                appEditor.putStringSet(key, value)
                appChanged = true
            }
        }

        putIfMissing("swap_top_bottom_panels", false)
        putIfMissing("wallpaper_scale_mode", 1)
        putIfMissing("climate_controls_scale", 1.3485069f)
        putIfMissing("car_paint_id", "cheqi04")
        putIfMissing("fullscreen_packages", emptySet())
        putIfMissing("camera_switch_mode", 1)
        putIfMissing("home_grid_rows", 8)
        putIfMissing("tire_pressure_badge_shape", "round")
        putIfMissing("default_media_package", "ru.yandex.music")
        putIfMissing("adas_lead_car_min_dist_gap_dp", 20f)
        putIfMissing("headlight_beams_mode", 2)
        putIfMissing("pdc_visual_mode", 0)
        putIfMissing("assist_panel_visible", true)
        putIfMissing("cruise_increase_interval", 50L)
        putIfMissing("nav_speed_y_ratio", 0.21664971f)
        putIfMissing("cruise_panel_visible", true)
        putIfMissing("tire_pressure_on_car_enabled", true)
        putIfMissing("tire_pressure_always_in_settings", true)
        putIfMissing("nav_traffic_light_visible", true)
        putIfMissing("body_controls_panel_visible", false)
        putIfMissing("quick_settings_top_panels", "car_color,drive_mode_offroad,smart_control,lanes,chassis,radars")
        putIfMissing("top_bar_left_indicators_visible", true)
        putIfMissing("disable_media_card_cover_color", false)
        putIfMissing("adas_top_panels", "drive_mode,assist")
        putIfMissing("edge_gestures_width_dp", 15)
        putIfMissing("bottom_slots_unified", "null,all_apps,car_dialer,null,windscreen_heat,steering_heat,seat_heat_left,temp_driver,null,hvac_auto,hvac_fan_speed,hvac_sync,temp_pass,null,seat_heat_right,front_defrost,rear_defrost,hvac_fan_direction,recirc,hvac_pm25")
        putIfMissing("drive_mode_bar_visible", true)
        putIfMissing("speed_limit_x_ratio", 0.49624139f)
        putIfMissing("edge_gesture_action_bottom", "home")
        putIfMissing("bottom_slots_preset_general", DEFAULT_BOTTOM_SLOTS_PRESET_GENERAL)
        putIfMissing("adas_acc_speed_offset", 20)
        putIfMissing("exp_player_launch_mode", "media_button_broadcast")
        putIfMissing("nav_speed_x_ratio", 0.48231483f)
        putIfMissing("home_icon_scale", 1.25f)
        putIfMissing("media_mini_player_visible", true)
        putIfMissing("adas_bsd_alert_overlay_enabled", true)
        putIfMissing("ngp_enabled", false)
        putIfMissing("close_player_after_playback", true)
        putIfMissing("adas_alerts_in_left_panel", true)
        putIfMissing("cruise_presets_kmh", "80,100,110,130")
        putIfMissing("floating_home_size_dp", 65)
        putIfMissing("quick_settings_bottom_panels", "body_controls_seat_memory,lights,wipers,body_controls_trunk,body_controls_seats")
        putIfMissing("nav_traffic_light_scale", 1.5f)
        putIfMissing("speed_limit_y_ratio", 0.20854779f)
        putIfMissing("media_card_alpha", 0.79613614f)
        putIfMissing("bottom_slots_preset_summer", DEFAULT_BOTTOM_SLOTS_PRESET_SUMMER)
        putIfMissing("adas_distance_text_size", 25)
        putIfMissing("sidebar_width_dp", 300)
        putIfMissing("car_model_drive_scale", 1.1f)
        putIfMissing("climate_temp_color_indication", true)
        putIfMissing("nav_speed_visible", true)
        putIfMissing("time_gap_level", 1)
        putIfMissing("car_color_panel_visible", true)
        putIfMissing("last_cruise_speed_kmh", 120)
        putIfMissing("tire_pressure_only_low", false)
        putIfMissing("fuel_shows_range", true)
        putIfMissing("cruise_panel_position", "top")
        putIfMissing("home_grid_row_spacing", 21)
        putIfMissing("home_items_json", LauncherHomeStore.DEFAULT_HOME_ITEMS_RAW)
        putIfMissing("bottom_bar_height_dp", 65)
        putIfMissing("trunk_long_press_enabled", true)
        putIfMissing("top_bar_height_dp", 40)
        putIfMissing("adas_bottom_panels", "media_player")
        putIfMissing("tire_pressure_low_threshold", 2.1f)
        putIfMissing("bottom_slots_preset_winter", DEFAULT_BOTTOM_SLOTS_PRESET_WINTER)
        putIfMissing("home_grid_columns", 12)
        putIfMissing("floating_home_visible", true)
        putIfMissing("top_bar_modem_cellular_visible", false)
        putIfMissing("nav_buttons_visible", false)
        putIfMissing("top_bar_indicators_border_enabled", true)
        putIfMissing("cruise_decrease_interval", 50L)
        putIfMissing("car_model_visible", true)
        putIfMissing("dcl_logs_enabled", false)
        putIfMissing("adas_panel_visible", true)
        putIfMissing("acc_presets_in_quick_settings_visible", false)
        putIfMissing("top_bar_items", "headlights|trunk|mirrors|rear_fog|hdc|pdc|avm|spacer_small_1794639492605|gps|combined_temp|air_quality|spacer_small_1794639527728|avg_fuel|fuel|trip_cost|spacer_small_1794639591091|engine_temp|gearbox_temp|voltage|spacer_small_1794639626549|vehicle_status|maintenance")
        putIfMissing("full_width_packages", LauncherAppConfigStore.DEFAULT_FULL_WIDTH_PACKAGES)
        putIfMissing("edge_gesture_action_right", "close_all")
        putIfMissing("launcher_settings_on_double_tap", true)
        putIfMissing("climate_controls_visible", true)
        putIfMissing("adas_acc_by_speed_limit_enabled", true)
        putIfMissing("climate_card_bg_visible", false)
        putIfMissing("car_model_scale", 1.8049309f)
        putIfMissing("avm_camera_action", 2)
        putIfMissing("assist_presets_active_tab", "assist")
        putIfMissing("media_card_height_scale", 1.0f)
        putIfMissing("speed_limit_in_center", true)

        putIfMissing("edge_gestures_enabled", true)
        putIfMissing("edge_gesture_action_left", "back")
        putIfMissing("edge_gesture_action_top", "shade")
        putIfMissing("show_player_name_in_widget", true)
        putIfMissing("seat_mirror_launch_mode", "standard")
        putIfMissing("exp_hide_apps_on_home", false)
        putIfMissing("dock_icon_scale", 1.0f)
        putIfMissing("top_bar_cellular_network_type_visible", true)
        putIfMissing("top_bar_time_signal_border_enabled", true)
        putIfMissing("camera_animation_enabled", true)
        putIfMissing("camd_use_stock_avm", true)
        putIfMissing("camd_turn_signal_cameras", false)
        putIfMissing("camd_side_cameras_shape_circle", false)
        putIfMissing("camd_turn_signal_left_preset", "")
        putIfMissing("camd_turn_signal_right_preset", "")
        putIfMissing("adas_icon_scale", 100)
        putIfMissing("adas_distance_label_offset", 0f)
        putIfMissing("seat_memory_adas_panel_enabled", true)
        putIfMissing("theme_button_offset_x_dp", 0f)
        putIfMissing("theme_button_offset_y_dp", 0f)
        putIfMissing("seat_memory_button_offset_x_dp", 0f)
        putIfMissing("seat_memory_button_offset_y_dp", 0f)
        putIfMissing("passenger_seat_button_enabled", true)
        putIfMissing("passenger_seat_button_offset_x_dp", 0f)
        putIfMissing("passenger_seat_button_offset_y_dp", 0f)
        putIfMissing("tire_pressure_only_top_view", false)
        putIfMissing("tire_pressure_inertia_animation_enabled", true)
        putIfMissing("tire_pressure_show_hundredths", false)
        putIfMissing("tire_pressure_show_temperature", false)
        putIfMissing("nav_maneuver_x_ratio", 0.15f)
        putIfMissing("nav_maneuver_y_ratio", 0.20f)
        putIfMissing("nav_street_x_ratio", 0.50f)
        putIfMissing("nav_street_y_ratio", 0.12f)
        putIfMissing("nav_traffic_light_x_ratio", 0.85f)
        putIfMissing("nav_traffic_light_y_ratio", 0.20f)
        putIfMissing("nav_text_x_ratio", 0.50f)
        putIfMissing("nav_text_y_ratio", 0.16f)
        putIfMissing("dock_second_row_enabled", false)
        putIfMissing("dock_columns", 20)
        putIfMissing("classic_speed_limit_visible", true)
        putIfMissing("tsr_system_enabled", true)
        putIfMissing("nav_maneuver_visible", false)
        putIfMissing("nav_street_visible", false)
        putIfMissing("nav_text_visible", false)
        putIfMissing("smart_panel_enabled", false)
        putIfMissing("drive_mode_glow_visible", true)
        putIfMissing("gearbox_type_is_6dct", false)
        putIfMissing("launcher_gearbox_type", "7dct")
        putIfMissing("cruise_control_type", 0)
        putIfMissing("cruise_engage_action", 0)
        putIfMissing("fuel_price_rub", 55.0f)
        putIfMissing("fuel_tank_capacity_liters", 57.0f)

        if (appChanged) {
            appEditor.commit()
        }

        // 2. tbox_launcher_theme
        val themePrefs = context.getSharedPreferences("tbox_launcher_theme", Context.MODE_PRIVATE)
        val themeEditor = themePrefs.edit()
        var themeChanged = false
        fun putThemeIfMissing(key: String, value: Boolean) {
            if (!themePrefs.contains(key)) { themeEditor.putBoolean(key, value); themeChanged = true }
        }
        fun putThemeIfMissing(key: String, value: Float) {
            if (!themePrefs.contains(key)) { themeEditor.putFloat(key, value); themeChanged = true }
        }
        putThemeIfMissing("auto_theme_solar_enabled", true)
        putThemeIfMissing("last_known_lon", 37.629051f)
        putThemeIfMissing("auto_theme_system_enabled", false)
        putThemeIfMissing("winter_theme_temp_zero_enabled", true)
        putThemeIfMissing("theme_button_visible", false)
        putThemeIfMissing("last_known_lat", 55.629299f)
        putThemeIfMissing("dark_theme", false)
        putThemeIfMissing("auto_theme_headlights_enabled", true)
        if (themeChanged) {
            themeEditor.commit()
        }

        // 3. tbox_launcher_split_presets
        val splitPrefs = context.getSharedPreferences("tbox_launcher_split_presets", Context.MODE_PRIVATE)
        if (!splitPrefs.contains("presets_json")) {
            splitPrefs.edit().putString("presets_json", LauncherSplitPresetStore.DEFAULT_PRESETS_JSON).commit()
        }

        // 4. tbox_mirror_fold_store
        val mirrorPrefs = context.getSharedPreferences("tbox_mirror_fold_store", Context.MODE_PRIVATE)
        if (!mirrorPrefs.contains("last_fold_value")) {
            mirrorPrefs.edit().putInt("last_fold_value", 1).commit()
        }

        // 5. launcher_top_shade_prefs
        val topShadePrefs = context.getSharedPreferences("launcher_top_shade_prefs", Context.MODE_PRIVATE)
        val topShadeEditor = topShadePrefs.edit()
        var topShadeChanged = false
        if (!topShadePrefs.contains("top_shade_tiles_json")) {
            topShadeEditor.putString("top_shade_tiles_json", LauncherTopShadeConfigStore.DEFAULT_TILES_JSON)
            topShadeChanged = true
        }
        if (!topShadePrefs.contains("top_shade_icon_scale_offset")) {
            topShadeEditor.putFloat("top_shade_icon_scale_offset", -16.528717f)
            topShadeChanged = true
        }
        if (!topShadePrefs.contains("top_shade_font_scale_offset")) {
            topShadeEditor.putFloat("top_shade_font_scale_offset", 29.96199f)
            topShadeChanged = true
        }
        if (topShadeChanged) {
            topShadeEditor.commit()
        }

        // 6. tbox_launcher_state
        val statePrefs = context.getSharedPreferences("tbox_launcher_state", Context.MODE_PRIVATE)
        val stateEditor = statePrefs.edit()
        var stateChanged = false
        if (!statePrefs.contains("last_app_label")) {
            stateEditor.putString("last_app_label", "Yandex Navi")
            stateChanged = true
        }
        if (!statePrefs.contains("last_app_package")) {
            stateEditor.putString("last_app_package", "ru.yandex.yandexnavi")
            stateChanged = true
        }
        if (stateChanged) {
            stateEditor.commit()
        }

        // 7. tbox_launcher_history
        val historyPrefs = context.getSharedPreferences("tbox_launcher_history", Context.MODE_PRIVATE)
        if (!historyPrefs.contains("history_json")) {
            historyPrefs.edit().putString("history_json", "[{\"type\":\"app\",\"package\":\"ru.yandex.yandexnavi\",\"activity\":\"ru.yandex.yandexnavi.core.NavigatorActivity\"}]").commit()
        }
    }
}
