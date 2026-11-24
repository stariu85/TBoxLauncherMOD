package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Environment
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
import vad.dashing.tbox.update.ApkInstaller
import vad.dashing.tbox.usbmount.RootOrchestrator
import java.io.File
import java.io.FileOutputStream

object MonjaroStubInstaller {
    private const val TAG = "MonjaroStubInstaller"
    const val PACKAGE_NAME = "plus.monjaro"
    private const val ASSET_FILE = "monjaro_stub.apk"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    fun checkInstalled(context: Context): Boolean {
        val installed = runCatching {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0) != null
        }.getOrDefault(false)
        _isInstalled.value = installed
        return installed
    }

    fun install(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val success = installInternal(context.applicationContext)
            withContext(Dispatchers.Main) {
                checkInstalled(context)
                onComplete?.invoke(success)
            }
        }
    }

    private suspend fun installInternal(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, ASSET_FILE)
            context.assets.open(ASSET_FILE).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile.setReadable(true, false)

            // 1. Try silent root install if autorun/mb_daemon mechanism is available
            val rootAttempt = runCatching {
                if (RootOrchestrator.isStorageMounted()) {
                    synchronized(RootOrchestrator.ROOT_LOCK) {
                        val tmpApk = File("/data/local/tmp/$ASSET_FILE")
                        cacheFile.copyTo(tmpApk, overwrite = true)
                        tmpApk.setReadable(true, false)

                        val flagFile = File(RootOrchestrator.AUTORUN_FLAG)
                        val script = "#!/system/bin/sh\n" +
                                "pm install -r -g ${tmpApk.absolutePath}\n" +
                                "rm -f ${tmpApk.absolutePath} \"$0\" ${RootOrchestrator.AUTORUN_FLAG}\n" +
                                "setprop ${RootOrchestrator.AUTORUN_PROP} ''\n"

                        val autorunFile = File(RootOrchestrator.AUTORUN)
                        FileOutputStream(autorunFile).use { it.write(script.toByteArray(Charsets.UTF_8)) }
                        Runtime.getRuntime().exec(arrayOf("chmod", "777", autorunFile.absolutePath)).waitFor()

                        flagFile.createNewFile()
                        Runtime.getRuntime().exec(arrayOf("chmod", "666", flagFile.absolutePath)).waitFor()

                        Runtime.getRuntime().exec(arrayOf("setprop", RootOrchestrator.AUTORUN_PROP, "")).waitFor()
                        Thread.sleep(300)
                        Runtime.getRuntime().exec(arrayOf("setprop", RootOrchestrator.AUTORUN_PROP, RootOrchestrator.AUTORUN_FLAG)).waitFor()
                    }
                }
            }

            // Wait briefly to see if silent install finished
            for (i in 0 until 10) {
                delay(300)
                if (checkInstalled(context)) {
                    DclLogRecorder.log("MONJARO", "Stub installed silently via root autorun")
                    return@withContext true
                }
            }

            // 2. Fallback to system package installer (shows standard prompt)
            withContext(Dispatchers.Main) {
                ApkInstaller.install(context, cacheFile)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract/install monjaro stub", t)
            DclLogRecorder.log("MONJARO", "Failed to install monjaro stub: ${t.message}")
            false
        }
    }
}
