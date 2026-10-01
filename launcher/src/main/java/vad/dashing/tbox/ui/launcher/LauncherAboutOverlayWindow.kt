package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.update.InstallPermissionHelper
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.update.UpdateViewModelFactory

/**
 * Small centered system overlay with the «О лаунчере» / OTA console.
 *
 * Replaces the fullscreen MainActivity when SYSTEM_ALERT_WINDOW is granted: the window
 * floats above everything (including foreign freeform apps) like a dialog.
 */
internal object LauncherAboutOverlayWindow {
    private const val TAG = "LauncherAboutOverlayWin"

    /** Fraction of the screen occupied by the dialog-like window. */
    private const val WIDTH_FRACTION = 0.62f
    private const val HEIGHT_FRACTION = 0.86f

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    /** Returns true when the About console was shown as a system overlay window. */
    fun show(context: Context): Boolean {
        if (isShowing()) return true
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val metrics = activity.resources.displayMetrics
        val width = (metrics.widthPixels * WIDTH_FRACTION).toInt()
        val height = (metrics.heightPixels * HEIGHT_FRACTION).toInt()
        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.55f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val settingsManager = SettingsManager(activity)
        val updateViewModel = ViewModelProvider(
            activity,
            UpdateViewModelFactory(activity.application, settingsManager),
        )[UpdateViewModel::class.java]

        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) hide()
                true
            }
            // Dialog behavior: a tap outside the window (delivered thanks to
            // FLAG_WATCH_OUTSIDE_TOUCH) dismisses it.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hide()
                    true
                } else {
                    false
                }
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                // Standalone launcher is dark-first.
                TboxAppTheme(theme = 2) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                        color = LauncherColors.LeftPanelBg,
                    ) {
                        LauncherSettingsScreen(
                            updateViewModel = updateViewModel,
                            settingsManager = settingsManager,
                            onOpenInstallPermissionSettings = {
                                launchSystemSettingsInFreeform(
                                    activity,
                                    InstallPermissionHelper.createUnknownSourcesSettingsIntent(activity),
                                )
                            },
                            onClose = ::hide,
                        )
                    }
                }
            }
        }

        return runCatching {
            lifecycleOwner = owner
            windowManager = wm
            wm.addView(view, params)
            composeView = view
            view.requestFocus()
            LauncherOverlayBar.bringToFront()
            Log.w(TAG, "about overlay shown ${width}x$height")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add about overlay", it)
            lifecycleOwner = null
            windowManager = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove about overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        Log.w(TAG, "about overlay hidden")
    }
}
