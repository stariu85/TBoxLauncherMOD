package vad.dashing.tbox

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore

/**
 * Standalone HOME launcher process — no TBox UDP / trips / fuel pipeline.
 */
class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        LauncherAppConfigStore.resetDclLogsOnStartup(this)
        DclLogRecorder.stopLogging("Launcher restarted")
        DiagFileLog.install(this)
        val settings = SettingsManager(this)
        appScope.launch {
            runCatching { syncHuRebootAfterUpdate(settings) }
                .onFailure { Log.w(TAG, "HU reboot-after-update sync failed", it) }
            runCatching {
                UniversalCanRepository.autoResolveModeOnStartup(settings, appScope)
            }.onFailure {
                Log.w(TAG, "CAN auto-bind failed", it)
            }
        }
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
