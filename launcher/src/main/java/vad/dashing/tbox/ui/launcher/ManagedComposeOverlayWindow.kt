package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.ui.MyLifecycleOwner

/**
 * Reusable host for a single [WindowManager] + [ComposeView] + [MyLifecycleOwner] system overlay.
 *
 * Guarantees:
 * - Atomic rollback if `wm.addView(...)` throws (destroys and clears [MyLifecycleOwner]).
 * - Retains its own [WindowManager] reference so [hide] works even if Activity is gone.
 * - Always transitions [MyLifecycleOwner] to [Lifecycle.State.DESTROYED] before `clear()`,
 *   preventing leaked Compose `Recomposer` coroutines.
 */
internal class ManagedComposeOverlayWindow(private val tag: String) {
    @Volatile
    var composeView: ComposeView? = null
        private set

    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    val isShowing: Boolean
        get() = composeView != null

    fun show(
        context: Context,
        params: WindowManager.LayoutParams,
        viewModelStoreOwner: ViewModelStoreOwner? = null,
        focusableBackHandler: (() -> Unit)? = null,
        configureView: (ComposeView.() -> Unit)? = null,
        content: @Composable () -> Unit,
    ): Boolean {
        if (isShowing) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(tag, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            owner.destroyAndClear()
            return false
        }

        val view = ComposeView(context).apply {
            if (focusableBackHandler != null) {
                isFocusable = true
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                    if (event.action == KeyEvent.ACTION_UP) {
                        focusableBackHandler()
                    }
                    true
                }
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner ?: owner)
            configureView?.invoke(this)
            setContent(content)
        }

        val added = runCatching {
            wm.addView(view, params)
            true
        }.onFailure { err ->
            Log.e(tag, "Failed to add overlay window", err)
            owner.destroyAndClear()
        }.getOrDefault(false)

        if (!added) return false

        composeView = view
        lifecycleOwner = owner
        windowManager = wm
        return true
    }

    fun hide(): Boolean {
        val view = composeView
        val owner = lifecycleOwner
        val wm = windowManager
        composeView = null
        lifecycleOwner = null
        windowManager = null

        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { err -> Log.w(tag, "removeViewImmediate failed: ${err.message}") }
        }
        owner?.destroyAndClear()
        return view != null
    }
}

/**
 * Shared calculation for the ADAS left-panel overlay rect used by App Drawer and Quick Settings.
 */
internal fun calculateSharedAdasPanelBounds(context: Context): Rect {
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
