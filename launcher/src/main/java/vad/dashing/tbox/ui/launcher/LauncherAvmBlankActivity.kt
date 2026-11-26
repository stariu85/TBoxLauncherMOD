package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivityHolder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Snapshot holder capturing the desktop (Home screen) before launching [LauncherAvmBlankActivity].
 * Retains snapshot in memory so activations of 360 camera reuse the cached desktop snapshot instantly.
 * Automatically invalidates and recaptures the desktop snapshot a couple of seconds after theme changes.
 */
object LauncherHomeSnapshotHolder {
    private const val TAG = "LauncherHomeSnapshot"
    internal const val THEME_CHANGE_CAPTURE_DELAY_MS = 2_000L
    internal const val SETTINGS_CLOSE_CAPTURE_DELAY_MS = 500L
    private const val SCREENCAP_TIMEOUT_MS = 3_000L

    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screencapExecutor = Executors.newCachedThreadPool { r ->
        Thread(
            {
                LauncherCpuAffinity.applyOptionalServiceBackgroundAffinityOnCurrentThread("dcl-screencap")
                r.run()
            },
            "dcl-screencap",
        ).apply { isDaemon = true }
    }

    @Volatile
    var lastSnapshot: Bitmap? = null
        private set

    @Volatile
    internal var snapshotDarkTheme: Boolean? = null
        private set

    @Volatile
    internal var pendingThemeRefresh: Boolean = false
        private set

    @Volatile
    private var lastThemeChangeElapsedMs: Long = 0L

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var themeRefreshRunnable: Runnable? = null

    private fun currentDarkTheme(): Boolean =
        runCatching { LauncherThemeState.darkTheme }.getOrDefault(true)

    internal fun isSnapshotValidForTheme(
        hasBitmap: Boolean,
        snapshotDarkTheme: Boolean?,
        currentDarkTheme: Boolean,
    ): Boolean = hasBitmap && snapshotDarkTheme == currentDarkTheme

    internal fun canCaptureDesktopSnapshot(
        avmActive: Boolean,
        blankActivityShowing: Boolean,
        vehicleSettingsOpen: Boolean,
        firstDrawComplete: Boolean,
        activityAlive: Boolean,
        activityStarted: Boolean,
    ): Boolean = !avmActive &&
        !blankActivityShowing &&
        !vehicleSettingsOpen &&
        firstDrawComplete &&
        activityAlive &&
        activityStarted

    internal fun computeRemainingRefreshDelayMs(
        lastThemeChangeElapsedMs: Long,
        nowElapsedMs: Long,
        minDelayMs: Long,
    ): Long {
        val elapsed = (nowElapsedMs - lastThemeChangeElapsedMs).coerceAtLeast(0L)
        return (THEME_CHANGE_CAPTURE_DELAY_MS - elapsed).coerceAtLeast(minDelayMs)
    }

    @Synchronized
    fun hasValidSnapshot(): Boolean {
        val bmp = lastSnapshot
        return isSnapshotValidForTheme(
            hasBitmap = bmp != null && !bmp.isRecycled,
            snapshotDarkTheme = snapshotDarkTheme,
            currentDarkTheme = currentDarkTheme(),
        )
    }

    @Synchronized
    fun validSnapshotForCurrentTheme(): Bitmap? {
        val bmp = lastSnapshot ?: return null
        if (bmp.isRecycled) return null
        return if (snapshotDarkTheme == currentDarkTheme()) bmp else null
    }

    /**
     * Called when launcher light/dark theme changes.
     * Clears any cached snapshot from the previous theme and schedules a fresh desktop snapshot
     * after [THEME_CHANGE_CAPTURE_DELAY_MS] so the UI finishes redrawing in the new theme.
     */
    fun onThemeChanged(darkTheme: Boolean) {
        Log.i(
            TAG,
            "Theme changed (darkTheme=$darkTheme) -> invalidating stale snapshot and scheduling refresh in ${THEME_CHANGE_CAPTURE_DELAY_MS}ms",
        )
        lastThemeChangeElapsedMs = SystemClock.elapsedRealtime()
        pendingThemeRefresh = true
        if (snapshotDarkTheme != darkTheme) {
            clearSnapshot(recycleBitmap = !LauncherAvmBlankActivity.isShowing())
        }
        LauncherAvmBlankActivity.onThemeChanged()
        scheduleDesktopSnapshotRefresh(THEME_CHANGE_CAPTURE_DELAY_MS)
    }

    /**
     * Called from [vad.dashing.tbox.LauncherHomeActivity.onResume] when Home is visible without foreign apps.
     */
    fun onHomeResumed(activity: Activity) {
        if (pendingThemeRefresh) {
            val delayMs = computeRemainingRefreshDelayMs(
                lastThemeChangeElapsedMs = lastThemeChangeElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                minDelayMs = THEME_CHANGE_CAPTURE_DELAY_MS,
            )
            scheduleDesktopSnapshotRefresh(delayMs)
        } else if (!hasValidSnapshot()) {
            captureSnapshot(activity) {}
        }
    }

    /**
     * Called when in-window settings overlay closes so a deferred theme-change snapshot can be captured.
     */
    fun onDesktopOverlaysClosed() {
        if (!pendingThemeRefresh) return
        val delayMs = computeRemainingRefreshDelayMs(
            lastThemeChangeElapsedMs = lastThemeChangeElapsedMs,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            minDelayMs = SETTINGS_CLOSE_CAPTURE_DELAY_MS,
        )
        scheduleDesktopSnapshotRefresh(delayMs)
    }

    /**
     * Called when 360 camera (AVM) closes so a theme change that occurred during AVM triggers a desktop snapshot.
     */
    fun onAvmClosed() {
        if (!pendingThemeRefresh) return
        val delayMs = computeRemainingRefreshDelayMs(
            lastThemeChangeElapsedMs = lastThemeChangeElapsedMs,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            minDelayMs = THEME_CHANGE_CAPTURE_DELAY_MS,
        )
        scheduleDesktopSnapshotRefresh(delayMs)
    }

    fun scheduleDesktopSnapshotRefresh(delayMs: Long = THEME_CHANGE_CAPTURE_DELAY_MS) {
        mainHandler.post {
            themeRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                themeRefreshRunnable = null
                captureDesktopSnapshotIfReady()
            }
            themeRefreshRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    private fun captureDesktopSnapshotIfReady() {
        val act = LauncherHomeActivityHolder.instance
        val canCapture = canCaptureDesktopSnapshot(
            avmActive = LauncherAvmRepository.isAvmActive.value,
            blankActivityShowing = LauncherAvmBlankActivity.isShowing(),
            vehicleSettingsOpen = LauncherVehicleSettingsUiState.open,
            firstDrawComplete = LauncherHomeActivityHolder.isFirstDrawComplete,
            activityAlive = act != null && !act.isFinishing && !act.isDestroyed,
            activityStarted = act?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true,
        )
        if (!canCapture || act == null) {
            Log.i(
                TAG,
                "Deferring theme-change desktop snapshot (avm=${LauncherAvmRepository.isAvmActive.value}, " +
                    "blank=${LauncherAvmBlankActivity.isShowing()}, settings=${LauncherVehicleSettingsUiState.open}, " +
                    "started=${act?.lifecycle?.currentState})",
            )
            return
        }
        val targetDarkTheme = currentDarkTheme()
        captureViaPixelCopy(
            activity = act,
            targetDarkTheme = targetDarkTheme,
            allowScreencapFallback = false,
            onComplete = {},
        )
    }

    fun captureSnapshot(activity: Activity?, onComplete: () -> Unit) {
        if (hasValidSnapshot()) {
            Log.i(TAG, "Reusing cached screen snapshot")
            mainHandler.post { onComplete() }
            return
        }

        val targetDarkTheme = currentDarkTheme()
        val act = activity ?: LauncherHomeActivityHolder.instance
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            captureViaPixelCopy(
                activity = act,
                targetDarkTheme = targetDarkTheme,
                allowScreencapFallback = true,
                onComplete = onComplete,
            )
        } else {
            launchScreencapFallback(targetDarkTheme, onComplete)
        }
    }

    private fun launchScreencapFallback(targetDarkTheme: Boolean, onComplete: () -> Unit) {
        snapshotScope.launch {
            val bitmap = captureViaScreencap()
            if (bitmap != null) {
                if (currentDarkTheme() == targetDarkTheme) {
                    updateSnapshot(bitmap, targetDarkTheme, markDesktopCaptured = false)
                } else {
                    bitmap.recycle()
                }
            }
            mainHandler.post { onComplete() }
        }
    }

    private fun captureViaScreencap(): Bitmap? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("screencap", "-p")
                .redirectErrorStream(false)
                .start()
            val proc = process
            runCatching { proc.outputStream.close() }
            val bitmapFuture = screencapExecutor.submit<Bitmap?> {
                proc.inputStream.buffered().use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
            val stderrFuture = screencapExecutor.submit {
                runCatching {
                    proc.errorStream.use { err ->
                        val buf = ByteArray(1024)
                        while (err.read(buf) > 0) {
                            // Drain stderr to avoid child pipe deadlock.
                        }
                    }
                }
            }
            val finished = proc.waitFor(SCREENCAP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                bitmapFuture.cancel(true)
                stderrFuture.cancel(true)
                Log.w(TAG, "screencap timed out after ${SCREENCAP_TIMEOUT_MS}ms")
                return null
            }
            val bitmap = runCatching { bitmapFuture.get(500L, TimeUnit.MILLISECONDS) }.getOrNull()
            runCatching { stderrFuture.get(200L, TimeUnit.MILLISECONDS) }
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                Log.i(TAG, "Captured screen snapshot via screencap ${bitmap.width}x${bitmap.height}")
                bitmap
            } else {
                bitmap?.recycle()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed screencap execution", e)
            process?.destroyForcibly()
            null
        } finally {
            process?.let { p ->
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
                runCatching { p.outputStream.close() }
                if (p.isAlive) {
                    runCatching { p.destroyForcibly() }
                }
            }
        }
    }

    private fun captureViaPixelCopy(
        activity: Activity?,
        targetDarkTheme: Boolean,
        allowScreencapFallback: Boolean,
        onComplete: () -> Unit,
    ) {
        val act = activity ?: LauncherHomeActivityHolder.instance ?: run {
            onComplete()
            return
        }
        if (act.isFinishing || act.isDestroyed) {
            onComplete()
            return
        }
        val window = act.window ?: run {
            onComplete()
            return
        }
        val view = window.decorView
        val width = view.width
        val height = view.height
        if (!view.isAttachedToWindow || width <= 0 || height <= 0) {
            if (allowScreencapFallback) {
                launchScreencapFallback(targetDarkTheme, onComplete)
            } else {
                Log.w(TAG, "Skipping PixelCopy: decorView not ready (${width}x${height}, attached=${view.isAttachedToWindow})")
                onComplete()
            }
            return
        }

        var allocatedBitmap: Bitmap? = null
        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            allocatedBitmap = bitmap
            PixelCopy.request(
                window,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        if (currentDarkTheme() == targetDarkTheme) {
                            updateSnapshot(bitmap, targetDarkTheme, markDesktopCaptured = true)
                            Log.i(TAG, "Captured desktop PixelCopy snapshot ${width}x${height} (darkTheme=$targetDarkTheme)")
                        } else {
                            Log.i(TAG, "Discarding PixelCopy snapshot: theme changed while capture was in flight")
                            bitmap.recycle()
                        }
                        onComplete()
                    } else {
                        bitmap.recycle()
                        if (allowScreencapFallback) {
                            Log.w(TAG, "PixelCopy failed with result=$copyResult, trying screencap fallback")
                            launchScreencapFallback(targetDarkTheme, onComplete)
                        } else {
                            Log.w(TAG, "Scheduled desktop PixelCopy failed with result=$copyResult (will retry when desktop is visible)")
                            onComplete()
                        }
                    }
                },
                mainHandler,
            )
        }.onFailure { e ->
            allocatedBitmap?.let { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
            if (allowScreencapFallback) {
                Log.e(TAG, "Failed to request PixelCopy snapshot, trying screencap fallback", e)
                launchScreencapFallback(targetDarkTheme, onComplete)
            } else {
                Log.w(TAG, "Failed to request scheduled PixelCopy snapshot (will retry when desktop is visible)", e)
                onComplete()
            }
        }
    }

    @Synchronized
    private fun updateSnapshot(
        newBitmap: Bitmap,
        darkTheme: Boolean,
        markDesktopCaptured: Boolean,
    ) {
        // Do not eagerly recycle the previous bitmap: ImageView / RenderThread may still draw it.
        lastSnapshot = newBitmap
        snapshotDarkTheme = darkTheme
        if (markDesktopCaptured && currentDarkTheme() == darkTheme) {
            pendingThemeRefresh = false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun clearSnapshot(recycleBitmap: Boolean = !LauncherAvmBlankActivity.isShowing()) {
        // Detach reference and let GC collect safely once RenderThread finishes using any drawn frame.
        lastSnapshot = null
        snapshotDarkTheme = null
    }
}

/**
 * Super lightweight activity launched when 360 Camera (AVM) starts.
 *
 * Placed on top of the Activity task stack, forcing Android's ActivityManager to transition
 * background applications (e.g. Yandex Music, Navigation) into `onStop()`.
 * Displays a snapshot of the Home screen if available.
 */
@Suppress("DEPRECATION")
class LauncherAvmBlankActivity : Activity() {

    private var snapshotImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!shouldBeShowing) {
            Log.w(TAG, "LauncherAvmBlankActivity created but shouldBeShowing is false -> finishing immediately")
            finish()
            overridePendingTransition(0, 0)
            return
        }
        instance = this
        overridePendingTransition(0, 0)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        val snapshot = LauncherHomeSnapshotHolder.validSnapshotForCurrentTheme()
        if (snapshot != null && !snapshot.isRecycled) {
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(snapshot)
            }
            snapshotImageView = imageView
            setContentView(imageView)
            Log.i(TAG, "LauncherAvmBlankActivity created with home snapshot background")
        } else {
            val bgColor = getThemeBackgroundColor(this)
            val bgView = View(this).apply {
                setBackgroundColor(bgColor)
            }
            setContentView(bgView)
            Log.i(TAG, "LauncherAvmBlankActivity created with theme background color")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!shouldBeShowing) {
            finish()
            overridePendingTransition(0, 0)
            return
        }
        overridePendingTransition(0, 0)
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        snapshotImageView?.setImageDrawable(null)
        snapshotImageView = null
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "LauncherAvmBlankActivity destroyed")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent closing via back press while AVM camera is active
    }

    companion object {
        private const val TAG = "LauncherAvmBlankAct"

        @Suppress("UNUSED_PARAMETER")
        internal fun getThemeBackgroundColor(context: Context): Int {
            val isDark = runCatching { LauncherThemeState.darkTheme }.getOrDefault(true)

            return if (isDark) {
                Color.parseColor("#1A1D24")
            } else {
                Color.parseColor("#E5E7EB")
            }
        }

        @Volatile
        private var instance: LauncherAvmBlankActivity? = null

        @Volatile
        private var shouldBeShowing: Boolean = false

        fun isShowing(): Boolean = shouldBeShowing || instance != null

        fun onThemeChanged() {
            val act = instance ?: return
            act.runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                act.snapshotImageView?.setImageDrawable(null)
                act.snapshotImageView = null
                val bgColor = getThemeBackgroundColor(act)
                val bgView = View(act).apply {
                    setBackgroundColor(bgColor)
                }
                act.setContentView(bgView)
                Log.i(TAG, "Updated live LauncherAvmBlankActivity background for theme change")
            }
        }

        fun show(context: Context) {
            shouldBeShowing = true
            val act = instance
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                return
            }
            val intent = Intent(context, LauncherAvmBlankActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            runCatching {
                context.startActivity(intent)
                Log.i(TAG, "Starting LauncherAvmBlankActivity to cover active apps during AVM")
            }.onFailure { e ->
                Log.e(TAG, "Failed to start LauncherAvmBlankActivity", e)
            }
        }

        fun hide() {
            shouldBeShowing = false
            val act = instance
            instance = null
            if (act != null) {
                runCatching {
                    act.finish()
                    act.overridePendingTransition(0, 0)
                    Log.i(TAG, "Hiding LauncherAvmBlankActivity")
                }.onFailure { e ->
                    Log.e(TAG, "Failed to finish LauncherAvmBlankActivity", e)
                }
            }
        }
    }
}

