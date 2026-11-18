package vad.dashing.tbox.ui.launcher

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.UniversalCanRepository

object HeadUnitDayNightMapping {
    const val NIGHT_MODE_LIGHT_MANUAL = 0
    const val NIGHT_MODE_AUTO = 1
    const val NIGHT_MODE_DARK_MANUAL = 2

    const val DAY_NIGHT_STATUS_KEY = "DAY_NIGHT_STATUS"
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val MB_NIGHT_MODE_AUTO_KEY = "com.mb.provider.night_mode_auto"
    const val ADAYO_NIGHT_MODE_AUTO_KEY = "com.adayo.provider.night_mode_auto"

    fun usesAdayoKeys(canMode: HeadUnitCanMode = UniversalCanRepository.mode.value): Boolean {
        return canMode == HeadUnitCanMode.Android10Vhal || Build.VERSION.SDK_INT >= 29
    }

    fun getNightModeAutoKey(canMode: HeadUnitCanMode): String {
        return if (usesAdayoKeys(canMode)) ADAYO_NIGHT_MODE_AUTO_KEY else MB_NIGHT_MODE_AUTO_KEY
    }
}

/**
 * Repository for reading and writing head unit system day/night theme settings.
 * Supports Android 9 (mbCAN / Mengbo) and Android 10 (Adayo / VHAL).
 */
object HeadUnitDayNightRepository {
    private const val TAG = "HeadUnitDayNightRepo"

    fun getCurrentCanMode(): HeadUnitCanMode {
        return UniversalCanRepository.mode.value
    }

    fun getSystemNightModeAuto(context: Context): Int? {
        val resolver = context.contentResolver
        val canMode = getCurrentCanMode()
        val key = HeadUnitDayNightMapping.getNightModeAutoKey(canMode)
        return runCatching {
            Settings.Global.getInt(resolver, key)
        }.getOrNull()
    }

    fun setSystemNightModeAuto(context: Context, modeValue: Int): Boolean {
        val resolver = context.contentResolver
        val canMode = getCurrentCanMode()
        val key = HeadUnitDayNightMapping.getNightModeAutoKey(canMode)
        return runCatching {
            Settings.Global.putInt(resolver, key, modeValue)
        }.onFailure {
            Log.w(TAG, "Failed to write $key=$modeValue to Settings.Global", it)
        }.getOrDefault(false)
    }

    /**
     * Reads system theme status from Settings.System "DAY_NIGHT_STATUS".
     * Returns 1 (THEME_LIGHT), 2 (THEME_DARK), or null if missing.
     */
    fun getSystemDayNightStatus(context: Context): Int? {
        val resolver = context.contentResolver
        return runCatching {
            Settings.System.getInt(resolver, HeadUnitDayNightMapping.DAY_NIGHT_STATUS_KEY)
        }.getOrNull()
    }

    /**
     * Writes system theme status to Settings.System "DAY_NIGHT_STATUS".
     * 1 = Light, 2 = Dark.
     */
    fun setSystemDayNightStatus(context: Context, status: Int): Boolean {
        val resolver = context.contentResolver
        return runCatching {
            Settings.System.putInt(resolver, HeadUnitDayNightMapping.DAY_NIGHT_STATUS_KEY, status)
        }.onFailure {
            Log.w(TAG, "Failed to write DAY_NIGHT_STATUS=$status to Settings.System", it)
        }.getOrDefault(false)
    }

    /**
     * Updates Android system-wide night mode using [UiModeManager] and Settings.Secure "ui_night_mode".
     * [mode] is [UiModeManager.MODE_NIGHT_YES], [UiModeManager.MODE_NIGHT_NO], or [UiModeManager.MODE_NIGHT_AUTO].
     */
    fun setAndroidNightMode(context: Context, mode: Int): Boolean {
        var success = false
        runCatching {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            uiModeManager?.setNightMode(mode)
            success = true
        }.onFailure {
            Log.w(TAG, "Failed to set UiModeManager nightMode=$mode", it)
        }

        runCatching {
            Settings.Secure.putInt(context.contentResolver, "ui_night_mode", mode)
        }.onFailure {
            Log.w(TAG, "Failed to write ui_night_mode to Settings.Secure", it)
        }

        return success
    }

    /**
     * Determines whether system theme is dark.
     * Checks DAY_NIGHT_STATUS first (2 = dark, 1 = light).
     * If missing, falls back to checking night_mode_auto (2 = dark, 0 = light).
     * If still missing, falls back to UiModeManager.
     */
    fun isSystemDark(context: Context): Boolean? {
        val status = getSystemDayNightStatus(context)
        if (status != null) {
            return when (status) {
                HeadUnitDayNightMapping.THEME_DARK -> true
                HeadUnitDayNightMapping.THEME_LIGHT -> false
                else -> null
            }
        }
        val autoMode = getSystemNightModeAuto(context)
        if (autoMode != null) {
            return when (autoMode) {
                HeadUnitDayNightMapping.NIGHT_MODE_DARK_MANUAL -> true
                HeadUnitDayNightMapping.NIGHT_MODE_LIGHT_MANUAL -> false
                else -> null
            }
        }
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val nightMode = uiModeManager?.nightMode
        if (nightMode != null) {
            return when (nightMode) {
                UiModeManager.MODE_NIGHT_YES -> true
                UiModeManager.MODE_NIGHT_NO -> false
                else -> null
            }
        }
        return null
    }

    /**
     * Sets system theme to dark or light manually.
     * 1. Updates Settings.System "DAY_NIGHT_STATUS" (1 = light, 2 = dark).
     * 2. Updates Settings.Global "night_mode_auto" (0 = manual light, 2 = manual dark).
     * 3. Updates Android system-wide night mode (UiModeManager & Settings.Secure "ui_night_mode").
     */
    fun setSystemDark(context: Context, dark: Boolean): Boolean {
        val status = if (dark) {
            HeadUnitDayNightMapping.THEME_DARK
        } else {
            HeadUnitDayNightMapping.THEME_LIGHT
        }
        val statusResult = setSystemDayNightStatus(context, status)

        val mode = if (dark) {
            HeadUnitDayNightMapping.NIGHT_MODE_DARK_MANUAL
        } else {
            HeadUnitDayNightMapping.NIGHT_MODE_LIGHT_MANUAL
        }
        val autoResult = setSystemNightModeAuto(context, mode)

        val androidNightMode = if (dark) {
            UiModeManager.MODE_NIGHT_YES
        } else {
            UiModeManager.MODE_NIGHT_NO
        }
        val uiModeResult = setAndroidNightMode(context, androidNightMode)

        return statusResult || autoResult || uiModeResult
    }

    /**
     * Sets system theme to AUTO (NIGHT_MODE_AUTO = 1).
     */
    fun setSystemAuto(context: Context): Boolean {
        val autoResult = setSystemNightModeAuto(context, HeadUnitDayNightMapping.NIGHT_MODE_AUTO)
        setAndroidNightMode(context, UiModeManager.MODE_NIGHT_AUTO)
        return autoResult
    }
}
