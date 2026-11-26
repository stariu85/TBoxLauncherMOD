package vad.dashing.tbox.usbmount

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.SettingsManager

object UsbMountManager {
    private const val TAG = "UsbMountManager"
    const val USB_HANDLER_PACKAGE = "android.car.usb.handler"
    const val ACTION_ARM = "vad.dashing.usbmount.ARM"

    private const val MAX_REARM_ATTEMPTS = 10
    private const val MAX_STORAGE_WAIT_ATTEMPTS = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stage2Lock = Any()
    private var stage2Job: Job? = null

    @Volatile
    private var isGateUnlockedThisBoot = false

    /**
     * Checks whether the stock CarUsbHandler (android.car.usb.handler) is hidden (pm hide) or disabled.
     * When hidden via `pm hide`, PackageManager.getApplicationInfo throws NameNotFoundException.
     * If the app is present and enabled, returns false (not hidden -> must block usb_mount).
     */
    fun isUsbHandlerDisabledOrHidden(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(USB_HANDLER_PACKAGE, 0)
            !appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            // Package not found (hidden via pm hide or missing from system) -> safe!
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Checks if a removable USB storage volume is already mounted in Android StorageManager.
     */
    fun isRemovableStorageAlreadyMounted(context: Context): Boolean {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
            sm.storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Checks if the vold secure keyguard gate was already unlocked during the current boot session:
     * 1. In-memory flag [isGateUnlockedThisBoot].
     * 2. StorageManager already reports a mounted removable storage volume.
     * 3. Valid gate marker file created during this boot session exists.
     */
    fun isGateAlreadyUnlocked(context: Context): Boolean {
        if (isGateUnlockedThisBoot) return true
        if (isRemovableStorageAlreadyMounted(context)) {
            isGateUnlockedThisBoot = true
            return true
        }
        if (RootOrchestrator.isGateMarkerValidForCurrentBoot()) {
            isGateUnlockedThisBoot = true
            return true
        }
        return false
    }

    /**
     * Stage 2 startup watcher: invoked during Stage 2 before the full system boot sequence finishes.
     * If external storage is not ready yet, checks every 1 second until mounted, then runs unlockusb.
     * Unlocking the vold gate is needed only once per boot. Even if no USB flash drive is currently
     * inserted, unlocking the gate succeeds and vold will automatically mount drives upon later insertion.
     * After [isGateAlreadyUnlocked] is true, no re-arming is performed.
     * In case of failures, attempts are strictly limited to [MAX_REARM_ATTEMPTS] (10 attempts).
     */
    fun startStage2Watcher(context: Context) {
        startStage2WatcherJob(context)
    }

    internal fun startStage2WatcherJob(context: Context): Job {
        val appCtx = context.applicationContext
        synchronized(stage2Lock) {
            val existing = stage2Job
            if (existing?.isActive == true) {
                Log.i(TAG, "Stage 2 watcher is already running")
                return existing
            }

            val job = scope.launch {
                val settings = SettingsManager(appCtx)
                if (!settings.isUsbMountEnabled()) {
                    Log.i(TAG, "USB mount disabled in settings, skipping Stage 2 mount")
                    DclLogRecorder.log("USBMNT", "Stage 2: disabled in settings")
                    return@launch
                }

                if (!isUsbHandlerDisabledOrHidden(appCtx)) {
                    Log.w(TAG, "Stage 2: $USB_HANDLER_PACKAGE is active (not hidden). Not starting unlockusb!")
                    DclLogRecorder.log("USBMNT", "Stage 2 BLOCKED: $USB_HANDLER_PACKAGE is not hidden")
                    return@launch
                }

                RootOrchestrator.resetMarkerOnNewBoot()

                if (isGateAlreadyUnlocked(appCtx)) {
                    Log.i(TAG, "Stage 2: gate is already unlocked this boot, no action needed")
                    DclLogRecorder.log("USBMNT", "Stage 2: gate already unlocked, skipping")
                    return@launch
                }

                Log.i(TAG, "Starting Stage 2 USB mount watcher (checking storage every 1s, max $MAX_STORAGE_WAIT_ATTEMPTS attempts)")
                DclLogRecorder.log("USBMNT", "Stage 2: watcher started (checking storage every 1s)")

                var storageAttempt = 0
                while (isActive) {
                    storageAttempt++
                    if (RootOrchestrator.isStorageMounted()) {
                        DclLogRecorder.log("USBMNT", "Stage 2: storage is ready (attempt $storageAttempt/$MAX_STORAGE_WAIT_ATTEMPTS)")
                        break
                    } else {
                        Log.d(TAG, "Stage 2: storage not mounted yet (state=${Environment.getExternalStorageState()}, attempt $storageAttempt), retry in 1s")
                    }

                    if (storageAttempt >= MAX_STORAGE_WAIT_ATTEMPTS) {
                        Log.w(TAG, "Stage 2: storage wait timed out after $MAX_STORAGE_WAIT_ATTEMPTS attempts")
                        DclLogRecorder.log("USBMNT", "Stage 2: storage wait timed out after ${MAX_STORAGE_WAIT_ATTEMPTS}s")
                        return@launch
                    }
                    delay(1000L)
                }

                if (!isActive) return@launch

                // Execute unlockUsb with bounded retries (max 10 attempts with backoff)
                for (attempt in 1..MAX_REARM_ATTEMPTS) {
                    if (!isActive) break
                    if (attempt > 1) {
                        val backoffMs = ((attempt - 1) * 1500L).coerceAtMost(10_000L)
                        delay(backoffMs)
                    }
                    val ok = synchronized(RootOrchestrator.ROOT_LOCK) {
                        if (isGateAlreadyUnlocked(appCtx)) {
                            Log.i(TAG, "Stage 2: gate already unlocked before attempt $attempt, skipping")
                            DclLogRecorder.log("USBMNT", "Stage 2: gate already unlocked, skipping")
                            return@launch
                        }
                        DclLogRecorder.log("USBMNT", "Stage 2: executing unlockUsb (attempt $attempt/$MAX_REARM_ATTEMPTS)")
                        val unlocked = RootOrchestrator.runUnlockUsbSync(appCtx)
                        if (unlocked) {
                            isGateUnlockedThisBoot = true
                        }
                        unlocked
                    }
                    if (ok) {
                        DclLogRecorder.log("USBMNT", "Stage 2: unlockUsb succeeded (gate unlocked)")
                        return@launch
                    } else {
                        DclLogRecorder.log("USBMNT", "Stage 2: unlockUsb failed on attempt $attempt/$MAX_REARM_ATTEMPTS")
                    }
                }
                DclLogRecorder.log("USBMNT", "Stage 2: unlockUsb exhausted all $MAX_REARM_ATTEMPTS attempts")
            }
            stage2Job = job
            return job
        }
    }

    /**
     * Hotplug: invoked when a USB device is attached (USB_DEVICE_ATTACHED or MEDIA_CHECKING).
     * If the vold gate is already unlocked, vold handles scanning and mounting automatically.
     */
    fun onUsbDeviceAttached(context: Context) {
        onUsbDeviceAttachedJob(context)
    }

    internal fun onUsbDeviceAttachedJob(context: Context): Job {
        val appCtx = context.applicationContext
        return scope.launch {
            val settings = SettingsManager(appCtx)
            if (!settings.isUsbMountEnabled()) {
                Log.i(TAG, "Hotplug: USB mount disabled in settings, ignoring attach")
                return@launch
            }
            if (!isUsbHandlerDisabledOrHidden(appCtx)) {
                Log.w(TAG, "Hotplug: $USB_HANDLER_PACKAGE is active, blocked")
                DclLogRecorder.log("USBMNT", "Hotplug BLOCKED: $USB_HANDLER_PACKAGE is not hidden")
                return@launch
            }
            synchronized(RootOrchestrator.ROOT_LOCK) {
                if (isGateAlreadyUnlocked(appCtx)) {
                    Log.i(TAG, "Hotplug: Gate is already unlocked this boot, vold handles mount automatically")
                    DclLogRecorder.log("USBMNT", "Hotplug: gate already unlocked, vold handles mount")
                    return@launch
                }
                DclLogRecorder.log("USBMNT", "Hotplug: gate not unlocked yet, executing unlockUsb")
                val ok = RootOrchestrator.runUnlockUsbSync(appCtx)
                if (ok) {
                    isGateUnlockedThisBoot = true
                }
            }
        }
    }

    fun onBootOrQuickboot(context: Context) {
        onBootOrQuickbootJob(context)
    }

    internal fun onBootOrQuickbootJob(context: Context): Job? {
        val appCtx = context.applicationContext
        if (isGateAlreadyUnlocked(appCtx)) {
            Log.i(TAG, "onBootOrQuickboot: gate already unlocked this boot, skipping")
            return null
        }
        return startStage2WatcherJob(context)
    }

    suspend fun triggerManualRemount(context: Context): Result<Unit> {
        val appCtx = context.applicationContext
        if (!isUsbHandlerDisabledOrHidden(appCtx)) {
            return Result.failure(IllegalStateException("Штатный обработчик USB (android.car.usb.handler) не скрыт!"))
        }
        val ok = RootOrchestrator.runUnlockUsb(appCtx)
        return if (ok) {
            isGateUnlockedThisBoot = true
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Скрипт монтирования не подтвердил успех"))
        }
    }
}
