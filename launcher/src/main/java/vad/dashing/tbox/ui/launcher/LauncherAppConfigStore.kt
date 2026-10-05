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
private const val KEY_FLOATING_HOME_SIZE = "floating_home_size_dp"
private const val KEY_ADAS_DISTANCE_TEXT_SIZE = "adas_distance_text_size"
private const val KEY_ADAS_DISTANCE_LABEL_OFFSET = "adas_distance_label_offset"
private const val KEY_FUEL_SHOWS_RANGE = "fuel_shows_range"
private const val KEY_FULLSCREEN = "fullscreen_packages"
private const val KEY_FULL_WIDTH = "full_width_packages"
private const val KEY_BOTTOM_SLOTS_UNIFIED = "bottom_slots_unified"

internal const val GRID_SLOTS_TOTAL_COUNT = 20

internal val DEFAULT_BOTTOM_SLOTS_UNIFIED: List<String?> = listOf(
    null, // Reserved empty slot 0 so floating Home button never overlaps icons
    "seat_heat_left",
    "seat_vent_left",
    "temp_driver",
    "hvac_auto",
    "temp_pass",
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
internal const val TOP_BAR_HEIGHT_DEFAULT = 40
internal const val TOP_BAR_HEIGHT_MIN = 28
internal const val TOP_BAR_HEIGHT_MAX = 72
internal const val BOTTOM_BAR_HEIGHT_DEFAULT = 72
internal const val BOTTOM_BAR_HEIGHT_MIN = 48
internal const val BOTTOM_BAR_HEIGHT_MAX = 110
internal const val FLOATING_HOME_SIZE_DEFAULT = 56
internal const val FLOATING_HOME_SIZE_MIN = 32
internal const val FLOATING_HOME_SIZE_MAX = 96
internal const val CAR_MODEL_SCALE_DEFAULT = 1.00f
internal const val CAR_MODEL_SCALE_MIN = 0.50f
internal const val CAR_MODEL_SCALE_MAX = 1.80f
internal const val SIDEBAR_WIDTH_DEFAULT = 440
internal const val SIDEBAR_WIDTH_MIN = 240
internal const val SIDEBAR_WIDTH_MAX = 760
internal const val ADAS_DISTANCE_TEXT_SIZE_DEFAULT = 28
internal const val ADAS_DISTANCE_TEXT_SIZE_MIN = 20
internal const val ADAS_DISTANCE_TEXT_SIZE_MAX = 40
internal const val ADAS_DISTANCE_LABEL_OFFSET_DEFAULT = 0.15f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MIN = 0.00f
internal const val ADAS_DISTANCE_LABEL_OFFSET_MAX = 1.00f
internal const val CLIMATE_SCALE_DEFAULT = 1.00f
internal const val CLIMATE_SCALE_MIN = 0.70f
internal const val CLIMATE_SCALE_MAX = 1.40f
internal val CRUISE_PRESET_DEFAULTS_KMH = listOf(110, 80, 60)
internal const val CRUISE_PRESET_MIN_KMH = 30
internal const val CRUISE_PRESET_MAX_KMH = 160
internal const val CRUISE_PRESET_STEP_KMH = 5
private const val KEY_CRUISE_PRESETS = "cruise_presets_kmh"
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
    private val climateControlsRevision = MutableStateFlow(0)
    internal val climateControlsRevisionFlow: StateFlow<Int> = climateControlsRevision
    private val climateCardBgRevision = MutableStateFlow(0)
    internal val climateCardBgRevisionFlow: StateFlow<Int> = climateCardBgRevision
    private val bottomSlotsRevision = MutableStateFlow(0)
    internal val bottomSlotsRevisionFlow: StateFlow<Int> = bottomSlotsRevision
    private val cruisePresetsRevision = MutableStateFlow(0)
    internal val cruisePresetsRevisionFlow: StateFlow<Int> = cruisePresetsRevision

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
        prefs(context).getBoolean(KEY_CLIMATE_CARD_BG_VISIBLE, true)

    fun setClimateCardBgVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLIMATE_CARD_BG_VISIBLE, visible).apply()
        climateCardBgRevision.value++
    }

    fun bottomSlotsUnified(context: Context): List<String?> {
        val raw = prefs(context).getString(KEY_BOTTOM_SLOTS_UNIFIED, null)
        if (raw.isNullOrBlank()) return DEFAULT_BOTTOM_SLOTS_UNIFIED
        val list = raw.split(',').map { if (it.trim() == "null" || it.isBlank()) null else it.trim() }.toMutableList()
        if (list.size == GRID_SLOTS_TOTAL_COUNT) {
            list[0] = null // Reserved empty slot 0 for Home button buffer
            return list
        }
        return DEFAULT_BOTTOM_SLOTS_UNIFIED
    }

    fun setBottomSlotsUnified(context: Context, slots: List<String?>) {
        val raw = slots.take(GRID_SLOTS_TOTAL_COUNT).joinToString(",") { it ?: "null" }
        prefs(context).edit().putString(KEY_BOTTOM_SLOTS_UNIFIED, raw).apply()
        bottomSlotsRevision.value++
    }

    fun setButtonInUnifiedSlot(context: Context, slotIndex: Int, buttonId: String?) {
        val slots = bottomSlotsUnified(context).toMutableList()
        if (slotIndex in 1 until GRID_SLOTS_TOTAL_COUNT) {
            slots[slotIndex] = buttonId
            setBottomSlotsUnified(context, slots)
        }
    }

    fun reorderUnifiedSlot(
        context: Context,
        itemId: String,
        currentIndex: Int,
        slotsShift: Int,
    ) {
        val slots = bottomSlotsUnified(context).toMutableList()
        val targetIndex = (currentIndex + slotsShift).coerceIn(1, GRID_SLOTS_TOTAL_COUNT - 1)
        if (targetIndex == currentIndex) return

        val targetOld = slots[targetIndex]
        slots[currentIndex] = targetOld
        slots[targetIndex] = itemId
        slots[0] = null // Enforce slot 0 stays empty for Home button reserved buffer

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
