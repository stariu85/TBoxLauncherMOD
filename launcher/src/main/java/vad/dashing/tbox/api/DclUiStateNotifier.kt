package vad.dashing.tbox.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import vad.dashing.tbox.MainActivity
import vad.dashing.tbox.ui.launcher.LauncherActiveAppTracker
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import vad.dashing.tbox.ui.launcher.LauncherAppLaunchMode
import vad.dashing.tbox.ui.launcher.LauncherCruisePresetController
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import vad.dashing.tbox.ui.launcher.LauncherRightPanelOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherTopShadeWindow
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsUiState
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
    private const val MSG_NOTIFY_STATE = 1
    private val notifyLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_NOTIFY_STATE) {
            val payload = msg.obj as? NotifyRequest
            if (payload != null) {
                dispatchBroadcast(payload.appContext, payload.immediate)
            }
            true
        } else {
            false
        }
    }

    @Volatile
    private var lastSentStateHash: Int = 0

    @Volatile
    private var cachedUiStateBundle: Bundle? = null

    private data class NotifyRequest(
        val appContext: Context,
        val immediate: Boolean,
    )

    val currentRevision: Long
        get() = revisionCounter.get()

    /**
     * Increment revision counter.
     */
    fun incrementRevision(): Long {
        return revisionCounter.incrementAndGet()
    }

    /**
     * Returns the cached UI state bundle or builds a fresh snapshot if none exists yet.
     * Guarantees consistency between getUiState query and broadcast pushes.
     * Always returns a defensive copy so callers cannot mutate the cached instance.
     */
    fun getUiState(context: Context?): Bundle {
        val snapshot = cachedUiStateBundle ?: buildAndCacheUiStateBundle(context)
        return Bundle(snapshot)
    }

    /**
     * Builds and caches the complete snapshot Bundle of UI state and geometry according to DCL API v1.2.
     */
    @Synchronized
    fun buildAndCacheUiStateBundle(context: Context?): Bundle {
        val appCtx = context?.applicationContext
        val activePkg = LauncherActiveAppTracker.activeAppPackage().orEmpty()
        val hasActiveApp = activePkg.isNotBlank()

        val trackerLaunchMode = LauncherActiveAppTracker.launchMode()
        val isFullscreenApp = hasActiveApp && (
            trackerLaunchMode == LauncherAppLaunchMode.FULLSCREEN ||
            (trackerLaunchMode == null && LauncherActiveAppTracker.isFullscreen()) ||
            (trackerLaunchMode == null && appCtx != null && LauncherAppConfigStore.appLaunchMode(appCtx, activePkg) == LauncherAppLaunchMode.FULLSCREEN)
        )

        val isFullWidthApp = !isFullscreenApp && hasActiveApp && (
            trackerLaunchMode == LauncherAppLaunchMode.FULL_WIDTH ||
            (trackerLaunchMode == null && LauncherActiveAppTracker.isFullWidth()) ||
            (trackerLaunchMode == null && appCtx != null && LauncherAppConfigStore.appLaunchMode(appCtx, activePkg) == LauncherAppLaunchMode.FULL_WIDTH)
        )

        val isVehicleSettingsOpen = LauncherVehicleSettingsUiState.open || LauncherVehicleSettingsOverlayWindow.isShowing()
        val isTopShadeOpen = LauncherTopShadeWindow.isShowing()
        val isRightPanelOpen = LauncherRightPanelOverlayWindow.isShowing()
        val isMainActivityOpen = MainActivity.isOpen()
        val isOverlayOpen = isVehicleSettingsOpen || isTopShadeOpen || isRightPanelOpen || isMainActivityOpen

        val screenMode = when {
            isFullscreenApp -> "fullscreen"
            isFullWidthApp -> "full_width"
            isVehicleSettingsOpen || isMainActivityOpen -> if (hasActiveApp) "single" else "home"
            !hasActiveApp -> "home"
            else -> "single"
        }

        val isAdasVisibleOnScreen = when {
            isFullscreenApp || isFullWidthApp -> false
            isVehicleSettingsOpen || isMainActivityOpen -> false
            appCtx != null -> LauncherAppConfigStore.adasPanelVisible(appCtx)
            else -> true
        }

        val isTopBarVisibleOnScreen = when {
            isFullscreenApp -> false
            isVehicleSettingsOpen || isMainActivityOpen -> false
            else -> true
        }

        val isBottomBarVisibleOnScreen = when {
            isFullscreenApp -> false
            isVehicleSettingsOpen -> true // Bottom bar is rendered in vehicle settings overlay
            isMainActivityOpen -> false
            else -> true
        }

        val isImmersiveMode = isFullscreenApp

        val screenBounds = LauncherEmbeddedBoundsState.screenBounds
        val displayW = screenBounds?.width()?.takeIf { it > 0 } ?: 1920
        val displayH = screenBounds?.height()?.takeIf { it > 0 } ?: 1080

        val topBarH = if (isTopBarVisibleOnScreen) {
            LauncherEmbeddedBoundsState.topHeaderBottomPx.takeIf { it > 0 } ?: 51
        } else {
            0
        }

        val bottomBarTop = LauncherEmbeddedBoundsState.bottomBarTopPx.takeIf { it > 0 } ?: 998
        val bottomBarH = if (isBottomBarVisibleOnScreen) {
            (displayH - bottomBarTop).coerceAtLeast(0)
        } else {
            0
        }

        val adasBounds = if (isAdasVisibleOnScreen) {
            val b = LauncherEmbeddedBoundsState.leftPanelBounds
            if (b != null && b.width() > 0) {
                "${b.left},${b.top},${b.right},${b.bottom}"
            } else {
                "0,51,344,998"
            }
        } else {
            "0,0,0,0"
        }

        val appAreaBounds = when (screenMode) {
            "fullscreen" -> "0,0,$displayW,$displayH"
            "full_width" -> {
                val b = LauncherEmbeddedBoundsState.fullWidthBounds()
                if (b != null && b.width() > 0) {
                    "${b.left},${b.top},${b.right},${b.bottom}"
                } else {
                    "0,$topBarH,$displayW,$bottomBarTop"
                }
            }
            else -> {
                val b = LauncherEmbeddedBoundsState.embeddedBounds()
                if (b != null && b.width() > 0) {
                    "${b.left},${b.top},${b.right},${b.bottom}"
                } else {
                    "344,51,$displayW,$bottomBarTop"
                }
            }
        }

        val bundle = Bundle().apply {
            putString("status", "ok")

            // Visible chrome & screen mode
            putBoolean("adasPanelVisible", isAdasVisibleOnScreen)
            putBoolean("topBarVisible", isTopBarVisibleOnScreen)
            putBoolean("bottomBarVisible", isBottomBarVisibleOnScreen)
            putString("screenMode", screenMode)
            putString("foregroundPackage", activePkg)
            putString("leftPackage", "")
            putString("rightPackage", "")
            putLong("revision", currentRevision)
            putLong("updatedAtEpochMs", System.currentTimeMillis())
            putBoolean("immersive", isImmersiveMode)

            // Panel Heights and Geometry
            putInt("topBarHeightPx", topBarH)
            putInt("bottomBarHeightPx", bottomBarH)
            putString("adasPanelBounds", adasBounds)
            putString("appAreaBounds", appAreaBounds)
            putInt("displayWidthPx", displayW)
            putInt("displayHeightPx", displayH)

            // Split and Overlay metrics
            putString("splitFocusSide", "unknown")
            putFloat("splitRatio", 0.5f)
            putString("focusedPackage", activePkg)
            putBoolean("notificationShadeExpanded", isTopShadeOpen)
            putBoolean("overlayOpen", isOverlayOpen)
            putBoolean("modalOpen", false)
            putBoolean("transientChromeVisible", isOverlayOpen)

            // System info
            putBoolean("isDefaultLauncher", true)
            putBoolean("isLauncherResumed", true)

            // Smart ACC / Cruise state
            val isSmartAccActive = LauncherCruisePresetController.isSmartSpeedLimitActive.value
            val isSmartAccConfigEnabled = if (appCtx != null) LauncherAppConfigStore.adasAccBySpeedLimitEnabled(appCtx) else true
            val cruiseState = LauncherCruisePresetController.accCruiseState.value.name
            val speedLimit = if (appCtx != null) LauncherCruisePresetController.currentEffectiveSpeedLimit(appCtx) else null

            putBoolean("smartAccActive", isSmartAccActive)
            putBoolean("isSmartAccActive", isSmartAccActive)
            putBoolean("smartAccConfigEnabled", isSmartAccConfigEnabled)
            putBoolean("adasAccBySpeedLimitEnabled", isSmartAccConfigEnabled)
            putString("cruiseState", cruiseState)
            if (speedLimit != null) {
                putInt("effectiveSpeedLimit", speedLimit)
            }
        }

        cachedUiStateBundle = bundle
        return bundle
    }

    /**
     * Builds the complete snapshot Bundle of UI state and geometry according to DCL API v1.2.
     */
    fun buildUiStateBundle(context: Context?): Bundle {
        return buildAndCacheUiStateBundle(context)
    }

    /**
     * Schedules or immediately dispatches a broadcast notification to TBox Monitor (`vad.dashing.tbox`).
     */
    fun notifyUiStateChanged(context: Context, immediate: Boolean = true) {
        incrementRevision()
        val appCtx = context.applicationContext
        // Invalidate cache immediately so concurrent getUiState() callers build a fresh snapshot
        // rather than reading stale pre-change state during a debounce window.
        cachedUiStateBundle = null

        synchronized(notifyLock) {
            mainHandler.removeMessages(MSG_NOTIFY_STATE)
            if (immediate) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    dispatchBroadcast(appCtx, immediate = true)
                } else {
                    mainHandler.obtainMessage(MSG_NOTIFY_STATE, NotifyRequest(appCtx, immediate = true))
                        .sendToTarget()
                }
            } else {
                val msg = mainHandler.obtainMessage(MSG_NOTIFY_STATE, NotifyRequest(appCtx, immediate = false))
                mainHandler.sendMessageDelayed(msg, 150L)
            }
        }
    }

    private fun dispatchBroadcast(appCtx: Context, immediate: Boolean) {
        try {
            val bundle = buildAndCacheUiStateBundle(appCtx)
            val stateHash = computeStateHash(bundle)
            synchronized(notifyLock) {
                if (stateHash == lastSentStateHash && !immediate) {
                    return
                }
                lastSentStateHash = stateHash
            }

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

            Log.d(TAG, "Sent UI_STATE_CHANGED broadcast to $TARGET_PACKAGE (rev=$currentRevision, mode=${bundle.getString("screenMode")}, adas=${bundle.getBoolean("adasPanelVisible")}, topBar=${bundle.getBoolean("topBarVisible")}, bottomBar=${bundle.getBoolean("bottomBarVisible")}, overlay=${bundle.getBoolean("overlayOpen")})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send UI_STATE_CHANGED broadcast", e)
        }
    }

    private fun computeStateHash(bundle: Bundle): Int {
        val mode = bundle.getString("screenMode").orEmpty()
        val pkg = bundle.getString("foregroundPackage").orEmpty()
        val overlay = bundle.getBoolean("overlayOpen")
        val adasVisible = bundle.getBoolean("adasPanelVisible")
        val topVisible = bundle.getBoolean("topBarVisible")
        val bottomVisible = bundle.getBoolean("bottomBarVisible")
        val adasBounds = bundle.getString("adasPanelBounds").orEmpty()
        val appBounds = bundle.getString("appAreaBounds").orEmpty()
        val smartAcc = bundle.getBoolean("smartAccActive")
        val cruise = bundle.getString("cruiseState").orEmpty()
        return (mode + "|" + pkg + "|" + overlay + "|" + adasVisible + "|" + topVisible + "|" + bottomVisible + "|" + adasBounds + "|" + appBounds + "|" + smartAcc + "|" + cruise).hashCode()
    }
}
