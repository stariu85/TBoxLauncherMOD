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
private const val KEY_MEDIA_MINI_PLAYER_VISIBLE = "media_mini_player_visible"
private const val KEY_DOCK_ICON_SCALE = "dock_icon_scale"
private const val KEY_TOP_BAR_HEIGHT = "top_bar_height_dp"
private const val KEY_BOTTOM_BAR_HEIGHT = "bottom_bar_height_dp"
private const val KEY_CAR_MODEL_SCALE = "car_model_scale"
private const val KEY_SIDEBAR_WIDTH = "sidebar_width_dp"
private const val KEY_NAV_BUTTONS_VISIBLE = "nav_buttons_visible"
private const val KEY_CLIMATE_VISIBLE = "climate_controls_visible"
private const val KEY_CLIMATE_SCALE = "climate_controls_scale"
private const val KEY_CLIMATE_CARD_BG_VISIBLE = "climate_card_bg_visible"
private const val KEY_FLOATING_HOME_VISIBLE = "floating_home_visible"
private const val KEY_FLOATING_HOME_SIZE = "floating_home_size_dp"
private const val KEY_ADAS_DISTANCE_TEXT_SIZE = "adas_distance_text_size"
private const val KEY_ADAS_DISTANCE_LABEL_OFFSET = "adas_distance_label_offset"
private const val KEY_ADAS_ALERTS_IN_LEFT_PANEL = "adas_alerts_in_left_panel"
private const val KEY_SPEED_LIMIT_X_RATIO = "speed_limit_x_ratio"
private const val KEY_SPEED_LIMIT_Y_RATIO = "speed_limit_y_ratio"
private const val KEY_FUEL_SHOWS_RANGE = "fuel_shows_range"
private const val KEY_FULLSCREEN = "fullscreen_packages"
private const val KEY_FULL_WIDTH = "full_width_packages"
private const val KEY_BOTTOM_SLOTS_UNIFIED = "bottom_slots_unified"
private const val KEY_GRID_COLUMNS = "home_grid_columns"
private const val KEY_GRID_ROWS = "home_grid_rows"
private const val KEY_HOME_ICON_SCALE = "home_icon_scale"

internal const val GRID_SLOTS_TOTAL_COUNT = 20

internal val DEFAULT_BOTTOM_SLOTS_UNIFIED: List<String?> = listOf(
    null, // Reserved empty slot 0 so floating Home button never overlaps icons
    "seat_heat_left",
    "seat_vent_left",
    "temp_driver", // Occupies slots 3 and 4 (span = 2)
    null,          // Slot 4 absorbed by temp_driver
    "hvac_auto",
    "temp_pass",   // Occupies slots 6 and 7 (span = 2)
    null,          // Slot 7 absorbed by temp_pass
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
internal const val MEDIA_CARD_ALPHA_DEFAULT = 0.88f
internal const val MEDIA_CARD_ALPHA_MIN = 0.40f
internal const val MEDIA_CARD_ALPHA_MAX = 1.00f
internal const val DOCK_ICON_SCALE_DEFAULT = 1.00f
internal const val DOCK_ICON_SCALE_MIN = 1.00f
internal const val DOCK_ICON_SCALE_MAX = 1.45f
internal const val TOP_BAR_HEIGHT_DEFAULT = 32
internal const val TOP_BAR_HEIGHT_MIN = 28
internal const val TOP_BAR_HEIGHT_MAX = 72
internal const val BOTTOM_BAR_HEIGHT_DEFAULT = 65
internal const val BOTTOM_BAR_HEIGHT_MIN = 48
internal const val BOTTOM_BAR_HEIGHT_MAX = 110
internal const val FLOATING_HOME_SIZE_DEFAULT = 55
internal const val FLOATING_HOME_SIZE_MIN = 32
internal const val FLOATING_HOME_SIZE_MAX = 96
internal const val CAR_MODEL_SCALE_DEFAULT = 1.20f
internal const val CAR_MODEL_SCALE_MIN = 0.50f
internal const val CAR_MODEL_SCALE_MAX = 1.80f
internal const val SIDEBAR_WIDTH_DEFAULT = 300
internal const val SIDEBAR_WIDTH_MIN = 240
internal const val SIDEBAR_WIDTH_MAX = 400
internal const val ADAS_DISTANCE_TEXT_SIZE_DEFAULT = 28
internal const val ADAS_DISTANCE_TEXT_SIZE_MIN = 20
internal const val ADAS_DISTANCE_TEXT_SIZE_MAX = 40
internal const val ADAS_DISTANCE_LABEL_OFFSET_DEFAULT = 0.50f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MIN = 0.00f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MAX = 1.00f
internal const val CLIMATE_SCALE_DEFAULT = 1.00f
internal const val CLIMATE_SCALE_MIN = 0.70f
internal const val CLIMATE_SCALE_MAX = 1.40f
internal const val GRID_COLUMNS_DEFAULT = 12
internal const val GRID_COLUMNS_MIN = 5
internal const val GRID_COLUMNS_MAX = 20
internal const val GRID_ROWS_DEFAULT = 7
internal const val GRID_ROWS_MIN = 1
internal const val GRID_ROWS_MAX = 10
internal const val HOME_ICON_SCALE_DEFAULT = 1.35f
internal const val HOME_ICON_SCALE_MIN = 0.60f
internal const val HOME_ICON_SCALE_MAX = 1.80f
internal val CRUISE_PRESET_DEFAULTS_KMH = listOf(110, 80, 60)
internal const val CRUISE_PRESET_MIN_KMH = 30
internal const val CRUISE_PRESET_MAX_KMH = 160
internal const val CRUISE_PRESET_STEP_KMH = 5
private const val KEY_CRUISE_PRESETS = "cruise_presets_kmh"
private const val KEY_LAST_CRUISE_SPEED = "last_cruise_speed_kmh"
private const val KEY_CRUISE_PANEL_VISIBLE = "cruise_panel_visible"
private const val KEY_DRIVE_MODE_BAR_VISIBLE = "drive_mode_bar_visible"
private const val KEY_NGP_ENABLED = "ngp_enabled"
private const val KEY_TIME_GAP_LEVEL = "time_gap_level"
internal const val GRID_SLOT_COUNT = 9
private const val DOCK_SLOT_COUNT = 4

private val DEFAULT_DOCK = listOf(
    "com.autopai.car.dialer",
    "com.wt.multimedia.platform3",
    "com.tencent.wecarnavi",
    "com.autopai.system.settings",
)

internal object LauncherAppConfigStore {

    private val mediaCardAlphaRevision = MutableStateFlow(0)
    internal val mediaCardAlphaRevisionFlow: StateFlow<Int> = mediaCardAlphaRevision
    private val mediaMiniPlayerRevision = MutableStateFlow(0)
    internal val mediaMiniPlayerRevisionFlow: StateFlow<Int> = mediaMiniPlayerRevision
    private val dockIconScaleRevision = MutableStateFlow(0)
    internal val dockIconScaleRevisionFlow: StateFlow<Int> = dockIconScaleRevision
    private val topBarHeightRevision = MutableStateFlow(0)
    internal val topBarHeightRevisionFlow: StateFlow<Int> = topBarHeightRevision
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
    private val navButtonsRevision = MutableStateFlow(0)
    internal val navButtonsRevisionFlow: StateFlow<Int> = navButtonsRevision
    private val adasDistanceTextSizeRevision = MutableStateFlow(0)
    internal val adasDistanceTextSizeRevisionFlow: StateFlow<Int> = adasDistanceTextSizeRevision
    private val adasDistanceLabelOffsetRevision = MutableStateFlow(0)
    internal val adasDistanceLabelOffsetRevisionFlow: StateFlow<Int> = adasDistanceLabelOffsetRevision
    private val adasAlertsPlacementRevision = MutableStateFlow(0)
    internal val adasAlertsPlacementRevisionFlow: StateFlow<Int> = adasAlertsPlacementRevision
    private val speedLimitPositionRevision = MutableStateFlow(0)
    internal val speedLimitPositionRevisionFlow: StateFlow<Int> = speedLimitPositionRevision
    private val climateControlsRevision = MutableStateFlow(0)
    internal val climateControlsRevisionFlow: StateFlow<Int> = climateControlsRevision
    private val climateCardBgRevision = MutableStateFlow(0)
    internal val climateCardBgRevisionFlow: StateFlow<Int> = climateCardBgRevision
    private val bottomSlotsRevision = MutableStateFlow(0)
    internal val bottomSlotsRevisionFlow: StateFlow<Int> = bottomSlotsRevision
    private val cruisePresetsRevision = MutableStateFlow(0)
    internal val cruisePresetsRevisionFlow: StateFlow<Int> = cruisePresetsRevision
    private val cruisePanelRevision = MutableStateFlow(0)
    internal val cruisePanelRevisionFlow: StateFlow<Int> = cruisePanelRevision
    private val driveModeBarRevision = MutableStateFlow(0)
    internal val driveModeBarRevisionFlow: StateFlow<Int> = driveModeBarRevision
    private val ngpRevision = MutableStateFlow(0)
    internal val ngpRevisionFlow: StateFlow<Int> = ngpRevision
    private val gridColumnsRevision = MutableStateFlow(0)
    internal val gridColumnsRevisionFlow: StateFlow<Int> = gridColumnsRevision
    private val gridRowsRevision = MutableStateFlow(0)
    internal val gridRowsRevisionFlow: StateFlow<Int> = gridRowsRevision
    private val homeIconScaleRevision = MutableStateFlow(0)
    internal val homeIconScaleRevisionFlow: StateFlow<Int> = homeIconScaleRevision

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    fun mediaMiniPlayerVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, true)

    fun setMediaMiniPlayerVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, visible).apply()
        mediaMiniPlayerRevision.value++
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

    fun adasAlertsInLeftPanel(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADAS_ALERTS_IN_LEFT_PANEL, true)

    fun setAdasAlertsInLeftPanel(context: Context, inLeftPanel: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAS_ALERTS_IN_LEFT_PANEL, inLeftPanel).apply()
        adasAlertsPlacementRevision.value++
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

    fun fuelShowsRange(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FUEL_SHOWS_RANGE, false)

    fun setFuelShowsRange(context: Context, showRange: Boolean) {
        prefs(context).edit().putBoolean(KEY_FUEL_SHOWS_RANGE, showRange).apply()
    }

    fun cruisePresetsKmh(context: Context): List<Int> {
        val parsed = prefs(context).getString(KEY_CRUISE_PRESETS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull()?.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH) }
        return if (parsed != null && parsed.size == CRUISE_PRESET_DEFAULTS_KMH.size) {
            parsed
        } else {
            CRUISE_PRESET_DEFAULTS_KMH
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
        prefs(context).getBoolean(KEY_CRUISE_PANEL_VISIBLE, true)

    fun setCruisePanelVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_CRUISE_PANEL_VISIBLE, visible).apply()
        cruisePanelRevision.value++
    }

    fun driveModeBarVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DRIVE_MODE_BAR_VISIBLE, true)

    fun setDriveModeBarVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_DRIVE_MODE_BAR_VISIBLE, visible).apply()
        driveModeBarRevision.value++
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
