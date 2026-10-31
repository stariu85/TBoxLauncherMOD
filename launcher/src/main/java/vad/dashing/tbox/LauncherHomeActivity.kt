package vad.dashing.tbox

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import vad.dashing.tbox.ui.disposeAppLauncherPickerIconCache
import vad.dashing.tbox.ui.launcher.LauncherAboutOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherAccessibilityHelper
import vad.dashing.tbox.ui.launcher.LauncherAppDrawerWindow
import vad.dashing.tbox.ui.launcher.LauncherAppListVersion
import vad.dashing.tbox.ui.launcher.LauncherAppPickerOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherCarModelCache
import vad.dashing.tbox.ui.launcher.LauncherEdgeGestureOverlay
import vad.dashing.tbox.ui.launcher.LauncherOverlayBar
import vad.dashing.tbox.ui.launcher.LauncherOverlayElevator
import vad.dashing.tbox.ui.launcher.LauncherRightPanelOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherThemeState
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherWifiApRepository
import vad.dashing.tbox.ui.launcher.TeslaLauncherScreen
import vad.dashing.tbox.ui.launcher.ensureFreeformImmersivePolicy
import vad.dashing.tbox.ui.launcher.findForeignAppTask
import vad.dashing.tbox.ui.launcher.goLauncherHome
import vad.dashing.tbox.ui.launcher.launchAutostartShortcut
import vad.dashing.tbox.ui.launcher.minimizeForeignFreeformTasks

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherHomeActivityHolder.instance = this
        Log.w("LauncherAppLaunch", "LauncherHomeActivity onCreate")
        // ActivityViewCapabilityProbe: construct+VD OK; startActivity on launchDisplayId still denied
        // even with MANAGE_ACTIVITY_STACKS+ACTIVITY_EMBEDDING granted. Needs INJECT_EVENTS (signature).
        applyLauncherWindowFlags()
        ensureFreeformImmersivePolicy(this)
        // Dock Back = accessibility GLOBAL_ACTION_BACK (no INJECT_EVENTS on user builds).
        val a11y = LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this)
        Log.w(TAG, "nav-back a11y enabled=$a11y connected=${vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService.isConnected()}")
        LauncherAppListVersion.ensurePackageChangeReceiver(applicationContext)
        // After reinstall / process death OEM freeform stacks often survive alone on top.
        // Delay slightly so WM finishes enumerating stacks post-install.
        val appCtx = applicationContext
        vad.dashing.tbox.ui.launcher.LauncherHistoryTracker.clearHistory(appCtx)
        window.decorView.postDelayed({ minimizeForeignFreeformTasks(appCtx) }, 400L)

        settingsManager = SettingsManager(this)
        appDataManager = AppDataManager(this)

        // SoftAP params are broadcast into mbCAN only for ~10–30 s after boot — poll eagerly.
        LauncherWifiApRepository.init(appCtx)
        LauncherThemeState.init(appCtx)
        LauncherCarModelCache.preload(appCtx)
        vad.dashing.tbox.ui.launcher.LauncherAvmRepository.startObserving(appCtx)
        // Home-dock autostart shortcut (long-tap icon). Once per process start; delayed so
        // freeform machinery and the 400ms stack cleanup above settle first.
        if (!autostartFired) {
            autostartFired = true
            window.decorView.postDelayed({ launchAutostartShortcut(appCtx) }, 1600L)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            delay(2000L)
            vad.dashing.tbox.backup.LauncherBackupManager.performAutoBackupIfEnabled(appCtx)
        }

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
        startBackgroundService()
        if (findForeignAppTask(this) == null) {
            LauncherRightPanelOverlayWindow.ensureShowing(this)
        }
        LauncherOverlayBar.ensureShowing(this)
    }

    override fun onRestart() {
        super.onRestart()
        LauncherHomeActivityHolder.instance = this
        LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this)
        startBackgroundService()
        if (findForeignAppTask(this) == null) {
            LauncherRightPanelOverlayWindow.ensureShowing(this)
        }
        LauncherOverlayBar.ensureShowing(this)
    }

    override fun onResume() {
        super.onResume()
        LauncherHomeActivityHolder.isResumed = true
        LauncherAccessibilityHelper.ensureNavBackServiceEnabled(this)
        val foreign = findForeignAppTask(this)
        val inAppView = foreign != null && !LauncherRightPanelOverlayWindow.isShowing()
        if (inAppView) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            LauncherForegroundHandoff.restoreLauncherWindow()
            window.decorView.visibility = View.VISIBLE
        }
        if (foreign == null) {
            LauncherRightPanelOverlayWindow.ensureShowing(this)
        }
        LauncherOverlayBar.ensureShowing(this)
        LauncherEdgeGestureOverlay.ensureShowing(this)
    }

    override fun onPause() {
        LauncherHomeActivityHolder.isResumed = false
        super.onPause()
    }

    @SuppressLint("BackPressed")
    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall", "DEPRECATION", "OVERRIDE_DEPRECATION")
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
        LauncherRightPanelOverlayWindow.destroy()
        LauncherAppDrawerWindow.hide()
        LauncherAppPickerOverlayWindow.hide()
        LauncherVehicleSettingsOverlayWindow.hide()
        LauncherAboutOverlayWindow.hide()
        LauncherOverlayBar.hide()
        runCatching { minimizeForeignFreeformTasks(this) }
        LauncherOverlayElevator.reset()
        if (LauncherHomeActivityHolder.instance === this) {
            LauncherHomeActivityHolder.instance = null
        }
        super.onDestroy()
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
