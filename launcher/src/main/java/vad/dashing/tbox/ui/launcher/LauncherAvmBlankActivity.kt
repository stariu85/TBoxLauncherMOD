package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
 * Snapshot holder capturing the Home screen before launching [LauncherAvmBlankActivity].
 */
object LauncherHomeSnapshotHolder {
    private const val TAG = "LauncherHomeSnapshot"

    @Volatile
    var lastSnapshot: Bitmap? = null
        private set

    fun captureSnapshot(activity: Activity?, onComplete: () -> Unit) {
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
                        val old = lastSnapshot
                        lastSnapshot = bitmap
                        old?.recycle()
                        Log.i(TAG, "Captured home snapshot ${width}x${height}")
                    } else {
                        Log.w(TAG, "PixelCopy failed with result=$copyResult")
                        bitmap.recycle()
                    }
                    onComplete()
                },
                Handler(Looper.getMainLooper()),
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to request PixelCopy snapshot", e)
            onComplete()
        }
    }

    fun clearSnapshot() {
        val bmp = lastSnapshot
        lastSnapshot = null
        bmp?.recycle()
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
        LauncherHomeSnapshotHolder.clearSnapshot()
        super.onDestroy()
        Log.i(TAG, "LauncherAvmBlankActivity destroyed")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
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

        fun isShowing(): Boolean = instance != null

        fun show(context: Context) {
            if (isShowing()) return
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
                Log.i(TAG, "Starting LauncherAvmBlankActivity to pause foreign app render")
            }.onFailure { e ->
                Log.e(TAG, "Failed to start LauncherAvmBlankActivity", e)
            }
        }

        fun hide() {
            val act = instance ?: return
            instance = null
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
