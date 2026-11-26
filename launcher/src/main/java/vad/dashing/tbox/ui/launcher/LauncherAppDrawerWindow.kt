package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Focusable system overlay for the launcher app drawer positioned over the ADAS car zone.
 *
 * Launches apps in EMBEDDED mode.
 */
internal object LauncherAppDrawerWindow {
    private const val TAG = "LauncherAppDrawerWin"

    private val overlayWindow = ManagedComposeOverlayWindow(TAG)
    private var configRevision by mutableIntStateOf(0)
    private var onConfigChanged: (() -> Unit)? = null

    fun isShowing(): Boolean = overlayWindow.isShowing

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
        if (LauncherTopShadeWindow.isShowing()) {
            LauncherTopShadeWindow.hide()
        }
        closeVehicleSettingsOverlay()
        LauncherAboutOverlayWindow.hide()
        LauncherAdasQuickSettingsWindow.hide()
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

        val bounds = calculateSharedAdasPanelBounds(activity)

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

        this.onConfigChanged = onConfigChanged
        val shown = overlayWindow.show(
            context = activity,
            params = params,
            viewModelStoreOwner = activity,
            focusableBackHandler = ::hide,
        ) {
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

        if (!shown) {
            this.onConfigChanged = null
            return false
        }

        overlayWindow.composeView?.requestFocus()
        LauncherOverlayBar.bringToFront()
        LauncherEdgeGestureOverlay.bringToFront()
        return true
    }

    fun hide() {
        if (!overlayWindow.hide()) return
        onConfigChanged = null
        LauncherDropTargetState.clearDrag()
    }
}
