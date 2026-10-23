package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * System overlay floating Home button that stays ALWAYS ON TOP of all external fullscreen / freeform app windows.
 * Compact floating widget (not a full black bar) so the user can always tap to return to TBox Launcher.
 */
internal object LauncherOverlayBar {
    private const val TAG = "LauncherOverlayBar"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    fun ensureShowing(context: Context) {
        if (isShowing()) return
        show(context)
    }

    fun show(context: Context): Boolean {
        if (isShowing()) return true
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (!LauncherAppConfigStore.floatingHomeVisible(activity)) return false
        if (!Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val screenW = activity.resources.displayMetrics.widthPixels.coerceAtLeast(1920)
        val density = activity.resources.displayMetrics.density

        val initialHomeSizeDp = LauncherAppConfigStore.floatingHomeSizeDp(activity)
        val initialButtonDiameterPx = (initialHomeSizeDp * density).toInt()

        val bottomBarHeightDp = LauncherAppConfigStore.bottomBarHeightDp(activity)
        val bottomBarHeightPx = (bottomBarHeightDp * density).toInt()

        val slotUnitWidthPx = (screenW - (24 * density)) / 20f
        val slot0CenterX = (12 * density) + (slotUnitWidthPx / 2f)

        val slot0X = (slot0CenterX - initialButtonDiameterPx / 2f).toInt().coerceAtLeast(0)
        val slot0Y = ((bottomBarHeightPx - initialButtonDiameterPx) / 2).coerceAtLeast(0)

        val params = WindowManager.LayoutParams(
            initialButtonDiameterPx,
            initialButtonDiameterPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = slot0X
            y = slot0Y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                val context = LocalContext.current
                val homeSizeRevision by LauncherAppConfigStore.floatingHomeSizeRevisionFlow.collectAsStateWithLifecycle()
                val currentHomeSizeDp = remember(context, homeSizeRevision) {
                    LauncherAppConfigStore.floatingHomeSizeDp(context)
                }
                val buttonDiameterPx = (currentHomeSizeDp * density).toInt()
                val iconSizeDp = (currentHomeSizeDp * 0.52f).dp

                val calcBottomBarHeightDp = remember(context) { LauncherAppConfigStore.bottomBarHeightDp(context) }
                val calcBottomBarHeightPx = (calcBottomBarHeightDp * density).toInt()
                val calcSlot0X = ((12 * density) + ((screenW - (24 * density)) / 20f) / 2f - buttonDiameterPx / 2f).toInt().coerceAtLeast(0)
                val calcSlot0Y = ((calcBottomBarHeightPx - buttonDiameterPx) / 2).coerceAtLeast(0)

                SideEffect {
                    if (params.width != buttonDiameterPx || params.height != buttonDiameterPx || params.x != calcSlot0X || params.y != calcSlot0Y) {
                        params.gravity = Gravity.BOTTOM or Gravity.START
                        params.width = buttonDiameterPx
                        params.height = buttonDiameterPx
                        params.x = calcSlot0X
                        params.y = calcSlot0Y
                        runCatching { wm.updateViewLayout(this, params) }
                    }
                }

                val isDark = LauncherThemeState.darkTheme
                val buttonBg = if (isDark) Color(0xE6141820) else Color(0xFFE5E7EB)
                val buttonBorder = if (isDark) Color(0x6038BDF8) else Color.Black.copy(alpha = 0.20f)
                val iconTint = if (isDark) LauncherColors.AccentCyan else Color(0xFF4B5563)

                ProvideUiClickSounds {
                    TboxAppTheme(theme = if (isDark) 2 else 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(buttonBg)
                                .border(1.dp, buttonBorder, CircleShape)
                                .clickable {
                                    goLauncherHome(activity, source = "OverlayBar:home")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = stringResource(R.string.launcher_home_cd),
                                tint = iconTint,
                                modifier = Modifier.size(iconSizeDp),
                            )
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
            Log.w(TAG, "Floating Home button shown on top of all windows")
            true
        }.onFailure {
            Log.e(TAG, "Unable to add floating Home button", it)
            lifecycleOwner = null
            windowManager = null
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }.getOrDefault(false)
    }

    fun bringToFront() {
        val view = composeView ?: return
        val wm = windowManager ?: return
        val p = view.layoutParams as? WindowManager.LayoutParams ?: return
        runCatching {
            wm.removeViewImmediate(view)
            wm.addView(view, p)
            Log.w(TAG, "LauncherOverlayBar brought to front above settings/overlays")
        }.onFailure {
            Log.w(TAG, "bringToFront failed", it)
        }
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Unable to remove floating Home button", it) }
        windowManager = null
        lifecycleOwner?.let { owner ->
            owner.setCurrentState(Lifecycle.State.DESTROYED)
            owner.clear()
        }
        lifecycleOwner = null
        Log.w(TAG, "Floating Home button hidden")
    }
}
