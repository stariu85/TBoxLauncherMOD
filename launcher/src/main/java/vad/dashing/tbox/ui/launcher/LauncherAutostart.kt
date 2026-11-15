package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val TAG = "LauncherAutostart"

/**
 * Launches the home-dock shortcut marked as autostart (long-tap icon → "Автозапуск при старте").
 * Intended to be called once per process start from LauncherHomeActivity after desktop is ready.
 */
internal fun launchAutostartShortcut(context: Context) {
    val activity = (context as? Activity)
        ?: LauncherHomeActivityHolder.instance
        ?: run {
            var ctx: Context? = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) return@run ctx
                ctx = ctx.baseContext
            }
            null
        }

    if (activity != null && (activity.isFinishing || activity.isDestroyed)) {
        Log.w(TAG, "autostart aborted: activity is finishing or destroyed")
        return
    }

    val launchContext = activity ?: context
    val key = LauncherHomeStore.autostartKey(launchContext) ?: return
    Log.w(TAG, "autostart key=$key using context=$launchContext")
    when {
        key.startsWith("app:") -> {
            val pkg = key.removePrefix("app:").substringBefore('@')
            runCatching { launchLauncherApp(launchContext, pkg) }
                .onFailure { Log.w(TAG, "autostart app failed pkg=$pkg ${it.message}") }
        }
        key.startsWith("split:") -> {
            val presetId = key.removePrefix("split:").substringBefore('@')
            val preset = LauncherSplitPresetStore.loadPresets(launchContext).firstOrNull { it.id == presetId }
            if (preset == null) {
                Log.w(TAG, "autostart preset missing id=$presetId, clearing")
                LauncherHomeStore.setAutostartKey(launchContext, null)
                return
            }
            // launchSplitPreset only needs packageName (+ optional activityName) per side.
            val stubApps = listOf(preset.leftPackage, preset.rightPackage).map { pkg ->
                LaunchableAppEntry(
                    packageName = pkg,
                    label = pkg.substringAfterLast('.'),
                    icon = null,
                )
            }
            runCatching { launchSplitPreset(launchContext, preset, stubApps) }
                .onFailure { Log.w(TAG, "autostart split failed id=$presetId ${it.message}") }
        }
        key.startsWith("tbm_auto:") -> {
            val autoId = key.removePrefix("tbm_auto:").substringBefore('@')
            val tbmRepo = TbmRepositoryProvider.get(launchContext)
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { tbmRepo.runAutomation(autoId) }
                    .onFailure { Log.w(TAG, "autostart tbm_auto failed id=$autoId ${it.message}") }
            }
        }
        else -> Log.w(TAG, "autostart unknown key=$key")
    }
}

