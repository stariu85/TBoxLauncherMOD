package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val TAG = "LauncherSplit"

internal fun launchSplitPreset(
    context: Context,
    preset: LauncherSplitPreset,
    apps: List<LaunchableAppEntry>,
) {
    val left = apps.firstOrNull { it.packageName == preset.leftPackage }
    val right = apps.firstOrNull { it.packageName == preset.rightPackage }
    if (left == null || right == null) {
        Toast.makeText(context, context.getString(R.string.launcher_split_preset_missing), Toast.LENGTH_SHORT).show()
        return
    }
    LauncherHistoryTracker.recordLaunch(
        context,
        LaunchedTarget.SplitPreset(
            presetId = preset.id,
            leftPackage = preset.leftPackage,
            rightPackage = preset.rightPackage,
            leftRatio = preset.leftRatio,
        ),
    )
    launchSplitApps(context, left, right, preset.leftRatio, recordHistory = false)
}

internal fun launchSplitApps(
    context: Context,
    left: LaunchableAppEntry,
    right: LaunchableAppEntry,
    leftRatio: Float = 0.5f,
    recordHistory: Boolean = true,
) {
    LauncherRightPanelOverlayWindow.hide()
    if (!isFreeformEnabled(context)) {
        LauncherNavLogger.logNavError("SPLIT_LAUNCH", "launchSplitApps", "Freeform mode is disabled in system settings")
        Toast.makeText(context, context.getString(R.string.launcher_split_freeform_required), Toast.LENGTH_LONG).show()
        return
    }
    val base = LauncherEmbeddedBoundsState.splitBounds() ?: run {
        LauncherNavLogger.logNavError("SPLIT_LAUNCH", "launchSplitApps", "Split bounds unavailable")
        Toast.makeText(context, context.getString(R.string.launcher_split_bounds_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val ratio = leftRatio.coerceIn(0.2f, 0.8f)
    val panes = LauncherEmbeddedBoundsState.splitPaneBounds(ratio) ?: run {
        LauncherNavLogger.logNavError("SPLIT_LAUNCH", "launchSplitApps", "Split pane bounds calculation failed for ratio=$ratio")
        Toast.makeText(context, context.getString(R.string.launcher_split_bounds_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val (leftBounds, rightBounds) = panes

    val leftIntent = resolveLaunchIntent(context, left.packageName)
        ?: left.activityName?.let { activity ->
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(left.packageName, activity)
            }
        }
    val rightIntent = resolveLaunchIntent(context, right.packageName)
        ?: right.activityName?.let { activity ->
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(right.packageName, activity)
            }
        }
    if (leftIntent == null || rightIntent == null) {
        LauncherNavLogger.logNavError(
            action = "SPLIT_LAUNCH",
            source = "launchSplitApps",
            reason = "Failed resolving launch intent leftPkg=${left.packageName} (ok=${leftIntent != null}), rightPkg=${right.packageName} (ok=${rightIntent != null})",
        )
        Toast.makeText(context, context.getString(R.string.launcher_split_resolve_failed), Toast.LENGTH_SHORT).show()
        return
    }

    if (recordHistory) {
        LauncherHistoryTracker.recordLaunch(
            context,
            LaunchedTarget.SplitPreset(
                presetId = null,
                leftPackage = left.packageName,
                rightPackage = right.packageName,
                leftRatio = ratio,
            ),
        )
    }

    Log.w(TAG, "split bounds base=$base left=$leftBounds right=$rightBounds ratio=$ratio")
    LauncherNavLogger.logNavAction(
        action = "SPLIT_LAUNCH",
        source = "launchSplitApps",
        decision = "Launching split preset left=${left.packageName} bounds=$leftBounds, right=${right.packageName} bounds=$rightBounds, ratio=$ratio",
    )

    val leftOk = tryLaunchIntentInBounds(context, left.packageName, leftIntent, leftBounds, launchAdjacent = true)
    if (!leftOk) {
        LauncherNavLogger.logNavError("SPLIT_LAUNCH", "launchSplitApps", "Failed launching left split app pkg=${left.packageName}")
        Toast.makeText(context, context.getString(R.string.launcher_split_left_failed), Toast.LENGTH_SHORT).show()
        return
    }
    Handler(Looper.getMainLooper()).postDelayed({
        val rightOk = tryLaunchIntentInBounds(context, right.packageName, rightIntent, rightBounds, launchAdjacent = true)
        if (!rightOk) {
            LauncherNavLogger.logNavError("SPLIT_LAUNCH", "launchSplitApps", "Failed launching right split app pkg=${right.packageName}")
            Toast.makeText(context, context.getString(R.string.launcher_split_right_failed), Toast.LENGTH_SHORT).show()
        }
    }, 150L)
}
