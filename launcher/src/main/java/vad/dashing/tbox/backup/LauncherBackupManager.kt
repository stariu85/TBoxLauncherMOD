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
     * Creates a backup file in Downloads/DCLbackups containing all SharedPreferences, DataStore entries,
     * and copying custom wallpaper background files (if modified).
     */
    suspend fun createBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getBackupsDir()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val backupFile = File(dir, "dcl_backup_$timeStamp.json")

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
                    val darkTargetName = "dcl_backup_${timeStamp}_wallpaper_dark.jpg"
                    darkSourceFile.copyTo(File(dir, darkTargetName), overwrite = true)
                    darkSourceFile.copyTo(File(dir, "dcl_wallpaper_dark.jpg"), overwrite = true)
                    wallpapersJson.put("dark", darkTargetName)
                }

                val lightUriStr = LauncherAppConfigStore.wallpaperLightUri(context)
                val lightSourceFile = findWallpaperSourceFile(context, lightUriStr, isDark = false)
                if (lightSourceFile != null && lightSourceFile.exists() && lightSourceFile.length() > 0) {
                    val lightTargetName = "dcl_backup_${timeStamp}_wallpaper_light.jpg"
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
                        deserializeAndApplySharedPref(editor, key, item)
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

    private fun deserializeAndApplySharedPref(editor: SharedPreferences.Editor, key: String, item: Any) {
        if (item is JSONObject && item.has("t") && item.has("v")) {
            val type = item.getString("t")
            when (type) {
                "b" -> editor.putBoolean(key, item.getBoolean("v"))
                "i" -> editor.putInt(key, item.getInt("v"))
                "l" -> editor.putLong(key, item.getLong("v"))
                "f" -> editor.putFloat(key, item.getDouble("v").toFloat())
                "s" -> editor.putString(key, item.getString("v"))
                "ss" -> {
                    val array = item.getJSONArray("v")
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.getString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
        } else {
            when (item) {
                is Boolean -> editor.putBoolean(key, item)
                is Int -> editor.putInt(key, item)
                is Long -> editor.putLong(key, item)
                is Double -> editor.putFloat(key, item.toFloat())
                is String -> editor.putString(key, item)
                is JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until item.length()) {
                        set.add(item.getString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
    }

    private fun deserializeAndApplyDataStore(dsPrefs: MutablePreferences, keyName: String, item: Any) {
        if (item is JSONObject && item.has("t") && item.has("v")) {
            val type = item.getString("t")
            when (type) {
                "b" -> dsPrefs[booleanPreferencesKey(keyName)] = item.getBoolean("v")
                "i" -> dsPrefs[intPreferencesKey(keyName)] = item.getInt("v")
                "l" -> dsPrefs[longPreferencesKey(keyName)] = item.getLong("v")
                "f" -> dsPrefs[floatPreferencesKey(keyName)] = item.getDouble("v").toFloat()
                "s" -> dsPrefs[stringPreferencesKey(keyName)] = item.getString("v")
                "ss" -> {
                    val array = item.getJSONArray("v")
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.getString(i))
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
            }
        }
    }
}
