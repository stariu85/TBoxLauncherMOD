package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.LastAppTracker

private const val TAG = "LauncherFreeform"
private const val WINDOWING_MODE_FREEFORM = 5
/** OEM WT custom modes seen in launcher vdex (treat as freeform-like). */
private const val WINDOWING_MODE_EXTEND = 11
private const val RESIZE_MODE_SYSTEM = 0

/** Packages we launched into freeform — used when stack windowingMode field is unreliable. */
internal object FreeformLaunchRegistry {
    private val packages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lastBounds = java.util.concurrent.ConcurrentHashMap<String, Rect>()

    fun record(packageName: String, bounds: Rect? = null) {
        if (packageName.isBlank()) return
        packages += packageName
        if (bounds != null && !bounds.isEmpty) {
            lastBounds[packageName] = Rect(bounds)
        }
    }

    fun snapshot(): Set<String> = packages.toSet()

    fun lastBounds(packageName: String): Rect? = lastBounds[packageName]?.let { Rect(it) }

    fun remove(packageName: String) {
        packages -= packageName
        lastBounds -= packageName
    }

    fun clear() {
        packages.clear()
        lastBounds.clear()
    }
}

internal fun isFreeformEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    return runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)
}

/** Required so freeform is not clamped to y≈78 under OEM status insets. */
private const val POLICY_CONTROL_IMMERSIVE = "immersive.full=*"

internal fun ensureFreeformImmersivePolicy(context: Context) {
    val cr = context.contentResolver
    val current = runCatching { Settings.Global.getString(cr, "policy_control") }.getOrNull()
    if (current == POLICY_CONTROL_IMMERSIVE) return
    val viaSettings = runCatching {
        Settings.Global.putString(cr, "policy_control", POLICY_CONTROL_IMMERSIVE)
        Settings.Global.getString(cr, "policy_control") == POLICY_CONTROL_IMMERSIVE
    }.getOrDefault(false)
    if (!viaSettings) {
        runCatching {
            val proc = ProcessBuilder(
                "settings", "put", "global", "policy_control", POLICY_CONTROL_IMMERSIVE,
            ).redirectErrorStream(true).start()
            proc.waitFor()
        }
    }
    val after = runCatching { Settings.Global.getString(cr, "policy_control") }.getOrNull()
    Log.w("LauncherAppLaunch", "policy_control want=$POLICY_CONTROL_IMMERSIVE now=$after")
}

/**
 * Launch [packageName] in a freeform window at [bounds] (screen pixels).
 * Returns true when startActivity succeeded with bounded options.
 */
internal fun tryLaunchInFreeformBounds(
    context: Context,
    packageName: String,
    bounds: Rect,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    val intent = resolveLaunchIntent(context, packageName) ?: return false
    return tryLaunchIntentInBounds(context, packageName, intent, bounds)
}

/**
 * Launch a system settings screen (resolved to a concrete component) into the shared
 * freeform zone, so permission dialogs float next to other freeform apps.
 */
internal fun tryLaunchSettingsIntentInFreeform(
    context: Context,
    intent: Intent,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    if (!isFreeformEnabled(context)) return false
    val resolved = intent.resolveActivity(context.packageManager) ?: return false
    val bounds = LauncherEmbeddedBoundsState.embeddedBounds() ?: return false
    val targeted = Intent(intent).apply { component = resolved }
    return tryLaunchIntentInBounds(context, resolved.packageName, targeted, bounds)
}

internal fun tryLaunchIntentInBounds(
    context: Context,
    packageName: String,
    intent: Intent,
    bounds: Rect,
    launchAdjacent: Boolean = false,
): Boolean {
    ensureFreeformImmersivePolicy(context)
    val launchIntent = Intent(intent).apply {
        if (action.isNullOrBlank()) action = Intent.ACTION_MAIN
        if (categories?.isEmpty() != false) addCategory(Intent.CATEGORY_LAUNCHER)
        flags = 0
        // Reuse one freeform task so resize applies to the window the user sees.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchAdjacent) {
            addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
    }
    val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, WINDOWING_MODE_FREEFORM)
        }.onFailure {
            Log.w(TAG, "setLaunchWindowingMode unavailable: ${it.message}")
        }
    }
    val activity = context.findLauncherActivity()
    val appCtx = context.applicationContext
    val targetBounds = Rect(bounds)
    return runCatching {
        (activity ?: context).startActivity(launchIntent, options.toBundle())
    }.onSuccess {
        LastAppTracker.recordLaunch(context, packageName)
        Log.w("LauncherAppLaunch", "freeform startActivity OK pkg=$packageName bounds=$targetBounds")
        FreeformLaunchRegistry.record(packageName, targetBounds)
        // Games often splash then request fullscreen. Keep pinning freeform
        // for a few seconds so they stay in the embedded window.
        pinFreeformBounds(appCtx, packageName, targetBounds)
    }.onFailure {
        Log.w("LauncherAppLaunch", "freeform startActivity failed pkg=$packageName bounds=$targetBounds", it)
    }.isSuccess
}

@Suppress("DEPRECATION")
private val FREEFORM_PIN_DELAYS_MS = longArrayOf(300L)

/** Re-apply freeform bounds while splash/game activities try to go fullscreen. */
private fun pinFreeformBounds(context: Context, packageName: String, bounds: Rect) {
    val handler = Handler(Looper.getMainLooper())
    val apply = { forceFreeformBounds(context, packageName, bounds, bringToFront = false) }
    FREEFORM_PIN_DELAYS_MS.forEach { delayMs ->
        if (delayMs <= 0L) apply() else handler.postDelayed(apply, delayMs)
    }
}

internal fun forceFreeformBounds(
    context: Context,
    packageName: String,
    bounds: Rect,
    bringToFront: Boolean = false,
) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val taskId = findTaskIdForPackage(am, packageName)
    if (taskId == null) {
        Log.w("LauncherAppLaunch", "resizeTask: no task yet pkg=$packageName")
        return
    }
    forceFreeformBoundsByTaskId(context, taskId, bounds, bringToFront = bringToFront, label = packageName)
}

/**
 * Resize a known task. Prefer this for our own activities (same package as HOME)
 * so we never accidentally resize the launcher task.
 */
@Suppress("DEPRECATION")
internal fun forceFreeformBoundsByTaskId(
    context: Context,
    taskId: Int,
    bounds: Rect,
    bringToFront: Boolean = false,
    label: String = "task=$taskId",
) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val rect = Rect(bounds)
    if (bringToFront) {
        runCatching { am.moveTaskToFront(taskId, 0) }
            .onFailure { Log.w("LauncherAppLaunch", "moveTaskToFront failed task=$taskId", it) }
    }
    setTaskWindowingModeFreeform(am, taskId)

    val resized = runCatching {
        val method = ActivityManager::class.java.getMethod(
            "resizeTask",
            Int::class.javaPrimitiveType,
            Rect::class.java,
            Int::class.javaPrimitiveType,
        )
        method.invoke(am, taskId, rect, RESIZE_MODE_SYSTEM)
        true
    }.recoverCatching {
        val method = ActivityManager::class.java.getMethod(
            "resizeTask",
            Int::class.javaPrimitiveType,
            Rect::class.java,
        )
        method.invoke(am, taskId, rect)
        true
    }.recoverCatching {
        // Proven on this HU: am task resize <id> L T R B
        val pb = ProcessBuilder(
            "am", "task", "resize",
            taskId.toString(),
            rect.left.toString(),
            rect.top.toString(),
            rect.right.toString(),
            rect.bottom.toString(),
        )
        val proc = pb.redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val code = proc.waitFor()
        if (code != 0) error("am task resize exit=$code out=$out")
        true
    }.onFailure {
        Log.w("LauncherAppLaunch", "resizeTask failed task=$taskId label=$label", it)
    }.getOrDefault(false)
    Log.w(
        "LauncherAppLaunch",
        "resizeTask ok=$resized task=$taskId label=$label bounds=$rect",
    )
}

private fun setTaskWindowingModeFreeform(am: ActivityManager, taskId: Int) {
    runCatching {
        val method = ActivityManager::class.java.getMethod(
            "setTaskWindowingMode",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        method.invoke(am, taskId, WINDOWING_MODE_FREEFORM, true)
        return
    }
    runCatching {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val getInstance = atmClass.getMethod("getInstance")
        val atm = getInstance.invoke(null)
        val method = atmClass.getMethod(
            "setTaskWindowingMode",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        method.invoke(atm, taskId, WINDOWING_MODE_FREEFORM, true)
    }
}

/**
 * Launch one of our own Activities into a freeform rect without recording the
 * launcher package in [FreeformLaunchRegistry] (that would confuse Home dismiss).
 */
internal fun launchOwnActivityInFreeformBounds(
    context: Context,
    intent: Intent,
    bounds: Rect,
    resolveTaskId: () -> Int?,
): Boolean {
    ensureFreeformImmersivePolicy(context)
    val launchIntent = Intent(intent).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    }
    val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, WINDOWING_MODE_FREEFORM)
        }.onFailure {
            Log.w(TAG, "setLaunchWindowingMode unavailable: ${it.message}")
        }
    }
    val activity = context.findLauncherActivity()
    val appCtx = context.applicationContext
    val targetBounds = Rect(bounds)
    return runCatching {
        (activity ?: context).startActivity(launchIntent, options.toBundle())
    }.onSuccess {
        Log.w("LauncherAppLaunch", "own freeform startActivity OK bounds=$targetBounds")
        val apply: () -> Unit = {
            val taskId = resolveTaskId()
            if (taskId != null) {
                forceFreeformBoundsByTaskId(appCtx, taskId, targetBounds, label = "own")
            } else {
                Log.w("LauncherAppLaunch", "own freeform: taskId not ready yet")
            }
        }
        apply()
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(apply, 250L)
        handler.postDelayed(apply, 700L)
        handler.postDelayed(apply, 1400L)
    }.onFailure {
        Log.w("LauncherAppLaunch", "own freeform startActivity failed bounds=$targetBounds", it)
    }.isSuccess
}

/** @deprecated use [forceFreeformBounds] */
internal fun resizeRunningTaskToBounds(
    context: Context,
    packageName: String,
    bounds: Rect,
) = forceFreeformBounds(context, packageName, bounds)

@Suppress("DEPRECATION")
private fun findTaskIdForPackage(am: ActivityManager, packageName: String): Int? {
    // Prefer visible freeform stack via hidden getAllStackInfos (works for HOME on this HU).
    findTaskIdFromStackInfos(packageName)?.let { return it }

    runCatching {
        am.getRunningTasks(32)?.firstOrNull { info ->
            info.topActivity?.packageName == packageName ||
                info.baseActivity?.packageName == packageName
        }?.id
    }.getOrNull()?.let { return it }

    runCatching {
        am.getRecentTasks(64, ActivityManager.RECENT_WITH_EXCLUDED)
            ?.firstOrNull { info ->
                val pkg = info.baseIntent?.component?.packageName
                    ?: info.baseIntent?.`package`
                    ?: info.topActivity?.packageName
                pkg == packageName
            }?.let { info ->
                info.persistentId.takeIf { it > 0 } ?: info.id
            }
    }.getOrNull()?.let { return it }

    return null
}

/**
 * Walk ActivityManager.getService().getAllStackInfos() and pick the best task for [packageName].
 * Prefer visible stacks — OEM often keeps an invisible freeform task with correct bounds
 * while the user sees a default-sized visible one.
 */
private fun findTaskIdFromStackInfos(packageName: String): Int? {
    data class Hit(val taskId: Int, val visible: Boolean, val position: Int)
    val hits = mutableListOf<Hit>()
    for (info in activityManagerStackInfos()) {
        val visible = stackInfoVisible(info)
        val position = stackInfoPosition(info)
        val taskIds = stackInfoTaskIds(info) ?: continue
        val topPkg = stackInfoTopPackage(info)
        if (topPkg == packageName && taskIds.isNotEmpty()) {
            hits += Hit(taskIds[0], visible, position)
            continue
        }
        val taskNames = stackInfoTaskNames(info) ?: continue
        for (i in taskNames.indices) {
            val name = taskNames[i]
            if (name.startsWith("$packageName/") || name == packageName) {
                if (i < taskIds.size) hits += Hit(taskIds[i], visible, position)
            }
        }
    }
    if (hits.isEmpty()) return null
    return hits.sortedWith(compareByDescending<Hit> { it.visible }.thenByDescending { it.position })
        .first()
        .taskId
}

/** Visible freeform tasks that are not our own launcher (for overlay z-order demotion). */
internal fun listVisibleForeignFreeformTaskIds(launcherPackage: String): List<Int> =
    listForeignFreeformTaskIds(launcherPackage, visibleOnly = true)

/** All foreign freeform tasks (including after launcher was killed / reinstalled). */
internal fun listForeignFreeformTaskIds(
    launcherPackage: String,
    visibleOnly: Boolean = false,
): List<Int> {
    val out = linkedSetOf<Int>()
    val stacks = activityManagerStackInfos()
    for (info in stacks) {
        if (visibleOnly && !stackInfoVisible(info)) continue
        if (!stackInfoLooksFreeform(info)) continue
        val topPkg = stackInfoTopPackage(info)
        if (topPkg == launcherPackage) continue
        val taskIds = stackInfoTaskIds(info) ?: continue
        val taskNames = stackInfoTaskNames(info)
        for (i in taskIds.indices) {
            val name = taskNames?.getOrNull(i).orEmpty()
            val pkg = name.substringBefore('/', missingDelimiterValue = topPkg.orEmpty())
            if (pkg == launcherPackage) continue
            out += taskIds[i]
        }
        if (taskIds.isNotEmpty() && topPkg != null && topPkg != launcherPackage) {
            out += taskIds[0]
        }
    }
    return out.toList()
}

private val SYSTEM_PROTECTED_PACKAGES = setOf(
    "android",
    "com.android.systemui",
    "com.wt.launcher3",
    "com.android.launcher3",
    "com.autopai.system.settings",
    "com.google.android.inputmethod.latin",
)

private fun isPackageSafeToStop(context: Context, pkg: String): Boolean {
    val myPkg = context.packageName
    if (pkg.isBlank()) return false
    if (pkg == myPkg) return false
    if (pkg in SYSTEM_PROTECTED_PACKAGES) return false
    if (pkg.startsWith("com.android.system")) return false
    return true
}

/**
 * Hide/dismiss freeform, full-width, or split windows left above the desktop when Home is pressed.
 * Force-stops external app packages so windows disappear instantly without freezing or dropping down.
 */
internal fun dismissForeignFreeformTasks(
    context: Context,
    onComplete: () -> Unit = {},
) {
    val appCtx = context.applicationContext
    val launcherPackage = appCtx.packageName
    val tracked = FreeformLaunchRegistry.snapshot()
    FreeformLaunchRegistry.clear()

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val packagesToStop = mutableSetOf<String>()
            packagesToStop += tracked

            // Collect packages from visible foreign tasks
            for (taskId in listForeignFreeformTaskIds(launcherPackage, visibleOnly = true)) {
                runCatching {
                    @Suppress("DEPRECATION")
                    am.getRunningTasks(32)?.firstOrNull { it.id == taskId }?.let { taskInfo ->
                        val pkg = taskInfo.topActivity?.packageName ?: taskInfo.baseActivity?.packageName
                        if (!pkg.isNullOrBlank()) packagesToStop += pkg
                    }
                }
            }

            for (pkg in packagesToStop) {
                if (!isPackageSafeToStop(appCtx, pkg)) continue

                // Force-stop the package via shell `am force-stop`
                runCatching {
                    val pb = ProcessBuilder("am", "force-stop", pkg)
                    val proc = pb.start()
                    proc.waitFor()
                    Log.w("LauncherAppLaunch", "am force-stop $pkg completed")
                }.onFailure {
                    Log.w("LauncherAppLaunch", "am force-stop $pkg failed: ${it.message}")
                }

                findTaskIdForPackage(am, pkg)?.let { taskId ->
                    removeTaskId(am, taskId)
                }
            }

            // Shell fallback: am stack remove
            LauncherAmStackShell.dismissForeign(launcherPackage, tracked)
        }.onFailure {
            Log.w("LauncherAppLaunch", "dismissForeignFreeform failed", it)
        }
        withContext(Dispatchers.Main) {
            onComplete()
        }
    }
}

private fun stackInfoLooksFreeform(info: Any): Boolean {
    val mode = stackInfoWindowingMode(info)
    if (mode == WINDOWING_MODE_FREEFORM || mode == WINDOWING_MODE_EXTEND) return true
    // Fallback: non-fullscreen bounds (OEM StackInfo.bounds / configuration).
    val bounds = stackInfoBounds(info) ?: return false
    if (bounds.isEmpty) return false
    val w = bounds.width()
    val h = bounds.height()
    // Embedded: ~1400x950. Full width: ~1920x950 (h < 1050, bounded by top/bottom bars).
    return w in 200..1930 && h in 200..1050
}

private fun stackInfoBounds(info: Any): Rect? {
    runCatching {
        val field = info.javaClass.getField("bounds")
        return field.get(info) as? Rect
    }
    runCatching {
        val cfg = info.javaClass.getField("configuration").get(info) ?: return null
        val winConfig = cfg.javaClass.getField("windowConfiguration").get(cfg) ?: return null
        val method = winConfig.javaClass.methods.firstOrNull {
            it.name == "getBounds" && it.parameterTypes.isEmpty()
        } ?: return null
        return method.invoke(winConfig) as? Rect
    }
    return null
}

private fun removeTaskId(am: ActivityManager, taskId: Int): Boolean {
    val viaAm = runCatching {
        val method = ActivityManager::class.java.getMethod(
            "removeTask",
            Int::class.javaPrimitiveType,
        )
        method.invoke(am, taskId) as? Boolean ?: true
    }.getOrDefault(false)
    if (viaAm) return true
    return runCatching {
        val service = activityManagerService() ?: return false
        val method = service.javaClass.methods.firstOrNull { m ->
            m.name == "removeTask" && m.parameterTypes.size == 1
        } ?: return false
        method.invoke(service, taskId)
        true
    }.onFailure {
        Log.w("LauncherAppLaunch", "removeTask failed task=$taskId", it)
    }.getOrDefault(false)
}

internal fun moveTaskBackwards(taskId: Int): Boolean =
    runCatching {
        val service = activityManagerService() ?: return false
        val method = service.javaClass.methods.firstOrNull { m ->
            (m.name == "moveTaskBackwards" || m.name == "moveTaskToBack") &&
                m.parameterTypes.size == 1 &&
                (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Integer.TYPE)
        } ?: return false
        method.invoke(service, taskId)
        true
    }.onFailure {
        Log.w("LauncherAppLaunch", "moveTaskBackwards failed task=$taskId", it)
    }.getOrDefault(false)

internal fun moveTaskToFrontId(context: Context, taskId: Int): Boolean =
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.moveTaskToFront(taskId, 0)
        true
    }.onFailure {
        Log.w("LauncherAppLaunch", "moveTaskToFront failed task=$taskId", it)
    }.getOrDefault(false)

private fun activityManagerService(): Any? {
    val getService = ActivityManager::class.java.getDeclaredMethod("getService")
    getService.isAccessible = true
    return getService.invoke(null)
}

private fun activityManagerStackInfos(): List<Any> {
    return runCatching {
        val service = activityManagerService() ?: return emptyList()
        val method = service.javaClass.methods.firstOrNull {
            it.name == "getAllStackInfos" && it.parameterTypes.isEmpty()
        } ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        (method.invoke(service) as? List<Any?>)?.filterNotNull().orEmpty()
    }.onFailure {
        Log.w("LauncherAppLaunch", "getAllStackInfos failed: ${it.message}")
    }.getOrDefault(emptyList())
}

private fun stackInfoVisible(info: Any): Boolean =
    runCatching { info.javaClass.getField("visible").getBoolean(info) }.getOrDefault(false)

private fun stackInfoPosition(info: Any): Int =
    runCatching { info.javaClass.getField("position").getInt(info) }.getOrDefault(0)

private fun stackInfoWindowingMode(info: Any): Int {
    runCatching { return info.javaClass.getField("windowingMode").getInt(info) }
    runCatching {
        val m = info.javaClass.methods.firstOrNull {
            it.name == "getWindowingMode" && it.parameterTypes.isEmpty()
        }
        if (m != null) return m.invoke(info) as Int
    }
    runCatching {
        val cfg = info.javaClass.getField("configuration").get(info) ?: return 0
        val winConfig = cfg.javaClass.getField("windowConfiguration").get(cfg) ?: return 0
        val m = winConfig.javaClass.methods.firstOrNull {
            it.name == "getWindowingMode" && it.parameterTypes.isEmpty()
        } ?: return 0
        return m.invoke(winConfig) as Int
    }
    return 0
}

private fun stackInfoTaskIds(info: Any): IntArray? =
    runCatching { info.javaClass.getField("taskIds").get(info) as? IntArray }.getOrNull()

private fun stackInfoTaskNames(info: Any): Array<String>? =
    runCatching {
        (info.javaClass.getField("taskNames").get(info) as? Array<*>)?.map { it?.toString().orEmpty() }?.toTypedArray()
    }.getOrNull()

private fun stackInfoTopPackage(info: Any): String? =
    runCatching {
        (info.javaClass.getField("topActivity").get(info) as? android.content.ComponentName)?.packageName
    }.getOrNull()

private fun Context.findLauncherActivity(): android.app.Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? android.app.Activity
}
