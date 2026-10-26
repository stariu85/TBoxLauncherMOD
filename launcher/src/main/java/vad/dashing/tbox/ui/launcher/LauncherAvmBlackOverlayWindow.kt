package vad.dashing.tbox.ui.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * Fullscreen solid black system overlay window (`TYPE_APPLICATION_OVERLAY`) shown
 * when 360 Camera (AVM) starts, if "Minimize/Hide apps" option (`AVM_CAMERA_ACTION_MINIMIZE_APPS`) is selected.
 *
 * It covers all foreign applications on screen with pure black (`#000000`), forcing
 * loss of visual/render focus and stopping background rendering.
 */
@SuppressLint("StaticFieldLeak")
object LauncherAvmBlackOverlayWindow {
    private const val TAG = "LauncherAvmOverlayWin"

    @Volatile
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context) {
        mainHandler.post {
            if (isShowing()) return@post
            val appCtx = context.applicationContext

            if (!Settings.canDrawOverlays(appCtx)) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted, cannot show AVM black overlay")
                return@post
            }

            val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm == null) {
                Log.e(TAG, "WindowManager is null, cannot show AVM black overlay")
                return@post
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            @Suppress("DEPRECATION")
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE,
            )

            val view = View(appCtx).apply {
                setBackgroundColor(Color.BLACK)
            }

            runCatching {
                wm.addView(view, params)
                overlayView = view
                windowManager = wm
                Log.i(TAG, "AVM black overlay shown successfully")
            }.onFailure { e ->
                Log.e(TAG, "Failed to add AVM black overlay", e)
                overlayView = null
                windowManager = null
            }
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            val wm = windowManager ?: (view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            overlayView = null
            windowManager = null

            runCatching {
                wm?.removeViewImmediate(view)
                Log.i(TAG, "AVM black overlay hidden successfully")
            }.onFailure { e ->
                Log.w(TAG, "Failed to remove AVM black overlay", e)
            }
        }
    }
}
