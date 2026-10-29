package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.rememberPlaySystemClickSound
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Three thin system overlay touch strips for edge swipe gestures (Left, Right, Bottom).
 *
 * Each strip is placed along its respective edge and occupies only [stripWidthDp] (15-35dp).
 * The center of the screen remains 100% uncovered, allowing all touches on the launcher UI
 * and external apps to pass through without any interference.
 */
internal object LauncherEdgeGestureOverlay {
    private const val TAG = "LauncherEdgeOverlay"

    @Volatile
    private var leftView: ComposeView? = null
    @Volatile
    private var rightView: ComposeView? = null
    @Volatile
    private var bottomView: ComposeView? = null

    private var leftOwner: MyLifecycleOwner? = null
    private var rightOwner: MyLifecycleOwner? = null
    private var bottomOwner: MyLifecycleOwner? = null

    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = leftView != null || rightView != null || bottomView != null

    fun ensureShowing(context: Context) {
        if (isShowing()) return
        show(context)
    }

    fun show(context: Context): Boolean {
        if (isShowing()) {
            updateStrips(context)
            return true
        }
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (!LauncherAppConfigStore.edgeGesturesEnabled(activity)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return false
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        updateStrips(activity)
        return isShowing()
    }

    fun updateStrips(context: Context) {
        val activity = LauncherHomeActivityHolder.instance ?: (context as? LauncherHomeActivity)
            ?: return
        val wm = windowManager ?: (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { windowManager = it }

        if (!LauncherAppConfigStore.edgeGesturesEnabled(activity)) {
            hide()
            return
        }

        val density = activity.resources.displayMetrics.density
        val stripWidthPx = (LauncherAppConfigStore.edgeGesturesWidthDp(activity) * density).toInt().coerceIn(20, 100)

        val leftAction = LauncherAppConfigStore.edgeGestureActionLeft(activity)
        val rightAction = LauncherAppConfigStore.edgeGestureActionRight(activity)
        val bottomAction = LauncherAppConfigStore.edgeGestureActionBottom(activity)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Left Strip
        if (leftAction != EDGE_GESTURE_ACTION_NONE) {
            if (leftView == null) {
                leftView = createStripView(activity, EDGE_GESTURE_SIDE_LEFT)
                leftOwner = createOwner()
                val params = createLayoutParams(type, stripWidthPx, WindowManager.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.TOP)
                runCatching { wm.addView(leftView, params) }
            } else {
                val params = leftView?.layoutParams as? WindowManager.LayoutParams
                if (params != null && params.width != stripWidthPx) {
                    params.width = stripWidthPx
                    runCatching { wm.updateViewLayout(leftView, params) }
                }
            }
        } else {
            removeLeftStrip()
        }

        // 2. Right Strip
        if (rightAction != EDGE_GESTURE_ACTION_NONE) {
            if (rightView == null) {
                rightView = createStripView(activity, EDGE_GESTURE_SIDE_RIGHT)
                rightOwner = createOwner()
                val params = createLayoutParams(type, stripWidthPx, WindowManager.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.TOP)
                runCatching { wm.addView(rightView, params) }
            } else {
                val params = rightView?.layoutParams as? WindowManager.LayoutParams
                if (params != null && params.width != stripWidthPx) {
                    params.width = stripWidthPx
                    runCatching { wm.updateViewLayout(rightView, params) }
                }
            }
        } else {
            removeRightStrip()
        }

        // 3. Bottom Strip
        if (bottomAction != EDGE_GESTURE_ACTION_NONE) {
            if (bottomView == null) {
                bottomView = createStripView(activity, EDGE_GESTURE_SIDE_BOTTOM)
                bottomOwner = createOwner()
                val params = createLayoutParams(type, WindowManager.LayoutParams.MATCH_PARENT, stripWidthPx, Gravity.START or Gravity.BOTTOM)
                runCatching { wm.addView(bottomView, params) }
            } else {
                val params = bottomView?.layoutParams as? WindowManager.LayoutParams
                if (params != null && params.height != stripWidthPx) {
                    params.height = stripWidthPx
                    runCatching { wm.updateViewLayout(bottomView, params) }
                }
            }
        } else {
            removeBottomStrip()
        }
    }

    private fun createOwner(): MyLifecycleOwner = MyLifecycleOwner().also {
        it.setCurrentState(Lifecycle.State.CREATED)
        it.setCurrentState(Lifecycle.State.STARTED)
        it.setCurrentState(Lifecycle.State.RESUMED)
    }

    private fun createLayoutParams(type: Int, widthPx: Int, heightPx: Int, gravityFlags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            widthPx,
            heightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = gravityFlags
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

    private fun createStripView(activity: LauncherHomeActivity, side: Int): ComposeView {
        val owner = createOwner()
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                ProvideUiClickSounds {
                    TboxAppTheme(theme = 2) {
                        EdgeStripContent(activity, side)
                    }
                }
            }
        }
    }

    private fun removeLeftStrip() {
        leftView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        leftView = null
        leftOwner?.clear()
        leftOwner = null
    }

    private fun removeRightStrip() {
        rightView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        rightView = null
        rightOwner?.clear()
        rightOwner = null
    }

    private fun removeBottomStrip() {
        bottomView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        bottomView = null
        bottomOwner?.clear()
        bottomOwner = null
    }

    fun bringToFront() {
        val wm = windowManager ?: return
        leftView?.let { view ->
            val p = view.layoutParams as? WindowManager.LayoutParams ?: return@let
            runCatching {
                wm.removeViewImmediate(view)
                wm.addView(view, p)
            }
        }
        rightView?.let { view ->
            val p = view.layoutParams as? WindowManager.LayoutParams ?: return@let
            runCatching {
                wm.removeViewImmediate(view)
                wm.addView(view, p)
            }
        }
        bottomView?.let { view ->
            val p = view.layoutParams as? WindowManager.LayoutParams ?: return@let
            runCatching {
                wm.removeViewImmediate(view)
                wm.addView(view, p)
            }
        }
        Log.w(TAG, "Edge gesture overlays brought to front above settings/overlays")
    }

    fun hide() {
        removeLeftStrip()
        removeRightStrip()
        removeBottomStrip()
        windowManager = null
        Log.w(TAG, "Edge gesture overlays hidden")
    }
}

private const val EDGE_GESTURE_SIDE_LEFT = 1
private const val EDGE_GESTURE_SIDE_RIGHT = 2
private const val EDGE_GESTURE_SIDE_BOTTOM = 3

@Composable
private fun EdgeStripContent(context: Context, side: Int) {
    val revision by LauncherAppConfigStore.edgeGesturesRevisionFlow.collectAsStateWithLifecycle()
    val playSound = rememberPlaySystemClickSound()
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 35.dp.toPx() } }

    val actionKey = remember(context, revision, side) {
        when (side) {
            EDGE_GESTURE_SIDE_LEFT -> LauncherAppConfigStore.edgeGestureActionLeft(context)
            EDGE_GESTURE_SIDE_RIGHT -> LauncherAppConfigStore.edgeGestureActionRight(context)
            EDGE_GESTURE_SIDE_BOTTOM -> LauncherAppConfigStore.edgeGestureActionBottom(context)
            else -> EDGE_GESTURE_ACTION_NONE
        }
    }

    if (actionKey == EDGE_GESTURE_ACTION_NONE) return

    fun trigger() {
        playSound()
        Log.w("LauncherEdgeOverlay", "Gesture triggered side=$side actionKey=$actionKey")
        executeEdgeGesture(context, actionKey)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(side, actionKey, thresholdPx) {
                var accum = 0f
                when (side) {
                    EDGE_GESTURE_SIDE_LEFT -> {
                        detectHorizontalDragGestures(
                            onDragStart = { accum = 0f },
                            onDragEnd = {
                                if (accum > thresholdPx) trigger()
                                accum = 0f
                            },
                            onDragCancel = { accum = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                            },
                        )
                    }
                    EDGE_GESTURE_SIDE_RIGHT -> {
                        detectHorizontalDragGestures(
                            onDragStart = { accum = 0f },
                            onDragEnd = {
                                if (accum < -thresholdPx) trigger()
                                accum = 0f
                            },
                            onDragCancel = { accum = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                            },
                        )
                    }
                    EDGE_GESTURE_SIDE_BOTTOM -> {
                        detectVerticalDragGestures(
                            onDragStart = { accum = 0f },
                            onDragEnd = {
                                if (accum < -thresholdPx) trigger()
                                accum = 0f
                            },
                            onDragCancel = { accum = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                            },
                        )
                    }
                }
            },
    )
}

internal fun executeEdgeGesture(context: Context, actionKey: String) {
    if (actionKey.isBlank() || actionKey == EDGE_GESTURE_ACTION_NONE) return

    when {
        actionKey == EDGE_GESTURE_ACTION_HOME -> {
            goLauncherHome(context, source = "EdgeGesture:home")
        }
        actionKey == EDGE_GESTURE_ACTION_BACK -> {
            goLauncherBack(
                context = context,
                vehicleSettingsOpen = LauncherVehicleSettingsUiState.open,
                onCloseVehicleSettings = { closeVehicleSettingsOverlay() },
                source = "EdgeGesture:back",
            )
        }
        actionKey == EDGE_GESTURE_ACTION_PREVIOUS_APP -> {
            performUndoLaunch(context, source = "EdgeGesture:previous_app")
        }
        actionKey == EDGE_GESTURE_ACTION_CLOSE_ALL -> {
            dismissForeignFreeformTasks(context, source = "EdgeGesture:close_all")
        }
        actionKey.startsWith("shortcut:") -> {
            val key = actionKey.removePrefix("shortcut:")
            val items = LauncherHomeStore.loadItems(context)
            val matchingItem = items.firstOrNull { it.key == key || it.key.startsWith(key) }
            if (matchingItem != null) {
                launchHomeItem(context, matchingItem)
            } else {
                when {
                    key.startsWith("app:") -> {
                        val pkg = key.removePrefix("app:").substringBefore("@")
                        launchLauncherApp(context, pkg)
                    }
                    key.startsWith("split:") -> {
                        val presetId = key.removePrefix("split:").substringBefore("@")
                        val preset = LauncherSplitPresetStore.loadPresets(context).firstOrNull { it.id == presetId }
                        if (preset != null) {
                            val leftApp = LaunchableAppEntry(preset.leftPackage, preset.leftPackage, null, null)
                            val rightApp = LaunchableAppEntry(preset.rightPackage, preset.rightPackage, null, null)
                            launchSplitApps(context, leftApp, rightApp, preset.leftRatio)
                        }
                    }
                }
            }
        }
    }
}

private fun launchHomeItem(context: Context, item: LauncherHomeItem) {
    when (item) {
        is LauncherHomeItem.App -> {
            launchLauncherApp(context, item.packageName)
        }
        is LauncherHomeItem.Split -> {
            val preset = LauncherSplitPresetStore.loadPresets(context).firstOrNull { it.id == item.presetId }
            if (preset != null) {
                val leftApp = LaunchableAppEntry(preset.leftPackage, preset.leftPackage, null, null)
                val rightApp = LaunchableAppEntry(preset.rightPackage, preset.rightPackage, null, null)
                launchSplitApps(context, leftApp, rightApp, preset.leftRatio)
            }
        }
        is LauncherHomeItem.TbmAutomation -> {
            val tbmRepo = TbmRepositoryProvider.get(context)
            CoroutineScope(Dispatchers.Main).launch {
                tbmRepo.runAutomation(item.automationId)
            }
        }
    }
}
