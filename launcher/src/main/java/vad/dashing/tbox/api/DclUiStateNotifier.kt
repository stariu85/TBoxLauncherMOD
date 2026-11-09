package vad.dashing.tbox.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import vad.dashing.tbox.ui.launcher.LauncherActiveAppTracker
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import vad.dashing.tbox.ui.launcher.LauncherRightPanelOverlayWindow
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles aggregation of DCL launcher UI state and dispatches Push notifications (Broadcast)
 * to TBox Monitor (`vad.dashing.tbox`) on UI/chrome changes according to DCL LAUNCHER API v1.2 spec.
 */
object DclUiStateNotifier {

    private const val TAG = "DclUiStateNotifier"
    const val ACTION_UI_STATE_CHANGED = "ras.dashing.tbox.launcher.action.UI_STATE_CHANGED"
    const val ACTION_UI_CHROME_CHANGED = "ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED"
    const val TARGET_PACKAGE = "vad.dashing.tbox"

    private val revisionCounter = AtomicLong(1L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingNotifyRunnable: Runnable? = null
    private var lastSentStateHash: Int = 0

    val currentRevision: Long
        get() = revisionCounter.get()

    /**
     * Increment revision counter.
     */
    fun incrementRevision(): Long {
        return revisionCounter.incrementAndGet()
    }

    /**
     * Builds the complete snapshot Bundle of UI state and geometry according to DCL API v1.2.
     */
    fun buildUiStateBundle(context: Context?): Bundle {
        val screenBounds = LauncherEmbeddedBoundsState.screenBounds
        val displayW = screenBounds?.width()?.takeIf { it > 0 } ?: 1920
        val displayH = screenBounds?.height()?.takeIf { it > 0 } ?: 1080

        val topBarH = LauncherEmbeddedBoundsState.topHeaderBottomPx.takeIf { it > 0 } ?: 51
        val bottomBarTop = LauncherEmbeddedBoundsState.bottomBarTopPx.takeIf { it > 0 } ?: 998
        val bottomBarH = (displayH - bottomBarTop).coerceAtLeast(0)

        val activePkg = LauncherActiveAppTracker.activeAppPackage()
        val hasActiveApp = !activePkg.isNullOrBlank()

        val screenMode = when {
            !hasActiveApp -> "home"
            else -> "single"
        }

        val adasBounds = LauncherEmbeddedBoundsState.leftPanelBounds
        val adasStr = if (adasBounds != null && adasBounds.width() > 0) {
            "${adasBounds.left},${adasBounds.top},${adasBounds.right},${adasBounds.bottom}"
        } else {
            "0,$topBarH,344,$bottomBarTop"
        }

        val appAreaBounds = LauncherEmbeddedBoundsState.embeddedBounds()
        val appAreaStr = if (appAreaBounds != null && appAreaBounds.width() > 0) {
            "${appAreaBounds.left},${appAreaBounds.top},${appAreaBounds.right},${appAreaBounds.bottom}"
        } else {
            "344,$topBarH,$displayW,$bottomBarTop"
        }

        val isOverlayOpen = LauncherRightPanelOverlayWindow.isShowing()

        return Bundle().apply {
            putString("status", "ok")

            // Visible chrome & screen mode
            putBoolean("adasPanelVisible", true)
            putBoolean("topBarVisible", true)
            putBoolean("bottomBarVisible", true)
            putString("screenMode", screenMode)
            putString("foregroundPackage", activePkg.orEmpty())
            putString("leftPackage", "")
            putString("rightPackage", "")
            putLong("revision", currentRevision)
            putLong("updatedAtEpochMs", System.currentTimeMillis())
            putBoolean("immersive", false)

            // Panel Heights and Geometry
            putInt("topBarHeightPx", topBarH)
            putInt("bottomBarHeightPx", bottomBarH)
            putString("adasPanelBounds", adasStr)
            putString("appAreaBounds", appAreaStr)
            putInt("displayWidthPx", displayW)
            putInt("displayHeightPx", displayH)

            // Split and Overlay metrics
            putString("splitFocusSide", "unknown")
            putFloat("splitRatio", 0.5f)
            putString("focusedPackage", activePkg.orEmpty())
            putBoolean("notificationShadeExpanded", false)
            putBoolean("overlayOpen", isOverlayOpen)
            putBoolean("modalOpen", false)
            putBoolean("transientChromeVisible", isOverlayOpen)

            // System info
            putBoolean("isDefaultLauncher", true)
            putBoolean("isLauncherResumed", true)
        }
    }

    /**
     * Schedules a debounced broadcast notification (150ms delay) to prevent spamming broadcasts
     * during rapid UI state changes.
     */
    fun notifyUiStateChanged(context: Context, immediate: Boolean = false) {
        incrementRevision()
        val appCtx = context.applicationContext

        pendingNotifyRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            try {
                val bundle = buildUiStateBundle(appCtx)
                val stateHash = computeStateHash(bundle)
                if (stateHash == lastSentStateHash && !immediate) {
                    return@Runnable
                }
                lastSentStateHash = stateHash

                val broadcastIntent = Intent(ACTION_UI_STATE_CHANGED).apply {
                    setPackage(TARGET_PACKAGE)
                    putExtras(bundle)
                }
                appCtx.sendBroadcast(broadcastIntent)

                // Secondary alias broadcast for compatibility
                val aliasIntent = Intent(ACTION_UI_CHROME_CHANGED).apply {
                    setPackage(TARGET_PACKAGE)
                    putExtras(bundle)
                }
                appCtx.sendBroadcast(aliasIntent)

                Log.d(TAG, "Sent UI_STATE_CHANGED broadcast to $TARGET_PACKAGE (rev=$currentRevision)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send UI_STATE_CHANGED broadcast", e)
            }
        }

        if (immediate) {
            mainHandler.post(runnable)
        } else {
            pendingNotifyRunnable = runnable
            mainHandler.postDelayed(runnable, 150L)
        }
    }

    private fun computeStateHash(bundle: Bundle): Int {
        val mode = bundle.getString("screenMode").orEmpty()
        val pkg = bundle.getString("foregroundPackage").orEmpty()
        val overlay = bundle.getBoolean("overlayOpen")
        val adasBounds = bundle.getString("adasPanelBounds").orEmpty()
        val appBounds = bundle.getString("appAreaBounds").orEmpty()
        return (mode + "|" + pkg + "|" + overlay + "|" + adasBounds + "|" + appBounds).hashCode()
    }
}
