package vad.dashing.tbox

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Recording system for 3D model coordinates, ADAS window, and render parameters.
 * Saves log files into `Downloads/DCLlogs` directory.
 *
 * Requirements:
 * - Started manually via bottom bar (dock) log button.
 * - Writes for 5 minutes (300 seconds) after start.
 * - Keeps max 5 log files in `downloads/DCLlogs`, deleting the oldest ones on rotation.
 */
object DclLogRecorder {
    private const val TAG = "DclLogRecorder"
    private const val DIR_NAME = "DCLlogs"
    private const val MAX_LOG_FILES = 5
    private const val SESSION_DURATION_MS = 300_000L // 5 minutes

    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dcl-logger").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val logDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
    private val fileDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }

    @Volatile
    private var activeFile: File? = null

    @Volatile
    private var sessionDeadlineMs: Long = 0L

    @Volatile
    private var isSessionActive: Boolean = false

    private val stopRunnable = Runnable {
        checkSessionExpiration()
    }

    /**
     * Checks if logging session is currently active.
     */
    fun isLoggingActive(): Boolean = isSessionActive

    /**
     * Manually stops the active logging session and saves the current log file.
     */
    fun stopLogging(reason: String = "Stopped by user") {
        synchronized(lock) {
            if (!isSessionActive) return
            stopSession(reason)
        }
    }

    /**
     * Checks if external storage write permission is available for DCL logs.
     */
    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dclDir = File(downloads, DIR_NAME)
            if (!dclDir.exists()) {
                runCatching { dclDir.mkdirs() }
            }
            return dclDir.exists() && dclDir.canWrite()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Opens Android System Settings page for storage permission.
     */
    fun openStorageSettings(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Starts or extends a recording session triggered manually by the dock log button.
     */
    fun startSession(
        context: Context,
        reason: String = "BOTTOM_BAR_LOG_BUTTON",
        durationMs: Long = SESSION_DURATION_MS,
    ) {
        val appCtx = context.applicationContext
        if (!hasStoragePermission(appCtx)) {
            Log.w(TAG, "Cannot start DCL logging session: storage permission not granted")
            return
        }

        synchronized(lock) {
            val nowMs = SystemClock.elapsedRealtime()
            val deadlineMs = nowMs + durationMs
            sessionDeadlineMs = deadlineMs

            mainHandler.removeCallbacks(stopRunnable)
            mainHandler.postDelayed(stopRunnable, durationMs)

            if (isSessionActive && (activeFile?.exists() == true)) {
                writeLine("SESSION_EXTENDED reason=$reason duration_ms=$durationMs deadline_in=${durationMs / 1000}s")
                Log.i(TAG, "DCL logging session extended (reason=$reason, durationMs=$durationMs)")
                return
            }

            // Start new session
            isSessionActive = true
            val logsDir = resolveLogsDir(appCtx)
            rotateLogFiles(logsDir)

            val timeStamp = fileDateFormat.get()?.format(Date()) ?: System.currentTimeMillis().toString()
            val newFile = File(logsDir, "dcl_log_$timeStamp.txt")
            activeFile = newFile

            val header = buildString {
                append("========================================\n")
                append("DCL LOG SESSION STARTED\n")
                append("Reason: ").append(reason).append("\n")
                append("Duration: ").append(durationMs / 1000).append(" sec\n")
                append("Time: ").append(logDateFormat.get()?.format(Date())).append("\n")
                append("Dir: ").append(logsDir.absolutePath).append("\n")
                append("========================================\n")
            }
            writeRaw(newFile, header)
            Log.i(TAG, "DCL logging session started (file=${newFile.absolutePath}, reason=$reason, durationMs=$durationMs)")
        }
    }

    /**
     * General log method.
     */
    fun log(tag: String, message: String) {
        if (!isSessionActive) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs >= sessionDeadlineMs) {
            stopSession("Timeout")
            return
        }
        val timestamp = logDateFormat.get()?.format(Date()) ?: System.currentTimeMillis().toString()
        writeLine("$timestamp [$tag] $message")
    }

    /**
     * Log UI button / control taps.
     */
    fun logUiClick(
        tag: String,
        label: String? = null,
        details: String? = null,
    ) {
        if (!isSessionActive) return
        val msg = buildString {
            append("UI_CLICK ")
            append("source=").append(tag)
            if (!label.isNullOrBlank()) append(" label=").append(label)
            if (!details.isNullOrBlank()) append(" details=").append(details)
        }
        log("UiClick", msg)
    }

    /**
     * Log application launches and parameters (Intent, flags, bounds, extras).
     */
    fun logAppLaunch(
        packageName: String,
        component: String?,
        intentAction: String?,
        intentCategories: Set<String>?,
        intentFlags: String?,
        preferEmbedded: Boolean,
        forceFullscreen: Boolean,
        bounds: Rect?,
        extras: Map<String, String>? = null,
    ) {
        if (!isSessionActive) return
        val msg = buildString {
            append("APP_LAUNCH ")
            append("pkg=").append(packageName)
            if (!component.isNullOrBlank()) append(" comp=").append(component)
            if (!intentAction.isNullOrBlank()) append(" action=").append(intentAction)
            if (!intentCategories.isNullOrEmpty()) append(" cat=").append(intentCategories)
            if (!intentFlags.isNullOrBlank()) append(" flags=").append(intentFlags)
            append(" embedded=").append(preferEmbedded)
            append(" fullscreen=").append(forceFullscreen)
            if (bounds != null) append(" bounds=").append(bounds.toShortString())
            if (!extras.isNullOrEmpty()) append(" extras=").append(extras)
        }
        log("AppLaunch", msg)
    }

    /**
     * Log 3D Model position, camera, scale, transition, and render parameters.
     */
    fun logModelRender(
        transition: Float,
        driveBlend: Float,
        modelX: Float,
        modelY: Float,
        modelZ: Float,
        modelYaw: Float,
        modelScale: Float,
        cameraX: Float,
        cameraY: Float,
        cameraZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float,
        viewportW: Int,
        viewportH: Int,
        customModelScale: Float,
        userYaw: Float,
        userScale: Float,
        isAvmActive: Boolean,
        watchdogTriggered: Boolean = false,
    ) {
        if (!isSessionActive) return
        val msg = buildString(256) {
            append("MODEL_RENDER ")
            if (watchdogTriggered) append("[WATCHDOG_RESET] ")
            append("trans="); appendFloat(transition, 3)
            append(" blend="); appendFloat(driveBlend, 3)
            append(" pos=("); appendFloat(modelX, 4); append(','); appendFloat(modelY, 4); append(','); appendFloat(modelZ, 4); append(')')
            append(" yaw="); appendFloat(modelYaw, 1)
            append(" scale="); appendFloat(modelScale, 4)
            append(" camPos=("); appendFloat(cameraX, 4); append(','); appendFloat(cameraY, 4); append(','); appendFloat(cameraZ, 4); append(')')
            append(" camTarget=("); appendFloat(targetX, 4); append(','); appendFloat(targetY, 4); append(','); appendFloat(targetZ, 4); append(')')
            append(" vp=").append(viewportW).append('x').append(viewportH)
            append(" cfgScale="); appendFloat(customModelScale, 2)
            append(" usrYaw="); appendFloat(userYaw, 1)
            append(" usrScale="); appendFloat(userScale, 2)
            append(" avm=").append(isAvmActive)
        }
        log("ModelRender", msg)
    }

    private fun StringBuilder.appendFloat(value: Float, decimals: Int) {
        if (value.isNaN() || value.isInfinite()) {
            append(value)
            return
        }
        if (value < 0f) {
            append('-')
            appendFloat(-value, decimals)
            return
        }
        var multiplier = 1f
        repeat(decimals) { multiplier *= 10f }
        val rounded = kotlin.math.round(value * multiplier).toLong()
        val integerPart = rounded / multiplier.toLong()
        val fractionalPart = rounded % multiplier.toLong()
        append(integerPart)
        if (decimals > 0) {
            append('.')
            var fracStr = fractionalPart.toString()
            while (fracStr.length < decimals) {
                fracStr = "0$fracStr"
            }
            append(fracStr)
        }
    }

    /**
     * Log ADAS window & panel bounds.
     */
    fun logAdasBounds(
        tag: String,
        leftPanelBounds: Rect?,
        contentRowBounds: Rect?,
        embeddedZoneBounds: Rect?,
        screenBounds: Rect?,
        computedEmbeddedBounds: Rect?,
        topHeaderBottomPx: Int,
        bottomBarTopPx: Int,
    ) {
        if (!isSessionActive) return
        val msg = buildString {
            append(tag)
            append(" leftPanel=").append(leftPanelBounds?.toShortString() ?: "null")
            append(" contentRow=").append(contentRowBounds?.toShortString() ?: "null")
            append(" embeddedZone=").append(embeddedZoneBounds?.toShortString() ?: "null")
            append(" screen=").append(screenBounds?.toShortString() ?: "null")
            append(" computedEmbedded=").append(computedEmbeddedBounds?.toShortString() ?: "null")
            append(" topHeaderBottom=").append(topHeaderBottomPx)
            append(" bottomBarTop=").append(bottomBarTopPx)
        }
        log("AdasBounds", msg)
    }

    /**
     * Log ADAS panel drag and layout changes.
     */
    fun logAdasPanelDrag(
        panelId: String,
        initialTopPx: Float,
        panelHeightPx: Float,
        dragOffsetY: Float,
        releasedCenterY: Float,
        topPanels: List<String>,
        bottomPanels: List<String>,
    ) {
        if (!isSessionActive) return
        val msg = buildString {
            append("PANEL_DRAG panelId=").append(panelId)
            append(" initTop=").append(String.format(Locale.US, "%.1f", initialTopPx))
            append(" height=").append(String.format(Locale.US, "%.1f", panelHeightPx))
            append(" offsetY=").append(String.format(Locale.US, "%.1f", dragOffsetY))
            append(" relCenterY=").append(String.format(Locale.US, "%.1f", releasedCenterY))
            append(" topPanels=").append(topPanels)
            append(" bottomPanels=").append(bottomPanels)
        }
        log("AdasPanel", msg)
    }

    private fun checkSessionExpiration() {
        synchronized(lock) {
            if (!isSessionActive) return
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs >= sessionDeadlineMs) {
                stopSession("2 minutes expired")
            } else {
                val remaining = sessionDeadlineMs - nowMs
                mainHandler.removeCallbacks(stopRunnable)
                mainHandler.postDelayed(stopRunnable, remaining)
            }
        }
    }

    private fun stopSession(reason: String) {
        synchronized(lock) {
            if (!isSessionActive) return
            isSessionActive = false
            mainHandler.removeCallbacks(stopRunnable)
            val file = activeFile
            if (file != null) {
                val footer = buildString {
                    append("========================================\n")
                    append("DCL LOG SESSION ENDED (").append(reason).append(")\n")
                    append("Time: ").append(logDateFormat.get()?.format(Date())).append("\n")
                    append("========================================\n")
                }
                writeRaw(file, footer)
            }
            activeFile = null
            Log.i(TAG, "DCL logging session stopped ($reason)")
        }
    }

    private fun resolveLogsDir(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dclDir = File(publicDownloads, DIR_NAME)
        if (!dclDir.exists()) {
            runCatching { dclDir.mkdirs() }
        }
        if (dclDir.exists() && dclDir.canWrite()) {
            return dclDir
        }
        val sdcardDir = File(Environment.getExternalStorageDirectory(), "Download/$DIR_NAME")
        if (!sdcardDir.exists()) {
            runCatching { sdcardDir.mkdirs() }
        }
        if (sdcardDir.exists() && sdcardDir.canWrite()) {
            return sdcardDir
        }
        val appExtDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
        if (!appExtDir.exists()) {
            runCatching { appExtDir.mkdirs() }
        }
        return appExtDir
    }

    private fun rotateLogFiles(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("dcl_log_") } ?: return
        if (files.size >= MAX_LOG_FILES) {
            val sorted = files.sortedBy { it.lastModified() }
            val deleteCount = files.size - (MAX_LOG_FILES - 1)
            for (i in 0 until deleteCount) {
                if (i < sorted.size) {
                    val fileToDelete = sorted[i]
                    val deleted = fileToDelete.delete()
                    Log.i(TAG, "Rotated old log file: ${fileToDelete.name} deleted=$deleted")
                }
            }
        }
    }

    private fun writeLine(line: String) {
        val file = activeFile ?: return
        executor.execute {
            synchronized(lock) {
                runCatching {
                    file.appendText(line + "\n")
                }
            }
        }
    }

    private fun writeRaw(file: File, text: String) {
        executor.execute {
            synchronized(lock) {
                runCatching {
                    file.appendText(text)
                }
            }
        }
    }
}
