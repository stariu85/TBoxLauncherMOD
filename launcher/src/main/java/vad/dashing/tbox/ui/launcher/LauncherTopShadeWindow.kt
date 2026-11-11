package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.api.DclUiStateNotifier
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme
import kotlin.math.roundToInt

/**
 * Top Control Center (Shade Dropdown Window) that slides down from the top of the screen
 * spanning 100% width and extending down to the height of the bottom dock.
 */
internal object LauncherTopShadeWindow {
    private const val TAG = "LauncherTopShadeWin"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = composeView != null

    fun toggle(context: Context): Boolean {
        return if (isShowing()) {
            hide()
            true
        } else {
            show(context)
        }
    }

    fun show(context: Context): Boolean {
        if (isShowing()) return true
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val realMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.getRealMetrics(realMetrics)
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(realMetrics)
        }

        val density = activity.resources.displayMetrics.density
        val screenWidthPx = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else activity.resources.displayMetrics.widthPixels
        val screenHeightPx = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else activity.resources.displayMetrics.heightPixels

        val bottomBarHeightDp = LauncherAppConfigStore.bottomBarHeightDp(activity)
        val bottomBarHeightPx = (bottomBarHeightDp * density).toInt()

        // Height of the shade = Physical Screen Height minus Bottom Dock Height
        val targetHeightPx = (screenHeightPx - bottomBarHeightPx).coerceAtLeast(300)

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        lifecycleOwner = owner

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            screenWidthPx,
            targetHeightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    TboxAppTheme(theme = if (LauncherThemeState.darkTheme) 2 else 1) {
                        TopShadeWrapperContent(
                            heightDp = (targetHeightPx / density).dp,
                            onClose = { hide() },
                        )
                    }
                }
            }
        }

        return runCatching {
            wm.addView(view, params)
            composeView = view
            LauncherCar3DModelTrigger.setTopShadeOpen(true)
            DclUiStateNotifier.notifyUiStateChanged(activity, immediate = true)
            Log.w(TAG, "Top Shade Window shown successfully: width=$screenWidthPx height=$targetHeightPx")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to show Top Shade Window", e)
            false
        }
    }

    fun hide(context: Context? = null) {
        val view = composeView ?: return
        val wm = windowManager ?: return
        runCatching { wm.removeViewImmediate(view) }
        composeView = null
        lifecycleOwner?.clear()
        lifecycleOwner = null
        windowManager = null
        LauncherCar3DModelTrigger.setTopShadeOpen(false)
        context?.let { DclUiStateNotifier.notifyUiStateChanged(it, immediate = true) }
        Log.w(TAG, "Top Shade Window hidden")
    }
}

@Composable
private fun TopShadeWrapperContent(
    heightDp: Dp,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val heightPx = with(density) { heightDp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetYAnim = remember { Animatable(-heightPx) }

    LaunchedEffect(heightPx) {
        if (heightPx > 0f) {
            offsetYAnim.animateTo(0f, animationSpec = tween(durationMillis = 300))
        }
    }

    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch {
            offsetYAnim.animateTo(-heightPx, animationSpec = tween(durationMillis = 220))
            onClose()
        }
    }

    BackHandler(enabled = true) {
        dismissWithAnimation()
    }

    // Dynamic backdrop darkening based on current drag position
    val openFraction = (1f - (-offsetYAnim.value / heightPx)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * openFraction))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismissWithAnimation,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .offset { IntOffset(0, offsetYAnim.value.roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // Catch clicks inside panel
                ),
        ) {
            LauncherTopShadeContent(
                onClose = dismissWithAnimation,
                onHandleDrag = { dragY ->
                    val newOffset = (offsetYAnim.value + dragY).coerceAtMost(0f)
                    coroutineScope.launch {
                        offsetYAnim.snapTo(newOffset)
                    }
                },
                onHandleDragEnd = {
                    val currentDragUpPx = -offsetYAnim.value
                    val thresholdPx = heightPx * 0.30f
                    coroutineScope.launch {
                        if (currentDragUpPx >= thresholdPx) {
                            offsetYAnim.animateTo(-heightPx, animationSpec = tween(durationMillis = 220))
                            onClose()
                        } else {
                            offsetYAnim.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
            )
        }
    }
}
