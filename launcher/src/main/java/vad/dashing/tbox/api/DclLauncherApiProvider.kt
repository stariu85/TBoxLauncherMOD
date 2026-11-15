package vad.dashing.tbox.api

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import vad.dashing.tbox.ui.launcher.LauncherActiveAppTracker
import vad.dashing.tbox.ui.launcher.LauncherAppConfigStore
import vad.dashing.tbox.ui.launcher.LauncherAppLaunchMode
import vad.dashing.tbox.ui.launcher.LauncherCruisePresetController
import vad.dashing.tbox.ui.launcher.launchLauncherAppEmbedded
import vad.dashing.tbox.ui.launcher.launchLauncherAppFullscreen
import vad.dashing.tbox.ui.launcher.launchLauncherAppFullWidth
import java.util.concurrent.ConcurrentHashMap

/**
 * ContentProvider implementing DCL LAUNCHER API FOR VENDOR RU v1.2 spec.
 *
 * Authority: `ras.dashing.tbox.launcher.api`
 * Transport: `ContentResolver.call(uri, method, arg, extras)`
 *
 * Methods:
 * - `launchApp`: Opens applications in specified mode (`fullscreen` or `app_area`).
 * - `getUiState` / `getUiChrome`: Returns desktop geometry, panel heights, ADAS bounds, screen mode and Smart ACC state.
 * - `getCapabilities`: Returns launcher API capabilities.
 * - `setSmartAcc` / `setSmartSpeedLimit`: Enables or disables Smart ACC by speed limit signs.
 * - `toggleSmartAcc` / `toggleSmartSpeedLimit`: Toggles Smart ACC on/off.
 * - `getSmartAccState`: Returns detailed Smart ACC status and speed limit info.
 */
class DclLauncherApiProvider : ContentProvider() {

    companion object {
        private const val TAG = "DclLauncherApiProvider"
        private const val TARGET_PACKAGE = "vad.dashing.tbox"
        private val processedRequestIds = ConcurrentHashMap<String, Long>()
        private const val REQUEST_ID_EXPIRE_MS = 2000L
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        try {
            checkCallingPermission()
        } catch (e: SecurityException) {
            Log.w(TAG, "Access denied for caller: ${e.message}")
            return Bundle().apply {
                putString("status", "error")
                putString("errorCode", "forbidden")
                putString("message", e.message.orEmpty())
            }
        }

        return when (method) {
            "launchApp" -> handleLaunchApp(arg, extras)
            "getUiState", "getUiChrome" -> handleGetUiState()
            "getCapabilities" -> handleGetCapabilities()
            "setSmartAcc", "setSmartSpeedLimit" -> handleSetSmartAcc(arg, extras)
            "toggleSmartAcc", "toggleSmartSpeedLimit" -> handleToggleSmartAcc()
            "getSmartAccState", "getSmartSpeedLimitState" -> handleGetSmartAccState()
            else -> Bundle().apply {
                putString("status", "error")
                putString("errorCode", "unsupported")
                putString("message", "Unsupported method: $method")
            }
        }
    }

    private fun checkCallingPermission() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }

        val ctx = context ?: throw SecurityException("Context is null")
        val packages = ctx.packageManager.getPackagesForUid(callingUid)
        if (packages == null || !packages.contains(TARGET_PACKAGE)) {
            throw SecurityException("Calling package (UID $callingUid) is not $TARGET_PACKAGE")
        }
    }

    private fun handleLaunchApp(arg: String?, extras: Bundle?): Bundle {
        val pkg = extras?.getString("packageName")
            ?.takeIf { it.isNotBlank() }
            ?: arg?.takeIf { it.isNotBlank() }

        if (pkg.isNullOrBlank()) {
            return Bundle().apply {
                putString("status", "error")
                putString("errorCode", "invalid_args")
                putString("message", "packageName parameter is missing or empty")
            }
        }

        val mode = extras?.getString("mode") ?: "app_area"
        val activityClass = extras?.getString("activityClass")
        val requestId = extras?.getString("requestId")

        val isFullscreen = mode.equals("fullscreen", ignoreCase = true)
        val isFullWidth = mode.equals("full_width", ignoreCase = true) ||
            mode.equals("fullwidth", ignoreCase = true) ||
            mode.equals("full_width_no_adas", ignoreCase = true) ||
            mode.equals("no_adas", ignoreCase = true)

        // Idempotency check
        if (!requestId.isNullOrBlank()) {
            cleanExpiredRequestIds()
            val now = System.currentTimeMillis()
            val prevTime = processedRequestIds.putIfAbsent(requestId, now)
            if (prevTime != null && (now - prevTime) < REQUEST_ID_EXPIRE_MS) {
                Log.d(TAG, "Duplicate launch request ignored for requestId=$requestId")
                return Bundle().apply {
                    putString("status", "accepted")
                    putString("mode", mode)
                    putString("packageName", pkg)
                    putString("message", "Duplicate request ignored")
                }
            }
        }

        val appCtx = context?.applicationContext ?: return Bundle().apply {
            putString("status", "error")
            putString("errorCode", "internal")
            putString("message", "Application context is null")
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val launchMode = when {
                    isFullscreen -> LauncherAppLaunchMode.FULLSCREEN
                    isFullWidth -> LauncherAppLaunchMode.FULL_WIDTH
                    else -> LauncherAppLaunchMode.EMBEDDED
                }
                val launchCtx = vad.dashing.tbox.LauncherHomeActivityHolder.instance ?: appCtx
                when (launchMode) {
                    LauncherAppLaunchMode.FULLSCREEN -> launchLauncherAppFullscreen(launchCtx, pkg, activityClass)
                    LauncherAppLaunchMode.FULL_WIDTH -> launchLauncherAppFullWidth(launchCtx, pkg, activityClass)
                    LauncherAppLaunchMode.EMBEDDED -> launchLauncherAppEmbedded(launchCtx, pkg, activityClass)
                }
                LauncherActiveAppTracker.onAppLaunched(
                    packageName = pkg,
                    topActivityClass = activityClass,
                    isFullscreen = isFullscreen,
                    launchMode = launchMode,
                )
                DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app $pkg in mode $mode", e)
            }
        }

        return Bundle().apply {
            putString("status", "ok")
            putString("mode", mode)
            putString("packageName", pkg)
        }
    }

    private fun handleGetUiState(): Bundle {
        val ctx = context?.applicationContext
        return DclUiStateNotifier.getUiState(ctx)
    }

    private fun handleSetSmartAcc(arg: String?, extras: Bundle?): Bundle {
        val appCtx = context?.applicationContext ?: return Bundle().apply {
            putString("status", "error")
            putString("errorCode", "internal")
            putString("message", "Application context is null")
        }

        val enabled = when {
            extras?.containsKey("enabled") == true -> extras.getBoolean("enabled")
            extras?.containsKey("active") == true -> extras.getBoolean("active")
            arg != null -> arg.equals("true", ignoreCase = true) || arg == "1" || arg.equals("on", ignoreCase = true) || arg.equals("enable", ignoreCase = true)
            else -> true
        }

        val targetSpeed = extras?.getInt("targetSpeed")?.takeIf { it in 30..160 }

        if (extras?.containsKey("configEnabled") == true) {
            LauncherAppConfigStore.setAdasAccBySpeedLimitEnabled(appCtx, extras.getBoolean("configEnabled"))
        }

        Handler(Looper.getMainLooper()).post {
            try {
                if (enabled) {
                    LauncherAppConfigStore.setAdasAccBySpeedLimitEnabled(appCtx, true)
                    if (targetSpeed != null) {
                        LauncherCruisePresetController.applySpeedLimitPreset(appCtx, targetSpeed)
                    } else {
                        val limit = LauncherCruisePresetController.currentEffectiveSpeedLimit(appCtx)
                        val offset = LauncherAppConfigStore.adasAccSpeedOffset(appCtx)
                        if (limit != null) {
                            LauncherCruisePresetController.applySpeedLimitPreset(appCtx, limit + offset)
                        } else {
                            LauncherCruisePresetController.setSmartSpeedLimitActive(true)
                        }
                    }
                } else {
                    LauncherCruisePresetController.setSmartSpeedLimitActive(false)
                    if (extras?.getBoolean("disableSetting") == true || arg == "disable_setting") {
                        LauncherAppConfigStore.setAdasAccBySpeedLimitEnabled(appCtx, false)
                    }
                }
                DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set smart ACC state to $enabled", e)
            }
        }

        return Bundle().apply {
            putString("status", "ok")
            putBoolean("requestedEnabled", enabled)
            putBoolean("smartAccActive", enabled)
            putBoolean("smartAccConfigEnabled", LauncherAppConfigStore.adasAccBySpeedLimitEnabled(appCtx))
        }
    }

    private fun handleToggleSmartAcc(): Bundle {
        val appCtx = context?.applicationContext ?: return Bundle().apply {
            putString("status", "error")
            putString("errorCode", "internal")
            putString("message", "Application context is null")
        }

        val wasActive = LauncherCruisePresetController.isSmartSpeedLimitActive.value
        val willBeActive = !wasActive

        Handler(Looper.getMainLooper()).post {
            try {
                if (wasActive) {
                    LauncherCruisePresetController.setSmartSpeedLimitActive(false)
                } else {
                    LauncherAppConfigStore.setAdasAccBySpeedLimitEnabled(appCtx, true)
                    val limit = LauncherCruisePresetController.currentEffectiveSpeedLimit(appCtx)
                    val offset = LauncherAppConfigStore.adasAccSpeedOffset(appCtx)
                    if (limit != null) {
                        LauncherCruisePresetController.applySpeedLimitPreset(appCtx, limit + offset)
                    } else {
                        LauncherCruisePresetController.setSmartSpeedLimitActive(true)
                    }
                }
                DclUiStateNotifier.notifyUiStateChanged(appCtx, immediate = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle smart ACC state", e)
            }
        }

        return Bundle().apply {
            putString("status", "ok")
            putBoolean("smartAccActive", willBeActive)
            putBoolean("smartAccConfigEnabled", LauncherAppConfigStore.adasAccBySpeedLimitEnabled(appCtx))
        }
    }

    private fun handleGetSmartAccState(): Bundle {
        val appCtx = context?.applicationContext ?: return Bundle().apply {
            putString("status", "error")
            putString("errorCode", "internal")
            putString("message", "Application context is null")
        }

        val limit = LauncherCruisePresetController.currentEffectiveSpeedLimit(appCtx)
        val offset = LauncherAppConfigStore.adasAccSpeedOffset(appCtx)
        val isSmartActive = LauncherCruisePresetController.isSmartSpeedLimitActive.value
        val configEnabled = LauncherAppConfigStore.adasAccBySpeedLimitEnabled(appCtx)
        val cruiseState = LauncherCruisePresetController.accCruiseState.value.name

        return Bundle().apply {
            putString("status", "ok")
            putBoolean("smartAccActive", isSmartActive)
            putBoolean("isSmartAccActive", isSmartActive)
            putBoolean("smartAccConfigEnabled", configEnabled)
            putBoolean("adasAccBySpeedLimitEnabled", configEnabled)
            putInt("adasAccSpeedOffset", offset)
            putString("cruiseState", cruiseState)
            if (limit != null) {
                putInt("effectiveSpeedLimit", limit)
                putInt("targetSpeed", limit + offset)
            }
        }
    }

    private fun handleGetCapabilities(): Bundle {
        return Bundle().apply {
            putString("status", "ok")
            putInt("apiVersion", 1)
            putBoolean("launchFullscreen", true)
            putBoolean("launchAppArea", true)
            putBoolean("launchFullWidth", true)
            putBoolean("uiStateQuery", true)
            putBoolean("uiStateEvents", true)
            putBoolean("adasPanel", true)
            putBoolean("splitSupported", true)
            putBoolean("smartAccControl", true)
            putBoolean("smartAccBySpeedLimit", true)
            putString("screenModes", "home,single,full_width,fullscreen,split")
        }
    }

    private fun cleanExpiredRequestIds() {
        val now = System.currentTimeMillis()
        val iterator = processedRequestIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > REQUEST_ID_EXPIRE_MS) {
                iterator.remove()
            }
        }
    }

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
