package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.PackagePresenceCache
import vad.dashing.tbox.update.ApkInstaller
import vad.dashing.tbox.usbmount.RootOrchestrator
import java.io.File
import java.io.FileOutputStream

object MonjaroStubInstaller {
    private const val TAG = "MonjaroStubInstaller"
    const val PACKAGE_NAME = "plus.monjaro"
    private const val ASSET_FILE = "monjaro_stub.apk"

    private enum class InstallOutcome {
        SILENT_INSTALLED,
        FALLBACK_PROMPT_LAUNCHED,
        FAILED,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    fun checkInstalled(context: Context): Boolean {
        return checkInstalledInternal(context.applicationContext, forceRefresh = false)
    }

    private fun checkInstalledInternal(context: Context, forceRefresh: Boolean): Boolean {
        val installed = PackagePresenceCache.isInstalled(
            context = context.applicationContext,
            packageName = PACKAGE_NAME,
            forceRefresh = forceRefresh,
        )
        _isInstalled.value = installed
        return installed
    }

    fun install(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appCtx = context.applicationContext
        scope.launch {
            val outcome = installInternal(appCtx)
            PackagePresenceCache.invalidate(PACKAGE_NAME)
            val success = outcome != InstallOutcome.FAILED
            withContext(Dispatchers.Main) {
                checkInstalledInternal(appCtx, forceRefresh = true)
                onComplete?.invoke(success)
            }
        }
    }

    private suspend fun installInternal(appCtx: Context): InstallOutcome = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(appCtx.cacheDir, ASSET_FILE)
            appCtx.assets.open(ASSET_FILE).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile.setReadable(true, false)

            // 1. Try silent root install if autorun/mb_daemon mechanism is available
            runCatching {
                if (RootOrchestrator.isStorageMounted()) {
                    synchronized(RootOrchestrator.ROOT_LOCK) {
                        val tmpApk = File("/data/local/tmp/$ASSET_FILE")
                        cacheFile.copyTo(tmpApk, overwrite = true)
                        tmpApk.setReadable(true, false)

                        val quotedApk = RootOrchestrator.shellQuote(tmpApk.absolutePath)
                        val script = "#!/system/bin/sh\n" +
                                "pm install -r -g $quotedApk\n" +
                                "rm -f $quotedApk \"\$0\" ${RootOrchestrator.AUTORUN_FLAG}\n" +
                                "setprop ${RootOrchestrator.AUTORUN_PROP} ''\n"

                        RootOrchestrator.runAutorunScript(script)
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "Root autorun install attempt failed", err)
                DclLogRecorder.log("MONJARO", "Root autorun install failed: ${err.message}")
            }

            // Wait briefly to see if silent install finished
            for (i in 0 until 10) {
                delay(300)
                PackagePresenceCache.invalidate(PACKAGE_NAME)
                if (checkInstalledInternal(appCtx, forceRefresh = true)) {
                    Log.i(TAG, "Stub installed silently via root autorun")
                    DclLogRecorder.log("MONJARO", "Stub installed silently via root autorun")
                    return@withContext InstallOutcome.SILENT_INSTALLED
                }
            }

            // 2. Fallback to system package installer (shows standard prompt)
            Log.i(TAG, "Silent root install did not complete; launching fallback package installer")
            DclLogRecorder.log("MONJARO", "Silent install not confirmed; launching fallback installer")
            withContext(Dispatchers.Main) {
                ApkInstaller.install(appCtx, cacheFile)
            }
            InstallOutcome.FALLBACK_PROMPT_LAUNCHED
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract/install monjaro stub", t)
            DclLogRecorder.log("MONJARO", "Failed to install monjaro stub: ${t.message}")
            InstallOutcome.FAILED
        }
    }
}
