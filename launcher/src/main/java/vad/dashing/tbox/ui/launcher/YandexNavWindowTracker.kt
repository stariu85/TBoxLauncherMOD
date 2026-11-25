package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.dclwm.Client as DclwmClient
import vad.dashing.dclwm.Protocol as DclwmProtocol

/**
 * Checks whether a package is a Yandex navigation app (Navigator or Maps).
 * Explicitly excludes non-navigation Yandex apps like Music, Radio, Disk, etc.
 */
internal fun isYandexNavigationPackage(pkg: String?): Boolean {
    if (pkg.isNullOrBlank()) return false
    val lower = pkg.lowercase().trim()
    if (lower.contains("music") || lower.contains("radio") || lower.contains("disk") || lower.contains("weather")) {
        return false
    }
    return lower == "ru.yandex.yandexnavi" ||
            lower == "ru.yandex.yandexmaps" ||
            lower == "ru.yandex.mobile.navigator" ||
            lower == "com.yandex.navi" ||
            lower == "ru.yandex.navi" ||
            lower == "ru.yandex.autonavi" ||
            lower.startsWith("ru.yandex.yandexnavi") ||
            lower.startsWith("ru.yandex.yandexmaps") ||
            lower.startsWith("ru.yandex.mobile.navigator") ||
            lower.startsWith("com.yandex.navi") ||
            lower.startsWith("ru.yandex.autonavi") ||
            lower.contains("yandex.navi") ||
            lower.contains("yandexnavi") ||
            lower.contains("yandexmaps")
}

/**
 * Snapshot of a window task's geometry and visibility in top-to-bottom Z-order.
 */
internal data class TrackedWindowRect(
    val packageName: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val visible: Boolean,
    val isLauncher: Boolean = false,
)

/**
 * Combined presence and on-screen visibility status for Yandex navigation apps.
 */
internal data class YandexWindowStatus(
    val isOpen: Boolean,
    val isOnScreen: Boolean,
)

/**
 * Pure helper controlling whether widgets broadcast by Yandex Navigator should be hidden
 * while the Navigator window is visible on the desktop.
 */
internal fun shouldHideYandexNavWidgets(
    hideWhenOnScreenEnabled: Boolean,
    isYandexWindowOnScreen: Boolean,
): Boolean = hideWhenOnScreenEnabled && isYandexWindowOnScreen

/**
 * Pure helper controlling whether a widget backed by Yandex Navigator broadcasts
 * is allowed to appear on screen.
 * Mirrors speed-limit and minimap behavior: visible only while Yandex Navigator is running
 * (on screen or in the background / parked off-screen) and has active data; hidden once Navigator
 * is completely closed even if vehicle simulation mode is enabled.
 */
internal fun shouldShowYandexBroadcastWidget(
    isYandexWindowOpen: Boolean,
    hasData: Boolean,
): Boolean = isYandexWindowOpen && hasData

/**
 * Determines whether any Yandex navigation task in a top-to-bottom Z-ordered list is actually
 * visible on the desktop screen (not parked off-screen, not invisible, and not completely covered
 * by a higher Z-order window such as the foreground launcher or another full/embedded app).
 */
internal fun isYandexWindowVisibleOnScreen(tasksTopToBottom: List<TrackedWindowRect>): Boolean {
    for (i in tasksTopToBottom.indices) {
        val task = tasksTopToBottom[i]
        if (!isYandexNavigationPackage(task.packageName)) continue
        if (!isWindowBoundsOnScreen(task.left, task.top, task.right, task.bottom, task.visible)) {
            continue
        }
        var occluded = false
        for (j in 0 until i) {
            val above = tasksTopToBottom[j]
            if (above.visible &&
                isWindowBoundsOnScreen(above.left, above.top, above.right, above.bottom, visible = true) &&
                above.left <= task.left &&
                above.top <= task.top &&
                above.right >= task.right &&
                above.bottom >= task.bottom
            ) {
                occluded = true
                break
            }
        }
        if (!occluded) return true
    }
    return false
}

/**
 * Tracks whether a Yandex navigation app is running (on screen or parked off-screen in the background)
 * via [isWindowOpen], and separately whether its window is currently visible on the desktop screen
 * via [isWindowOnScreen].
 */
object YandexNavWindowTracker {
    private const val TAG = "YandexNavWindowTracker"
    private const val POLL_INTERVAL_MS = 2_000L
    private const val EXPLICIT_TRANSITION_GRACE_MS = 1_200L

    private val _isWindowOpen = MutableStateFlow(false)
    val isWindowOpen: StateFlow<Boolean> = _isWindowOpen.asStateFlow()

    private val _isWindowOnScreen = MutableStateFlow(false)
    val isWindowOnScreen: StateFlow<Boolean> = _isWindowOnScreen.asStateFlow()

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var appContext: Context? = null

    @Volatile
    private var suppressPollerUntilMs: Long = 0L

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (pollJob == null || pollJob?.isActive != true) {
            pollJob = trackerScope.launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    if (System.currentTimeMillis() < suppressPollerUntilMs) continue
                    runCatching {
                        val status = checkWindowStatus(app)
                        applyStatus(status, source = "poller")
                    }
                }
            }
        }
        trackerScope.launch {
            runCatching {
                val initial = checkWindowStatus(app)
                applyStatus(initial, source = "start")
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        refreshJob?.cancel()
        refreshJob = null
    }

    fun onAppLaunched(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        if (isYandexNavigationPackage(packageName)) {
            logD(TAG, "onAppLaunched: Yandex nav opened on screen: $packageName")
            suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
            _isWindowOpen.value = true
            _isWindowOnScreen.value = true
        } else if (!isSystemOrLauncherPackage(packageName)) {
            logD(TAG, "onAppLaunched: other app opened: $packageName")
            val app = appContext
            if (app != null) {
                scheduleAsyncRefresh(app, followUpDelayMs = 400L)
            } else {
                _isWindowOnScreen.value = false
            }
        }
    }

    fun onHomePressed() {
        logD(TAG, "onHomePressed: home pressed; off-screen/background Yandex stays open, no longer on screen")
        suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
        _isWindowOnScreen.value = false
        // Off-screen window is NOT considered closed per requirement ("не считай окно закрытым если оно за границей экрана").
    }

    fun onAppClosed(packageName: String? = null) {
        if (packageName == null || isYandexNavigationPackage(packageName)) {
            logD(TAG, "onAppClosed: $packageName")
            suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
            _isWindowOpen.value = false
            _isWindowOnScreen.value = false
        }
    }

    fun onWindowMinimized(packageName: String? = null) {
        if (packageName == null || isYandexNavigationPackage(packageName)) {
            logD(TAG, "onWindowMinimized: $packageName (parked off-screen/background, still open, not on screen)")
            suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
            _isWindowOnScreen.value = false
        }
        // Off-screen window is NOT considered closed per requirement ("не считай окно закрытым если оно за границей экрана").
    }

    fun onFreeformRemoved(packageName: String) {
        if (isYandexNavigationPackage(packageName)) {
            logD(TAG, "onFreeformRemoved: $packageName")
            suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
            _isWindowOpen.value = false
            _isWindowOnScreen.value = false
        }
    }

    fun onWindowStateChanged(packageName: String?, context: Context) {
        if (packageName.isNullOrBlank()) return
        val app = context.applicationContext
        appContext = app
        if (isYandexNavigationPackage(packageName)) {
            suppressPollerUntilMs = System.currentTimeMillis() + EXPLICIT_TRANSITION_GRACE_MS
            _isWindowOpen.value = true
            _isWindowOnScreen.value = true
        } else if (isLauncherPackage(packageName, app.packageName)) {
            // Launcher became foreground (desktop). Check if Yandex is still open (off-screen/background) and whether it's on screen.
            scheduleAsyncRefresh(app, followUpDelayMs = 350L)
        } else if (!isSystemOrLauncherPackage(packageName)) {
            // Another third party app opened. Check if Yandex is still open and whether it's still visible on screen (e.g. split screen).
            scheduleAsyncRefresh(app, followUpDelayMs = 350L)
        }
    }

    private fun scheduleAsyncRefresh(app: Context, followUpDelayMs: Long = 0L) {
        refreshJob?.cancel()
        refreshJob = trackerScope.launch {
            runCatching {
                applyStatus(checkWindowStatus(app), source = "refresh")
            }
            if (followUpDelayMs > 0L) {
                delay(followUpDelayMs)
                if (System.currentTimeMillis() >= suppressPollerUntilMs) {
                    runCatching {
                        applyStatus(checkWindowStatus(app), source = "refresh-followup")
                    }
                }
            }
        }
    }

    private fun applyStatus(status: YandexWindowStatus, source: String) {
        if (_isWindowOpen.value != status.isOpen || _isWindowOnScreen.value != status.isOnScreen) {
            logD(TAG, "Window status changed by $source: isOpen=${status.isOpen}, isOnScreen=${status.isOnScreen}")
            _isWindowOpen.value = status.isOpen
            _isWindowOnScreen.value = status.isOnScreen
        }
    }

    fun checkWindowOpen(context: Context): Boolean = checkWindowStatus(context).isOpen

    fun checkWindowOnScreen(context: Context): Boolean = checkWindowStatus(context).isOnScreen

    internal fun checkWindowStatus(context: Context): YandexWindowStatus {
        val ownPkg = runCatching { context.packageName }.getOrNull()

        // 1. Authoritative check via DclwmClient if running on head unit
        val dclwmStatus: YandexWindowStatus? = runCatching {
            val client = DclwmClient()
            val tasks = client.list()
            if (tasks.isNotEmpty()) {
                val isOpen = tasks.any { isYandexNavigationPackage(it.packageName) }
                val rects = tasks.map { task ->
                    TrackedWindowRect(
                        packageName = task.packageName,
                        left = task.left,
                        top = task.top,
                        right = task.right,
                        bottom = task.bottom,
                        visible = task.visible,
                        isLauncher = isLauncherPackage(task.packageName, ownPkg),
                    )
                }
                YandexWindowStatus(
                    isOpen = isOpen,
                    isOnScreen = isOpen && isYandexWindowVisibleOnScreen(rects),
                )
            } else {
                null
            }
        }.getOrNull()

        if (dclwmStatus != null) return dclwmStatus

        // 2. Authoritative check via am stack list
        val stackStatus: YandexWindowStatus? = runCatching {
            val stacks = LauncherAmStackShell.listStacks()
            if (stacks.isNotEmpty()) {
                val isOpen = stacks.any { isYandexNavigationPackage(it.packageName) }
                val rects = stacks.map { s ->
                    val b = s.bounds
                    TrackedWindowRect(
                        packageName = s.packageName,
                        left = b?.left ?: 0,
                        top = b?.top ?: 0,
                        right = b?.right ?: if (s.visible) 1920 else 0,
                        bottom = b?.bottom ?: if (s.visible) 1080 else 0,
                        visible = s.visible,
                        isLauncher = isLauncherPackage(s.packageName, ownPkg),
                    )
                }
                YandexWindowStatus(
                    isOpen = isOpen,
                    isOnScreen = isOpen && isYandexWindowVisibleOnScreen(rects),
                )
            } else {
                null
            }
        }.getOrNull()

        if (stackStatus != null) return stackStatus

        // 3. Check in-memory tracker (tracks the currently visible foreground app)
        val activePkg = LauncherActiveAppTracker.activeAppPackage()
        if (isYandexNavigationPackage(activePkg)) {
            return YandexWindowStatus(isOpen = true, isOnScreen = true)
        }

        // 4. Check active freeform launch registry (may be parked off-screen / backgrounded)
        val freeformPkgs = FreeformLaunchRegistry.snapshot()
        if (freeformPkgs.any { isYandexNavigationPackage(it) }) {
            return YandexWindowStatus(isOpen = true, isOnScreen = false)
        }

        // 5. Check ActivityManager top/running tasks
        val am = runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
        if (am != null) {
            @Suppress("DEPRECATION")
            val runningTasks = runCatching { am.getRunningTasks(10) }.getOrNull()
            if (runningTasks != null) {
                val topTask = runningTasks.firstOrNull()
                val topIsYandex = topTask != null && (
                    isYandexNavigationPackage(topTask.topActivity?.packageName) ||
                        isYandexNavigationPackage(topTask.baseActivity?.packageName)
                    )
                if (topIsYandex) {
                    return YandexWindowStatus(isOpen = true, isOnScreen = true)
                }
                val hasYandex = runningTasks.any { task ->
                    val top = task.topActivity?.packageName
                    val base = task.baseActivity?.packageName
                    isYandexNavigationPackage(top) || isYandexNavigationPackage(base)
                }
                if (hasYandex) {
                    return YandexWindowStatus(isOpen = true, isOnScreen = false)
                }
            }
        }

        return YandexWindowStatus(isOpen = false, isOnScreen = false)
    }

    private fun isLauncherPackage(pkg: String?, ownPackageName: String? = null): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        return (ownPackageName != null && lower == ownPackageName.lowercase()) ||
            lower == "vad.dashing.tbox" ||
            lower == DclwmProtocol.LAUNCHER_PACKAGE.lowercase() ||
            lower == "com.wt.launcher3" ||
            lower == "com.android.launcher3" ||
            lower == "com.android.launcherwt" ||
            lower == "ca.dstudio.atvlauncher"
    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return isLauncherPackage(lower, appContext?.packageName) ||
            lower == "com.android.systemui" ||
            lower.startsWith("com.android.system")
    }

    @VisibleForTesting
    internal fun setWindowOpenForTest(open: Boolean) {
        suppressPollerUntilMs = 0L
        _isWindowOpen.value = open
        if (!open) {
            _isWindowOnScreen.value = false
        }
    }

    @VisibleForTesting
    internal fun setWindowOnScreenForTest(onScreen: Boolean) {
        suppressPollerUntilMs = 0L
        _isWindowOnScreen.value = onScreen
        if (onScreen) {
            _isWindowOpen.value = true
        }
    }

    private fun logD(tag: String, msg: String) {
        runCatching { android.util.Log.d(tag, msg) }
    }
}

/**
 * Checks whether a window task's bounds are within the visible screen area.
 * Returns false if the window is parked off-screen (e.g. left >= 2000, top >= 2000).
 */
internal fun isWindowBoundsOnScreen(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    visible: Boolean = true,
): Boolean {
    if (!visible) return false
    // dclwm parks off-screen windows at (2000, 2000 - 2200, 2200)
    if (left >= DclwmProtocol.OFF_LEFT || top >= DclwmProtocol.OFF_TOP) return false
    // Also if window is placed completely beyond screen edges
    if (left >= 1920 || top >= 1080) return false
    // Standard screen sanity
    if (right <= 0 || bottom <= 0) return false
    if (right <= left || bottom <= top) return false
    return true
}
