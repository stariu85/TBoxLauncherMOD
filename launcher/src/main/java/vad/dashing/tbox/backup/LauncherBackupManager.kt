package vad.dashing.tbox.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.settingsDataStore
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFileInfo(
    val file: File,
    val name: String,
    val formattedDate: String,
    val sizeBytes: Long,
)

object LauncherBackupManager {

    private const val BACKUP_DIR_NAME = "DCLbackups"

    /**
     * Target directory: Downloads/DCLbackups.
     */
    fun getBackupsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dclDir = File(downloads, BACKUP_DIR_NAME)
        if (!dclDir.exists()) {
            dclDir.mkdirs()
        }
        return dclDir
    }

    /**
     * Lists all .json backup files in Downloads/DCLbackups, sorted newest first.
     */
    fun listBackupFiles(): List<BackupFileInfo> {
        val dir = getBackupsDir()
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return dir.listFiles { file ->
            file.isFile && file.name.endsWith(".json", ignoreCase = true)
        }?.map { file ->
            BackupFileInfo(
                file = file,
                name = file.name,
                formattedDate = dateFormat.format(Date(file.lastModified())),
                sizeBytes = file.length(),
            )
        }?.sortedByDescending { it.file.lastModified() }.orEmpty()
    }

    /**
     * Performs an automatic backup if enabled in settings, keeping max 5 AUTO_... backups.
     */
    suspend fun performAutoBackupIfEnabled(context: Context) {
        runCatching {
            val settingsManager = SettingsManager(context.applicationContext)
            val enabled = settingsManager.autoBackupEnabledFlow.first()
            if (!enabled) return@runCatching
            createBackup(context, isAuto = true).getOrThrow()
            pruneAutoBackups()
        }.onFailure { err ->
            android.util.Log.e("LauncherBackupManager", "Auto backup failed", err)
        }
    }

    /**
     * Prunes old automatic backups starting with AUTO, keeping at most 5 newest files.
     */
    fun pruneAutoBackups() {
        val dir = getBackupsDir()
        if (!dir.exists() || !dir.isDirectory) return
        val autoJsonFiles = dir.listFiles { file ->
            file.isFile && file.name.startsWith("AUTO", ignoreCase = true) && file.name.endsWith(".json", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        if (autoJsonFiles.size > 5) {
            val toDelete = autoJsonFiles.drop(5)
            for (file in toDelete) {
                val baseName = file.name.removeSuffix(".json")
                file.delete()
                File(dir, "${baseName}_wallpaper_dark.jpg").delete()
                File(dir, "${baseName}_wallpaper_light.jpg").delete()
            }
        }
    }

    /**
     * Creates a backup file in Downloads/DCLbackups containing all SharedPreferences, DataStore entries,
     * and copying custom wallpaper background files (if modified).
     */
    suspend fun createBackup(context: Context, isAuto: Boolean = false): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getBackupsDir()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val filePrefix = if (isAuto) "AUTO_dcl_backup_" else "dcl_backup_"
            val backupFile = File(dir, "$filePrefix$timeStamp.json")

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("app", "TBox Launcher")
                put("packageName", context.packageName)
                put("timestamp", System.currentTimeMillis())
                put("createdAt", dateStr)

                // 1. Export all SharedPreferences
                val sharedPrefsJson = JSONObject()
                val prefNames = mutableSetOf(
                    "tbox_launcher_app_config",
                    "tbox_launcher_home",
                    "tbox_launcher_theme",
                    "tbox_launcher_state",
                    "tbox_launcher_solar",
                    "tbox_launcher_split_preset",
                    "tbox_launcher_wifi_ap",
                    "tbox_launcher_history",
                )

                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".xml")) {
                            prefNames.add(file.name.removeSuffix(".xml"))
                        }
                    }
                }

                for (prefName in prefNames) {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val allEntries = prefs.all
                    if (allEntries.isNotEmpty()) {
                        val fileJson = JSONObject()
                        for ((key, value) in allEntries) {
                            if (value != null) {
                                fileJson.put(key, serializeTypedValue(value))
                            }
                        }
                        sharedPrefsJson.put(prefName, fileJson)
                    }
                }
                put("shared_prefs", sharedPrefsJson)

                // 2. Export DataStore
                val dataStoreJson = JSONObject()
                val dsPrefs = context.settingsDataStore.data.first()
                for ((key, value) in dsPrefs.asMap()) {
                    if (value != null) {
                        dataStoreJson.put(key.name, serializeTypedValue(value))
                    }
                }
                put("datastore", dataStoreJson)

                // 3. Backup Wallpaper background files if customized by user
                val wallpapersJson = JSONObject()

                val darkUriStr = LauncherAppConfigStore.wallpaperDarkUri(context)
                val darkSourceFile = findWallpaperSourceFile(context, darkUriStr, isDark = true)
                if (darkSourceFile != null && darkSourceFile.exists() && darkSourceFile.length() > 0) {
                    val darkTargetName = "${if (isAuto) "AUTO_" else ""}dcl_backup_${timeStamp}_wallpaper_dark.jpg"
                    darkSourceFile.copyTo(File(dir, darkTargetName), overwrite = true)
                    darkSourceFile.copyTo(File(dir, "dcl_wallpaper_dark.jpg"), overwrite = true)
                    wallpapersJson.put("dark", darkTargetName)
                }

                val lightUriStr = LauncherAppConfigStore.wallpaperLightUri(context)
                val lightSourceFile = findWallpaperSourceFile(context, lightUriStr, isDark = false)
                if (lightSourceFile != null && lightSourceFile.exists() && lightSourceFile.length() > 0) {
                    val lightTargetName = "${if (isAuto) "AUTO_" else ""}dcl_backup_${timeStamp}_wallpaper_light.jpg"
                    lightSourceFile.copyTo(File(dir, lightTargetName), overwrite = true)
                    lightSourceFile.copyTo(File(dir, "dcl_wallpaper_light.jpg"), overwrite = true)
                    wallpapersJson.put("light", lightTargetName)
                }

                if (wallpapersJson.length() > 0) {
                    put("wallpapers", wallpapersJson)
                }
            }

            backupFile.writeText(rootJson.toString(2), Charsets.UTF_8)
            backupFile
        }
    }

    /**
     * Restores backup settings from a Uri.
     */
    suspend fun restoreBackupFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalArgumentException("Не удалось открыть файл: $uri")
            val backupFile = if (uri.scheme == "file") uri.path?.let { File(it) } else null
            restoreBackupFromJsonText(context, jsonText, backupFile).getOrThrow()
        }
    }

    /**
     * Restores backup settings from a File.
     */
    suspend fun restoreBackupFromFile(context: Context, file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = file.readText(Charsets.UTF_8)
            restoreBackupFromJsonText(context, jsonText, file).getOrThrow()
        }
    }

    private suspend fun restoreBackupFromJsonText(
        context: Context,
        jsonText: String,
        backupFile: File?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootJson = JSONObject(jsonText)

            // 1. Restore SharedPreferences
            if (rootJson.has("shared_prefs")) {
                val sharedPrefsJson = rootJson.getJSONObject("shared_prefs")
                val keys = sharedPrefsJson.keys()
                while (keys.hasNext()) {
                    val prefName = keys.next()
                    val fileJson = sharedPrefsJson.getJSONObject(prefName)
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    editor.clear()

                    val itemKeys = fileJson.keys()
                    while (itemKeys.hasNext()) {
                        val key = itemKeys.next()
                        val item = fileJson.get(key)
                        deserializeAndApplySharedPref(editor, prefName, key, item)
                    }
                    editor.commit()
                }
            }

            // 2. Restore DataStore
            if (rootJson.has("datastore")) {
                val dataStoreJson = rootJson.getJSONObject("datastore")
                context.settingsDataStore.edit { dsPrefs ->
                    dsPrefs.clear()
                    val keys = dataStoreJson.keys()
                    while (keys.hasNext()) {
                        val keyName = keys.next()
                        val item = dataStoreJson.get(keyName)
                        deserializeAndApplyDataStore(dsPrefs, keyName, item)
                    }
                }
            }

            // 3. Restore Wallpapers if available
            restoreWallpapersIfNeeded(context, rootJson, backupFile)

            // 4. Invalidate all in-memory config revisions so UI reloads from restored SharedPreferences
            LauncherAppConfigStore.notifyAllRevisionsChanged()
        }
    }

    private fun findWallpaperSourceFile(context: Context, uriStr: String, isDark: Boolean): File? {
        val fileName = if (isDark) "wallpaper_dark.jpg" else "wallpaper_light.jpg"
        val internalFile = File(context.filesDir, "wallpapers/$fileName")
        if (internalFile.exists() && internalFile.length() > 0) {
            return internalFile
        }

        if (uriStr.isNotBlank() && !uriStr.startsWith("content://")) {
            val f = File(uriStr)
            if (f.exists() && f.length() > 0) {
                return f
            }
        }

        if (uriStr.startsWith("content://")) {
            runCatching {
                val uri = Uri.parse(uriStr)
                val tempFile = File(context.cacheDir, "temp_wp_${if (isDark) "dark" else "light"}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    return tempFile
                }
            }
        }

        return null
    }

    private fun restoreWallpapersIfNeeded(context: Context, rootJson: JSONObject, backupFile: File?) {
        val wallpapersDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val dclDir = getBackupsDir()
        val parentDir = backupFile?.parentFile ?: dclDir

        val wallpapersJson = rootJson.optJSONObject("wallpapers")

        // 1. Dark theme wallpaper restore
        val darkFilename = wallpapersJson?.optString("dark")
        val darkCandidates = listOfNotNull(
            darkFilename?.let { File(parentDir, it) },
            darkFilename?.let { File(dclDir, it) },
            File(parentDir, "dcl_wallpaper_dark.jpg"),
            File(dclDir, "dcl_wallpaper_dark.jpg"),
        )
        val darkSource = darkCandidates.firstOrNull { it.exists() && it.length() > 0 }
        if (darkSource != null) {
            val targetDark = File(wallpapersDir, "wallpaper_dark.jpg")
            darkSource.copyTo(targetDark, overwrite = true)
            LauncherAppConfigStore.setWallpaperDarkUri(context, targetDark.absolutePath)
        }

        // 2. Light theme wallpaper restore
        val lightFilename = wallpapersJson?.optString("light")
        val lightCandidates = listOfNotNull(
            lightFilename?.let { File(parentDir, it) },
            lightFilename?.let { File(dclDir, it) },
            File(parentDir, "dcl_wallpaper_light.jpg"),
            File(dclDir, "dcl_wallpaper_light.jpg"),
        )
        val lightSource = lightCandidates.firstOrNull { it.exists() && it.length() > 0 }
        if (lightSource != null) {
            val targetLight = File(wallpapersDir, "wallpaper_light.jpg")
            lightSource.copyTo(targetLight, overwrite = true)
            LauncherAppConfigStore.setWallpaperLightUri(context, targetLight.absolutePath)
        }
    }

    private fun serializeTypedValue(value: Any): JSONObject {
        return JSONObject().apply {
            when (value) {
                is Boolean -> {
                    put("t", "b")
                    put("v", value)
                }
                is Int -> {
                    put("t", "i")
                    put("v", value)
                }
                is Long -> {
                    put("t", "l")
                    put("v", value)
                }
                is Float -> {
                    put("t", "f")
                    put("v", value.toDouble())
                }
                is Double -> {
                    put("t", "f")
                    put("v", value)
                }
                is String -> {
                    put("t", "s")
                    put("v", value)
                }
                is Set<*> -> {
                    put("t", "ss")
                    val jsonArray = JSONArray()
                    for (item in value) {
                        if (item != null) jsonArray.put(item.toString())
                    }
                    put("v", jsonArray)
                }
                else -> {
                    put("t", "s")
                    put("v", value.toString())
                }
            }
        }
    }

    private fun deserializeAndApplySharedPref(
        editor: SharedPreferences.Editor,
        prefName: String,
        key: String,
        item: Any,
    ) {
        // First check explicitly tagged format { "t": ..., "v": ... }
        if (item is JSONObject && item.has("t") && item.has("v")) {
            val typeTag = item.getString("t")
            val rawVal = item.get("v")
            when (typeTag) {
                "b" -> {
                    val b = when (rawVal) {
                        is Boolean -> rawVal
                        is Number -> rawVal.toInt() != 0
                        else -> rawVal.toString().toBoolean()
                    }
                    editor.putBoolean(key, b)
                }
                "i" -> {
                    val num = when (rawVal) {
                        is Number -> rawVal.toInt()
                        else -> rawVal.toString().toDoubleOrNull()?.toInt() ?: 0
                    }
                    editor.putInt(key, num)
                }
                "l" -> {
                    val num = when (rawVal) {
                        is Number -> rawVal.toLong()
                        else -> rawVal.toString().toDoubleOrNull()?.toLong() ?: 0L
                    }
                    editor.putLong(key, num)
                }
                "f" -> {
                    val num = when (rawVal) {
                        is Number -> rawVal.toFloat()
                        else -> rawVal.toString().toFloatOrNull() ?: 0f
                    }
                    editor.putFloat(key, num)
                }
                "s" -> {
                    val str = when (rawVal) {
                        is JSONArray -> rawVal.toString()
                        is JSONObject -> rawVal.toString()
                        else -> rawVal.toString()
                    }
                    editor.putString(key, str)
                }
                "ss" -> {
                    val set = mutableSetOf<String>()
                    if (rawVal is JSONArray) {
                        for (i in 0 until rawVal.length()) {
                            set.add(rawVal.optString(i))
                        }
                    } else if (rawVal is String) {
                        rawVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { set.add(it) }
                    }
                    editor.putStringSet(key, set)
                }
            }
            return
        }

        // Untagged raw values (fallback) — enforce correct preference type for known keys
        val targetType = getKnownPrefType(prefName, key, item)
        when (targetType) {
            PrefType.BOOLEAN -> {
                val b = when (item) {
                    is Boolean -> item
                    is Number -> item.toInt() != 0
                    else -> item.toString().toBoolean()
                }
                editor.putBoolean(key, b)
            }
            PrefType.INT -> {
                val num = when (item) {
                    is Number -> item.toInt()
                    else -> item.toString().toDoubleOrNull()?.toInt() ?: 0
                }
                editor.putInt(key, num)
            }
            PrefType.LONG -> {
                val num = when (item) {
                    is Number -> item.toLong()
                    else -> item.toString().toDoubleOrNull()?.toLong() ?: 0L
                }
                editor.putLong(key, num)
            }
            PrefType.FLOAT -> {
                val num = when (item) {
                    is Number -> item.toFloat()
                    else -> item.toString().toFloatOrNull() ?: 0f
                }
                editor.putFloat(key, num)
            }
            PrefType.STRING -> {
                val str = when (item) {
                    is JSONArray -> item.toString()
                    is JSONObject -> item.toString()
                    else -> item.toString()
                }
                editor.putString(key, str)
            }
            PrefType.STRING_SET -> {
                val set = mutableSetOf<String>()
                if (item is JSONArray) {
                    for (i in 0 until item.length()) {
                        set.add(item.optString(i))
                    }
                } else if (item is Collection<*>) {
                    item.forEach { set.add(it.toString()) }
                } else if (item is String) {
                    item.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { set.add(it) }
                }
                editor.putStringSet(key, set)
            }
        }
    }

    private enum class PrefType { BOOLEAN, INT, LONG, FLOAT, STRING, STRING_SET }

    private fun getKnownPrefType(prefName: String, key: String, value: Any): PrefType {
        if (prefName == "tbox_launcher_app_config") {
            return when (key) {
                "hidden_packages", "fullscreen_packages", "full_width_packages" -> PrefType.STRING_SET
                "grid_packages", "dock_packages", "home_items_json", "autostart_item_key",
                "top_bar_items", "bottom_slots_unified", "cruise_presets", "cruise_panel_position",
                "assist_presets_active_tab", "tire_pressure_badge_shape", "wallpaper_dark_uri",
                "wallpaper_light_uri", "car_paint_id", "default_media_package",
                "edge_gesture_action_left", "edge_gesture_action_right", "edge_gesture_action_bottom" -> PrefType.STRING

                "home_grid_columns", "home_grid_rows", "home_grid_row_spacing", "top_bar_height_dp",
                "bottom_bar_height_dp", "floating_home_size_dp", "sidebar_width_dp", "camera_switch_mode",
                "avm_camera_action", "pdc_visual_mode", "edge_gestures_width_dp", "adas_distance_text_size",
                "time_gap_level", "last_cruise_speed", "wallpaper_scale_mode" -> PrefType.INT

                "cruise_increase_interval", "cruise_decrease_interval" -> PrefType.LONG

                "media_card_alpha", "media_card_height_scale", "dock_icon_scale", "car_model_scale",
                "car_model_drive_scale", "climate_controls_scale", "home_icon_scale",
                "adas_distance_label_offset", "speed_limit_x_ratio", "speed_limit_y_ratio",
                "tire_pressure_low_threshold", "fuel_price" -> PrefType.FLOAT

                "top_bar_left_indicators_visible", "top_bar_indicators_border_enabled",
                "media_mini_player_visible", "close_player_after_playback", "disable_media_card_cover_color",
                "floating_home_visible", "camera_animation_enabled", "trunk_long_press_enabled",
                "edge_gestures_enabled", "nav_buttons_visible", "climate_controls_visible",
                "climate_card_bg_visible", "adas_panel_visible", "adas_alerts_in_left_panel",
                "adas_bsd_alert_overlay_enabled", "tire_pressure_on_car_enabled",
                "tire_pressure_only_low", "tire_pressure_only_top_view", "tire_pressure_always_in_settings",
                "tire_pressure_inertia_animation_enabled", "tire_pressure_persist_enabled",
                "fuel_shows_range", "cruise_panel_visible", "swap_top_bottom_panels",
                "drive_mode_bar_visible", "assist_panel_visible", "body_controls_panel_visible",
                "drive_mode_glow_visible", "headlight_beams_visible", "ngp_enabled" -> PrefType.BOOLEAN

                else -> {
                    if (key.startsWith("bottom_slots_preset_")) PrefType.STRING
                    else inferTypeFromValue(value)
                }
            }
        }
        return inferTypeFromValue(value)
    }

    private fun inferTypeFromValue(value: Any): PrefType {
        return when (value) {
            is Boolean -> PrefType.BOOLEAN
            is Int -> PrefType.INT
            is Long -> PrefType.LONG
            is Float, is Double -> PrefType.FLOAT
            is JSONArray -> PrefType.STRING_SET
            else -> PrefType.STRING
        }
    }

    private fun deserializeAndApplyDataStore(dsPrefs: MutablePreferences, keyName: String, item: Any) {
        if (item is JSONObject && item.has("t") && item.has("v")) {
            val typeTag = item.getString("t")
            val rawVal = item.get("v")
            when (typeTag) {
                "b" -> dsPrefs[booleanPreferencesKey(keyName)] = (rawVal as? Boolean) ?: rawVal.toString().toBoolean()
                "i" -> dsPrefs[intPreferencesKey(keyName)] = (rawVal as? Number)?.toInt() ?: rawVal.toString().toIntOrNull() ?: 0
                "l" -> dsPrefs[longPreferencesKey(keyName)] = (rawVal as? Number)?.toLong() ?: rawVal.toString().toLongOrNull() ?: 0L
                "f" -> dsPrefs[floatPreferencesKey(keyName)] = (rawVal as? Number)?.toFloat() ?: rawVal.toString().toFloatOrNull() ?: 0f
                "s" -> dsPrefs[stringPreferencesKey(keyName)] = rawVal.toString()
                "ss" -> {
                    val set = mutableSetOf<String>()
                    if (rawVal is JSONArray) {
                        for (i in 0 until rawVal.length()) {
                            set.add(rawVal.optString(i))
                        }
                    }
                    dsPrefs[stringSetPreferencesKey(keyName)] = set
                }
            }
        } else {
            when (item) {
                is Boolean -> dsPrefs[booleanPreferencesKey(keyName)] = item
                is Int -> dsPrefs[intPreferencesKey(keyName)] = item
                is Long -> dsPrefs[longPreferencesKey(keyName)] = item
                is Double -> dsPrefs[floatPreferencesKey(keyName)] = item.toFloat()
                is String -> dsPrefs[stringPreferencesKey(keyName)] = item
                is JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until item.length()) {
                        set.add(item.optString(i))
                    }
                    dsPrefs[stringSetPreferencesKey(keyName)] = set
                }
            }
        }
    }
}
