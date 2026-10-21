package vad.dashing.tbox

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated rotating file log for launcher crash / body / media diagnostics.
 *
 * Files land in app-private storage so they survive restarts and can be pulled via:
 * `adb shell run-as vad.dashing.tbox cat files/diag/tbox-diag.log`
 * or from external files dir when available:
 * `/sdcard/Android/data/vad.dashing.tbox/files/diag/tbox-diag.log`
 */
object DiagFileLog {
    private const val TAG = "TboxDiag"
    private const val DIR_NAME = "diag"
    private const val FILE_NAME = "tbox-diag.log"
    private const val PREV_FILE_NAME = "tbox-diag.prev.log"
    private const val MAX_BYTES = 1_500_000L

    private val lock = Any()
    private val installed = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tbox-diag-log").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    @Volatile
    var enabled: Boolean = true

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        logFile = resolveLogFile(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e(
                    "FATAL",
                    "Uncaught in ${thread.name} pid=${Process.myPid()}",
                    throwable,
                )
                flushSync()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
        i("Diag", "installed file=${logFile?.absolutePath}")
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String) = write("W", tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** Blocking flush for crash path only. */
    fun flushSync() {
        synchronized(lock) {
            // Single-thread executor drains before this returns if we submit and await.
        }
        runCatching {
            writer.submit { }.get()
        }
    }

    private fun resolveLogFile(context: Context): File {
        val external = context.getExternalFilesDir(null)
        val root = external ?: context.filesDir
        val dir = File(root, DIR_NAME).apply { mkdirs() }
        return File(dir, FILE_NAME)
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val file = logFile
        val stamp = timeFormat.get()?.format(Date()) ?: System.currentTimeMillis().toString()
        val stack = error?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            "\n" + sw.toString().trimEnd()
        }.orEmpty()
        val line = "$stamp $level/$tag: $message$stack"
        when (level) {
            "E" -> Log.e(TAG, "$tag: $message", error)
            "W" -> Log.w(TAG, "$tag: $message")
            else -> Log.i(TAG, "$tag: $message")
        }
        if (file == null || !enabled) return
        writer.execute {
            synchronized(lock) {
                runCatching {
                    rotateIfNeeded(file)
                    file.appendText(line + "\n")
                }
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_BYTES) return
        val prev = File(file.parentFile, PREV_FILE_NAME)
        if (prev.exists()) prev.delete()
        file.renameTo(prev)
    }
}
