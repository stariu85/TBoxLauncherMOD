package vad.dashing.tbox.ui.launcher

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Screen-pixel zones for embedded / split app windows. */
object LauncherEmbeddedBoundsState {
    var leftPanelBounds: Rect? by mutableStateOf(null)
    var embeddedZoneBounds: Rect? by mutableStateOf(null)
    var dockGridTopPx: Int by mutableIntStateOf(0)
    var topHeaderBottomPx: Int by mutableIntStateOf(0)
    var rightFooterBottomPx: Int by mutableIntStateOf(0)
    var contentRowBounds: Rect? by mutableStateOf(null)
    var screenBounds: Rect? by mutableStateOf(null)
    var bottomBarTopPx: Int by mutableIntStateOf(0)
    var bottomBarTopOnScreenPx: Int by mutableIntStateOf(0)
    var bottomBarHeightOnScreenPx: Int by mutableIntStateOf(0)

    /** No gap between split panes — even 8px left a ~4px HOME strip visible between apps. */
    private const val SPLIT_GAP_PX = 0
    private const val BLEED_RIGHT_PX = 8
    private const val MARGIN_LEFT_PX = 0

    /**
     * Calibrated on Jetour X50 (1920×1080) with `policy_control=immersive.full=*`:
     * under StatusBar date strip, above climate — adb-verified ideal
     * `[left, 51] → [right, 998]`.
     */
    private const val FREEFORM_TOP_PX = 51
    private const val FREEFORM_BOTTOM_PX = 998
    /** Vehicle settings freeform: ~40% width over the car column (larger than sidebar). */
    private const val VEHICLE_SETTINGS_WIDTH_FRACTION = 0.40f

    fun embeddedBounds(): Rect? {
        val row = contentRowBounds ?: return null
        val leftEdge = (leftPanelBounds?.right ?: row.left) + MARGIN_LEFT_PX
        val right = row.right + BLEED_RIGHT_PX
        val top = topHeaderBottomPx.takeIf { it > 0 } ?: FREEFORM_TOP_PX
        val bottom = bottomBarTopPx.takeIf { it > 0 } ?: FREEFORM_BOTTOM_PX
        if (bottom <= top || right <= leftEdge) return null
        val rect = Rect(leftEdge, top, right, bottom)
        android.util.Log.w(
            "LauncherAppLaunch",
            "embeddedBounds=$rect leftPanel=${leftPanelBounds?.right} rowRight=${row.right}",
        )
        return rect
    }

    fun splitBounds(): Rect? = embeddedBounds()

    fun splitPaneBounds(leftRatio: Float): Pair<Rect, Rect>? {
        val base = splitBounds() ?: return null
        val ratio = leftRatio.coerceIn(0.2f, 0.8f)
        val gap = SPLIT_GAP_PX
        val splitX = base.left + (base.width() * ratio).toInt()
        val left = Rect(base.left, base.top, splitX - gap / 2, base.bottom)
        val right = Rect(splitX + gap / 2, base.top, base.right, base.bottom)
        return left to right
    }

    fun fullScreenBounds(): Rect? {
        val screen = screenBounds ?: return null
        if (screen.width() <= 0 || screen.height() <= 0) return null
        return Rect(screen)
    }

    /** Full screen width, height bounded between top and bottom panels. */
    fun fullWidthBounds(): Rect? {
        val row = contentRowBounds ?: return null
        val left = 0
        val right = row.right + BLEED_RIGHT_PX
        val top = topHeaderBottomPx.takeIf { it > 0 } ?: FREEFORM_TOP_PX
        val bottom = bottomBarTopPx.takeIf { it > 0 } ?: FREEFORM_BOTTOM_PX
        if (bottom <= top || right <= left) return null
        return Rect(left, top, right, bottom)
    }

    /** Left freeform pane for vehicle settings (covers car strip, a bit wider). */
    fun vehicleSettingsBounds(): Rect {
        val row = contentRowBounds
        val screenW = row?.right?.coerceAtLeast(1920) ?: 1920
        val width = (screenW * VEHICLE_SETTINGS_WIDTH_FRACTION).toInt().coerceIn(520, 860)
        val left = 0
        val top = topHeaderBottomPx.takeIf { it > 0 } ?: FREEFORM_TOP_PX
        val bottom = bottomBarTopPx.takeIf { it > 0 } ?: FREEFORM_BOTTOM_PX
        return Rect(left, top, width, bottom)
    }
}
