package vad.dashing.tbox.usbmount

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * BroadcastReceiver for boot, quickboot power on, manual ARM trigger,
 * and USB device attach/check hotplug events.
 */
open class UsbMountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive action=$action")
        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeoutOrNull(GO_ASYNC_TIMEOUT_MS) {
                    when (action) {
                        Intent.ACTION_BOOT_COMPLETED,
                        "android.intent.action.QUICKBOOT_POWERON",
                        UsbMountManager.ACTION_ARM -> {
                            UsbMountManager.onBootOrQuickbootJob(appCtx)?.join()
                        }
                        UsbManager.ACTION_USB_DEVICE_ATTACHED,
                        Intent.ACTION_MEDIA_CHECKING -> {
                            UsbMountManager.onUsbDeviceAttachedJob(appCtx).join()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UsbMountReceiver"
        private const val GO_ASYNC_TIMEOUT_MS = 8_000L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * Dedicated signature-permission-protected receiver for manual [UsbMountManager.ACTION_ARM] triggers.
 */
class UsbMountArmReceiver : UsbMountReceiver()
