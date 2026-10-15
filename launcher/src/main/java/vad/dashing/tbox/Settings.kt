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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
class SettingsManager(private val context: Context) {

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
        private val KEY_HU_REBOOT_LAST_SEEN_VERSION =
            longPreferencesKey("hu_reboot_last_seen_version")
        private val KEY_HU_REBOOT_PENDING_VERSION =
            longPreferencesKey("hu_reboot_pending_version")
        private val KEY_HU_REBOOT_UPGRADE_ELAPSED =
            longPreferencesKey("hu_reboot_upgrade_elapsed")
        private val KEY_HU_REBOOT_PROMPT_SHOWN_VERSION =
            longPreferencesKey("hu_reboot_prompt_shown_version")
        private val KEY_HU_REBOOT_AWAITING_INSTALL_VERSION =
            longPreferencesKey("hu_reboot_awaiting_install_version")
        private val KEY_TBM_API_TOKEN = stringPreferencesKey("tbm_api_token")
        private val KEY_TBM_API_PORT = intPreferencesKey("tbm_api_port")
        private val KEY_UI_CLICK_SOUNDS = booleanPreferencesKey("ui_click_sounds_enabled")
        private const val MAX_ICON_EDGE_PX = 512
    }

    private val _iconRevision = MutableStateFlow(0)
    val launcherAppIconRevisionFlow: Flow<Int> = _iconRevision.asStateFlow()

    val uiClickSoundsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_UI_CLICK_SOUNDS] ?: true
    }

    val headUnitCanModeFlow: Flow<HeadUnitCanMode> = context.settingsDataStore.data.map { prefs ->
        HeadUnitCanMode.fromStorageValue(prefs[KEY_HEAD_UNIT_CAN])
    }

    val canAutoBindEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CAN_AUTO_BIND] ?: true
    }

    val canAutoBindLockedFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CAN_AUTO_BIND_LOCKED] ?: false
    }

    val updateChannelFlow: Flow<UpdateChannel> = context.settingsDataStore.data.map { prefs ->
        UpdateChannel.fromStorageValue(prefs[KEY_UPDATE_CHANNEL])
    }

    val updateCheckEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_UPDATE_CHECK_ENABLED] ?: true
    }

    val tbmApiTokenFlow: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_TBM_API_TOKEN]
    }

    val tbmApiPortFlow: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_TBM_API_PORT] ?: 8765
    }

    val huRebootAfterUpdateFlow: Flow<HuRebootAfterUpdateSnapshot> =
        context.settingsDataStore.data.map { huRebootSnapshotFrom(it) }

    suspend fun saveHeadUnitCanMode(mode: HeadUnitCanMode) {
        context.settingsDataStore.edit { it[KEY_HEAD_UNIT_CAN] = mode.storageValue }
    }

    suspend fun saveCanAutoBindEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND] = enabled }
    }

    suspend fun saveCanAutoBindLocked(locked: Boolean) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_LOCKED] = locked }
    }

    suspend fun saveCanAutoBindLastPrimaryMode(mode: HeadUnitCanMode?) {
        context.settingsDataStore.edit {
            if (mode == null) it.remove(KEY_CAN_AUTO_BIND_PRIMARY)
            else it[KEY_CAN_AUTO_BIND_PRIMARY] = mode.storageValue
        }
    }

    suspend fun saveCanAutoBindLastResult(result: String) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_RESULT] = result }
    }

    suspend fun saveUpdateChannel(channel: UpdateChannel) {
        context.settingsDataStore.edit { it[KEY_UPDATE_CHANNEL] = channel.storageValue }
    }

    suspend fun saveUpdateCheckEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_UPDATE_CHECK_ENABLED] = enabled }
    }

    suspend fun saveTbmApiToken(token: String?) {
        context.settingsDataStore.edit {
            if (token.isNullOrEmpty()) it.remove(KEY_TBM_API_TOKEN)
            else it[KEY_TBM_API_TOKEN] = token
        }
    }

    suspend fun saveTbmApiPort(port: Int) {
        context.settingsDataStore.edit { it[KEY_TBM_API_PORT] = port }
    }

    suspend fun saveUiClickSoundsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_UI_CLICK_SOUNDS] = enabled }
    }

    suspend fun getTbmApiToken(): String? = tbmApiTokenFlow.first()

    suspend fun getTbmApiPort(): Int = tbmApiPortFlow.first()

    suspend fun syncHuRebootAfterUpdate(
        currentVersionCode: Long,
        elapsedRealtimeMs: Long,
        lastUpdateTimeMs: Long,
        firstInstallTimeMs: Long,
        nowWallClockMs: Long,
    ) {
        context.settingsDataStore.edit { prefs ->
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
        context.settingsDataStore.edit { prefs ->
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
        context.settingsDataStore.edit { prefs ->
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
        context.settingsDataStore.edit { prefs ->
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
    }

    suspend fun launcherAppIconLookup(): LauncherAppIconPaths.Lookup =
        LauncherAppIconPaths.Lookup.None

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            LauncherAppIconPaths.hasSharedOverride(context.filesDir, packageName)
        }

    suspend fun clearCustomLauncherAppIcon(packageName: String) {
        withContext(Dispatchers.IO) {
            val dir = LauncherAppIconPaths.sharedIconsDir(context.filesDir)
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
        val decoded = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        if (decoded.width > MAX_ICON_EDGE_PX || decoded.height > MAX_ICON_EDGE_PX) {
            decoded.recycle()
            return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
        }
        val dir = LauncherAppIconPaths.sharedIconsDir(context.filesDir)
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

    private fun bumpIconRevision() {
        _iconRevision.update { it + 1 }
        // Persist so cold start keeps a non-zero counter for caches that key on it.
        // Fire-and-forget: revision is also kept in memory for the process lifetime.
    }
}
