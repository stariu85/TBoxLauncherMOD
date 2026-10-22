package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import vad.dashing.tbox.LauncherForegroundHandoff
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LauncherNav"
private const val HOME_DEBOUNCE_MS = 200L

@Volatile
private var lastHomeAtMs = 0L

/** Prevents Compose BackHandler ↔ inject KEYCODE_BACK recursion on the launcher. */
private val backDispatchInProgress = AtomicBoolean(false)

/**
 * Home: close overlays, dismiss freeform windows, bring launcher to front.
 *
 * [fromHomeIntent]=true when already handling ACTION_MAIN+HOME (onNewIntent).
 * In that case we must NOT startActivity(HOME) again — that re-enters onNewIntent
 * and freezes the UI in a tight loop.
 */
internal fun goLauncherHome(
    context: Context,
    onCloseOverlays: () -> Unit = {},
    fromHomeIntent: Boolean = false,
    source: String = "unknown",
) {
    val stateBefore = LauncherNavLogger.captureCurrentState(context)
    val now = SystemClock.uptimeMillis()
    if (now - lastHomeAtMs < HOME_DEBOUNCE_MS) {
        Log.w(TAG, "goLauncherHome: debounce skip fromHomeIntent=$fromHomeIntent source=$source")
        LauncherNavLogger.logNavAction(
            action = "HOME",
            source = source,
            stateBefore = stateBefore,
            decision = "Skipped due to debounce (<${HOME_DEBOUNCE_MS}ms)",
        )
        return
    }
    lastHomeAtMs = now
    Log.w(TAG, "goLauncherHome fromHomeIntent=$fromHomeIntent source=$source")

    val settingsWereOpen = LauncherVehicleSettingsUiState.open ||
        LauncherVehicleSettingsOverlayWindow.isShowing() ||
        LauncherVehicleSettingsActivity.isOpen() ||
        MainActivity.isOpen() ||
        LauncherAboutOverlayWindow.isShowing() ||
        LauncherAppDrawerWindow.isShowing() ||
        LauncherAppPickerOverlayWindow.isShowing()

    val closedList = mutableListOf<String>()
    runCatching {
        onCloseOverlays()
        closedList += "CustomOverlays"
    }
    runCatching {
        closeVehicleSettingsOverlay()
        closedList += "VehicleSettings"
    }
    runCatching {
        if (LauncherAboutOverlayWindow.isShowing()) {
            LauncherAboutOverlayWindow.hide()
            closedList += "AboutOverlay"
        }
    }
    runCatching {
        if (LauncherAppDrawerWindow.isShowing()) {
            LauncherAppDrawerWindow.hide()
            closedList += "AppDrawer"
        }
    }
    runCatching {
        if (LauncherAppPickerOverlayWindow.isShowing()) {
            LauncherAppPickerOverlayWindow.hide()
            closedList += "AppPicker"
        }
    }
    runCatching {
        if (MainActivity.isOpen()) {
            MainActivity.finishIfOpen()
            closedList += "MainActivity"
        }
    }

    val hasForeignApp = hasActiveForeignApp(context)

    LauncherActiveAppTracker.onHomePressed()

    runCatching { LauncherForegroundHandoff.restoreLauncherWindow() }
    runCatching { LauncherOverlayElevator.clearHoldWithoutRestore() }
    runCatching { LauncherOverlayElevator.bringLauncherToFront(context) }

    // Bring Launcher Home activity to front immediately on Main Thread
    val home = LauncherHomeActivityHolder.instance
    var broughtToFront = false
    if (home != null && !home.isFinishing && !home.isDestroyed) {
        runCatching { home.window.decorView.visibility = View.VISIBLE }
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (home.taskId > 0) {
                am.moveTaskToFront(home.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
                broughtToFront = true
            }
            Log.w(TAG, "goLauncherHome: brought to front task=${home.taskId}")
        }.onFailure {
            LauncherNavLogger.logNavError("HOME", source, "Failed moveTaskToFront for Home taskId=${home.taskId}", it, stateBefore)
        }
    }

    if (!fromHomeIntent && !hasForeignApp) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }.onFailure {
            Log.w(TAG, "goLauncherHome: startActivity failed", it)
            LauncherNavLogger.logNavError("HOME", source, "startActivity(CATEGORY_HOME) failed", it, stateBefore)
        }
    }

    // Show Primary Right Panel Desktop Overlay IMMEDIATELY (0ms delay)
    LauncherRightPanelOverlayWindow.show(context)

    LauncherNavLogger.logNavAction(
        action = "HOME",
        source = source,
        stateBefore = stateBefore,
        decision = "closedOverlays=[${closedList.joinToString()}], settingsWereOpen=$settingsWereOpen, broughtHomeToFront=$broughtToFront, fromHomeIntent=$fromHomeIntent",
        result = "hasForeignApp=$hasForeignApp, RightPanelOverlay=${if (hasForeignApp) "shown" else "hidden"}",
    )
}

/**
 * Back: close launcher overlays first; else send KEYCODE_BACK into freeform
 * only when the task has a deeper stack (numActivities > 1).
 *
 * On the app root — do nothing (keep the freeform window). Never relaunch.
 * Closing freeform is Home only.
 *
 * Back Button Pro does the same delivery via AccessibilityService.performGlobalAction
 * (GLOBAL_ACTION_BACK); we mirror that, with InputManager inject as fallback.
 */
internal fun goLauncherBack(
    context: Context,
    vehicleSettingsOpen: Boolean,
    onCloseVehicleSettings: () -> Unit,
    source: String = "unknown",
) {
    val stateBefore = LauncherNavLogger.captureCurrentState(context)

    if (backDispatchInProgress.get()) {
        Log.w(TAG, "goLauncherBack: skip re-entrant (inject/BackHandler loop) source=$source")
        LauncherNavLogger.logNavAction(
            action = "BACK",
            source = source,
            stateBefore = stateBefore,
            decision = "Skipped re-entrant BACK loop",
        )
        return
    }

    when {
        LauncherRightPanelOverlayWindow.isShowing() -> {
            LauncherRightPanelOverlayWindow.hide()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed RightPanelOverlay")
            return
        }
        vehicleSettingsOpen ||
            LauncherVehicleSettingsUiState.open ||
            LauncherVehicleSettingsOverlayWindow.isShowing() ||
            LauncherVehicleSettingsActivity.isOpen() -> {
            onCloseVehicleSettings()
            closeVehicleSettingsOverlay()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed VehicleSettingsOverlay")
            return
        }
        MainActivity.isOpen() -> {
            MainActivity.finishIfOpen()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed MainActivity")
            return
        }
        LauncherAboutOverlayWindow.isShowing() -> {
            LauncherAboutOverlayWindow.hide()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed AboutOverlay")
            return
        }
        LauncherAppPickerOverlayWindow.isShowing() -> {
            LauncherAppPickerOverlayWindow.hide()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed AppPickerOverlay")
            return
        }
        LauncherDockSlotPickerOverlayWindow.isShowing() -> {
            LauncherDockSlotPickerOverlayWindow.hide()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed DockSlotPickerOverlay")
            return
        }
        LauncherAppDrawerWindow.isShowing() -> {
            LauncherAppDrawerWindow.hide()
            LauncherNavLogger.logNavAction("BACK", source, stateBefore, result = "Closed AppDrawerWindow")
            return
        }
    }

    val foreign = findForeignAppTask(context)
    if (foreign == null) {
        Log.w(TAG, "goLauncherBack: no foreign app — ignore source=$source")
        LauncherNavLogger.logNavAction(
            action = "BACK",
            source = source,
            stateBefore = stateBefore,
            decision = "No active foreign app task found in stack",
            result = "Ignored",
        )
        return
    }

    // Keep freeform on screen, but never Back-finish the root activity.
    moveTaskToFrontId(context, foreign.taskId)

    if (isTaskAtRoot(context, foreign)) {
        Log.w(
            TAG,
            "goLauncherBack: at root task=${foreign.taskId} pkg=${foreign.packageName} — keep (no Back) source=$source",
        )
        LauncherNavLogger.logNavAction(
            action = "BACK",
            source = source,
            stateBefore = stateBefore,
            decision = "Task is at root (numActivities <= 1 or top==base) for pkg=${foreign.packageName} taskId=${foreign.taskId}",
            result = "Skipped KEYCODE_BACK to keep freeform window open (prevent app finish)",
        )
        return
    }

    Log.w(TAG, "goLauncherBack: BACK task=${foreign.taskId} pkg=${foreign.packageName} source=$source")
    LauncherNavLogger.logNavAction(
        action = "BACK",
        source = source,
        stateBefore = stateBefore,
        decision = "Dispatching BACK into foreign task=${foreign.taskId} pkg=${foreign.packageName} top=${foreign.topActivityClass}",
    )
    dispatchPhysicalBackDelayed(context, foreign, source)
}

/**
 * Focus freeform, then deliver Back. No relaunch if the app finishes itself.
 */
private fun dispatchPhysicalBackDelayed(context: Context, foreign: ForeignAppTask, source: String) {
    val appCtx = context.applicationContext
    if (!backDispatchInProgress.compareAndSet(false, true)) {
        Log.w(TAG, "dispatchPhysicalBack: already in progress")
        return
    }
    moveTaskToFrontId(context, foreign.taskId)
    Thread({
        try {
            runCatching { Thread.sleep(180L) }
            // Re-check: stack may already be at root after focus settle.
            if (isTaskAtRoot(appCtx, foreign)) {
                Log.w(TAG, "BACK skip after settle: at root pkg=${foreign.packageName}")
                LauncherNavLogger.logNavAction(
                    action = "BACK_DELAYED",
                    source = source,
                    decision = "Skipped after focus settle: task reached root for pkg=${foreign.packageName}",
                )
                return@Thread
            }
            moveTaskToFrontId(appCtx, foreign.taskId)
            runCatching { Thread.sleep(60L) }
            dispatchPhysicalBack(source)
        } finally {
            backDispatchInProgress.set(false)
        }
    }, "launcher-back").apply { isDaemon = true }.start()
}

/** True when Back would finish the task (single activity / top == base). */
internal fun isTaskAtRoot(context: Context, foreign: ForeignAppTask): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
    @Suppress("DEPRECATION")
    val task = runCatching {
        am.getRunningTasks(64)?.firstOrNull { it.id == foreign.taskId }
    }.getOrNull()
    if (task != null) {
        val num = task.numActivities
        val top = task.topActivity
        val base = task.baseActivity
        val atRoot = num <= 1 || (top != null && base != null && top == base)
        Log.w(TAG, "isTaskAtRoot task=${foreign.taskId} num=$num top=$top base=$base -> $atRoot")
        return atRoot
    }
    val launch = context.packageManager.getLaunchIntentForPackage(foreign.packageName)
    val launchCls = launch?.component?.className
    val topCls = foreign.topActivityClass
    val atRoot = launchCls != null && topCls != null &&
        (launchCls == topCls || launchCls.endsWith(topCls) || topCls.endsWith(launchCls))
    Log.w(TAG, "isTaskAtRoot fallback pkg=${foreign.packageName} launch=$launchCls top=$topCls -> $atRoot")
    return atRoot
}

/**
 * Same path as Back Button Pro: accessibility GLOBAL_ACTION_BACK when the service
 * is connected; otherwise InputManager inject (scrcpy/HW-style flags).
 */
internal fun dispatchPhysicalBack(source: String = "unknown") {
    if (!LauncherNavAccessibilityService.isConnected()) {
        LauncherHomeActivityHolder.instance?.let {
            LauncherAccessibilityHelper.ensureNavBackServiceEnabled(it)
        }
    }
    if (LauncherNavAccessibilityService.dispatchGlobalBack()) {
        Log.w(TAG, "BACK via accessibility global action source=$source")
        LauncherNavLogger.logNavAction("BACK_DELIVER", source, result = "Dispatched via AccessibilityService GLOBAL_ACTION_BACK")
        return
    }
    if (injectKeyEvent(KeyEvent.KEYCODE_BACK)) {
        Log.w(TAG, "BACK via InputManager inject source=$source")
        LauncherNavLogger.logNavAction("BACK_DELIVER", source, result = "Dispatched via InputManager KEYCODE_BACK inject")
        return
    }
    Log.e(TAG, "BACK FAILED: could not deliver KEYCODE_BACK source=$source")
    LauncherNavLogger.logNavError(
        action = "BACK_DELIVER",
        source = source,
        reason = "Failed to deliver KEYCODE_BACK: AccessibilityService not connected and InputManager inject failed",
    )
}

internal fun injectKeyEvent(keyCode: Int): Boolean {
    return runCatching {
        val imClass = InputManager::class.java
        val im = imClass.getDeclaredMethod("getInstance").invoke(null)
        val inject = imClass.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY
        val down = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD,
        )
        val up = KeyEvent(
            now + 10, now + 10, KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD,
        )
        val modeAsync = 0
        val downOk = inject.invoke(im, down, modeAsync) as? Boolean ?: false
        val upOk = inject.invoke(im, up, modeAsync) as? Boolean ?: false
        Log.w(TAG, "injectKeyEvent keyCode=$keyCode down=$downOk up=$upOk")
        downOk && upOk
    }.onFailure {
        Log.w(TAG, "injectKeyEvent keyCode=$keyCode failed", it)
    }.getOrDefault(false)
}

internal data class ForeignAppTask(
    val taskId: Int,
    val packageName: String,
    val topActivityClass: String?,
)

internal fun hasActiveForeignApp(context: Context): Boolean {
    return findForeignAppTask(context) != null
}

internal fun isSplitActiveOnScreen(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
    return FreeformLaunchRegistry.snapshot().size >= 2
}

internal fun findForeignAppTask(context: Context): ForeignAppTask? {
    val activeTask = LauncherActiveAppTracker.getActiveForeignTask()
    if (activeTask != null) return activeTask

    val trackedPackages = FreeformLaunchRegistry.snapshot()
    val lastActive = LauncherHistoryTracker.lastActivePackage()
    val fallbackPkg = if (lastActive != null && lastActive in trackedPackages) {
        lastActive
    } else {
        trackedPackages.firstOrNull()
    }

    if (fallbackPkg != null) {
        return ForeignAppTask(-1, fallbackPkg, null)
    }

    return null
}
