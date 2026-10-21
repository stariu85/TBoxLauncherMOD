package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Rect
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.DiagFileLog
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.MainActivity

/**
 * Unified logger for navigation actions (Home, Back, Prev App, Close Apps) and
 * application launches, positions, and layers.
 *
 * Automatically records detailed events into both [DiagFileLog] (persistent log file + Logcat)
 * and [DclLogRecorder] (DCL session log files when active).
 */
object LauncherNavLogger {
    private const val TAG = "LauncherNav"

    /**
     * Log a navigation button / gesture action (Home, Back, Previous App, Close Apps).
     */
    fun logNavAction(
        action: String,
        source: String,
        stateBefore: String? = null,
        decision: String? = null,
        result: String? = null,
    ) {
        val msg = buildString {
            append("NAV_ACTION action=").append(action)
            append(" source=").append(source)
            if (!stateBefore.isNullOrBlank()) append(" stateBefore=[").append(stateBefore).append("]")
            if (!decision.isNullOrBlank()) append(" decision=").append(decision)
            if (!result.isNullOrBlank()) append(" result=").append(result)
        }
        DiagFileLog.i(TAG, msg)
        DclLogRecorder.log("NavAction", msg)
    }

    /**
     * Log an error or failure during a navigation button / gesture action.
     */
    fun logNavError(
        action: String,
        source: String,
        reason: String,
        error: Throwable? = null,
        state: String? = null,
    ) {
        val msg = buildString {
            append("NAV_ERROR action=").append(action)
            append(" source=").append(source)
            append(" reason=").append(reason)
            if (!state.isNullOrBlank()) append(" state=[").append(state).append("]")
            if (error != null) append(" error=").append(error.message)
        }
        DiagFileLog.e(TAG, msg, error)
        DclLogRecorder.log("NavActionError", msg)
    }

    /**
     * Log detailed application launch parameters, positions, and windowing layers.
     */
    fun logAppLaunch(
        packageName: String,
        component: String?,
        intentAction: String?,
        intentFlags: String?,
        mode: String,
        bounds: Rect?,
        windowingMode: Int? = null,
        taskId: Int? = null,
        strategy: String? = null,
        success: Boolean,
        error: Throwable? = null,
    ) {
        val msg = buildString {
            append("APP_LAUNCH pkg=").append(packageName)
            if (!component.isNullOrBlank()) append(" comp=").append(component)
            if (!intentAction.isNullOrBlank()) append(" action=").append(intentAction)
            if (!intentFlags.isNullOrBlank()) append(" flags=").append(intentFlags)
            append(" mode=").append(mode)
            if (bounds != null) {
                append(" bounds=").append(bounds.toShortString())
                    .append(" size=").append(bounds.width()).append("x").append(bounds.height())
            }
            if (windowingMode != null) append(" winMode=").append(windowingMode)
            if (taskId != null) append(" taskId=").append(taskId)
            if (!strategy.isNullOrBlank()) append(" strategy=").append(strategy)
            append(" success=").append(success)
            if (error != null) append(" error=").append(error.message)
        }
        if (success) {
            DiagFileLog.i("AppLaunch", msg)
        } else {
            DiagFileLog.e("AppLaunch", msg, error)
        }
        DclLogRecorder.log("AppLaunch", msg)
    }

    /**
     * Capture a snapshot string of current overlay states and foreign app tasks.
     */
    fun captureCurrentState(context: Context): String {
        return runCatching {
            val homeResumed = LauncherHomeActivityHolder.isResumed
            val vehicleSettings = LauncherVehicleSettingsUiState.open ||
                LauncherVehicleSettingsOverlayWindow.isShowing() ||
                LauncherVehicleSettingsActivity.isOpen()
            val mainActivity = MainActivity.isOpen()
            val about = LauncherAboutOverlayWindow.isShowing()
            val drawer = LauncherAppDrawerWindow.isShowing()
            val picker = LauncherAppPickerOverlayWindow.isShowing()
            val rightPanel = LauncherRightPanelOverlayWindow.isShowing()

            val openOverlays = mutableListOf<String>()
            if (vehicleSettings) openOverlays += "VehicleSettings"
            if (mainActivity) openOverlays += "MainActivity"
            if (about) openOverlays += "AboutOverlay"
            if (drawer) openOverlays += "AppDrawer"
            if (picker) openOverlays += "AppPicker"
            if (rightPanel) openOverlays += "RightPanelOverlay"

            val foreignTask = findForeignAppTask(context)
            val foreignSummary = if (foreignTask != null) {
                "foreign[pkg=${foreignTask.packageName}, taskId=${foreignTask.taskId}, top=${foreignTask.topActivityClass}]"
            } else {
                "foreign[none]"
            }

            buildString {
                append("homeResumed=").append(homeResumed)
                append(", overlays=").append(if (openOverlays.isEmpty()) "none" else openOverlays.joinToString("+"))
                append(", ").append(foreignSummary)
            }
        }.getOrDefault("state_capture_failed")
    }
}
