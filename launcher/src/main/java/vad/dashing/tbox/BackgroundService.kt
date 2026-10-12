package vad.dashing.tbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanDiagnostics
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.LauncherEmbeddedBoundsState
import vad.dashing.tbox.ui.launcher.LauncherOemDelegate
import vad.dashing.tbox.ui.launcher.LauncherOemFullscreenPolicy
import vad.dashing.tbox.ui.launcher.isFreeformEnabled
import vad.dashing.tbox.ui.launcher.tryLaunchIntentInBounds

/**
 * Thin foreground service for the standalone launcher.
 * Handles app launch (freeform/embedded), mbCAN, and TBox UDP via tbox-proxy.
 */
class BackgroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tboxLink: TboxLinkManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        MbCanEngineFacade.ensureInitialized()
        scope.launch {
            UniversalCanRepository.bind(scope)
        }
        scope.launch {
            vad.dashing.tbox.tbm.TbmRepositoryProvider.get(applicationContext).checkHealth()
        }
        tboxLink = TboxLinkManager(applicationContext).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY
        when (intent.action) {
            ACTION_START -> Unit
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_OPEN_MAIN_ACTIVITY -> {
                openMainActivity()
            }
            ACTION_LAUNCH_APP -> {
                val pkg = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE).orEmpty()
                val component = intent.getStringExtra(EXTRA_LAUNCH_COMPONENT)
                    ?.let { ComponentName.unflattenFromString(it) }
                val preferEmbedded = intent.getBooleanExtra(EXTRA_LAUNCH_EMBEDDED, true)
                val forceFullscreen = intent.getBooleanExtra(EXTRA_LAUNCH_FULLSCREEN, false)
                launchExternalAppFromService(pkg, component, preferEmbedded, forceFullscreen)
            }
            ACTION_MBCAN_COMMAND -> {
                scope.launch {
                    val commandType = intent.getStringExtra(EXTRA_MBCAN_COMMAND_TYPE).orEmpty()
                    val propertyId = intent.getIntExtra(EXTRA_MBCAN_PROPERTY_ID, Int.MIN_VALUE)
                    val propertyValue = intent.getIntExtra(EXTRA_MBCAN_VALUE, 0)
                    when (commandType) {
                        MBCAN_COMMAND_TOGGLE_PROPERTY -> {
                            if (propertyId != Int.MIN_VALUE) {
                                UniversalCanRepository.execute(MbCanCommand.ToggleProperty(propertyId))
                            }
                        }
                        MBCAN_COMMAND_SET_PROPERTY -> {
                            if (propertyId != Int.MIN_VALUE) {
                                UniversalCanRepository.execute(
                                    MbCanCommand.SetProperty(propertyId, propertyValue),
                                )
                            }
                        }
                        MBCAN_COMMAND_SET_DRIVER_WINDOW -> {
                            MbCanEngineFacade.setDriverWindowPosition(propertyValue)
                        }
                        MBCAN_COMMAND_SET_WINDOW -> {
                            MbCanEngineFacade.setWindowPosition(
                                windowIndex = propertyId,
                                positionPercent = propertyValue,
                            )
                        }
                        else -> MbCanDiagnostics.log("WARN", "unknown command type=$commandType")
                    }
                }
            }
            ACTION_TBOX_REBOOT,
            ACTION_TRIP_FINISH_AND_START,
            ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS,
            ACTION_TOGGLE_FLOATING_PANELS_ENABLED,
            -> {
                Log.d(TAG, "Ignored TBox-only action=${intent.action}")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tboxLink?.stop()
        tboxLink = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "launcher_service"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.launcher_standalone_app_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherHomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.launcher_standalone_app_name))
            .setContentText(getString(R.string.launcher_standalone_service_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openMainActivity() {
        try {
            startActivity(MainActivityIntentHelper.createBringToFrontIntent(this))
            LauncherForegroundHandoff.requestLauncherHandoff()
        } catch (e: Exception) {
            Log.e(TAG, "Open MainActivity failed", e)
        }
    }

    private fun tryLaunchEmbedded(
        packageName: String,
        component: ComponentName?,
    ): Boolean {
        val bounds = LauncherEmbeddedBoundsState.embeddedBounds() ?: return false
        if (!isFreeformEnabled(this)) return false
        val launch = buildLaunchIntent(packageName, component) ?: return false
        val ok = tryLaunchIntentInBounds(this, packageName, launch, bounds)
        if (ok) {
            LastAppTracker.recordLaunch(this, packageName)
            Log.w(TAG, "embedded OK pkg=$packageName bounds=$bounds")
        }
        return ok
    }

    private fun tryLaunchFullscreenFreeform(
        packageName: String,
        component: ComponentName?,
    ): Boolean {
        val bounds = LauncherEmbeddedBoundsState.fullScreenBounds() ?: return false
        if (!isFreeformEnabled(this)) return false
        val launch = buildLaunchIntent(packageName, component) ?: return false
        val ok = tryLaunchIntentInBounds(this, packageName, launch, bounds)
        if (ok) {
            LastAppTracker.recordLaunch(this, packageName)
            Log.w(TAG, "fullscreen freeform OK pkg=$packageName")
        }
        return ok
    }

    private fun buildLaunchIntent(
        packageName: String,
        component: ComponentName?,
    ): Intent? = when {
        component != null -> Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setComponent(component)
        }
        packageName.isNotBlank() -> packageManager.getLaunchIntentForPackage(packageName)
        else -> null
    }

    private fun launchExternalAppFromService(
        packageName: String,
        component: ComponentName?,
        preferEmbedded: Boolean = true,
        forceFullscreen: Boolean = false,
    ) {
        if (forceFullscreen) {
            if (tryLaunchFullscreenFreeform(packageName, component)) {
                LauncherForegroundHandoff.requestLauncherHandoff()
                return
            }
            if (!LauncherOemFullscreenPolicy.allowsDirectFullscreen(this, packageName)) {
                LauncherOemDelegate.notifyFullscreenBlocked(this, packageName)
                LauncherForegroundHandoff.restoreLauncherWindow()
                return
            }
        } else if (preferEmbedded && tryLaunchEmbedded(packageName, component)) {
            return
        }

        if (!LauncherOemFullscreenPolicy.allowsDirectFullscreen(this, packageName)) {
            if (tryLaunchEmbedded(packageName, component)) return
            LauncherOemDelegate.notifyFullscreenBlocked(this, packageName)
            LauncherForegroundHandoff.restoreLauncherWindow()
            return
        }

        if (component != null) {
            val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java)
            if (launcherApps != null) {
                val viaLauncherApps = runCatching {
                    launcherApps.startMainActivity(
                        component,
                        android.os.Process.myUserHandle(),
                        null,
                        null,
                    )
                }.isSuccess
                if (viaLauncherApps) {
                    if (packageName.isNotBlank()) LastAppTracker.recordLaunch(this, packageName)
                    LauncherForegroundHandoff.requestLauncherHandoff()
                    return
                }
            }
        }

        val launch = buildLaunchIntent(packageName, component) ?: return
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        try {
            startActivity(launch)
            if (packageName.isNotBlank()) LastAppTracker.recordLaunch(this, packageName)
            LauncherForegroundHandoff.requestLauncherHandoff()
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed pkg=$packageName", e)
        }
    }

    companion object {
        private const val TAG = "LauncherService"
        private const val NOTIFICATION_ID = 1601

        const val ACTION_START = "vad.dashing.tbox.START"
        const val ACTION_STOP = "vad.dashing.tbox.STOP"
        const val ACTION_TBOX_REBOOT = "vad.dashing.tbox.TBOX_REBOOT"
        const val ACTION_TRIP_FINISH_AND_START = "vad.dashing.tbox.TRIP_FINISH_AND_START"
        const val ACTION_OPEN_MAIN_ACTIVITY = "vad.dashing.tbox.OPEN_MAIN_ACTIVITY"
        const val EXTRA_OPEN_MAIN_DELAY_MS = "vad.dashing.tbox.EXTRA_OPEN_MAIN_DELAY_MS"
        const val ACTION_LAUNCH_APP = "vad.dashing.tbox.LAUNCH_APP"
        const val EXTRA_LAUNCH_PACKAGE = "vad.dashing.tbox.EXTRA_LAUNCH_PACKAGE"
        const val EXTRA_LAUNCH_COMPONENT = "vad.dashing.tbox.EXTRA_LAUNCH_COMPONENT"
        const val EXTRA_LAUNCH_EMBEDDED = "vad.dashing.tbox.EXTRA_LAUNCH_EMBEDDED"
        const val EXTRA_LAUNCH_FULLSCREEN = "vad.dashing.tbox.EXTRA_LAUNCH_FULLSCREEN"
        const val ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS =
            "vad.dashing.tbox.TOGGLE_HIDE_OTHER_FLOATING_PANELS"
        const val EXTRA_FLOATING_PANEL_ORIGIN_ID = "vad.dashing.tbox.EXTRA_FLOATING_PANEL_ORIGIN_ID"
        const val EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN =
            "vad.dashing.tbox.EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN"
        const val ACTION_TOGGLE_FLOATING_PANELS_ENABLED =
            "vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED"
        const val EXTRA_TOGGLE_FLOATING_ENABLED_ALL =
            "vad.dashing.tbox.EXTRA_TOGGLE_FLOATING_ENABLED_ALL"
        const val ACTION_MBCAN_COMMAND = "vad.dashing.tbox.ACTION_MBCAN_COMMAND"
        const val EXTRA_MBCAN_COMMAND_TYPE = "vad.dashing.tbox.EXTRA_MBCAN_COMMAND_TYPE"
        const val EXTRA_MBCAN_PROPERTY_ID = "vad.dashing.tbox.EXTRA_MBCAN_PROPERTY_ID"
        const val EXTRA_MBCAN_VALUE = "vad.dashing.tbox.EXTRA_MBCAN_VALUE"
        const val MBCAN_COMMAND_TOGGLE_PROPERTY = "TOGGLE_PROPERTY"
        const val MBCAN_COMMAND_SET_PROPERTY = "SET_PROPERTY"
        const val MBCAN_COMMAND_SET_DRIVER_WINDOW = "SET_DRIVER_WINDOW"
        const val MBCAN_COMMAND_SET_WINDOW = "SET_WINDOW"
    }
}
