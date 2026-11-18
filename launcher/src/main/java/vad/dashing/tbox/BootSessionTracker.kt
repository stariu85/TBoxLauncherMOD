package vad.dashing.tbox

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore

/**
 * Tracks boot sessions to detect if the system rebooted within 5 minutes of app start.
 * If an unexpected reboot occurred within 5 minutes, automatically enables auto-backup
 * and boot log for the next startup.
 */
object BootSessionTracker {
    private const val TAG = "BootSessionTracker"
    private const val PREFS = "tbox_boot_tracker"
    private const val KEY_START_WALL_TIME = "session_start_wall_time"
    private const val KEY_START_REALTIME = "session_start_realtime"
    private const val KEY_5MIN_PASSED = "session_5min_passed"
    private const val FIVE_MIN_MS = 300_000L // 5 minutes

    fun onAppStart(context: Context, scope: CoroutineScope) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prevStartWall = prefs.getLong(KEY_START_WALL_TIME, 0L)
        val prevStartRealtime = prefs.getLong(KEY_START_REALTIME, 0L)
        val prev5MinPassed = prefs.getBoolean(KEY_5MIN_PASSED, true)

        val currentUptime = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()

        if (prevStartWall > 0L && !prev5MinPassed) {
            val systemRebooted = (currentUptime < prevStartRealtime) || (currentUptime < (FIVE_MIN_MS + 60_000L))
            val wallDelta = currentWall - prevStartWall
            val isWithinRebootWindow = (wallDelta in 1..(FIVE_MIN_MS + 120_000L)) || (wallDelta < 0 && currentUptime < (FIVE_MIN_MS + 60_000L))
            if (systemRebooted && isWithinRebootWindow) {
                Log.w(
                    TAG,
                    "System reboot detected within 5 minutes of previous start (prevUptime=$prevStartRealtime, currentUptime=$currentUptime, wallDelta=${wallDelta}ms). Auto-enabling auto-backup and boot log for startup!",
                )
                DclLogRecorder.log(
                    "BootTracker",
                    "System reboot detected within 5 min of last start (prevUptime=$prevStartRealtime, currentUptime=$currentUptime). Automatically enabling auto-backup and boot log.",
                )

                // Automatically enable auto-backup toggle at start
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        SettingsManager(appCtx).saveAutoBackupEnabled(true)
                    }
                }

                // Also enable boot log toggle
                LauncherAppConfigStore.setBootLogEnabled(appCtx, true)
            }
        }

        // Record start for current session
        prefs.edit()
            .putLong(KEY_START_WALL_TIME, currentWall)
            .putLong(KEY_START_REALTIME, currentUptime)
            .putBoolean(KEY_5MIN_PASSED, false)
            .apply()

        // Schedule 5-minute stable completion mark: if no reboot occurred within 5 minutes, turn off boot logging
        scope.launch(Dispatchers.Default) {
            delay(FIVE_MIN_MS)
            prefs.edit().putBoolean(KEY_5MIN_PASSED, true).apply()
            LauncherAppConfigStore.setBootLogEnabled(appCtx, false)
            Log.i(TAG, "5 minutes of stable runtime reached for current session. Boot log disabled.")
            DclLogRecorder.log("BootTracker", "5 minutes of stable runtime reached. Boot log disabled.")
        }
    }
}
