package vad.dashing.tbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vad.dashing.tbox.update.UpdateChannel
import java.io.FileOutputStream

private const val DATASTORE_NAME = "ras.dashing.tbox.launcher.settings"

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

enum class SetLauncherAppCustomIconResult {
    Success,
    InvalidPackage,
    DimensionsTooLarge,
    NotImageOrUnreadable,
    CopyFailed,
}

/**
 * Slim settings store for the standalone launcher — only fields the HOME UI and CAN bind need.
 */
class SettingsManager(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        const val LAUNCHER_APP_ICONS_DIR = "launcher_app_icons"
        private val KEY_HEAD_UNIT_CAN = stringPreferencesKey("head_unit_can_mode")
        private val KEY_CAN_AUTO_BIND = booleanPreferencesKey("can_auto_bind_enabled")
        private val KEY_CAN_AUTO_BIND_LOCKED = booleanPreferencesKey("can_auto_bind_locked")
        private val KEY_CAN_AUTO_BIND_PRIMARY = stringPreferencesKey("can_auto_bind_last_primary")
        private val KEY_CAN_AUTO_BIND_RESULT = stringPreferencesKey("can_auto_bind_last_result")
        private val KEY_ICON_REVISION = intPreferencesKey("launcher_app_icon_revision")
        private val KEY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        private val KEY_UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
        private val KEY_LAST_CLEARED_CACHE_VERSION =
            longPreferencesKey("last_cleared_cache_version")
        private val KEY_HU_REBOOT_LAST_SEEN_VERSION =
            longPreferencesKey("hu_reboot_last_seen_version")
        private val KEY_HU_REBOOT_PENDING_VERSION =
            longPreferencesKey("hu_reboot_pending_version")
        private val KEY_HU_REBOOT_UPGRADE_ELAPSED =
            longPreferencesKey("hu_reboot_upgrade_elapsed")
        private val KEY_HU_REBOOT_UPGRADE_BOOT_ID =
            stringPreferencesKey("hu_reboot_upgrade_boot_id")
        private val KEY_HU_REBOOT_PROMPT_SHOWN_VERSION =
            longPreferencesKey("hu_reboot_prompt_shown_version")
        private val KEY_HU_REBOOT_AWAITING_INSTALL_VERSION =
            longPreferencesKey("hu_reboot_awaiting_install_version")
        private val KEY_TBM_API_TOKEN = stringPreferencesKey("tbm_api_token")
        private val KEY_TBM_API_PORT = intPreferencesKey("tbm_api_port")
        private val KEY_UI_CLICK_SOUNDS = booleanPreferencesKey("ui_click_sounds_enabled")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_USB_MOUNT_ENABLED = booleanPreferencesKey("usb_mount_enabled")
        private const val MAX_ICON_EDGE_PX = 512
    }

    val launcherAppIconRevisionFlow: Flow<Int> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_ICON_REVISION] ?: 0 }
        .distinctUntilChanged()

    val autoBackupEnabledFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_AUTO_BACKUP_ENABLED] ?: true }
        .distinctUntilChanged()

    val usbMountEnabledFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_USB_MOUNT_ENABLED] ?: true }
        .distinctUntilChanged()

    val uiClickSoundsEnabledFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_UI_CLICK_SOUNDS] ?: true }
        .distinctUntilChanged()

    val headUnitCanModeFlow: Flow<HeadUnitCanMode> = appContext.settingsDataStore.data
        .map { prefs -> HeadUnitCanMode.fromStorageValue(prefs[KEY_HEAD_UNIT_CAN]) }
        .distinctUntilChanged()

    val canAutoBindEnabledFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_CAN_AUTO_BIND] ?: true }
        .distinctUntilChanged()

    val canAutoBindLockedFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_CAN_AUTO_BIND_LOCKED] ?: false }
        .distinctUntilChanged()

    val updateChannelFlow: Flow<UpdateChannel> = appContext.settingsDataStore.data
        .map { prefs -> UpdateChannel.fromStorageValue(prefs[KEY_UPDATE_CHANNEL]) }
        .distinctUntilChanged()

    val updateCheckEnabledFlow: Flow<Boolean> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_UPDATE_CHECK_ENABLED] ?: false }
        .distinctUntilChanged()

    val tbmApiTokenFlow: Flow<String?> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_TBM_API_TOKEN] }
        .distinctUntilChanged()

    val tbmApiPortFlow: Flow<Int> = appContext.settingsDataStore.data
        .map { prefs -> prefs[KEY_TBM_API_PORT] ?: 8765 }
        .distinctUntilChanged()

    val huRebootAfterUpdateFlow: Flow<HuRebootAfterUpdateSnapshot> =
        appContext.settingsDataStore.data
            .map { huRebootSnapshotFrom(it) }
            .distinctUntilChanged()

    suspend fun saveHeadUnitCanMode(mode: HeadUnitCanMode) {
        appContext.settingsDataStore.edit { it[KEY_HEAD_UNIT_CAN] = mode.storageValue }
    }

    suspend fun saveCanAutoBindEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND] = enabled }
    }

    suspend fun saveCanAutoBindLocked(locked: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_LOCKED] = locked }
    }

    suspend fun saveCanAutoBindLastPrimaryMode(mode: HeadUnitCanMode?) {
        appContext.settingsDataStore.edit {
            if (mode == null) it.remove(KEY_CAN_AUTO_BIND_PRIMARY)
            else it[KEY_CAN_AUTO_BIND_PRIMARY] = mode.storageValue
        }
    }

    suspend fun saveCanAutoBindLastResult(result: String) {
        appContext.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_RESULT] = result }
    }

    suspend fun saveUpdateChannel(channel: UpdateChannel) {
        appContext.settingsDataStore.edit { it[KEY_UPDATE_CHANNEL] = channel.storageValue }
    }

    suspend fun saveUpdateCheckEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_UPDATE_CHECK_ENABLED] = enabled }
    }

    suspend fun clearCacheOnAppUpdate(currentVersionCode: Long) {
        withContext(Dispatchers.IO) {
            val prefs = appContext.settingsDataStore.data.first()
            val lastClearedVersion = prefs[KEY_LAST_CLEARED_CACHE_VERSION] ?: 0L
            if (lastClearedVersion < currentVersionCode) {
                var filesDeletedCount = 0
                runCatching {
                    appContext.cacheDir?.listFiles()?.forEach { file ->
                        if (file.deleteRecursively()) filesDeletedCount++
                    }
                    appContext.externalCacheDir?.listFiles()?.forEach { file ->
                        if (file.deleteRecursively()) filesDeletedCount++
                    }
                }
                android.util.Log.i("SettingsManager", "App cache cleared on update (from $lastClearedVersion to $currentVersionCode, deleted $filesDeletedCount items)")
                appContext.settingsDataStore.edit { mutablePrefs ->
                    mutablePrefs[KEY_LAST_CLEARED_CACHE_VERSION] = currentVersionCode
                }
            }
        }
    }

    suspend fun saveTbmApiToken(token: String?) {
        appContext.settingsDataStore.edit {
            if (token.isNullOrEmpty()) it.remove(KEY_TBM_API_TOKEN)
            else it[KEY_TBM_API_TOKEN] = token
        }
    }

    suspend fun saveTbmApiPort(port: Int) {
        appContext.settingsDataStore.edit { it[KEY_TBM_API_PORT] = port }
    }

    suspend fun saveUiClickSoundsEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_UI_CLICK_SOUNDS] = enabled }
    }

    suspend fun saveAutoBackupEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun saveUsbMountEnabled(enabled: Boolean) {
        appContext.settingsDataStore.edit { it[KEY_USB_MOUNT_ENABLED] = enabled }
    }

    suspend fun isUsbMountEnabled(): Boolean = usbMountEnabledFlow.first()

    suspend fun getTbmApiToken(): String? = tbmApiTokenFlow.first()

    suspend fun getTbmApiPort(): Int = tbmApiPortFlow.first()

    suspend fun syncHuRebootAfterUpdate(
        currentVersionCode: Long,
        elapsedRealtimeMs: Long,
        lastUpdateTimeMs: Long,
        firstInstallTimeMs: Long,
        nowWallClockMs: Long,
    ) {
        appContext.settingsDataStore.edit { prefs ->
            val step = HuRebootAfterUpdate.onAppStart(
                currentVersionCode = currentVersionCode,
                elapsedRealtimeMs = elapsedRealtimeMs,
                lastUpdateTimeMs = lastUpdateTimeMs,
                firstInstallTimeMs = firstInstallTimeMs,
                nowWallClockMs = nowWallClockMs,
                previous = huRebootSnapshotFrom(prefs),
            )
            writeHuRebootSnapshot(prefs, step.snapshot)
        }
    }

    suspend fun markAwaitingHuRebootAfterInstall(installVersionCode: Long) {
        appContext.settingsDataStore.edit { prefs ->
            writeHuRebootSnapshot(
                prefs,
                HuRebootAfterUpdate.markAwaitingInstall(
                    huRebootSnapshotFrom(prefs),
                    installVersionCode,
                ),
            )
        }
    }

    suspend fun markHuRebootStartupPromptShown(currentVersionCode: Long) {
        appContext.settingsDataStore.edit { prefs ->
            writeHuRebootSnapshot(
                prefs,
                HuRebootAfterUpdate.markStartupPromptShown(
                    huRebootSnapshotFrom(prefs),
                    currentVersionCode,
                ),
            )
        }
    }

    suspend fun markHuRebootedAfterUpdate(currentVersionCode: Long) {
        appContext.settingsDataStore.edit { prefs ->
            writeHuRebootSnapshot(
                prefs,
                HuRebootAfterUpdate.markRebooted(
                    huRebootSnapshotFrom(prefs),
                    currentVersionCode,
                ),
            )
        }
    }

    private fun huRebootSnapshotFrom(prefs: Preferences): HuRebootAfterUpdateSnapshot =
        HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = prefs[KEY_HU_REBOOT_LAST_SEEN_VERSION] ?: 0L,
            pendingRebootVersionCode = prefs[KEY_HU_REBOOT_PENDING_VERSION] ?: 0L,
            upgradeDetectedElapsedMs = prefs[KEY_HU_REBOOT_UPGRADE_ELAPSED] ?: 0L,
            startupPromptShownForVersion = prefs[KEY_HU_REBOOT_PROMPT_SHOWN_VERSION] ?: 0L,
            awaitingInstallVersionCode = prefs[KEY_HU_REBOOT_AWAITING_INSTALL_VERSION] ?: 0L,
            upgradeDetectedBootId = prefs[KEY_HU_REBOOT_UPGRADE_BOOT_ID].orEmpty(),
        )

    private fun writeHuRebootSnapshot(
        prefs: MutablePreferences,
        snapshot: HuRebootAfterUpdateSnapshot,
    ) {
        prefs[KEY_HU_REBOOT_LAST_SEEN_VERSION] = snapshot.lastSeenVersionCode
        prefs[KEY_HU_REBOOT_PENDING_VERSION] = snapshot.pendingRebootVersionCode
        prefs[KEY_HU_REBOOT_UPGRADE_ELAPSED] = snapshot.upgradeDetectedElapsedMs
        prefs[KEY_HU_REBOOT_PROMPT_SHOWN_VERSION] = snapshot.startupPromptShownForVersion
        prefs[KEY_HU_REBOOT_AWAITING_INSTALL_VERSION] = snapshot.awaitingInstallVersionCode
        prefs[KEY_HU_REBOOT_UPGRADE_BOOT_ID] = snapshot.upgradeDetectedBootId
    }

    suspend fun launcherAppIconLookup(): LauncherAppIconPaths.Lookup =
        LauncherAppIconPaths.Lookup.None

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            LauncherAppIconPaths.hasSharedOverride(appContext.filesDir, packageName)
        }

    suspend fun clearCustomLauncherAppIcon(packageName: String) {
        withContext(Dispatchers.IO) {
            val dir = LauncherAppIconPaths.sharedIconsDir(appContext.filesDir)
            LauncherAppIconPaths.resolveStoredIconFile(dir, packageName)?.delete()
            bumpIconRevision()
        }
    }

    suspend fun setCustomLauncherAppIconFromUri(
        packageName: String,
        sourceUri: Uri?,
    ): SetLauncherAppCustomIconResult = withContext(Dispatchers.IO) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return@withContext SetLauncherAppCustomIconResult.InvalidPackage
        if (sourceUri == null) return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0
        }.getOrDefault(false)
        if (!boundsRead) return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        if (boundsOpts.outWidth > MAX_ICON_EDGE_PX || boundsOpts.outHeight > MAX_ICON_EDGE_PX) {
            return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
        }
        val decoded = runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        if (decoded.width > MAX_ICON_EDGE_PX || decoded.height > MAX_ICON_EDGE_PX) {
            decoded.recycle()
            return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
        }
        val dir = LauncherAppIconPaths.sharedIconsDir(appContext.filesDir)
        if (!dir.exists() && !dir.mkdirs()) {
            decoded.recycle()
            return@withContext SetLauncherAppCustomIconResult.CopyFailed
        }
        val dest = LauncherAppIconPaths.liveIconFile(dir, pkg)
        val ok = runCatching {
            FileOutputStream(dest).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.isSuccess
        decoded.recycle()
        if (!ok) return@withContext SetLauncherAppCustomIconResult.CopyFailed
        bumpIconRevision()
        SetLauncherAppCustomIconResult.Success
    }

    private suspend fun bumpIconRevision() {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_ICON_REVISION] = (prefs[KEY_ICON_REVISION] ?: 0) + 1
        }
    }
}
