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
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import vad.dashing.tbox.LauncherHomeActivityHolder

/**
 * Snapshot holder capturing the desktop (Home screen) before launching [LauncherAvmBlankActivity].
 * Retains snapshot in memory so activations of 360 camera reuse the cached desktop snapshot instantly.
 */
object LauncherHomeSnapshotHolder {
    private const val TAG = "LauncherHomeSnapshot"

    @Volatile
    var lastSnapshot: Bitmap? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasValidSnapshot(): Boolean {
        val bmp = lastSnapshot
        return bmp != null && !bmp.isRecycled
    }

    fun captureSnapshot(activity: Activity?, onComplete: () -> Unit) {
        if (hasValidSnapshot()) {
            Log.i(TAG, "Reusing cached screen snapshot")
            mainHandler.post { onComplete() }
            return
        }

        val act = activity ?: LauncherHomeActivityHolder.instance
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            captureViaPixelCopy(act, onComplete)
        } else {
            Thread {
                val bitmap = captureViaScreencap()
                if (bitmap != null) {
                    updateSnapshot(bitmap)
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

    private fun captureViaPixelCopy(activity: Activity?, onComplete: () -> Unit) {
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
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)

        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                window,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        updateSnapshot(bitmap)
                        Log.i(TAG, "Captured desktop PixelCopy snapshot ${width}x${height}")
                        onComplete()
                    } else {
                        Log.w(TAG, "PixelCopy failed with result=$copyResult, trying screencap fallback")
                        bitmap.recycle()
                        Thread {
                            val fb = captureViaScreencap()
                            if (fb != null) updateSnapshot(fb)
                            mainHandler.post { onComplete() }
                        }.start()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to request PixelCopy snapshot, trying screencap fallback", e)
            Thread {
                val fb = captureViaScreencap()
                if (fb != null) updateSnapshot(fb)
                mainHandler.post { onComplete() }
            }.start()
        }
    }

    @Synchronized
    private fun updateSnapshot(newBitmap: Bitmap) {
        val old = lastSnapshot
        lastSnapshot = newBitmap
        if (old != null && old != newBitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    @Synchronized
    fun clearSnapshot() {
        val bmp = lastSnapshot
        lastSnapshot = null
        if (bmp != null && !bmp.isRecycled) {
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

        val snapshot = LauncherHomeSnapshotHolder.lastSnapshot
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

