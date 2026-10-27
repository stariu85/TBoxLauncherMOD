package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import vad.dashing.tbox.LauncherForegroundHandoff
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherVehicleSettingsActivity
import vad.dashing.tbox.LauncherWindowState
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
    LauncherCar3DModelTrigger.notifyHomePressed()

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

    val home = LauncherHomeActivityHolder.instance
    if (home != null) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { home.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) }
        } else {
            home.runOnUiThread {
                runCatching { home.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) }
            }
        }
    }

    val hasForeignApp = hasActiveForeignApp(context)
    runCatching { minimizeForeignFreeformTasks(context) }
    LauncherActiveAppTracker.onHomePressed()

    runCatching { LauncherForegroundHandoff.restoreLauncherWindow() }
    runCatching { LauncherOverlayElevator.clearHoldWithoutRestore() }
    runCatching { LauncherOverlayElevator.bringLauncherToFront(context) }

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

    if (!fromHomeIntent) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }.onFailure {
            Log.w(TAG, "goLauncherHome: startActivity failed", it)
            LauncherNavLogger.logNavError("HOME", source, "startActivity(CATEGORY_HOME) failed", it, stateBefore)
        }
        LauncherNavAccessibilityService.dispatchGlobalHome()
    }

    LauncherRightPanelOverlayWindow.hide()

    LauncherNavLogger.logNavAction(
        action = "HOME",
        source = source,
        stateBefore = stateBefore,
        decision = "closedOverlays=[${closedList.joinToString()}], settingsWereOpen=$settingsWereOpen, broughtHomeToFront=$broughtToFront, fromHomeIntent=$fromHomeIntent",
        result = "hasForeignApp=$hasForeignApp",
    )
}

/**
 * Back action: complete analog of the physical Back button on the steering wheel.
 * Delivers a system BACK (GLOBAL_ACTION_BACK / KEYCODE_BACK) directly into the currently focused window.
 */
@Suppress("UNUSED_PARAMETER")
internal fun goLauncherBack(
    context: Context,
    vehicleSettingsOpen: Boolean = false,
    onCloseVehicleSettings: () -> Unit = {},
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

    if (findForeignAppTask(context) == null) {
        Log.w(TAG, "goLauncherBack: on root desktop — BACK ignored")
        LauncherNavLogger.logNavAction("BACK", source, stateBefore, decision = "Ignored on root desktop")
        return
    }

    Log.w(TAG, "goLauncherBack: dispatching physical Back source=$source")
    LauncherNavLogger.logNavAction(
        action = "BACK",
        source = source,
        stateBefore = stateBefore,
        decision = "Dispatching physical Back into active window",
    )
    dispatchPhysicalBack(source)
}

/**
 * Same path as Back Button Pro: accessibility GLOBAL_ACTION_BACK when the service
 * is connected; otherwise InputManager inject (scrcpy/HW-style flags).
 */
internal fun dispatchPhysicalBack(source: String = "unknown") {
    val home = LauncherHomeActivityHolder.instance
    if (home != null && LauncherHomeActivityHolder.isResumed && findForeignAppTask(home) == null) {
        val hasOverlays = LauncherVehicleSettingsOverlayWindow.isShowing() ||
            LauncherVehicleSettingsActivity.isOpen() ||
            MainActivity.isOpen() ||
            LauncherAboutOverlayWindow.isShowing() ||
            LauncherAppDrawerWindow.isShowing() ||
            LauncherAppPickerOverlayWindow.isShowing()
        if (!hasOverlays) {
            Log.w(TAG, "dispatchPhysicalBack: ignored on root desktop source=$source")
            LauncherNavLogger.logNavAction("BACK_DELIVER", source, result = "Ignored on root desktop")
            return
        }
    }
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
    if (LauncherHomeActivityHolder.isResumed && !LauncherWindowState.hiddenForExternalApp) {
        return null
    }

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
