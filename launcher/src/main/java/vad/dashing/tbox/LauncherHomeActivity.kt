package vad.dashing.tbox

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import vad.dashing.tbox.api.DclUiStateNotifier
import vad.dashing.tbox.ui.disposeAppLauncherPickerIconCache
import vad.dashing.tbox.ui.launcher.LauncherAboutOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherAccessibilityHelper
import vad.dashing.tbox.ui.launcher.LauncherAppDrawerWindow
import vad.dashing.tbox.ui.launcher.LauncherAppListVersion
import vad.dashing.tbox.ui.launcher.LauncherAppPickerOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherEdgeGestureOverlay
import vad.dashing.tbox.ui.launcher.LauncherOverlayBar
import vad.dashing.tbox.ui.launcher.LauncherOverlayElevator
import vad.dashing.tbox.ui.launcher.LauncherRightPanelOverlayWindow
import vad.dashing.tbox.ui.launcher.PassengerSeatOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherThemeState
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherVehicleStatusOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherWifiApRepository
import android.view.ViewTreeObserver
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.TeslaLauncherScreen
import vad.dashing.tbox.ui.launcher.ensureFreeformImmersivePolicy
import vad.dashing.tbox.ui.launcher.findForeignAppTask
import vad.dashing.tbox.ui.launcher.goLauncherHome
import vad.dashing.tbox.ui.launcher.launchAutostartShortcut
import vad.dashing.tbox.ui.launcher.minimizeForeignFreeformTasks
import vad.dashing.tbox.ui.launcher.startDclwmStorageWatcher

/**
 * Android HOME handler — Tesla-style launcher surface for the head unit.
 */
class LauncherHomeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LauncherHome"
        private var autostartFired = false
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var appDataManager: AppDataManager
    private var firstDrawComplete = false
    private var stagedWarmupStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherHomeActivityHolder.instance = this
        Log.w("LauncherAppLaunch", "LauncherHomeActivity onCreate")
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onCreate intent=$intent savedInstanceState=${savedInstanceState != null}")

        applyLauncherWindowFlags()
        settingsManager = SettingsManager(this)
        appDataManager = AppDataManager(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vad.dashing.tbox.ui.launcher.LauncherCar3DModelTrigger.notifyBackPressed()
                vad.dashing.tbox.ui.launcher.goLauncherBack(this@LauncherHomeActivity, source = "onBackPressedDispatcher")
            }
        })

        // 1. Initial minimal HOME frame: immediate background & UI composition
        setContent {
            DisposableEffect(Unit) {
                onDispose { disposeAppLauncherPickerIconCache() }
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                TeslaLauncherScreen(
                    settingsManager = settingsManager,
                    appDataManager = appDataManager,
                    onTboxRestart = { rebootTBox() },
                    onTripFinishAndStart = {
                        serviceCommand(BackgroundService.ACTION_TRIP_FINISH_AND_START, "", "")
                    },
                )
            }
        }

        // 2. Defer all heavy initialization until after the first frame is rendered
        setupFirstDrawAndStagedWarmup()
    }

    private fun setupFirstDrawAndStagedWarmup() {
        val decorView = window.decorView
        decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                decorView.viewTreeObserver.removeOnPreDrawListener(this)
                decorView.post {
                    onFirstFrameDrawn()
                }
                return true
            }
        })
    }

    private fun onFirstFrameDrawn() {
        if (firstDrawComplete || isFinishing || isDestroyed) return
        firstDrawComplete = true
        LauncherHomeActivityHolder.isFirstDrawComplete = true
        Log.i(TAG, "First HOME frame drawn -> calling reportFullyDrawn and scheduling staged warmup")
        vad.dashing.tbox.DclLogRecorder.log("UI_FRAME", "First HOME frame drawn -> reportFullyDrawn called")
        runCatching { reportFullyDrawn() }

        if (!stagedWarmupStarted) {
            scheduleStagedWarmup()
        }
    }

    private fun scheduleStagedWarmup() {
        stagedWarmupStarted = true
        lifecycleScope.launch(Dispatchers.Main) {
            val appCtx = applicationContext

            // --- Stage 1: Freeform policy, a11y nav back, background service, overlays ---
            vad.dashing.tbox.DclLogRecorder.log("WARMUP", "Stage 1 start: overlays, a11y, bg service")
            ensureFreeformImmersivePolicy(this@LauncherHomeActivity)
            val a11y = LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this@LauncherHomeActivity)
            Log.w(TAG, "nav-back a11y enabled=$a11y connected=${vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService.isConnected()}")
            LauncherAppListVersion.ensurePackageChangeReceiver(appCtx)
            startBackgroundService()

            if (findForeignAppTask(this@LauncherHomeActivity) == null) {
                LauncherRightPanelOverlayWindow.ensureShowing(this@LauncherHomeActivity)
            }
            delay(80L)
            LauncherOverlayBar.ensureShowing(this@LauncherHomeActivity)
            delay(80L)
            LauncherEdgeGestureOverlay.ensureShowing(this@LauncherHomeActivity)
            DclUiStateNotifier.notifyUiStateChanged(this@LauncherHomeActivity)

            // --- Stage 2: Heavy subsystems, mbCAN & caches (+400ms after Stage 1) ---
            delay(400L)
            vad.dashing.tbox.DclLogRecorder.log("WARMUP", "Stage 2 start: caches, UniversalCanRepository auto-bind")
            LauncherThemeState.init(appCtx)
            LauncherWifiApRepository.init(appCtx)
            vad.dashing.tbox.ui.launcher.LauncherAvmRepository.startObserving(appCtx)

            // Start CAN auto-bind now that desktop and UI thread are completely unblocked
            UniversalCanRepository.triggerStartupCanAutoBind(settingsManager, this)

            // --- Stage 3: Autostart shortcut, dclwm storage watcher & foreign stack cleanup ---
            delay(1000L)
            vad.dashing.tbox.DclLogRecorder.log("WARMUP", "Stage 3 start: autostart shortcut, dclwm storage watcher")
            if (!autostartFired && LauncherHomeActivityHolder.isResumed && !isFinishing && !isDestroyed) {
                autostartFired = true
                Log.i(TAG, "Triggering autostart shortcut with Activity context after desktop is fully ready")
                launchAutostartShortcut(this@LauncherHomeActivity)
            }

            // Launcher fully loaded -> Start cyclic storage check for dclwm (every 10s until ready)
            startDclwmStorageWatcher(appCtx)
            vad.dashing.tbox.ui.launcher.LauncherHistoryTracker.clearHistory(appCtx)
            minimizeForeignFreeformTasks(appCtx)
            vad.dashing.tbox.DclLogRecorder.log("WARMUP", "scheduleStagedWarmup completed")

            // Defer auto-backup until 15s after startup so startup I/O remains unburdened
            lifecycleScope.launch(Dispatchers.IO) {
                delay(15_000L)
                vad.dashing.tbox.DclLogRecorder.log("WARMUP", "Deferred auto backup start")
                vad.dashing.tbox.backup.LauncherBackupManager.performAutoBackupIfEnabled(appCtx)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        LauncherHomeActivityHolder.instance = this
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onRestart")
        if (firstDrawComplete) {
            LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this)
            startBackgroundService()
            if (findForeignAppTask(this) == null) {
                LauncherRightPanelOverlayWindow.ensureShowing(this)
            }
            LauncherOverlayBar.ensureShowing(this)
        }
    }

    override fun onStart() {
        super.onStart()
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onStart topTasks=${getTopTasksInfo(this)}")
    }

    override fun onResume() {
        super.onResume()
        LauncherHomeActivityHolder.isResumed = true
        DclLogRecorder.attachWindow(window)
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onResume")
        if (firstDrawComplete) {
            LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this)
            val foreign = findForeignAppTask(this)
            val inAppView = foreign != null && !LauncherRightPanelOverlayWindow.isShowing()
            if (inAppView) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                window.decorView.isFocusable = true
                window.decorView.isFocusableInTouchMode = true
                window.decorView.requestFocus()
                LauncherForegroundHandoff.restoreLauncherWindow()
                window.decorView.visibility = View.VISIBLE
            }
            if (foreign == null) {
                LauncherRightPanelOverlayWindow.ensureShowing(this)
            }
            LauncherOverlayBar.ensureShowing(this)
            LauncherEdgeGestureOverlay.ensureShowing(this)
            DclUiStateNotifier.notifyUiStateChanged(this)
        }
    }

    override fun onPause() {
        LauncherHomeActivityHolder.isResumed = false
        DclLogRecorder.detachWindow(window)
        val topTasks = getTopTasksInfo(this)
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onPause isFinishing=$isFinishing topTasks=$topTasks")
        super.onPause()
    }

    override fun onStop() {
        val topTasks = getTopTasksInfo(this)
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onStop isFinishing=$isFinishing topTasks=$topTasks")
        super.onStop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onDetachedFromWindow")
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val tasks = getTopTasksInfo(this)
        vad.dashing.tbox.DclLogRecorder.logUiFocus(hasFocus, "topTasks=$tasks")
        Log.i(TAG, "onWindowFocusChanged hasFocus=$hasFocus topTasks=$tasks")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        vad.dashing.tbox.DclLogRecorder.log("SYS_MEMORY", "LauncherHomeActivity onTrimMemory level=$level")
    }

    @SuppressLint("BackPressed", "GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall", "DEPRECATION", "OVERRIDE_DEPRECATION", "GestureBackNavigation")
    override fun onBackPressed() {
        vad.dashing.tbox.ui.launcher.LauncherCar3DModelTrigger.notifyBackPressed()
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            onBackPressedDispatcher.onBackPressed()
        } else {
            vad.dashing.tbox.ui.launcher.goLauncherBack(this, source = "activity_on_back_pressed")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vad.dashing.tbox.DclLogRecorder.log(
            "UI_LIFECYCLE",
            "LauncherHomeActivity onNewIntent action=${intent.action} categories=${intent.categories} flags=0x${Integer.toHexString(intent.flags)}",
        )
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            Log.w(TAG, "onNewIntent HOME")
            LauncherAppDrawerWindow.hide()
            LauncherForegroundHandoff.restoreLauncherWindow()
            goLauncherHome(this, fromHomeIntent = true, source = "onNewIntent")
        }
    }

    override fun onDestroy() {
        vad.dashing.tbox.DclLogRecorder.log("UI_LIFECYCLE", "LauncherHomeActivity onDestroy isFinishing=$isFinishing topTasks=${getTopTasksInfo(this)}")
        LauncherHomeActivityHolder.isFirstDrawComplete = false
        LauncherRightPanelOverlayWindow.destroy()
        LauncherAppDrawerWindow.hide()
        LauncherAppPickerOverlayWindow.hide()
        LauncherVehicleSettingsOverlayWindow.hide()
        LauncherVehicleStatusOverlayWindow.hide()
        LauncherAboutOverlayWindow.hide()
        PassengerSeatOverlayWindow.hide()
        LauncherOverlayBar.hide()
        runCatching { vad.dashing.tbox.camd.CamdCameraRepository.closeAllIfOpen(this) }
        runCatching { minimizeForeignFreeformTasks(this) }
        LauncherOverlayElevator.reset()
        if (LauncherHomeActivityHolder.instance === this) {
            LauncherHomeActivityHolder.instance = null
        }
        super.onDestroy()
    }

    private fun getTopTasksInfo(context: Context): String {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "am_null"
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(5) ?: return "tasks_null"
            tasks.joinToString("; ") { task ->
                "task[id=${task.id}, top=${task.topActivity?.packageName}/${task.topActivity?.shortClassName}, base=${task.baseActivity?.packageName}]"
            }
        }.getOrDefault("getRunningTasks_err")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLauncherWindowFlags()
    }

    private fun applyLauncherWindowFlags() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background service", e)
        }
    }

    private fun serviceCommand(sendAction: String, extraName: String, extraValue: String) {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = sendAction
            if (extraName.isNotEmpty() && extraValue.isNotEmpty()) {
                putExtra(extraName, extraValue)
            }
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Service command failed", e)
        }
    }

    private fun rebootTBox() {
        serviceCommand(BackgroundService.ACTION_TBOX_REBOOT, "", "")
    }
}
