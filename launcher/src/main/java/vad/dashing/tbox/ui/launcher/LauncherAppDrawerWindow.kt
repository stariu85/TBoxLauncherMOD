package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
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
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Focusable system overlay for the launcher app drawer positioned over the ADAS car zone.
 *
 * Launches apps in EMBEDDED mode.
 */
internal object LauncherAppDrawerWindow {
    private const val TAG = "LauncherAppDrawerWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var configRevision by mutableIntStateOf(0)
    private var onConfigChanged: (() -> Unit)? = null

    fun isShowing(): Boolean = composeView != null

    private fun calculateAdasPanelBounds(context: Context): Rect {
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
        val metrics = (activity ?: context).resources.displayMetrics
        val density = metrics.density
        val screenH = metrics.heightPixels.coerceAtLeast(1080)

        val adasVisible = LauncherAppConfigStore.adasPanelVisible(context)
        val sidebarDp = LauncherAppConfigStore.sidebarWidthDp(context)
        val topBarDp = LauncherAppConfigStore.topBarHeightDp(context)
        val bottomBarDp = LauncherAppConfigStore.bottomBarHeightDp(context)

        val widthPx = if (adasVisible) (sidebarDp * density).toInt() else (344 * density).toInt()
        val topPx = (topBarDp * density).toInt().coerceIn(0, 100).takeIf { it > 0 } ?: 51
        val bottomPx = screenH - (bottomBarDp * density).toInt().coerceIn(0, 150)

        val top = LauncherEmbeddedBoundsState.topHeaderBottomPx.takeIf { it > 0 } ?: topPx
        val bottom = LauncherEmbeddedBoundsState.bottomBarTopPx.takeIf { it > 0 } ?: bottomPx
        val measuredLeftPanel = LauncherEmbeddedBoundsState.leftPanelBounds

        val width = if (measuredLeftPanel != null && measuredLeftPanel.width() > 200) {
            measuredLeftPanel.width()
        } else {
            widthPx
        }
        val height = (bottom - top).coerceAtLeast(400)

        return Rect(0, top, width, top + height)
    }

    fun toggle(
        context: Context,
        settingsViewModel: SettingsViewModel,
        onConfigChanged: () -> Unit = {},
    ): Boolean {
        return if (isShowing()) {
            hide()
            true
        } else {
            show(context, settingsViewModel, onConfigChanged)
        }
    }

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

        val bounds = calculateAdasPanelBounds(activity)

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
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 0.35f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val tboxViewModel = ViewModelProvider(activity)[TboxViewModel::class.java]
        val view = ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) {
                    hide()
                }
                true
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::hide,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { bounds.left.toDp() },
                                y = with(density) { bounds.top.toDp() },
                            )
                            .size(
                                width = with(density) { bounds.width().toDp() },
                                height = with(density) { bounds.height().toDp() },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        ProvideUiClickSounds {
                            val theme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
                            TboxAppTheme(theme = 2) {
                                BackHandler {
                                    hide()
                                }
                                LauncherAppDrawer(
                                    visible = true,
                                    settingsViewModel = settingsViewModel,
                                    onDismiss = ::hide,
                                    configRevision = configRevision,
                                    onConfigChanged = {
                                        configRevision++
                                        this@LauncherAppDrawerWindow.onConfigChanged?.invoke()
                                    },
                                    onLaunchApp = { app ->
                                        hide()
                                        launchLauncherAppEmbedded(activity, app.packageName, app.activityName)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        return runCatching {
            this.onConfigChanged = onConfigChanged
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
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        LauncherDropTargetState.clearDrag()
    }
}
