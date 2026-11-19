package vad.dashing.tbox.usbmount

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DclLogRecorder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Unified coordinator for root tasks executed via mb_daemon (/sdcard/autorun.sh).
 * Synchronizes execution using [ROOT_LOCK] to eliminate race conditions between dclwm and usb_mount.
 */
object RootOrchestrator {
    private const val TAG = "RootOrchestrator"

    @JvmField
    val ROOT_LOCK = Any()

    const val AUTORUN = "/sdcard/autorun.sh"
    const val AUTORUN_FLAG = "/sdcard/autorun.flag"
    const val AUTORUN_PROP = "vendor.autorun.sdcard.logfile"
    const val UNLOCK_USB_DEX_ASSET = "unlockusb.dex"
    const val UNLOCK_USB_LOG = "/data/local/tmp/unlockusb_boot.txt"

    fun isStorageMounted(): Boolean {
        return Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
    }

    suspend fun runUnlockUsb(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isStorageMounted()) {
            Log.w(TAG, "Storage is not mounted yet, cannot write autorun script")
            DclLogRecorder.log("USBMNT", "Storage not mounted, skipping runUnlockUsb")
            return@withContext false
        }

        if (!UsbMountManager.isUsbHandlerDisabledOrHidden(context)) {
            Log.w(TAG, "android.car.usb.handler is not hidden! Refusing to run unlockusb")
            DclLogRecorder.log("USBMNT", "BLOCKED: android.car.usb.handler is active")
            return@withContext false
        }

        synchronized(ROOT_LOCK) {
            try {
                val dexFile = File(context.filesDir, UNLOCK_USB_DEX_ASSET)
                copyAsset(context, UNLOCK_USB_DEX_ASSET, dexFile)

                File(UNLOCK_USB_LOG).delete()

                val script = "#!/system/bin/sh\n" +
                        "CLASSPATH='" + dexFile.absolutePath + "' app_process /system/bin UnlockUsb > " + UNLOCK_USB_LOG + " 2>&1\n" +
                        "rm -f \"$0\" " + AUTORUN_FLAG + "\n" +
                        "setprop " + AUTORUN_PROP + " ''\n"

                val autorunFile = File(AUTORUN)
                FileOutputStream(autorunFile).use { it.write(script.toByteArray(Charsets.UTF_8)) }
                exec("chmod", "777", autorunFile.absolutePath)

                val flagFile = File(AUTORUN_FLAG)
                flagFile.createNewFile()
                exec("chmod", "666", flagFile.absolutePath)

                exec("setprop", AUTORUN_PROP, "")
                Thread.sleep(300)
                exec("setprop", AUTORUN_PROP, AUTORUN_FLAG)

                val deadline = System.currentTimeMillis() + 5000L
                val logFile = File(UNLOCK_USB_LOG)
                var logOutput = ""
                while (System.currentTimeMillis() < deadline) {
                    if (logFile.exists() && logFile.length() > 0) {
                        logOutput = logFile.readText(Charsets.UTF_8)
                        if (logOutput.contains("ok") || logOutput.contains("FAIL")) {
                            break
                        }
                    }
                    Thread.sleep(200)
                }

                val success = logOutput.contains("ok") || !flagFile.exists()
                DclLogRecorder.log("USBMNT", "unlockUsb completed: success=$success, log=${logOutput.trim()}")
                Log.i(TAG, "unlockUsb completed: success=$success, log=${logOutput.trim()}")
                return@withContext success
            } catch (e: Exception) {
                Log.e(TAG, "runUnlockUsb failed", e)
                DclLogRecorder.log("USBMNT", "runUnlockUsb exception: ${e.message}")
                return@withContext false
            }
        }
    }

    private fun copyAsset(context: Context, assetName: String, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        context.assets.open(assetName).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        dest.setReadable(true, false)
    }

    private fun exec(vararg args: String) {
        try {
            val process = ProcessBuilder(*args).redirectErrorStream(true).start()
            process.waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "exec failed for ${args.firstOrNull()}", e)
        }
    }
}
