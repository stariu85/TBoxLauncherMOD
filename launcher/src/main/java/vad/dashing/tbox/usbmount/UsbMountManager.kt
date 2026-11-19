package vad.dashing.tbox.usbmount

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stage2Job: Job? = null

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
     * Stage 2 startup watcher: invoked during Stage 2 before the full system boot sequence finishes.
     * If external storage is not ready yet, checks every 1 second until mounted, then runs unlockusb.
     */
    fun startStage2Watcher(context: Context) {
        val appCtx = context.applicationContext
        if (stage2Job?.isActive == true) {
            Log.i(TAG, "Stage 2 watcher is already running")
            return
        }

        stage2Job = scope.launch {
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

            Log.i(TAG, "Starting Stage 2 USB mount watcher (checking storage every 1s)")
            DclLogRecorder.log("USBMNT", "Stage 2: watcher started (checking storage every 1s)")

            var attempt = 0
            while (isActive) {
                attempt++
                if (RootOrchestrator.isStorageMounted()) {
                    DclLogRecorder.log("USBMNT", "Stage 2: storage is ready (attempt $attempt), executing unlockUsb")
                    val ok = RootOrchestrator.runUnlockUsb(appCtx)
                    DclLogRecorder.log("USBMNT", "Stage 2: unlockUsb result=$ok")
                    return@launch
                } else {
                    Log.d(TAG, "Stage 2: storage not mounted yet (state=${Environment.getExternalStorageState()}, attempt $attempt), retry in 1s")
                }

                if (attempt >= 60) {
                    Log.w(TAG, "Stage 2: storage wait timed out after 60 attempts")
                    DclLogRecorder.log("USBMNT", "Stage 2: storage wait timed out after 60s")
                    return@launch
                }
                delay(1000L)
            }
        }
    }

    /**
     * Hotplug: invoked when a USB device is attached (USB_DEVICE_ATTACHED or MEDIA_CHECKING).
     */
    fun onUsbDeviceAttached(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
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
            DclLogRecorder.log("USBMNT", "Hotplug: USB device attached, executing unlockUsb")
            RootOrchestrator.runUnlockUsb(appCtx)
        }
    }

    fun onBootOrQuickboot(context: Context) {
        startStage2Watcher(context)
    }

    suspend fun triggerManualRemount(context: Context): Result<Unit> {
        val appCtx = context.applicationContext
        if (!isUsbHandlerDisabledOrHidden(appCtx)) {
            return Result.failure(IllegalStateException("Штатный обработчик USB (android.car.usb.handler) не скрыт!"))
        }
        val ok = RootOrchestrator.runUnlockUsb(appCtx)
        return if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Скрипт монтирования не подтвердил успех"))
        }
    }
}
