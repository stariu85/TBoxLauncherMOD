package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.tbox.api.DclUiStateNotifier

private const val TAG = "ActiveAppTracker"

/**
 * Fast in-memory state tracker for active foreign applications.
 *
 * Replaces blocking synchronous shell calls (`am stack list`) during UI click handling.
 */
internal object LauncherActiveAppTracker {

    private data class TrackerState(
        val currentAppPackage: String? = null,
        val currentTaskId: Int? = null,
        val currentTopClass: String? = null,
        val currentIsFullscreen: Boolean = false,
        val currentLaunchMode: LauncherAppLaunchMode? = null,
        val previousAppPackage: String? = null,
        val previousTaskId: Int? = null,
        val previousTopClass: String? = null,
    )

    private val ignoredLauncherPackages = setOf(
        "vad.dashing.tbox",
        "com.wt.launcher3",
        "com.android.launcher3",
        "com.android.launcherWT",
        "ca.dstudio.atvlauncher",
        "com.android.systemui",
    )

    private val stateLock = Any()
    @Volatile
    private var state = TrackerState()

    private val resolveScope = CoroutineScope(SupervisorJob() + LauncherCpuAffinity.lightPollersDispatcher)
    @Volatile
    private var resolveTaskIdJob: Job? = null

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
        val snap = state
        if (snap.currentAppPackage == packageName && (snap.currentTaskId ?: -1) > 0) {
            return snap.currentTaskId
        }
        if (snap.previousAppPackage == packageName && (snap.previousTaskId ?: -1) > 0) {
            return snap.previousTaskId
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
        val resolvedMode = launchMode ?: if (isFullscreen) LauncherAppLaunchMode.FULLSCREEN else LauncherAppLaunchMode.EMBEDDED
        synchronized(stateLock) {
            val prev = state
            val switchedPackage = prev.currentAppPackage != packageName
            val nextTaskId = when {
                taskId != null && taskId > 0 -> taskId
                switchedPackage -> null
                else -> prev.currentTaskId
            }
            val nextTopClass = when {
                !topActivityClass.isNullOrBlank() -> topActivityClass
                switchedPackage -> null
                else -> prev.currentTopClass
            }
            state = prev.copy(
                currentAppPackage = packageName,
                currentTaskId = nextTaskId,
                currentTopClass = nextTopClass,
                currentIsFullscreen = isFullscreen,
                currentLaunchMode = resolvedMode,
            )
            _activeAppPackageFlow.value = packageName
            _isFullscreenFlow.value = isFullscreen
        }
        if (taskId != null && taskId > 0) {
            recordKnownTaskId(packageName, taskId)
        }
        YandexNavWindowTracker.onAppLaunched(packageName)
        Log.w(TAG, "onAppLaunched pkg=$packageName taskId=$taskId topClass=$topActivityClass fullscreen=$isFullscreen mode=$resolvedMode")
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
        resolveTaskIdJob?.cancel()
        resolveTaskIdJob = resolveScope.launch {
            delay(250L)
            runCatching {
                val am = appCtx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val taskId = findTaskIdForPackage(am, packageName)
                if (taskId != null && taskId > 0) {
                    recordKnownTaskId(packageName, taskId)
                    var applied = false
                    synchronized(stateLock) {
                        if (state.currentAppPackage == packageName) {
                            state = state.copy(
                                currentTaskId = taskId,
                                currentTopClass = topActivityClass?.takeIf { it.isNotBlank() } ?: state.currentTopClass,
                            )
                            applied = true
                        }
                    }
                    if (applied) {
                        Log.w(TAG, "Resolved taskId=$taskId post-launch for pkg=$packageName")
                    }
                }
            }
        }
    }

    fun onHomePressed(context: android.content.Context? = null) {
        resolveTaskIdJob?.cancel()
        resolveTaskIdJob = null
        val activePkg: String?
        val prevPkgAfter: String?
        synchronized(stateLock) {
            val prev = state
            activePkg = prev.currentAppPackage
            if (!activePkg.isNullOrBlank()) {
                prev.currentTaskId?.let { recordKnownTaskId(activePkg, it) }
            }
            state = TrackerState(
                previousAppPackage = if (!activePkg.isNullOrBlank()) activePkg else prev.previousAppPackage,
                previousTaskId = if (!activePkg.isNullOrBlank()) prev.currentTaskId else prev.previousTaskId,
                previousTopClass = if (!activePkg.isNullOrBlank()) prev.currentTopClass else prev.previousTopClass,
            )
            prevPkgAfter = state.previousAppPackage
            _activeAppPackageFlow.value = null
            _isFullscreenFlow.value = false
        }
        YandexNavWindowTracker.onHomePressed()
        Log.w(TAG, "onHomePressed activePkg=$activePkg previousPkg=$prevPkgAfter")
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun hasActiveApp(): Boolean {
        return !state.currentAppPackage.isNullOrBlank()
    }

    fun activeAppPackage(): String? = state.currentAppPackage

    fun isFullscreen(): Boolean = state.currentIsFullscreen

    fun isFullWidth(): Boolean = state.currentLaunchMode == LauncherAppLaunchMode.FULL_WIDTH

    fun launchMode(): LauncherAppLaunchMode? = state.currentLaunchMode

    fun activeTaskId(): Int? = state.currentTaskId

    fun activeTopClass(): String? = state.currentTopClass

    fun previousAppPackage(): String? {
        val snap = state
        return snap.previousAppPackage ?: snap.currentAppPackage
    }

    fun previousTaskId(): Int? {
        val snap = state
        return snap.previousTaskId ?: snap.currentTaskId
    }

    fun getActiveForeignTask(): ForeignAppTask? {
        val snap = state
        val pkg = snap.currentAppPackage ?: return null
        return ForeignAppTask(
            taskId = snap.currentTaskId ?: -1,
            packageName = pkg,
            topActivityClass = snap.currentTopClass,
        )
    }

    fun clearActiveApp(context: Context? = null) {
        resolveTaskIdJob?.cancel()
        resolveTaskIdJob = null
        val wasPkg: String?
        synchronized(stateLock) {
            val prev = state
            wasPkg = prev.currentAppPackage
            state = prev.copy(
                currentAppPackage = null,
                currentTaskId = null,
                currentTopClass = null,
                currentIsFullscreen = false,
                currentLaunchMode = null,
            )
            _activeAppPackageFlow.value = null
            _isFullscreenFlow.value = false
        }
        Log.w(TAG, "clearActiveApp wasPkg=$wasPkg")
        if (!wasPkg.isNullOrBlank()) {
            removeKnownTaskId(wasPkg)
        }
        YandexNavWindowTracker.onAppClosed(wasPkg)
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
    }

    fun clearAll() {
        resolveTaskIdJob?.cancel()
        resolveTaskIdJob = null
        val wasPkg: String?
        synchronized(stateLock) {
            wasPkg = state.currentAppPackage
            state = TrackerState()
            _activeAppPackageFlow.value = null
            _isFullscreenFlow.value = false
        }
        clearAllKnownTaskIds()
        YandexNavWindowTracker.onAppClosed(wasPkg)
    }
}
