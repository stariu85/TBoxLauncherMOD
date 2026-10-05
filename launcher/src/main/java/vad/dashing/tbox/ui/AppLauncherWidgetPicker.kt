package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.MainActivity
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.launcher.LauncherAppListVersion

internal data class LaunchableAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val activityName: String? = null,
)

private object LaunchableAppsWithIconsCache {
    private var cachedIconSizePx: Int? = null
    private var cachedIconRevision: Int? = null
    private var cachedAppListRevision: Int? = null
    private var cachedLookup: LauncherAppIconPaths.Lookup? = null
    private var entries: List<LaunchableAppEntry>? = null

    fun getOrLoad(
        iconSizePx: Int,
        iconRevision: Int,
        appListRevision: Int,
        lookup: LauncherAppIconPaths.Lookup,
        load: () -> List<LaunchableAppEntry>,
    ): List<LaunchableAppEntry> {
        synchronized(this) {
            if (cachedIconSizePx == iconSizePx &&
                cachedIconRevision == iconRevision &&
                cachedAppListRevision == appListRevision &&
                cachedLookup == lookup &&
                entries != null
            ) {
                return entries!!
            }
            val list = load()
            cachedIconSizePx = iconSizePx
            cachedIconRevision = iconRevision
            cachedAppListRevision = appListRevision
            cachedLookup = lookup
            entries = list
            return list
        }
    }

    fun clear() {
        synchronized(this) {
            cachedIconSizePx = null
            cachedIconRevision = null
            cachedAppListRevision = null
            cachedLookup = null
            entries = null
        }
    }
}

internal fun disposeAppLauncherPickerIconCache() {
    LaunchableAppsWithIconsCache.clear()
}

private fun loadLaunchableAppEntries(
    appContext: Context,
    iconSizePx: Int,
    lookup: LauncherAppIconPaths.Lookup,
    @Suppress("UNUSED_PARAMETER") iconRevision: Int,
): List<LaunchableAppEntry> {
    val pm = appContext.packageManager
    val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    val fromLauncherService = launcherApps
        ?.getActivityList(null, Process.myUserHandle())
        .orEmpty()
        .map { info ->
            val pkg = info.componentName.packageName
            val activity = info.componentName.className
            val label = info.label?.toString().orEmpty().ifBlank { pkg }
            val bitmap = decodeLauncherAppCustomIconIfPresent(appContext, pkg, iconSizePx, lookup)
                ?: LauncherAppIconLoader.fromLauncherActivity(appContext, info, iconSizePx)
            LaunchableAppEntry(packageName = pkg, label = label, icon = bitmap, activityName = activity)
        }
    val base = if (fromLauncherService.isNotEmpty()) {
        dedupePrimaryActivities(pm, fromLauncherService)
    } else {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        @Suppress("QueryPermissionsNeeded", "DEPRECATION") val resolves: List<ResolveInfo> =
            pm.queryIntentActivities(intent, 0)
        dedupePrimaryActivities(
            pm,
            resolves.map { ri ->
                val pkg = ri.activityInfo.packageName
                val activity = ri.activityInfo.name
                val label = ri.loadLabel(pm).toString()
                val bitmap = decodeLauncherAppCustomIconIfPresent(appContext, pkg, iconSizePx, lookup)
                    ?: LauncherAppIconLoader.fromResolveInfo(appContext, ri, iconSizePx)
                LaunchableAppEntry(packageName = pkg, label = label, icon = bitmap, activityName = activity)
            },
        )
    }
    return ensureOwnMonitorEntry(appContext, iconSizePx, lookup, base)
}

private fun ensureOwnMonitorEntry(
    appContext: Context,
    iconSizePx: Int,
    lookup: LauncherAppIconPaths.Lookup,
    entries: List<LaunchableAppEntry>,
): List<LaunchableAppEntry> {
    val pkg = appContext.packageName
    val monitorClass = MainActivity::class.java.name
    val homeClass = LauncherHomeActivity::class.java.name
    val monitorLabel = appContext.getString(R.string.launcher_standalone_about_title)
    val withoutHome = entries.filterNot {
        it.packageName == pkg && it.activityName == homeClass
    }
    val existing = withoutHome.firstOrNull {
        it.packageName == pkg && (it.activityName == null || it.activityName == monitorClass)
    }
    if (existing != null) {
        return withoutHome.map { entry ->
            if (entry.packageName == pkg &&
                (entry.activityName == null || entry.activityName == monitorClass)
            ) {
                entry.copy(label = monitorLabel, activityName = monitorClass)
            } else {
                entry
            }
        }.sortedBy { it.label.lowercase() }
    }
    val icon = decodeLauncherAppCustomIconIfPresent(appContext, pkg, iconSizePx, lookup)
        ?: LauncherAppIconLoader.fromPackage(appContext, pkg, iconSizePx)
    return (withoutHome + LaunchableAppEntry(
        packageName = pkg,
        label = monitorLabel,
        icon = icon,
        activityName = monitorClass,
    )).sortedBy { it.label.lowercase() }
}

private fun dedupePrimaryActivities(
    pm: PackageManager,
    entries: List<LaunchableAppEntry>,
): List<LaunchableAppEntry> =
    entries
        .groupBy { it.packageName }
        .map { (pkg, list) -> pickPrimaryLaunchEntry(pm, pkg, list) }
        .sortedBy { it.label.lowercase() }

private fun pickPrimaryLaunchEntry(
    pm: PackageManager,
    packageName: String,
    entries: List<LaunchableAppEntry>,
): LaunchableAppEntry {
    val component = pm.getLaunchIntentForPackage(packageName)?.component
        ?: pm.getLeanbackLaunchIntentForPackage(packageName)?.component
    if (component != null) {
        entries.firstOrNull { it.activityName == component.className }?.let { return it }
    }
    return entries.first()
}

@Composable
internal fun rememberLaunchableAppEntries(
    settingsViewModel: SettingsViewModel? = null,
    launcherIconRevision: Int = 0,
): List<LaunchableAppEntry> {
    val context = LocalContext.current
    val appContext = context.applicationContext
    LauncherAppListVersion.ensurePackageChangeReceiver(appContext)
    val appListRevision = LauncherAppListVersion.version
    val iconLookup = if (settingsViewModel != null) rememberLauncherAppIconLookup(settingsViewModel) else LauncherAppIconPaths.Lookup.None
    // Match the largest on-screen use (drawer ~56dp) with headroom for sharp scaling.
    // Previous ceiling of 96px forced upscale and looked soft on HU density.
    val iconSizePx = remember(appContext) {
        (64f * appContext.resources.displayMetrics.density).toInt().coerceIn(96, 256)
    }
    return remember(appContext, iconSizePx, launcherIconRevision, appListRevision, iconLookup) {
        LaunchableAppsWithIconsCache.getOrLoad(iconSizePx, launcherIconRevision, appListRevision, iconLookup) {
            loadLaunchableAppEntries(appContext, iconSizePx, iconLookup, launcherIconRevision)
        }
    }
}
