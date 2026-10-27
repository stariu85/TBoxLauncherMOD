package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vad.dashing.tbox.tbm.TbmRepositoryProvider
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val TAG = "LauncherAutostart"

/**
 * Launches the home-dock shortcut marked as autostart (long-tap icon → "Автозапуск при старте").
 * Intended to be called once per process start from LauncherHomeActivity.
 */
internal fun launchAutostartShortcut(context: Context) {
    val key = LauncherHomeStore.autostartKey(context) ?: return
    Log.w(TAG, "autostart key=$key")
    when {
        key.startsWith("app:") -> {
            val pkg = key.removePrefix("app:").substringBefore('@')
            runCatching { launchLauncherApp(context, pkg) }
                .onFailure { Log.w(TAG, "autostart app failed pkg=$pkg ${it.message}") }
        }
        key.startsWith("split:") -> {
            val presetId = key.removePrefix("split:").substringBefore('@')
            val preset = LauncherSplitPresetStore.loadPresets(context).firstOrNull { it.id == presetId }
            if (preset == null) {
                Log.w(TAG, "autostart preset missing id=$presetId, clearing")
                LauncherHomeStore.setAutostartKey(context, null)
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
            runCatching { launchSplitPreset(context, preset, stubApps) }
                .onFailure { Log.w(TAG, "autostart split failed id=$presetId ${it.message}") }
        }
        key.startsWith("tbm_auto:") -> {
            val autoId = key.removePrefix("tbm_auto:").substringBefore('@')
            val tbmRepo = TbmRepositoryProvider.get(context)
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { tbmRepo.runAutomation(autoId) }
                    .onFailure { Log.w(TAG, "autostart tbm_auto failed id=$autoId ${it.message}") }
            }
        }
        else -> Log.w(TAG, "autostart unknown key=$key")
    }
}
