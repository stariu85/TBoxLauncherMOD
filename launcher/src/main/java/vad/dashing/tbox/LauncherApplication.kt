package vad.dashing.tbox

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.YandexNavRepository

/**
 * Standalone HOME launcher process — no TBox UDP / trips / fuel pipeline.
 */
class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        vad.dashing.tbox.ui.launcher.LauncherFirstInstallDefaults.applyIfNeeded(this)

        val settings = SettingsManager(this)
        BootSessionTracker.onAppStart(this, appScope)

        if (vad.dashing.tbox.ui.launcher.LauncherAppConfigStore.bootLogEnabled(this)) {
            DclLogRecorder.startBootSession(this)
        } else {
            DclLogRecorder.stopLogging("Launcher restarted")
        }

        DiagFileLog.install(this)
        YandexNavRepository.start(this)

        // Asynchronously preload heavy assets on IO so they are ready before the first Compose frame
        vad.dashing.tbox.ui.launcher.LauncherCarModelCache.preload(this)
        vad.dashing.tbox.ui.LaunchableAppsWithIconsCache.preload(this)
        appScope.launch {
            runCatching { clearCacheOnAppUpdate(settings) }
                .onFailure { Log.w(TAG, "App cache clear on update failed", it) }
            runCatching { syncHuRebootAfterUpdate(settings) }
                .onFailure { Log.w(TAG, "HU reboot-after-update sync failed", it) }
            // Defer mbCAN startup from Application.onCreate() to prioritize the initial HOME frame draw.
            // If LauncherHomeActivity starts, it will trigger CAN auto-bind right after first draw.
            // This 10s fallback ensures CAN connects even if started for a background service only.
            delay(10_000L)
            UniversalCanRepository.triggerStartupCanAutoBind(settings, appScope)
        }
    }

    private suspend fun clearCacheOnAppUpdate(settings: SettingsManager) {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        settings.clearCacheOnAppUpdate(versionCode)
    }

    private suspend fun syncHuRebootAfterUpdate(settings: SettingsManager) {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        settings.syncHuRebootAfterUpdate(
            currentVersionCode = versionCode,
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            lastUpdateTimeMs = packageInfo.lastUpdateTime,
            firstInstallTimeMs = packageInfo.firstInstallTime,
            nowWallClockMs = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "LauncherApp"
    }
}
