package vad.dashing.tbox.ui.launcher

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

/** Screen-pixel zones for embedded / split app windows. Thread-safe across UI and background workers. */
object LauncherEmbeddedBoundsState {
    val leftPanelBoundsFlow = MutableStateFlow<Rect?>(null)
    var leftPanelBounds: Rect?
        get() = leftPanelBoundsFlow.value
        set(value) { leftPanelBoundsFlow.value = value }

    val embeddedZoneBoundsFlow = MutableStateFlow<Rect?>(null)
    var embeddedZoneBounds: Rect?
        get() = embeddedZoneBoundsFlow.value
        set(value) { embeddedZoneBoundsFlow.value = value }

    val contentRowBoundsFlow = MutableStateFlow<Rect?>(null)
    var contentRowBounds: Rect?
        get() = contentRowBoundsFlow.value
        set(value) { contentRowBoundsFlow.value = value }

    val screenBoundsFlow = MutableStateFlow<Rect?>(null)
    var screenBounds: Rect?
        get() = screenBoundsFlow.value
        set(value) { screenBoundsFlow.value = value }

    val slot0BoundsFlow = MutableStateFlow<Rect?>(null)
    var slot0Bounds: Rect?
        get() = slot0BoundsFlow.value
        set(value) { slot0BoundsFlow.value = value }

    @Volatile
    var dockGridTopPx: Int = 0
    @Volatile
    var topHeaderBottomPx: Int = 0
    @Volatile
    var rightFooterBottomPx: Int = 0
    @Volatile
    var bottomBarTopPx: Int = 0
    @Volatile
    var bottomBarTopOnScreenPx: Int = 0
    @Volatile
    var bottomBarHeightOnScreenPx: Int = 0

    /** No gap between split panes — even 8px left a ~4px HOME strip visible between apps. */
    private const val SPLIT_GAP_PX = 0
    private const val BLEED_RIGHT_PX = 0
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
        val leftPanel = leftPanelBounds
        val leftEdge = if (leftPanel != null && leftPanel.width() > 0 && leftPanel.right > row.left) {
            leftPanel.right + MARGIN_LEFT_PX
        } else {
            row.left + MARGIN_LEFT_PX
        }
        val right = row.right + BLEED_RIGHT_PX
        val top = topHeaderBottomPx.takeIf { it > 0 } ?: FREEFORM_TOP_PX
        val bottom = bottomBarTopPx.takeIf { it > 0 } ?: FREEFORM_BOTTOM_PX
        if (bottom <= top || right <= leftEdge) return null
        val rect = Rect(leftEdge, top, right, bottom)
        vad.dashing.tbox.DclLogRecorder.logAdasBounds(
            "embeddedBounds()",
            leftPanelBounds,
            contentRowBounds,
            embeddedZoneBounds,
            screenBounds,
            rect,
            topHeaderBottomPx,
            bottomBarTopPx,
        )
        android.util.Log.w(
            "LauncherAppLaunch",
            "embeddedBounds=$rect leftPanel=${leftPanel?.right} rowRight=${row.right}",
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
        val right = contentRowBounds?.let { it.right + BLEED_RIGHT_PX }
            ?: screenBounds?.width()
            ?: 1920
        val left = 0
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
        val rect = Rect(left, top, width, bottom)
        vad.dashing.tbox.DclLogRecorder.logAdasBounds(
            "vehicleSettingsBounds()",
            leftPanelBounds,
            contentRowBounds,
            embeddedZoneBounds,
            screenBounds,
            rect,
            topHeaderBottomPx,
            bottomBarTopPx,
        )
        return rect
    }

    /** Small square freeform window centered vertically within the ADAS zone. */
    fun adasSquareBounds(context: android.content.Context? = null): Rect {
        val adasZoneTop = topHeaderBottomPx.takeIf { it > 0 } ?: FREEFORM_TOP_PX
        val adasZoneBottom = bottomBarTopPx.takeIf { it > 0 } ?: FREEFORM_BOTTOM_PX
        val adasZoneHeight = (adasZoneBottom - adasZoneTop).coerceAtLeast(300)

        val leftPanel = leftPanelBounds
        val width = if (leftPanel != null && leftPanel.width() > 200) {
            leftPanel.width()
        } else {
            val density = context?.resources?.displayMetrics?.density ?: 1.0f
            val sidebarDp = context?.let { LauncherAppConfigStore.sidebarWidthDp(it) } ?: 344
            (sidebarDp * density).toInt().coerceAtLeast(300)
        }
        val sideLength = width.coerceAtMost(adasZoneHeight)
        val top = adasZoneTop + (adasZoneHeight - sideLength) / 2
        val rect = Rect(0, top, width, top + sideLength)
        vad.dashing.tbox.DclLogRecorder.logAdasBounds(
            "adasSquareBounds()",
            leftPanelBounds,
            contentRowBounds,
            embeddedZoneBounds,
            screenBounds,
            rect,
            topHeaderBottomPx,
            bottomBarTopPx,
        )
        return rect
    }
}
