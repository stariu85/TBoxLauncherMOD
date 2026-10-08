package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.ui.MyLifecycleOwner

/**
 * Full-screen overlay component for BSD Alert (high-priority blind spot warning).
 * Draws 100.dp vertical gradient bars on the left/right screen edges:
 * solid bright red at the outer edge, fading smoothly towards the center.
 * Blinks with the exact same frequency as the road lane beam (tween 650ms).
 */
@Composable
fun LauncherBsdAlertOverlay(
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.adasBsdAlertOverlayRevisionFlow.collectAsStateWithLifecycle()
    val enabled = remember(context, revision) {
        LauncherAppConfigStore.adasBsdAlertOverlayEnabled(context)
    }

    if (!enabled) return

    val bsdLeftAlert = adas.rearThreats.bsdLeft == LauncherRearThreatLevel.Alert
    val bsdRightAlert = adas.rearThreats.bsdRight == LauncherRearThreatLevel.Alert

    if (!bsdLeftAlert && !bsdRightAlert) return

    val pulse by rememberInfiniteTransition(label = "bsdOverlayPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bsdOverlayPulseValue",
    )

    val alertRed = Color(0xFFFF1744)

    Box(modifier = modifier.fillMaxSize()) {
        // Left 100.dp bar (solid red at x=0 -> transparent at x=100dp)
        if (bsdLeftAlert) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(100.dp)
                    .fillMaxHeight(),
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.00f to alertRed.copy(alpha = 0.85f * pulse),
                        0.30f to alertRed.copy(alpha = 0.55f * pulse),
                        0.70f to alertRed.copy(alpha = 0.20f * pulse),
                        1.00f to alertRed.copy(alpha = 0f),
                        startX = 0f,
                        endX = size.width,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
            }
        }

        // Right 100.dp bar (transparent at x=0 -> solid red at x=100dp)
        if (bsdRightAlert) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(100.dp)
                    .fillMaxHeight(),
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.00f to alertRed.copy(alpha = 0f),
                        0.30f to alertRed.copy(alpha = 0.20f * pulse),
                        0.70f to alertRed.copy(alpha = 0.55f * pulse),
                        1.00f to alertRed.copy(alpha = 0.85f * pulse),
                        startX = 0f,
                        endX = size.width,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
            }
        }
    }
}

/**
 * System overlay window manager that presents [LauncherBsdAlertOverlay]
 * above all external application windows when SYSTEM_ALERT_WINDOW permission is available.
 */
internal object LauncherBsdAlertOverlayWindow {
    private const val TAG = "LauncherBsdAlertOverlayWindow"

    @Volatile
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null

    fun isShowing(): Boolean = composeView != null

    fun update(context: Context, adas: LauncherAdasState) {
        if (!LauncherAppConfigStore.adasBsdAlertOverlayEnabled(context)) {
            hide()
            return
        }

        val hasAlert = adas.rearThreats.bsdLeft == LauncherRearThreatLevel.Alert ||
            adas.rearThreats.bsdRight == LauncherRearThreatLevel.Alert

        if (!hasAlert) {
            hide()
            return
        }

        if (isShowing()) return

        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (!Settings.canDrawOverlays(activity)) return

        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
                val currentAdas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive
                LauncherBsdAlertOverlay(adas = currentAdas)
            }
        }

        lifecycleOwner = owner
        composeView = view
        runCatching {
            wm.addView(view, params)
        }.onFailure {
            Log.e(TAG, "Failed to add BSD Alert overlay window", it)
            composeView = null
            lifecycleOwner = null
        }
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        val owner = lifecycleOwner
        lifecycleOwner = null

        val activity = LauncherHomeActivityHolder.instance ?: return
        runCatching {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(view)
        }
        owner?.setCurrentState(Lifecycle.State.DESTROYED)
    }
}
