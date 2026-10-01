package vad.dashing.tbox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import vad.dashing.tbox.ui.disposeAppLauncherPickerIconCache
import vad.dashing.tbox.ui.launcher.LauncherOverlayElevator
import vad.dashing.tbox.ui.launcher.LauncherAboutOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherAppDrawerWindow
import vad.dashing.tbox.ui.launcher.LauncherAppListVersion
import vad.dashing.tbox.ui.launcher.LauncherAppPickerOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherVehicleSettingsOverlayWindow
import vad.dashing.tbox.ui.launcher.LauncherWifiApRepository
import vad.dashing.tbox.ui.launcher.LauncherThemeState
import vad.dashing.tbox.ui.launcher.launchAutostartShortcut
import vad.dashing.tbox.ui.launcher.TeslaLauncherScreen
import vad.dashing.tbox.ui.launcher.dismissForeignFreeformTasks
import vad.dashing.tbox.ui.launcher.LauncherAccessibilityHelper
import vad.dashing.tbox.ui.launcher.ensureFreeformImmersivePolicy
import vad.dashing.tbox.ui.launcher.goLauncherHome
import androidx.compose.runtime.DisposableEffect
import vad.dashing.tbox.ui.launcher.LauncherCarModelCache
import vad.dashing.tbox.ui.launcher.LauncherOverlayBar

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
        Log.w("LauncherAppLaunch", "LauncherHomeActivity onCreate v${BuildConfig.VERSION_NAME}")
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
        window.decorView.postDelayed({ dismissForeignFreeformTasks(appCtx) }, 400L)

        settingsManager = SettingsManager(this)
        appDataManager = AppDataManager(this)

        // SoftAP params are broadcast into mbCAN only for ~10–30 s after boot — poll eagerly.
        LauncherWifiApRepository.init(appCtx)
        LauncherThemeState.init(appCtx)
        LauncherCarModelCache.preload(appCtx)
        // Home-dock autostart shortcut (long-tap icon). Once per process start; delayed so
        // freeform machinery and the 400ms stack cleanup above settle first.
        if (!autostartFired) {
            autostartFired = true
            window.decorView.postDelayed({ launchAutostartShortcut(appCtx) }, 1600L)
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
        LauncherOverlayBar.ensureShowing(this)
    }

    override fun onRestart() {
        super.onRestart()
        LauncherHomeActivityHolder.instance = this
        startBackgroundService()
        LauncherOverlayBar.ensureShowing(this)
    }

    override fun onResume() {
        super.onResume()
        LauncherForegroundHandoff.restoreLauncherWindow()
        window.decorView.visibility = android.view.View.VISIBLE
        LauncherOverlayBar.ensureShowing(this)
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
            goLauncherHome(this, fromHomeIntent = true)
        }
    }

    override fun onDestroy() {
        LauncherAppDrawerWindow.hide()
        LauncherAppPickerOverlayWindow.hide()
        LauncherVehicleSettingsOverlayWindow.hide()
        LauncherAboutOverlayWindow.hide()
        LauncherOverlayBar.hide()
        runCatching { dismissForeignFreeformTasks(this) }
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
