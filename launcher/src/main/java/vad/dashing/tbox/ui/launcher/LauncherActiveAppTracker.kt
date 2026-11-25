package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var currentLaunchMode: LauncherAppLaunchMode? = null

    @Volatile
    private var previousAppPackage: String? = null

    @Volatile
    private var previousTaskId: Int? = null

    @Volatile
    private var previousTopClass: String? = null

    private val _activeAppPackageFlow = MutableStateFlow<String?>(null)
    val activeAppPackageFlow: StateFlow<String?> = _activeAppPackageFlow.asStateFlow()

    private val _isFullscreenFlow = MutableStateFlow(false)
    val isFullscreenFlow: StateFlow<Boolean> = _isFullscreenFlow.asStateFlow()

    private val knownTaskIds = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun recordKnownTaskId(packageName: String, taskId: Int) {
        if (packageName.isBlank() || taskId <= 0) return
        knownTaskIds[packageName] = taskId
    }

    fun getKnownTaskId(packageName: String): Int? {
        if (packageName.isBlank()) return null
        if (currentAppPackage == packageName && (currentTaskId ?: -1) > 0) {
            return currentTaskId
        }
        if (previousAppPackage == packageName && (previousTaskId ?: -1) > 0) {
            return previousTaskId
        }
        return knownTaskIds[packageName]
    }

    fun removeKnownTaskId(packageName: String) {
        if (packageName.isBlank()) return
        knownTaskIds.remove(packageName)
    }

    fun clearAllKnownTaskIds() {
        knownTaskIds.clear()
    }

    fun onAppLaunched(
        packageName: String,
        taskId: Int? = null,
        topActivityClass: String? = null,
        isFullscreen: Boolean = false,
        launchMode: LauncherAppLaunchMode? = null,
    ) {
        if (packageName.isBlank() || (packageName in ignoredLauncherPackages)) return
        currentAppPackage = packageName
        currentIsFullscreen = isFullscreen
        _activeAppPackageFlow.value = packageName
        _isFullscreenFlow.value = isFullscreen
        currentLaunchMode = launchMode ?: if (isFullscreen) LauncherAppLaunchMode.FULLSCREEN else LauncherAppLaunchMode.EMBEDDED
        if (taskId != null && taskId > 0) {
            currentTaskId = taskId
            recordKnownTaskId(packageName, taskId)
        }
        if (!topActivityClass.isNullOrBlank()) {
            currentTopClass = topActivityClass
        }
        YandexNavWindowTracker.onAppLaunched(packageName)
        Log.w(TAG, "onAppLaunched pkg=$packageName taskId=$taskId topClass=$topActivityClass fullscreen=$isFullscreen mode=$currentLaunchMode")
    }

    fun recordAppLaunchedAndResolveTaskId(
        context: android.content.Context,
        packageName: String,
        topActivityClass: String? = null,
        isFullscreen: Boolean = false,
        launchMode: LauncherAppLaunchMode? = null,
    ) {
        onAppLaunched(packageName, topActivityClass = topActivityClass, isFullscreen = isFullscreen, launchMode = launchMode)
        val appCtx = context.applicationContext
        DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                val am = appCtx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val taskId = findTaskIdForPackage(am, packageName)
                if (taskId != null && taskId > 0) {
                    onAppLaunched(packageName, taskId = taskId, topActivityClass = topActivityClass, isFullscreen = isFullscreen, launchMode = launchMode)
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
            currentTaskId?.let { recordKnownTaskId(activePkg, it) }
        }
        currentIsFullscreen = false
        currentLaunchMode = null
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        _activeAppPackageFlow.value = null
        _isFullscreenFlow.value = false
        YandexNavWindowTracker.onHomePressed()
        Log.w(TAG, "onHomePressed activePkg=$activePkg previousPkg=$previousAppPackage")
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun hasActiveApp(): Boolean {
        return !currentAppPackage.isNullOrBlank()
    }

    fun activeAppPackage(): String? = currentAppPackage

    fun isFullscreen(): Boolean = currentIsFullscreen

    fun isFullWidth(): Boolean = currentLaunchMode == LauncherAppLaunchMode.FULL_WIDTH

    fun launchMode(): LauncherAppLaunchMode? = currentLaunchMode

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
        val wasPkg = currentAppPackage
        Log.w(TAG, "clearActiveApp wasPkg=$wasPkg")
        if (!wasPkg.isNullOrBlank()) {
            removeKnownTaskId(wasPkg)
        }
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        currentIsFullscreen = false
        currentLaunchMode = null
        _activeAppPackageFlow.value = null
        _isFullscreenFlow.value = false
        YandexNavWindowTracker.onAppClosed(wasPkg)
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun clearAll() {
        val wasPkg = currentAppPackage
        currentAppPackage = null
        currentTaskId = null
        currentTopClass = null
        currentIsFullscreen = false
        _activeAppPackageFlow.value = null
        _isFullscreenFlow.value = false
        previousAppPackage = null
        previousTaskId = null
        previousTopClass = null
        clearAllKnownTaskIds()
        YandexNavWindowTracker.onAppClosed(wasPkg)
    }
}
