package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class TopShadeTileType {
    CAR_CONTROL,
    SYSTEM_TOGGLE,
    TELEMETRY,
    SHORTCUT,
}

data class TopShadeTileSpec(
    val id: String,
    val title: String,
    val type: TopShadeTileType,
    val actionKey: String,
    val iconResName: String? = null,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val col: Int = -1,
    val row: Int = -1,
)

internal object LauncherTopShadeConfigStore {
    private const val TAG = "TopShadeConfigStore"
    private const val PREFS_NAME = "launcher_top_shade_prefs"
    private const val KEY_TILES_JSON = "top_shade_tiles_json"
    private const val KEY_FONT_SCALE_OFFSET = "top_shade_font_scale_offset"
    private const val KEY_ICON_SCALE_OFFSET = "top_shade_icon_scale_offset"

    const val GRID_COLS = 12
    const val GRID_ROWS = 12

    private val tilesRevision = MutableStateFlow(0)
    val tilesRevisionFlow: StateFlow<Int> = tilesRevision

    private val appearanceRevision = MutableStateFlow(0)
    val appearanceRevisionFlow: StateFlow<Int> = appearanceRevision

    fun getFontScaleOffset(context: Context): Float =
        prefs(context).getFloat(KEY_FONT_SCALE_OFFSET, 0f).coerceIn(-50f, 50f)

    fun setFontScaleOffset(context: Context, offset: Float) {
        val clamped = offset.coerceIn(-50f, 50f)
        prefs(context).edit().putFloat(KEY_FONT_SCALE_OFFSET, clamped).apply()
        appearanceRevision.value++
    }

    fun getIconScaleOffset(context: Context): Float =
        prefs(context).getFloat(KEY_ICON_SCALE_OFFSET, 0f).coerceIn(-50f, 50f)

    fun setIconScaleOffset(context: Context, offset: Float) {
        val clamped = offset.coerceIn(-50f, 50f)
        prefs(context).edit().putFloat(KEY_ICON_SCALE_OFFSET, clamped).apply()
        appearanceRevision.value++
    }

    fun fontScale(context: Context): Float =
        1.0f + (getFontScaleOffset(context) / 100f)

    fun iconScale(context: Context): Float =
        1.0f + (getIconScaleOffset(context) / 100f)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val DEFAULT_TILES = listOf(
        TopShadeTileSpec("brightness", "Яркость", TopShadeTileType.SYSTEM_TOGGLE, "brightness", null, 2, 1, 0, 0),
        TopShadeTileSpec("vol_media", "Громкость Медиа", TopShadeTileType.SYSTEM_TOGGLE, "vol_media", null, 2, 1, 2, 0),

        TopShadeTileSpec("mirror_fold", "Зеркала", TopShadeTileType.CAR_CONTROL, "mirror_fold", "ic_widget_mirror_fold", 1, 1, 4, 0),
        TopShadeTileSpec("seat_heat_left", "Подогрев водит.", TopShadeTileType.CAR_CONTROL, "seat_heat_left", "ic_widget_seat_heat_left", 1, 1, 5, 0),
        TopShadeTileSpec("seat_vent_left", "Вент. водит.", TopShadeTileType.CAR_CONTROL, "seat_vent_left", "ic_widget_seat_vent_left", 1, 1, 6, 0),
        TopShadeTileSpec("seat_heat_right", "Подогрев пасс.", TopShadeTileType.CAR_CONTROL, "seat_heat_right", "ic_widget_seat_heat_right", 1, 1, 7, 0),
        TopShadeTileSpec("seat_vent_right", "Вент. пасс.", TopShadeTileType.CAR_CONTROL, "seat_vent_right", "ic_widget_seat_vent_right", 1, 1, 8, 0),
        TopShadeTileSpec("steering_heat", "Руль", TopShadeTileType.CAR_CONTROL, "steering_heat", "ic_widget_steering_wheel_heat", 1, 1, 9, 0),
        TopShadeTileSpec("windscreen_heat", "Лобовое", TopShadeTileType.CAR_CONTROL, "windscreen_heat", "ic_widget_front_windscreen_heat", 1, 1, 10, 0),
        TopShadeTileSpec("rear_defrost", "Заднее стекло", TopShadeTileType.CAR_CONTROL, "rear_defrost", "ic_widget_rear_window_mirrors_defrost", 1, 1, 11, 0),

        TopShadeTileSpec("vol_phone", "Громкость Звонков", TopShadeTileType.SYSTEM_TOGGLE, "vol_phone", null, 2, 1, 0, 1),
        TopShadeTileSpec("vol_navi", "Громкость Навигации", TopShadeTileType.SYSTEM_TOGGLE, "vol_navi", null, 2, 1, 2, 1),
        TopShadeTileSpec("vol_voice", "Громкость Голоса", TopShadeTileType.SYSTEM_TOGGLE, "vol_voice", null, 2, 1, 4, 1),
        TopShadeTileSpec("headrest_speaker", "Подголовник", TopShadeTileType.SYSTEM_TOGGLE, "headrest_speaker", "ic_widget_reboot", 1, 1, 6, 1),
        TopShadeTileSpec("hvac_auto", "Климат AUTO", TopShadeTileType.CAR_CONTROL, "hvac_auto", null, 1, 1, 7, 1),
        TopShadeTileSpec("hvac_recirc", "Рециркуляция", TopShadeTileType.CAR_CONTROL, "hvac_recirc", "ic_widget_hvac_recirc_on", 1, 1, 8, 1),
        TopShadeTileSpec("wifi_toggle", "Wi-Fi", TopShadeTileType.SYSTEM_TOGGLE, "wifi", "ic_widget_wifi", 1, 1, 9, 1),
        TopShadeTileSpec("theme_toggle", "Тема День/Ночь", TopShadeTileType.SYSTEM_TOGGLE, "theme", "ic_widget_theme", 1, 1, 10, 1),
        TopShadeTileSpec("screen_off", "Экран выкл", TopShadeTileType.SYSTEM_TOGGLE, "screen_off", "ic_widget_screen_off", 1, 1, 11, 1),
    )

    fun getTiles(context: Context): List<TopShadeTileSpec> {
        val jsonStr = prefs(context).getString(KEY_TILES_JSON, null)

        val loaded = if (jsonStr.isNull_or_blank()) {
            DEFAULT_TILES
        } else {
            try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<TopShadeTileSpec>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val typeStr = obj.optString("type", TopShadeTileType.CAR_CONTROL.name)
                    val type = runCatching { TopShadeTileType.valueOf(typeStr) }.getOrDefault(TopShadeTileType.CAR_CONTROL)

                    val rawActionKey = obj.getString("actionKey")
                    val actionKey = if (rawActionKey == "volume") "vol_media" else rawActionKey

                    list.add(
                        TopShadeTileSpec(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            type = type,
                            actionKey = actionKey,
                            iconResName = obj.optString("iconResName", "").takeIf { it.isNotBlank() },
                            spanX = obj.optInt("spanX", 1),
                            spanY = obj.optInt("spanY", 1),
                            col = obj.optInt("col", -1),
                            row = obj.optInt("row", -1),
                        )
                    )
                }
                if (list.isEmpty()) DEFAULT_TILES else list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse top shade tiles JSON", e)
                DEFAULT_TILES
            }
        }
        return ensureValidGridCoordinates(loaded, GRID_COLS, GRID_ROWS)
    }

    fun setTiles(context: Context, tiles: List<TopShadeTileSpec>) {
        try {
            val jsonArray = JSONArray()
            for (tile in tiles) {
                val obj = JSONObject().apply {
                    put("id", tile.id)
                    put("title", tile.title)
                    put("type", tile.type.name)
                    put("actionKey", tile.actionKey)
                    put("iconResName", tile.iconResName ?: "")
                    put("spanX", tile.spanX)
                    put("spanY", tile.spanY)
                    put("col", tile.col)
                    put("row", tile.row)
                }
                jsonArray.put(obj)
            }
            prefs(context).edit().putString(KEY_TILES_JSON, jsonArray.toString()).apply()
            tilesRevision.value++
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save top shade tiles JSON", e)
        }
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit()
            .remove(KEY_TILES_JSON)
            .remove(KEY_FONT_SCALE_OFFSET)
            .remove(KEY_ICON_SCALE_OFFSET)
            .apply()
        tilesRevision.value++
        appearanceRevision.value++
    }

    private fun ensureValidGridCoordinates(
        tiles: List<TopShadeTileSpec>,
        numCols: Int,
        numRows: Int,
    ): List<TopShadeTileSpec> {
        var currentRow = 0
        var currentCol = 0

        return tiles.map { tile ->
            val clampedSpanX = tile.spanX.coerceIn(1, numCols)
            val clampedSpanY = tile.spanY.coerceIn(1, numRows)

            if (tile.col >= 0 && tile.row >= 0 &&
                tile.col + clampedSpanX <= numCols &&
                tile.row + clampedSpanY <= numRows
            ) {
                tile.copy(spanX = clampedSpanX, spanY = clampedSpanY)
            } else {
                if (currentCol + clampedSpanX > numCols) {
                    currentCol = 0
                    currentRow++
                }
                val assignedRow = currentRow.coerceAtMost((numRows - clampedSpanY).coerceAtLeast(0))
                val assignedCol = currentCol.coerceAtMost((numCols - clampedSpanX).coerceAtLeast(0))
                val assigned = tile.copy(col = assignedCol, row = assignedRow, spanX = clampedSpanX, spanY = clampedSpanY)
                currentCol += clampedSpanX
                if (currentCol >= numCols) {
                    currentCol = 0
                    currentRow++
                }
                assigned
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
