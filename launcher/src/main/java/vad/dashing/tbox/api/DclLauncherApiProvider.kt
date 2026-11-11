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
import vad.dashing.tbox.ui.launcher.launchLauncherAppEmbedded
import vad.dashing.tbox.ui.launcher.launchLauncherAppFullscreen
import java.util.concurrent.ConcurrentHashMap

/**
 * ContentProvider implementing DCL LAUNCHER API FOR VENDOR RU v1.2 spec.
 *
 * Authority: `ras.dashing.tbox.launcher.api`
 * Transport: `ContentResolver.call(uri, method, arg, extras)`
 *
 * Methods:
 * - `launchApp`: Opens applications in specified mode (`fullscreen` or `app_area`).
 * - `getUiState` / `getUiChrome`: Returns desktop geometry, panel heights, ADAS bounds, and screen mode.
 * - `getCapabilities`: Returns launcher API capabilities.
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
                val isFullscreen = mode == "fullscreen"
                if (isFullscreen) {
                    launchLauncherAppFullscreen(appCtx, pkg, activityClass)
                } else {
                    launchLauncherAppEmbedded(appCtx, pkg, activityClass)
                }
                LauncherActiveAppTracker.onAppLaunched(
                    packageName = pkg,
                    topActivityClass = activityClass,
                    isFullscreen = isFullscreen,
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

    private fun handleGetCapabilities(): Bundle {
        return Bundle().apply {
            putString("status", "ok")
            putInt("apiVersion", 1)
            putBoolean("launchFullscreen", true)
            putBoolean("launchAppArea", true)
            putBoolean("uiStateQuery", true)
            putBoolean("uiStateEvents", true)
            putBoolean("adasPanel", true)
            putBoolean("splitSupported", true)
            putString("screenModes", "home,single,fullscreen,split")
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
