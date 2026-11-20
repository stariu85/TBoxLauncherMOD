package vad.dashing.tbox

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore

/**
 * Tracks boot sessions to detect if the system rebooted within 5 minutes of app start.
 * If an unexpected reboot occurred within 5 minutes, automatically enables auto-backup
 * and boot log for the next startup.
 *
 * Avoids false positives from uncalibrated RTC / SNTP clock jumps by:
 * 1. Tracking kernel boot id (/proc/sys/kernel/random/boot_id) and SystemClock.elapsedRealtime().
 * 2. Waiting for system time synchronization (NtpTrustedTime / NetworkTimeClock / ACTION_TIME_SET)
 *    before evaluating wall-clock delta (wallDelta).
 * 3. Starting the 3-minute (boot log duration) and 5-minute (stable runtime) countdowns only
 *    after time synchronization is established.
 */
object BootSessionTracker {
    private const val TAG = "BootSessionTracker"
    private const val PREFS = "tbox_boot_tracker"
    private const val KEY_BOOT_ID = "session_boot_id"
    private const val KEY_START_WALL_TIME = "session_start_wall_time"
    private const val KEY_START_REALTIME = "session_start_realtime"
    private const val KEY_5MIN_PASSED = "session_5min_passed"
    private const val KEY_CLEAN_SHUTDOWN = "session_clean_shutdown"

    private const val THREE_MIN_MS = 180_000L // 3 minutes (boot log duration)
    private const val FIVE_MIN_MS = 300_000L // 5 minutes (stable session threshold)
    private const val TIME_SYNC_TIMEOUT_MS = 45_000L // 45 seconds max wait for SNTP/time sync

    @Volatile
    private var isStarted = false

    @Volatile
    private var shutdownReceiverRegistered = false

    fun onAppStart(context: Context, scope: CoroutineScope) {
        val appCtx = context.applicationContext
        if (isStarted) return
        isStarted = true

        registerShutdownReceiver(appCtx)

        scope.launch(Dispatchers.Default) {
            val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val prevBootId = prefs.getString(KEY_BOOT_ID, "").orEmpty()
            val prevStartWall = prefs.getLong(KEY_START_WALL_TIME, 0L)
            val prevStartRealtime = prefs.getLong(KEY_START_REALTIME, 0L)
            val prev5MinPassed = prefs.getBoolean(KEY_5MIN_PASSED, true)
            val prevCleanShutdown = prefs.getBoolean(KEY_CLEAN_SHUTDOWN, false)

            val currentBootId = currentBootId()
            val currentUptime = SystemClock.elapsedRealtime()

            // Wait for system time synchronization (or timeout) before checking wallDelta
            val timeSynchronized = awaitTimeSyncOrTimeout(appCtx, TIME_SYNC_TIMEOUT_MS)
            val currentWall = System.currentTimeMillis()

            val systemRebooted = (prevBootId.isNotEmpty() && prevBootId != currentBootId) ||
                (prevStartRealtime > 0L && currentUptime < prevStartRealtime)

            if (timeSynchronized && !prev5MinPassed && !prevCleanShutdown && systemRebooted && prevStartWall > 0L) {
                val wallDelta = currentWall - prevStartWall
                val isWithinRebootWindow = wallDelta in 1..(FIVE_MIN_MS + 120_000L)
                if (isWithinRebootWindow) {
                    Log.w(
                        TAG,
                        "System reboot detected within 5 minutes of previous start (prevBootId=$prevBootId, currentBootId=$currentBootId, prevUptime=$prevStartRealtime, currentUptime=$currentUptime, wallDelta=${wallDelta}ms). Auto-enabling auto-backup and boot log for startup!",
                    )
                    DclLogRecorder.log(
                        "BootTracker",
                        "System reboot detected within 5 min of last start (prevBootId=$prevBootId, currentBootId=$currentBootId, prevUptime=$prevStartRealtime, currentUptime=$currentUptime). Automatically enabling auto-backup and boot log.",
                    )

                    // Automatically enable auto-backup toggle at start
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            SettingsManager(appCtx).saveAutoBackupEnabled(true)
                        }
                    }

                    // Also enable boot log toggle and start boot session if not already recording
                    LauncherAppConfigStore.setBootLogEnabled(appCtx, true)
                    if (!DclLogRecorder.isBootLoggingActive()) {
                        DclLogRecorder.startBootSession(appCtx)
                    }
                } else {
                    Log.i(
                        TAG,
                        "System reboot detected but outside 5-minute window (wallDelta=${wallDelta}ms, prevBootId=$prevBootId). No action needed.",
                    )
                }
            } else if (!timeSynchronized) {
                Log.i(
                    TAG,
                    "Time synchronization not detected within timeout. Skipping wallDelta reboot check to avoid false positives from unsynced clock.",
                )
            }

            // Record start state for current session
            prefs.edit()
                .putString(KEY_BOOT_ID, currentBootId)
                .putLong(KEY_START_WALL_TIME, currentWall)
                .putLong(KEY_START_REALTIME, SystemClock.elapsedRealtime())
                .putBoolean(KEY_5MIN_PASSED, false)
                .putBoolean(KEY_CLEAN_SHUTDOWN, false)
                .apply()

            // Countdowns start only AFTER time synchronization (or timeout)
            // 1. 3-minute mark: stop boot logging session if active
            delay(THREE_MIN_MS)
            if (DclLogRecorder.isBootLoggingActive()) {
                DclLogRecorder.stopLogging("3 minutes boot session completed after time sync")
            }
            Log.i(TAG, "3 minutes reached after time sync: boot logging stopped if active.")

            // 2. 5-minute mark: mark stable session reached and disable boot log
            delay(FIVE_MIN_MS - THREE_MIN_MS)
            prefs.edit().putBoolean(KEY_5MIN_PASSED, true).apply()
            LauncherAppConfigStore.setBootLogEnabled(appCtx, false)
            Log.i(TAG, "5 minutes of stable runtime reached after time sync. Boot log disabled.")
            DclLogRecorder.log("BootTracker", "5 minutes of stable runtime reached after time sync. Boot log disabled.")
        }
    }

    private fun registerShutdownReceiver(context: Context) {
        if (shutdownReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val prefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
                prefs.edit().putBoolean(KEY_CLEAN_SHUTDOWN, true).apply()
                Log.i(TAG, "Clean shutdown received (${intent?.action}), recorded for next boot")
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            shutdownReceiverRegistered = true
        }
    }

    private suspend fun awaitTimeSyncOrTimeout(context: Context, timeoutMs: Long): Boolean {
        if (isTimeSynchronized(context)) {
            Log.d(TAG, "System time already verified as synchronized")
            return true
        }

        var receiverRegistered = false
        val syncEvent = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                Log.d(TAG, "Received ${intent?.action}, system time set/changed")
                syncEvent.complete(true)
            }
        }

        return try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction("android.intent.action.TIME_SET")
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true

            if (isTimeSynchronized(context)) {
                Log.d(TAG, "System time synchronized right after receiver registered")
                return true
            }

            withTimeoutOrNull(timeoutMs) {
                syncEvent.await()
            } ?: false
        } catch (e: Throwable) {
            Log.w(TAG, "Error while waiting for time synchronization", e)
            false
        } finally {
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun isTimeSynchronized(context: Context): Boolean {
        // 1. Android 13+ NetworkTimeClock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val synced = runCatching {
                SystemClock.currentNetworkTimeClock().millis()
                true
            }.getOrDefault(false)
            if (synced) return true
        }

        // 2. NtpTrustedTime reflection (Android 9/10/11/12)
        val ntpSynced = runCatching {
            val clazz = Class.forName("android.util.NtpTrustedTime")
            val method = clazz.getMethod("getInstance", Context::class.java)
            val instance = method.invoke(null, context)
            val hasCacheMethod = clazz.getMethod("hasCache")
            (hasCacheMethod.invoke(instance) as? Boolean) == true
        }.getOrDefault(false)
        if (ntpSynced) return true

        return false
    }

    /** Kernel boot id — unique UUID generated per Linux boot. */
    private fun currentBootId(): String = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("")
}
