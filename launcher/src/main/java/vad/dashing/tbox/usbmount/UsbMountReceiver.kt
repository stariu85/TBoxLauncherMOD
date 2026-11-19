package vad.dashing.tbox.usbmount

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * BroadcastReceiver for boot, quickboot power on, manual ARM trigger,
 * and USB device attach/check hotplug events.
 */
class UsbMountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive action=$action")
        val appCtx = context.applicationContext
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            UsbMountManager.ACTION_ARM -> {
                UsbMountManager.onBootOrQuickboot(appCtx)
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            Intent.ACTION_MEDIA_CHECKING -> {
                UsbMountManager.onUsbDeviceAttached(appCtx)
            }
        }
    }

    companion object {
        private const val TAG = "UsbMountReceiver"
    }
}
