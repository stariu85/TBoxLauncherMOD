package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Focusable system overlay for the launcher app drawer.
 *
 * Freeform tasks are separate application windows, therefore a Compose zIndex in HOME
 * cannot place the drawer above them. TYPE_APPLICATION_OVERLAY provides the required
 * window ordering while keeping the drawer implementation shared.
 */
internal object LauncherAppDrawerWindow {
    private const val TAG = "LauncherAppDrawerWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var editMode by mutableStateOf(false)
    private var configRevision by mutableIntStateOf(0)
    private var onConfigChanged: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    fun show(
        context: Context,
        settingsViewModel: SettingsViewModel,
        onConfigChanged: () -> Unit,
    ): Boolean {
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

        val tboxViewModel = ViewModelProvider(activity)[TboxViewModel::class.java]
        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) {
                    if (editMode) editMode = false else hide()
                }
                true
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                val theme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
                TboxAppTheme(theme = 2) {
                    val navigationBarInset = WindowInsets.navigationBars
                        .exclude(WindowInsets.ime)
                        .asPaddingValues()
                        .calculateBottomPadding()
                    BackHandler {
                        if (editMode) editMode = false else hide()
                    }
                    LauncherAppDrawer(
                        visible = true,
                        settingsViewModel = settingsViewModel,
                        onDismiss = ::hide,
                        editMode = editMode,
                        onEditModeChange = { editMode = it },
                        configRevision = configRevision,
                        onConfigChanged = {
                            configRevision++
                            this@LauncherAppDrawerWindow.onConfigChanged?.invoke()
                        },
                        onLaunchApp = { app ->
                            // Remove the focusable overlay before creating/focusing freeform.
                            hide()
                            launchLauncherApp(activity, app.packageName, app.activityName)
                        },
                        dragToLauncherEnabled = false,
                        bottomInset = navigationBarInset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        return runCatching {
            this.onConfigChanged = onConfigChanged
            editMode = false
            lifecycleOwner = owner
            windowManager = wm
            wm.addView(view, params)
            composeView = view
            view.requestFocus()
            LauncherOverlayBar.bringToFront()
            true
        }.onFailure {
            Log.e(TAG, "Unable to add app drawer overlay", it)
            this.onConfigChanged = null
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
            .onFailure { Log.w(TAG, "Unable to remove app drawer overlay", it) }
        windowManager = null
        onConfigChanged = null
        editMode = false
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        LauncherDropTargetState.clearDrag()
    }
}
