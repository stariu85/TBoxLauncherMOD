package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherStateStore
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.MainActivity
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val TAG = "LauncherHistory"

sealed interface LaunchedTarget {
    data class SingleApp(
        val packageName: String,
        val activityName: String? = null,
    ) : LaunchedTarget

    data class SplitPreset(
        val presetId: String? = null,
        val leftPackage: String,
        val rightPackage: String,
        val leftRatio: Float = 0.5f,
    ) : LaunchedTarget
}

object LauncherHistoryTracker {
    private const val PREFS = "tbox_launcher_history"
    private const val KEY_HISTORY_JSON = "history_json"
    private const val MAX_HISTORY_SIZE = 10
    private const val SPLIT_PROTECTION_TIMEOUT_MS = 3_500L

    private val ignoredPackages = setOf(
        "vad.dashing.tbox",
        "com.wt.launcher3",
        "com.android.launcher3",
        "com.android.launcherWT",
        "ca.dstudio.atvlauncher",
        "com.android.systemui",
    )

    @Volatile
    private var inMemoryHistory: MutableList<LaunchedTarget>? = null

    @Volatile
    private var lastSplitLaunchMs: Long = 0L

    @Volatile
    private var lastActivePackageName: String? = null

    @Volatile
    private var lastLaunchFromHome: Boolean = false

    fun lastActivePackage(): String? = lastActivePackageName

    fun wasLastLaunchFromHome(): Boolean = lastLaunchFromHome

    internal fun isSameTarget(a: LaunchedTarget, b: LaunchedTarget): Boolean {
        if (a == b) return true
        return when {
            a is LaunchedTarget.SingleApp && b is LaunchedTarget.SingleApp -> {
                a.packageName == b.packageName ||
                    (a.packageName in LAUNCHER_CLIMATE_PACKAGES && b.packageName in LAUNCHER_CLIMATE_PACKAGES)
            }
            a is LaunchedTarget.SplitPreset && b is LaunchedTarget.SplitPreset -> {
                (a.presetId != null && a.presetId == b.presetId) ||
                    (a.leftPackage == b.leftPackage && a.rightPackage == b.rightPackage)
            }
            else -> false
        }
    }

    @Synchronized
    fun recordLaunch(context: Context, target: LaunchedTarget, isExplicit: Boolean = false) {
        lastLaunchFromHome = LauncherHomeActivityHolder.isResumed
        when (target) {
            is LaunchedTarget.SingleApp -> {
                if (target.packageName.isBlank() || target.packageName in ignoredPackages) return
                lastActivePackageName = target.packageName
            }
            is LaunchedTarget.SplitPreset -> {
                if (target.leftPackage.isBlank() || target.rightPackage.isBlank()) return
                lastActivePackageName = target.leftPackage
            }
        }

        val list = getHistoryMutable(context)
        val last = list.lastOrNull()

        if (!isExplicit && target is LaunchedTarget.SingleApp) {
            val now = SystemClock.uptimeMillis()
            val isSplitProtected = (now - lastSplitLaunchMs) < SPLIT_PROTECTION_TIMEOUT_MS

            if (last is LaunchedTarget.SplitPreset) {
                if (target.packageName == last.leftPackage || target.packageName == last.rightPackage) {
                    Log.d(
                        TAG,
                        "Ignored implicit single app launch ${target.packageName} because top is SplitPreset(${last.leftPackage}, ${last.rightPackage})",
                    )
                    return
                }
            }

            if (isSplitProtected && last is LaunchedTarget.SplitPreset) {
                Log.d(
                    TAG,
                    "Ignored implicit single app launch ${target.packageName} due to recent split launch protection",
                )
                return
            }
        }

        if (target is LaunchedTarget.SplitPreset) {
            lastSplitLaunchMs = SystemClock.uptimeMillis()
        }

        if (last != null && isSameTarget(last, target)) {
            if (target is LaunchedTarget.SingleApp && last is LaunchedTarget.SingleApp) {
                if (!target.activityName.isNullOrBlank() && last.activityName.isNullOrBlank()) {
                    list[list.lastIndex] = target
                    saveHistory(context, list)
                }
            }
            return
        }

        val effectiveTarget = if (target is LaunchedTarget.SingleApp && target.activityName.isNullOrBlank()) {
            val existingActivity = (list.firstOrNull { isSameTarget(it, target) } as? LaunchedTarget.SingleApp)?.activityName
            if (!existingActivity.isNullOrBlank()) {
                target.copy(activityName = existingActivity)
            } else {
                target
            }
        } else {
            target
        }

        list.removeAll { isSameTarget(it, target) }
        list.add(effectiveTarget)

        while (list.size > MAX_HISTORY_SIZE) {
            list.removeAt(0)
        }

        saveHistory(context, list)
        Log.d(TAG, "Recorded launch target: $effectiveTarget (isExplicit=$isExplicit, history size=${list.size})")
    }

    @Synchronized
    fun getHistory(context: Context): List<LaunchedTarget> {
        return getHistoryMutable(context).toList()
    }

    private fun getHistoryMutable(context: Context): MutableList<LaunchedTarget> {
        inMemoryHistory?.let { return it }
        val loaded = loadHistory(context).toMutableList()
        inMemoryHistory = loaded
        return loaded
    }

    private fun loadHistory(context: Context): List<LaunchedTarget> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    when (obj.optString("type")) {
                        "app" -> {
                            val pkg = obj.optString("package")
                            val act = obj.optString("activity").takeIf { !it.isNullOrBlank() }
                            if (pkg.isNotBlank()) {
                                add(LaunchedTarget.SingleApp(pkg, act))
                            }
                        }
                        "split" -> {
                            val left = obj.optString("leftPackage")
                            val right = obj.optString("rightPackage")
                            val ratio = obj.optDouble("leftRatio", 0.5).toFloat().coerceIn(0.2f, 0.8f)
                            val presetId = obj.optString("presetId").takeIf { !it.isNullOrBlank() }
                            if (left.isNotBlank() && right.isNotBlank()) {
                                add(LaunchedTarget.SplitPreset(presetId, left, right, ratio))
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(context: Context, history: List<LaunchedTarget>) {
        val array = JSONArray()
        history.forEach { target ->
            when (target) {
                is LaunchedTarget.SingleApp -> {
                    array.put(
                        JSONObject()
                            .put("type", "app")
                            .put("package", target.packageName)
                            .put("activity", target.activityName.orEmpty()),
                    )
                }
                is LaunchedTarget.SplitPreset -> {
                    array.put(
                        JSONObject()
                            .put("type", "split")
                            .put("presetId", target.presetId.orEmpty())
                            .put("leftPackage", target.leftPackage)
                            .put("rightPackage", target.rightPackage)
                            .put("leftRatio", target.leftRatio.toDouble()),
                    )
                }
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, array.toString())
            .apply()
    }
}

internal fun isCameraPackage(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    val lower = packageName.lowercase()
    return lower.contains("avm") ||
        lower.contains("camera") ||
        lower.contains("car360") ||
        lower.contains("quanjing") ||
        lower == "com.wt.car360"
}

internal fun isCameraTarget(target: LaunchedTarget?): Boolean {
    if (target == null) return false
    return when (target) {
        is LaunchedTarget.SingleApp -> isCameraPackage(target.packageName)
        is LaunchedTarget.SplitPreset -> isCameraPackage(target.leftPackage) || isCameraPackage(target.rightPackage)
    }
}

/**
 * Executes the Return / Undo launch button behavior:
 * - If on Home screen or Launcher Settings: closes settings, launches the last launched app or split preset.
 * - If currently inside an app: launches the previous launched app or split preset.
 */
internal fun performUndoLaunch(context: Context) {
    val settingsWereOpen = LauncherVehicleSettingsUiState.open ||
        LauncherVehicleSettingsOverlayWindow.isShowing() ||
        LauncherVehicleSettingsActivity.isOpen() ||
        MainActivity.isOpen() ||
        LauncherAboutOverlayWindow.isShowing()

    if (settingsWereOpen) {
        closeVehicleSettingsOverlay()
        MainActivity.finishIfOpen()
        LauncherAboutOverlayWindow.hide()
    }

    val history = LauncherHistoryTracker.getHistory(context)
    if (history.isEmpty()) {
        val lastPkg = LauncherStateStore.lastLaunchedAppPackage(context)
        if (lastPkg.isNotBlank()) {
            launchLauncherApp(context, lastPkg)
        }
        return
    }

    val isHomeActive = settingsWereOpen || LauncherHomeActivityHolder.isResumed

    val targetToLaunch = if (isHomeActive) {
        history.last()
    } else {
        val currentActive = history.last()
        history.lastOrNull { !LauncherHistoryTracker.isSameTarget(it, currentActive) }
            ?: if (history.size >= 2) history[history.size - 2] else history.last()
    }

    Log.d(TAG, "performUndoLaunch isHomeActive=$isHomeActive targetToLaunch=$targetToLaunch historySize=${history.size}")
    launchTarget(context, targetToLaunch)
}

internal fun launchTarget(context: Context, target: LaunchedTarget) {
    when (target) {
        is LaunchedTarget.SingleApp -> {
            if (target.packageName in LAUNCHER_CLIMATE_PACKAGES) {
                launchClimateApp(context)
            } else {
                launchLauncherApp(context, target.packageName, target.activityName)
            }
        }
        is LaunchedTarget.SplitPreset -> {
            val savedPreset = target.presetId?.let { pid ->
                LauncherSplitPresetStore.loadPresets(context).firstOrNull { it.id == pid }
            }
            val leftPkg = savedPreset?.leftPackage ?: target.leftPackage
            val rightPkg = savedPreset?.rightPackage ?: target.rightPackage
            val ratio = savedPreset?.leftRatio ?: target.leftRatio

            val leftApp = LaunchableAppEntry(leftPkg, leftPkg, null, null)
            val rightApp = LaunchableAppEntry(rightPkg, rightPkg, null, null)
            launchSplitApps(
                context = context,
                left = leftApp,
                right = rightApp,
                leftRatio = ratio,
            )
        }
    }
}
