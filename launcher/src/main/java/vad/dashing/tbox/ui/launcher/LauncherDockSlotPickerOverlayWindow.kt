package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * System overlay window for the bottom bar slot picker dialog.
 *
 * Renders in a TYPE_APPLICATION_OVERLAY window above all running applications
 * (e.g. Yandex Maps or freeform apps) when editing bottom dock slots.
 */
internal object LauncherDockSlotPickerOverlayWindow {
    private const val TAG = "LauncherDockPickerOverlay"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var dismissCallback: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    fun show(
        context: Context,
        slotIndex: Int,
        missingButtons: List<LauncherDockButtonDescriptor>,
        pickerApps: List<LaunchableAppEntry>,
        splitPresets: List<LauncherSplitPreset>,
        bottomBarHeightDp: Int,
        onSelectButton: (String) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        hide()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

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
        val density = metrics.density
        val bottomOffsetPx = ((bottomBarHeightDp + 12) * density).toInt()
        val pickerWidthPx = ((560 * density).toInt()).coerceAtMost(metrics.widthPixels - (32 * density).toInt())

        val params = WindowManager.LayoutParams(
            pickerWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomOffsetPx
            dimAmount = 0.40f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val dismiss = {
            onDismiss()
            hide()
        }

        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) dismiss()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    val isDark = LauncherThemeState.darkTheme
                    TboxAppTheme(theme = if (isDark) 2 else 1) {
                        LauncherDockSlotPickerContent(
                            slotIndex = slotIndex,
                            missingButtons = missingButtons,
                            pickerApps = pickerApps,
                            splitPresets = splitPresets,
                            onSelectButton = { buttonId ->
                                onSelectButton(buttonId)
                                dismiss()
                            },
                            onDismiss = dismiss,
                        )
                    }
                }
            }
        }

        return runCatching {
            lifecycleOwner = owner
            windowManager = wm
            dismissCallback = onDismiss
            wm.addView(view, params)
            composeView = view
            view.requestFocus()
            LauncherOverlayBar.bringToFront()
            Log.w(TAG, "dock slot picker overlay shown for slot=$slotIndex")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add dock slot picker overlay", it)
            lifecycleOwner = null
            windowManager = null
            dismissCallback = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        val cb = dismissCallback
        dismissCallback = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove dock slot picker overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        cb?.invoke()
        Log.w(TAG, "dock slot picker overlay hidden")
    }
}
