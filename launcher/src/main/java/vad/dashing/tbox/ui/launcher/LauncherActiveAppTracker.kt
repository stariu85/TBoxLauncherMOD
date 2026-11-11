package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import vad.dashing.tbox.api.DclUiStateNotifier

private const val TAG = "ActiveAppTracker"

/**
 * Fast in-memory state tracker for active foreign applications.
 *
 * Replaces blocking synchronous shell calls (`am stack list`) during UI click handling.
 */
internal object LauncherActiveAppTracker {

    private val ignoredLauncherPackages = setOf(
        "vad.dashing.tbox",
        "com.wt.launcher3",
        "com.android.launcher3",
        "com.android.launcherWT",
        "ca.dstudio.atvlauncher",
        "com.android.systemui",
    )

    @Volatile
    private var currentAppPackage: String? = null

    @Volatile
    private var currentTaskId: Int? = null

    @Volatile
    private var currentTopClass: String? = null

    @Volatile
    private var currentIsFullscreen: Boolean = false

    @Volatile
    private var previousAppPackage: String? = null

    @Volatile
    private var previousTaskId: Int? = null

    @Volatile
    private var previousTopClass: String? = null

    fun onAppLaunched(
        packageName: String,
        taskId: Int? = null,
        topActivityClass: String? = null,
        isFullscreen: Boolean = false,
    ) {
        if (packageName.isBlank() || (packageName in ignoredLauncherPackages)) return
        currentAppPackage = packageName
        currentIsFullscreen = isFullscreen
        if (taskId != null && taskId > 0) {
            currentTaskId = taskId
        }
        if (!topActivityClass.isNullOrBlank()) {
            currentTopClass = topActivityClass
        }
        Log.w(TAG, "onAppLaunched pkg=$packageName taskId=$taskId topClass=$topActivityClass fullscreen=$isFullscreen")
    }

    fun recordAppLaunchedAndResolveTaskId(
        context: android.content.Context,
        packageName: String,
        topActivityClass: String? = null,
        isFullscreen: Boolean = false,
    ) {
        onAppLaunched(packageName, topActivityClass = topActivityClass, isFullscreen = isFullscreen)
        val appCtx = context.applicationContext
        DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                val am = appCtx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val taskId = findTaskIdForPackage(am, packageName)
                if (taskId != null && taskId > 0) {
                    onAppLaunched(packageName, taskId = taskId, topActivityClass = topActivityClass, isFullscreen = isFullscreen)
                    Log.w(TAG, "Resolved taskId=$taskId post-launch for pkg=$packageName")
                }
            }
        }, 250L)
    }

    fun onHomePressed(context: android.content.Context? = null) {
        val activePkg = currentAppPackage
        if (!activePkg.isNullOrBlank()) {
            previousAppPackage = activePkg
            previousTaskId = currentTaskId
            previousTopClass = currentTopClass
        }
        currentIsFullscreen = false
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        Log.w(TAG, "onHomePressed activePkg=$activePkg previousPkg=$previousAppPackage")
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun hasActiveApp(): Boolean {
        return !currentAppPackage.isNullOrBlank()
    }

    fun activeAppPackage(): String? = currentAppPackage

    fun isFullscreen(): Boolean = currentIsFullscreen

    fun activeTaskId(): Int? = currentTaskId

    fun activeTopClass(): String? = currentTopClass

    fun previousAppPackage(): String? = previousAppPackage ?: currentAppPackage

    fun previousTaskId(): Int? = previousTaskId ?: currentTaskId

    fun getActiveForeignTask(): ForeignAppTask? {
        val pkg = currentAppPackage ?: return null
        return ForeignAppTask(
            taskId = currentTaskId ?: -1,
            packageName = pkg,
            topActivityClass = currentTopClass,
        )
    }

    fun clearActiveApp(context: Context? = null) {
        Log.w(TAG, "clearActiveApp wasPkg=$currentAppPackage")
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        currentIsFullscreen = false
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun clearAll() {
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        currentIsFullscreen = false
        previousAppPackage = null
        previousTaskId = null
        previousTopClass = null
    }
}
