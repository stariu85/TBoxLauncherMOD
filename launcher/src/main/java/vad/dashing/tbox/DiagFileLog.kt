package vad.dashing.tbox

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private const val WRITER_THREAD_NAME = "tbox-diag-log"
    private const val FLUSH_LINE_INTERVAL = 16
    private const val FLUSH_TIME_INTERVAL_MS = 1_000L

    private val installed = AtomicBoolean(false)
    @Volatile
    private var writerThreadRef: Thread? = null
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, WRITER_THREAD_NAME).apply {
            isDaemon = true
            writerThreadRef = this
        }
    }

    // Accessed strictly on the writer thread after install() schedules initialization.
    private var logFile: File? = null
    private var bufferedWriter: BufferedWriter? = null
    private var approxBytesWritten: Long = 0L
    private var linesSinceFlush: Int = 0
    private var lastFlushElapsedMs: Long = 0L

    @Volatile
    var enabled: Boolean = true

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        writer.execute {
            runCatching {
                val resolved = resolveLogFile(app)
                logFile = resolved
                openWriterOnIo(resolved)
                appendLineOnIo("${formatStamp()} I/Diag: installed file=${resolved.absolutePath}", forceFlush = true)
            }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e(
                    "FATAL",
                    "Uncaught in ${thread.name} pid=${Process.myPid()}",
                    throwable,
                )
                flushSync()
                DclLogRecorder.log("FATAL_CRASH", "Uncaught in ${thread.name} pid=${Process.myPid()}: ${Log.getStackTraceString(throwable)}")
                DclLogRecorder.stopLogging("Fatal crash")
                DclLogRecorder.flushSync(1_500L)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String) = write("W", tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** Blocking flush for crash path only (bounded timeout, deadlock-safe). */
    fun flushSync() {
        val current = Thread.currentThread()
        if (current === writerThreadRef || current.name == WRITER_THREAD_NAME) {
            runCatching { bufferedWriter?.flush() }
            return
        }
        runCatching {
            writer.submit {
                runCatching { bufferedWriter?.flush() }
            }.get(1_500L, TimeUnit.MILLISECONDS)
        }
    }

    private fun resolveLogFile(context: Context): File {
        val external = context.getExternalFilesDir(null)
        val root = external ?: context.filesDir
        val dir = File(root, DIR_NAME).apply { mkdirs() }
        return File(dir, FILE_NAME)
    }

    private fun formatStamp(): String =
        timeFormat.get()?.format(Date()) ?: System.currentTimeMillis().toString()

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val stamp = formatStamp()
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
        if (!enabled || !installed.get()) return
        val forceFlush = level == "E"
        writer.execute {
            appendLineOnIo(line, forceFlush = forceFlush)
        }
    }

    private fun openWriterOnIo(file: File) {
        closeWriterOnIo()
        runCatching {
            approxBytesWritten = if (file.exists()) file.length() else 0L
            bufferedWriter = BufferedWriter(FileWriter(file, true), 8 * 1024)
            linesSinceFlush = 0
            lastFlushElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    private fun closeWriterOnIo() {
        val bw = bufferedWriter
        bufferedWriter = null
        if (bw != null) {
            runCatching {
                bw.flush()
                bw.close()
            }
        }
    }

    private fun appendLineOnIo(line: String, forceFlush: Boolean) {
        val file = logFile ?: return
        runCatching {
            rotateIfNeededOnIo(file)
            if (bufferedWriter == null) {
                openWriterOnIo(file)
            }
            val bw = bufferedWriter ?: return
            bw.write(line)
            bw.newLine()
            approxBytesWritten += line.length + 1
            linesSinceFlush++
            val nowMs = SystemClock.elapsedRealtime()
            if (forceFlush || linesSinceFlush >= FLUSH_LINE_INTERVAL || (nowMs - lastFlushElapsedMs) >= FLUSH_TIME_INTERVAL_MS) {
                bw.flush()
                linesSinceFlush = 0
                lastFlushElapsedMs = nowMs
            }
        }
    }

    private fun rotateIfNeededOnIo(file: File) {
        if (approxBytesWritten < MAX_BYTES) return
        closeWriterOnIo()
        if (file.exists() && file.length() >= MAX_BYTES) {
            val prev = File(file.parentFile, PREV_FILE_NAME)
            if (prev.exists()) prev.delete()
            file.renameTo(prev)
        }
        openWriterOnIo(file)
    }
}
