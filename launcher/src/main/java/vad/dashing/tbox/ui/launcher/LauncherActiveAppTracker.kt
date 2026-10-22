package vad.dashing.tbox.ui.launcher

import android.util.Log

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
    private var previousAppPackage: String? = null

    @Volatile
    private var previousTaskId: Int? = null

    @Volatile
    private var previousTopClass: String? = null

    fun onAppLaunched(packageName: String, taskId: Int? = null, topActivityClass: String? = null) {
        if (packageName.isBlank() || (packageName in ignoredLauncherPackages)) return
        currentAppPackage = packageName
        if (taskId != null && taskId > 0) {
            currentTaskId = taskId
        }
        if (!topActivityClass.isNullOrBlank()) {
            currentTopClass = topActivityClass
        }
        Log.w(TAG, "onAppLaunched pkg=$packageName taskId=$taskId topClass=$topActivityClass")
    }

    fun onHomePressed() {
        val activePkg = currentAppPackage
        if (!activePkg.isNullOrBlank()) {
            previousAppPackage = activePkg
            previousTaskId = currentTaskId
            previousTopClass = currentTopClass
        }
        Log.w(TAG, "onHomePressed activePkg=$activePkg previousPkg=$previousAppPackage")
    }

    fun hasActiveApp(): Boolean {
        return !currentAppPackage.isNullOrBlank()
    }

    fun activeAppPackage(): String? = currentAppPackage

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

    fun clearActiveApp() {
        Log.w(TAG, "clearActiveApp wasPkg=$currentAppPackage")
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
    }

    fun clearAll() {
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        previousAppPackage = null
        previousTaskId = null
        previousTopClass = null
    }
}
