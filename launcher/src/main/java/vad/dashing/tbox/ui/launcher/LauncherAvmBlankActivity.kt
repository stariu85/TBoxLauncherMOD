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
import vad.dashing.tbox.LauncherHomeActivityHolder

/**
 * Snapshot holder capturing the desktop (Home screen) before launching [LauncherAvmBlankActivity].
 * Retains snapshot in memory so activations of 360 camera reuse the cached desktop snapshot instantly.
 * Automatically invalidates and recaptures the desktop snapshot a couple of seconds after theme changes.
 */
object LauncherHomeSnapshotHolder {
    private const val TAG = "LauncherHomeSnapshot"
    internal const val THEME_CHANGE_CAPTURE_DELAY_MS = 2_000L
    internal const val SETTINGS_CLOSE_CAPTURE_DELAY_MS = 500L

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
            Thread {
                val bitmap = captureViaScreencap()
                if (bitmap != null) {
                    if (currentDarkTheme() == targetDarkTheme) {
                        updateSnapshot(bitmap, targetDarkTheme, markDesktopCaptured = false)
                    } else {
                        bitmap.recycle()
                    }
                }
                mainHandler.post { onComplete() }
            }.start()
        }
    }

    private fun captureViaScreencap(): Bitmap? {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p"))
            val inputStream = process.inputStream.buffered()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            process.waitFor()
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                Log.i(TAG, "Captured screen snapshot via screencap ${bitmap.width}x${bitmap.height}")
                bitmap
            } else {
                null
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed screencap execution", e)
        }.getOrNull()
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
                Thread {
                    val fb = captureViaScreencap()
                    if (fb != null) {
                        if (currentDarkTheme() == targetDarkTheme) {
                            updateSnapshot(fb, targetDarkTheme, markDesktopCaptured = false)
                        } else {
                            fb.recycle()
                        }
                    }
                    mainHandler.post { onComplete() }
                }.start()
            } else {
                Log.w(TAG, "Skipping PixelCopy: decorView not ready (${width}x${height}, attached=${view.isAttachedToWindow})")
                onComplete()
            }
            return
        }

        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
                            Thread {
                                val fb = captureViaScreencap()
                                if (fb != null) {
                                    if (currentDarkTheme() == targetDarkTheme) {
                                        updateSnapshot(fb, targetDarkTheme, markDesktopCaptured = false)
                                    } else {
                                        fb.recycle()
                                    }
                                }
                                mainHandler.post { onComplete() }
                            }.start()
                        } else {
                            Log.w(TAG, "Scheduled desktop PixelCopy failed with result=$copyResult (will retry when desktop is visible)")
                            onComplete()
                        }
                    }
                },
                mainHandler,
            )
        }.onFailure { e ->
            if (allowScreencapFallback) {
                Log.e(TAG, "Failed to request PixelCopy snapshot, trying screencap fallback", e)
                Thread {
                    val fb = captureViaScreencap()
                    if (fb != null) {
                        if (currentDarkTheme() == targetDarkTheme) {
                            updateSnapshot(fb, targetDarkTheme, markDesktopCaptured = false)
                        } else {
                            fb.recycle()
                        }
                    }
                    mainHandler.post { onComplete() }
                }.start()
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
        val old = lastSnapshot
        lastSnapshot = newBitmap
        snapshotDarkTheme = darkTheme
        if (markDesktopCaptured && currentDarkTheme() == darkTheme) {
            pendingThemeRefresh = false
        }
        if (old != null && old != newBitmap && !old.isRecycled && !LauncherAvmBlankActivity.isShowing()) {
            old.recycle()
        }
    }

    @Synchronized
    fun clearSnapshot(recycleBitmap: Boolean = !LauncherAvmBlankActivity.isShowing()) {
        val bmp = lastSnapshot
        lastSnapshot = null
        snapshotDarkTheme = null
        if (recycleBitmap && bmp != null && !bmp.isRecycled) {
            bmp.recycle()
        }
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

