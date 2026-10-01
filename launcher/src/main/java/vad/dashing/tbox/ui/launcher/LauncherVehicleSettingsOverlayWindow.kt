package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
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
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Focusable system overlay for the vehicle settings screen.
 *
 * The in-HOME Compose overlay can never rise above foreign freeform windows on this HU
 * (elevator/moveTaskToFront is racy), so when SYSTEM_ALERT_WINDOW is granted the settings
 * render in a TYPE_APPLICATION_OVERLAY window — same approach as [LauncherAppDrawerWindow].
 */
internal object LauncherVehicleSettingsOverlayWindow {
    private const val TAG = "LauncherVehSettingsWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    /** Returns true when the settings were shown as a system overlay window. */
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) hide()
                true
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                // Standalone launcher is dark-first.
                TboxAppTheme(theme = 2) {
                    // Mirror the in-HOME settings layout: full-screen settings plus the
                    // home/climate dock pinned at the bottom in the same size.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LauncherColors.LeftPanelBg),
                    ) {
                        LauncherVehicleSettingsScreen(
                            canViewModel = canViewModel,
                            tboxViewModel = tboxViewModel,
                            onClose = ::hide,
                            revealProgress = 1f,
                        )
                        LauncherDevScaleProvider {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
                            ) {
                                LauncherBottomBar(
                                    canViewModel = canViewModel,
                                    onCloseVehicleSettings = ::hide,
                                    onOpenVehicleSettings = {},
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
            view.requestFocus()
            Log.w(TAG, "vehicle settings overlay shown")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add vehicle settings overlay", it)
            lifecycleOwner = null
            windowManager = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        LauncherVehicleSettingsUiState.markClosed()
        LauncherOverlayElevator.setHoldSource("vehicle_settings", false)
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove vehicle settings overlay", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        Log.w(TAG, "vehicle settings overlay hidden")
    }
}
