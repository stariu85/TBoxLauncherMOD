package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.api.DclUiStateNotifier
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.theme.TboxAppTheme
import kotlin.math.roundToInt

/**
 * Top Control Center (Shade Dropdown Window) that slides down from the top of the screen
 * spanning 100% width and extending down to the height of the bottom dock.
 */
internal object LauncherTopShadeWindow {
    private const val TAG = "LauncherTopShadeWin"

    private val overlayWindow = ManagedComposeOverlayWindow(TAG)

    fun isShowing(): Boolean = overlayWindow.isShowing

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

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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

        val calculatedBottomPx = screenHeightPx - bottomBarHeightPx
        val targetHeightPx = (LauncherEmbeddedBoundsState.bottomBarTopPx.takeIf { it > 0 } ?: calculatedBottomPx).coerceAtLeast(300)

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

        val shown = overlayWindow.show(
            context = activity,
            params = params,
            viewModelStoreOwner = activity,
            focusableBackHandler = { hide() },
        ) {
            ProvideUiClickSounds {
                TboxAppTheme(theme = if (LauncherThemeState.darkTheme) 2 else 1) {
                    TopShadeWrapperContent(
                        heightDp = (targetHeightPx / density).dp,
                        onClose = { hide() },
                    )
                }
            }
        }
        if (shown) {
            overlayWindow.composeView?.requestFocus()
            LauncherCar3DModelTrigger.setTopShadeOpen(true)
            DclUiStateNotifier.notifyUiStateChanged(activity, immediate = true)
            Log.w(TAG, "Top Shade Window shown successfully: width=$screenWidthPx height=$targetHeightPx")
        }
        return shown
    }

    fun hide(context: Context? = null) {
        if (!overlayWindow.hide()) return
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

    // Dynamic backdrop effect deferred to draw phase to avoid 60 FPS recomposition during drag
    val backdropColor = if (LauncherThemeState.darkTheme) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val openFraction = if (heightPx > 0f) {
                    (1f - (-offsetYAnim.value / heightPx)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                if (openFraction > 0f) {
                    drawRect(backdropColor.copy(alpha = 0.6f * openFraction))
                }
            }
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
                onHandleDragEnd = { velocityY ->
                    val currentDragUpPx = -offsetYAnim.value
                    val thresholdPx = (heightPx * 0.12f).coerceAtLeast(with(density) { 60.dp.toPx() })
                    val isFlingUp = velocityY < -600f
                    coroutineScope.launch {
                        if (currentDragUpPx >= thresholdPx || isFlingUp) {
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
