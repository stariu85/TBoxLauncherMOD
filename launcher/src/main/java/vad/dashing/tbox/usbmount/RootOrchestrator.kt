package vad.dashing.tbox.usbmount

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.ShellExecutor
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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

    /** Safely single-quotes a string for POSIX /system/bin/sh. */
    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    /**
     * Centralized root script runner via mb_daemon (/sdcard/autorun.sh).
     * Acquires [ROOT_LOCK], waits for any existing [AUTORUN_FLAG] to clear,
     * clears [AUTORUN_PROP] and sleeps 300ms BEFORE writing [AUTORUN] to minimize TOCTOU,
     * avoids world-writable permissions (uses chmod 700 / 600), and triggers mb_daemon.
     * [onTriggered] runs while still holding [ROOT_LOCK] so callers can wait for completion.
     */
    fun runAutorunScript(
        scriptBody: String,
        waitPriorFlagMs: Long = 3_000L,
        onTriggered: (() -> Boolean)? = null,
    ): Boolean {
        if (!isStorageMounted()) {
            Log.w(TAG, "Storage is not mounted yet, cannot write autorun script")
            return false
        }
        synchronized(ROOT_LOCK) {
            val flagFile = File(AUTORUN_FLAG)
            if (flagFile.exists()) {
                Log.i(TAG, "Waiting for previous autorun.flag to clear...")
                val waitDeadline = System.currentTimeMillis() + waitPriorFlagMs
                while (flagFile.exists() && System.currentTimeMillis() < waitDeadline) {
                    Thread.sleep(200)
                }
                if (flagFile.exists()) {
                    Log.w(TAG, "Previous autorun.flag still present after ${waitPriorFlagMs}ms, proceeding")
                }
            }

            // Reset prop and settle BEFORE writing /sdcard/autorun.sh to minimize TOCTOU window.
            exec("setprop", AUTORUN_PROP, "")
            Thread.sleep(300)

            val normalizedScript = scriptBody.replace("\r\n", "\n").replace('\r', '\n')
            val autorunFile = File(AUTORUN)
            FileOutputStream(autorunFile).use { it.write(normalizedScript.toByteArray(Charsets.UTF_8)) }
            exec("chmod", "700", autorunFile.absolutePath)

            flagFile.createNewFile()
            exec("chmod", "600", flagFile.absolutePath)

            exec("setprop", AUTORUN_PROP, AUTORUN_FLAG)
            return onTriggered?.invoke() ?: true
        }
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
        runUnlockUsbSync(context)
    }

    internal fun runUnlockUsbSync(context: Context): Boolean {
        if (!isStorageMounted()) {
            Log.w(TAG, "Storage is not mounted yet, cannot write autorun script")
            DclLogRecorder.log("USBMNT", "Storage not mounted, skipping runUnlockUsb")
            return false
        }

        if (!UsbMountManager.isUsbHandlerDisabledOrHidden(context)) {
            Log.w(TAG, "android.car.usb.handler is not hidden! Refusing to run unlockusb")
            DclLogRecorder.log("USBMNT", "BLOCKED: android.car.usb.handler is active")
            return false
        }

        synchronized(ROOT_LOCK) {
            try {
                val dexFile = File(context.filesDir, UNLOCK_USB_DEX_ASSET)
                copyAsset(context, UNLOCK_USB_DEX_ASSET, dexFile)

                val logFile = File(UNLOCK_USB_LOG)
                val markerFile = File(GATE_DONE_MARKER)
                runCatching { logFile.delete() }
                runCatching { markerFile.delete() }

                val quotedDex = shellQuote(dexFile.absolutePath)
                val script = "#!/system/bin/sh\n" +
                        "rm -f " + UNLOCK_USB_LOG + " " + GATE_DONE_MARKER + "\n" +
                        "CLASSPATH=" + quotedDex + " app_process /system/bin UnlockUsb > " + UNLOCK_USB_LOG + " 2>&1 && echo OK > " + GATE_DONE_MARKER + "\n" +
                        "chmod 644 " + UNLOCK_USB_LOG + "\n" +
                        "[ -f " + GATE_DONE_MARKER + " ] && chmod 644 " + GATE_DONE_MARKER + "\n" +
                        "rm -f \"\$0\" " + AUTORUN_FLAG + "\n" +
                        "setprop " + AUTORUN_PROP + " ''\n"

                return runAutorunScript(script) {
                    val flagFile = File(AUTORUN_FLAG)
                    val deadline = System.currentTimeMillis() + 5000L
                    var logOutput = ""
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

                    if (logFile.exists() && logFile.length() > 0) {
                        logOutput = runCatching { logFile.readText(Charsets.UTF_8) }.getOrDefault("")
                    }

                    val success = !logOutput.contains("FAIL") &&
                            (logOutput.contains("ok") || (markerFile.exists() && markerFile.length() > 0))

                    DclLogRecorder.log("USBMNT", "unlockUsb completed: success=$success, log=${logOutput.trim()}")
                    Log.i(TAG, "unlockUsb completed: success=$success, log=${logOutput.trim()}")
                    success
                }
            } catch (e: Exception) {
                Log.e(TAG, "runUnlockUsb failed", e)
                DclLogRecorder.log("USBMNT", "runUnlockUsb exception: ${e.message}")
                return false
            }
        }
    }

    private fun copyAsset(context: Context, assetName: String, dest: File) {
        val assetBytes = context.assets.open(assetName).use { it.readBytes() }
        if (dest.exists() && dest.length() == assetBytes.size.toLong()) {
            val existingHash = runCatching { sha256(dest.readBytes()) }.getOrNull()
            if (existingHash != null && existingHash.contentEquals(sha256(assetBytes))) {
                dest.setReadable(true, false)
                return
            }
        }
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(tmp).use { it.write(assetBytes) }
        tmp.setReadable(true, false)
        if (!tmp.renameTo(dest)) {
            FileOutputStream(dest).use { it.write(assetBytes) }
            tmp.delete()
            dest.setReadable(true, false)
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun exec(vararg args: String) {
        val result = ShellExecutor.exec(args.toList(), timeoutMs = 5_000L)
        if (!result.isSuccess) {
            Log.w(TAG, "exec failed for ${args.firstOrNull()} (exit=${result.exitCode}, timedOut=${result.timedOut}): ${result.output.trim()}")
        }
    }
}
