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
 * Tracks whether the Yandex Navigator window is currently open (active / visible on screen).
 * Used by the speed & speed limit overlay to only display speed limits when the navigator window is open.
 */
object YandexNavWindowTracker {
    private const val TAG = "YandexNavWindowTracker"
    private const val POLL_INTERVAL_MS = 5_000L

    private val _isWindowOpen = MutableStateFlow(false)
    val isWindowOpen: StateFlow<Boolean> = _isWindowOpen.asStateFlow()

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (pollJob == null || pollJob?.isActive != true) {
            pollJob = trackerScope.launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    runCatching {
                        val open = checkWindowOpen(app)
                        if (_isWindowOpen.value != open) {
                            logD(TAG, "Window open state changed by poller: $open")
                            _isWindowOpen.value = open
                        }
                    }
                }
            }
        }
        val initial = checkWindowOpen(app)
        _isWindowOpen.value = initial
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onAppLaunched(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        if (isYandexNavigationPackage(packageName)) {
            logD(TAG, "onAppLaunched: Yandex nav opened: $packageName")
            _isWindowOpen.value = true
        } else if (!isSystemOrLauncherPackage(packageName)) {
            logD(TAG, "onAppLaunched: other app opened: $packageName")
            val app = appContext
            if (app != null) {
                _isWindowOpen.value = checkWindowOpen(app)
            }
        }
    }

    fun onHomePressed() {
        logD(TAG, "onHomePressed: home pressed; keeping Yandex nav window open if parked off-screen")
        // Off-screen window is NOT considered closed per requirement ("не считай окно закрытым если оно за границей экрана").
    }

    fun onAppClosed(packageName: String? = null) {
        if (packageName == null || isYandexNavigationPackage(packageName)) {
            logD(TAG, "onAppClosed: $packageName")
            _isWindowOpen.value = false
        }
    }

    fun onWindowMinimized(packageName: String? = null) {
        logD(TAG, "onWindowMinimized: $packageName (parked off-screen, still considered open)")
        // Off-screen window is NOT considered closed per requirement ("не считай окно закрытым если оно за границей экрана").
    }

    fun onFreeformRemoved(packageName: String) {
        if (isYandexNavigationPackage(packageName)) {
            logD(TAG, "onFreeformRemoved: $packageName")
            _isWindowOpen.value = false
        }
    }

    fun onWindowStateChanged(packageName: String?, context: Context) {
        if (packageName.isNullOrBlank()) return
        if (isYandexNavigationPackage(packageName)) {
            _isWindowOpen.value = true
        } else if (packageName == context.packageName) {
            // Launcher became foreground (desktop). Check if Yandex is still running/open (even off-screen).
            _isWindowOpen.value = checkWindowOpen(context)
        } else if (!isSystemOrLauncherPackage(packageName)) {
            // Another third party app opened. Check if Yandex is still running/open.
            _isWindowOpen.value = checkWindowOpen(context)
        }
    }

    fun checkWindowOpen(context: Context): Boolean {
        // 1. Authoritative check via DclwmClient if running on head unit
        val dclwmHandled: Boolean? = runCatching {
            val client = DclwmClient()
            val tasks = client.list()
            if (tasks.isNotEmpty()) {
                // If any Yandex task exists in dclwm (regardless of whether it is on-screen
                // or parked beyond screen boundaries at left >= 2000), it is considered open!
                tasks.any { isYandexNavigationPackage(it.packageName) }
            } else {
                null
            }
        }.getOrNull()

        if (dclwmHandled != null) return dclwmHandled

        // 2. Authoritative check via am stack list
        val stackHandled: Boolean? = runCatching {
            val stacks = LauncherAmStackShell.listStacks()
            if (stacks.isNotEmpty()) {
                // If any Yandex stack exists (regardless of whether bounds are on-screen or off-screen), it is open!
                stacks.any { isYandexNavigationPackage(it.packageName) }
            } else {
                null
            }
        }.getOrNull()

        if (stackHandled != null) return stackHandled

        // 3. Check in-memory tracker
        val activePkg = LauncherActiveAppTracker.activeAppPackage()
        if (isYandexNavigationPackage(activePkg)) {
            return true
        }

        // 4. Check active freeform launch registry (even if bounds are off-screen)
        val freeformPkgs = FreeformLaunchRegistry.snapshot()
        if (freeformPkgs.any { isYandexNavigationPackage(it) }) {
            return true
        }

        // 5. Check ActivityManager top/running tasks
        val am = runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
        if (am != null) {
            @Suppress("DEPRECATION")
            val runningTasks = runCatching { am.getRunningTasks(10) }.getOrNull()
            if (runningTasks != null) {
                val hasYandex = runningTasks.any { task ->
                    val top = task.topActivity?.packageName
                    val base = task.baseActivity?.packageName
                    isYandexNavigationPackage(top) || isYandexNavigationPackage(base)
                }
                if (hasYandex) return true
            }
        }

        return false
    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "vad.dashing.tbox" ||
                lower == "com.android.systemui" ||
                lower.startsWith("com.android.system")
    }

    @VisibleForTesting
    internal fun setWindowOpenForTest(open: Boolean) {
        _isWindowOpen.value = open
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
