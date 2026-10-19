package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * System overlay window for the Launcher Right Panel (Desktop Grid).
 *
 * Renders [LauncherRightPanel] inside a [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * window positioned at [LauncherEmbeddedBoundsState.embeddedBounds].
 * When Home is pressed while a foreign freeform app is running, this overlay covers the
 * freeform app zone with the launcher desktop grid instantly without needing to terminate or
 * reload foreign tasks.
 */
internal object LauncherRightPanelOverlayWindow {
    private const val TAG = "LauncherRightPanelWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    fun show(context: Context): Boolean {
        if (isShowing()) {
            updateBounds()
            return true
        }
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val bounds = LauncherEmbeddedBoundsState.embeddedBounds()
            ?: LauncherEmbeddedBoundsState.fullWidthBounds()
            ?: Rect(520, 51, 1920, 998)

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
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            bounds.left,
            bounds.top,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val provider = ViewModelProvider(activity)
        val tboxViewModel = provider[TboxViewModel::class.java]
        val canViewModel = provider[CanDataViewModel::class.java]
        val settingsViewModel = ViewModelProvider(
            activity,
            SettingsViewModelFactory(SettingsManager(activity)),
        )[SettingsViewModel::class.java]

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    TboxAppTheme(theme = 2) {
                        LauncherDevScaleProvider {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(LauncherColors.CanvasDark),
                            ) {
                                LauncherRightPanel(
                                    canViewModel = canViewModel,
                                    settingsViewModel = settingsViewModel,
                                    tboxViewModel = tboxViewModel,
                                    onOpenConsole = {
                                        hide()
                                        launchTBoxSettings(activity)
                                    },
                                    onOpenApps = {
                                        hide()
                                        LauncherAppDrawerWindow.show(activity, settingsViewModel, {})
                                    },
                                    configRevision = 0,
                                    onConfigChanged = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        return runCatching {
            lifecycleOwner = owner
            windowManager = wm
            wm.addView(view, params)
            composeView = view
            Log.w(TAG, "right panel overlay window shown bounds=$bounds")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add right panel overlay window", it)
            lifecycleOwner = null
            windowManager = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun updateBounds() {
        val view = composeView ?: return
        val wm = windowManager ?: return
        val bounds = LauncherEmbeddedBoundsState.embeddedBounds()
            ?: LauncherEmbeddedBoundsState.fullWidthBounds()
            ?: return
        runCatching {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width()
            params.height = bounds.height()
            wm.updateViewLayout(view, params)
            Log.w(TAG, "updated bounds=$bounds")
        }
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove right panel overlay window", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        Log.w(TAG, "right panel overlay window hidden")
    }
}
