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

    private val tilesRevision = MutableStateFlow(0)
    val tilesRevisionFlow: StateFlow<Int> = tilesRevision

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val DEFAULT_TILES = listOf(
        TopShadeTileSpec("mirror_fold", "Зеркала", TopShadeTileType.CAR_CONTROL, "mirror_fold", "ic_widget_mirror_fold", 1, 1, 0, 0),
        TopShadeTileSpec("seat_heat_left", "Подогрев водит.", TopShadeTileType.CAR_CONTROL, "seat_heat_left", "ic_widget_seat_heat_left_3", 1, 1, 1, 0),
        TopShadeTileSpec("seat_vent_left", "Вент. водит.", TopShadeTileType.CAR_CONTROL, "seat_vent_left", "ic_widget_seat_vent_left_3", 1, 1, 2, 0),
        TopShadeTileSpec("seat_heat_right", "Подогрев пасс.", TopShadeTileType.CAR_CONTROL, "seat_heat_right", "ic_widget_seat_heat_right_3", 1, 1, 3, 0),
        TopShadeTileSpec("seat_vent_right", "Вент. пасс.", TopShadeTileType.CAR_CONTROL, "seat_vent_right", "ic_widget_seat_vent_right_3", 1, 1, 4, 0),
        TopShadeTileSpec("steering_heat", "Руль", TopShadeTileType.CAR_CONTROL, "steering_heat", "ic_widget_steering_wheel_heat", 1, 1, 5, 0),

        TopShadeTileSpec("windscreen_heat", "Лобовое", TopShadeTileType.CAR_CONTROL, "windscreen_heat", "ic_widget_front_windscreen_heat", 1, 1, 0, 1),
        TopShadeTileSpec("rear_defrost", "Заднее стекло", TopShadeTileType.CAR_CONTROL, "rear_defrost", "ic_widget_rear_windscreen_heat", 1, 1, 1, 1),
        TopShadeTileSpec("hvac_auto", "Климат AUTO", TopShadeTileType.CAR_CONTROL, "hvac_auto", "ic_widget_hvac_ac", 1, 1, 2, 1),
        TopShadeTileSpec("hvac_recirc", "Рециркуляция", TopShadeTileType.CAR_CONTROL, "hvac_recirc", "ic_widget_hvac_air_recirculation", 1, 1, 3, 1),
        TopShadeTileSpec("wifi_toggle", "Wi-Fi", TopShadeTileType.SYSTEM_TOGGLE, "wifi", "ic_widget_wifi", 1, 1, 4, 1),
        TopShadeTileSpec("theme_toggle", "Тема День/Ночь", TopShadeTileType.SYSTEM_TOGGLE, "theme", "ic_widget_theme", 1, 1, 5, 1),

        TopShadeTileSpec("screen_off", "Экран выкл", TopShadeTileType.SYSTEM_TOGGLE, "screen_off", "ic_widget_screen_off", 1, 1, 0, 2),
        TopShadeTileSpec("reboot_hu", "Перезагрузка ГУ", TopShadeTileType.SYSTEM_TOGGLE, "reboot_hu", "ic_widget_reboot", 1, 1, 1, 2),
        TopShadeTileSpec("engine_battery", "ДВС и АКБ", TopShadeTileType.TELEMETRY, "engine_battery", null, 2, 1, 2, 2),
        TopShadeTileSpec("tpms_info", "Давление в шинах", TopShadeTileType.TELEMETRY, "tpms_info", null, 2, 1, 4, 2),

        TopShadeTileSpec("app_nav", "Яндекс Навигатор", TopShadeTileType.SHORTCUT, "com.yandex.yandexnavi", null, 1, 1, 0, 3),
        TopShadeTileSpec("app_music", "Яндекс Музыка", TopShadeTileType.SHORTCUT, "ru.yandex.music", null, 1, 1, 1, 3),
        TopShadeTileSpec("app_avm", "Камеры 360", TopShadeTileType.SHORTCUT, "avm_camera", "ic_launcher_avm", 1, 1, 2, 3),
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
                    list.add(
                        TopShadeTileSpec(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            type = type,
                            actionKey = obj.getString("actionKey"),
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
        return ensureValidGridCoordinates(loaded)
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
        prefs(context).edit().remove(KEY_TILES_JSON).apply()
        tilesRevision.value++
    }

    private fun ensureValidGridCoordinates(tiles: List<TopShadeTileSpec>): List<TopShadeTileSpec> {
        var currentRow = 0
        var currentCol = 0
        val numCols = 6

        return tiles.map { tile ->
            if (tile.col >= 0 && tile.row >= 0) {
                tile
            } else {
                if (currentCol + tile.spanX > numCols) {
                    currentCol = 0
                    currentRow++
                }
                val assigned = tile.copy(col = currentCol, row = currentRow)
                currentCol += tile.spanX
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
