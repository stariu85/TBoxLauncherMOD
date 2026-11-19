package vad.dashing.tbox.usbmount

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DclLogRecorder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Unified coordinator for root tasks executed via mb_daemon (/sdcard/autorun.sh).
 * Synchronizes execution using [ROOT_LOCK] to eliminate race conditions between dclwm, camd, and usb_mount.
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
    const val GATE_DONE_MARKER = "/data/local/tmp/usbmount_gate.done"

    fun isStorageMounted(): Boolean {
        return Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
    }

    /**
     * Resets or truncates stale gate marker from a previous boot session.
     */
    fun resetMarkerOnNewBoot() {
        val marker = File(GATE_DONE_MARKER)
        if (marker.exists()) {
            val bootTimeMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            if (marker.lastModified() < bootTimeMs - 10_000L) {
                if (!marker.delete()) {
                    runCatching {
                        FileOutputStream(marker).use { /* truncate to 0 bytes */ }
                    }
                }
            }
        }
    }

    /**
     * Checks if the gate marker file exists, is non-empty, and was created during the current boot session.
     */
    fun isGateMarkerValidForCurrentBoot(): Boolean {
        val marker = File(GATE_DONE_MARKER)
        if (!marker.exists() || marker.length() == 0L) return false
        val bootTimeMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return marker.lastModified() >= (bootTimeMs - 10_000L)
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
                // Wait for any prior autorun task (e.g. camd or dclwm) to finish
                val flagFile = File(AUTORUN_FLAG)
                if (flagFile.exists()) {
                    Log.i(TAG, "Waiting for previous autorun.flag to clear...")
                    val waitDeadline = System.currentTimeMillis() + 3000L
                    while (flagFile.exists() && System.currentTimeMillis() < waitDeadline) {
                        Thread.sleep(200)
                    }
                    if (flagFile.exists()) {
                        Log.w(TAG, "Previous autorun.flag still present after 3s, proceeding")
                    }
                }

                val dexFile = File(context.filesDir, UNLOCK_USB_DEX_ASSET)
                copyAsset(context, UNLOCK_USB_DEX_ASSET, dexFile)

                val logFile = File(UNLOCK_USB_LOG)
                runCatching { logFile.delete() }

                val script = "#!/system/bin/sh\n" +
                        "CLASSPATH='" + dexFile.absolutePath + "' app_process /system/bin UnlockUsb > " + UNLOCK_USB_LOG + " 2>&1\n" +
                        "chmod 666 " + UNLOCK_USB_LOG + "\n" +
                        "echo OK > " + GATE_DONE_MARKER + "\n" +
                        "chmod 666 " + GATE_DONE_MARKER + "\n" +
                        "rm -f \"$0\" " + AUTORUN_FLAG + "\n" +
                        "setprop " + AUTORUN_PROP + " ''\n"

                val autorunFile = File(AUTORUN)
                FileOutputStream(autorunFile).use { it.write(script.toByteArray(Charsets.UTF_8)) }
                exec("chmod", "777", autorunFile.absolutePath)

                flagFile.createNewFile()
                exec("chmod", "666", flagFile.absolutePath)

                exec("setprop", AUTORUN_PROP, "")
                Thread.sleep(300)
                exec("setprop", AUTORUN_PROP, AUTORUN_FLAG)

                val deadline = System.currentTimeMillis() + 5000L
                var logOutput = ""
                val markerFile = File(GATE_DONE_MARKER)
                while (System.currentTimeMillis() < deadline) {
                    if (logFile.exists() && logFile.length() > 0) {
                        logOutput = runCatching { logFile.readText(Charsets.UTF_8) }.getOrDefault("")
                        if (logOutput.contains("ok") || logOutput.contains("FAIL")) {
                            break
                        }
                    }
                    if (markerFile.exists() && markerFile.length() > 0 && !flagFile.exists()) {
                        break
                    }
                    Thread.sleep(200)
                }

                val success = (logOutput.contains("ok") && !logOutput.contains("FAIL")) ||
                        (markerFile.exists() && markerFile.length() > 0) ||
                        !flagFile.exists()

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
