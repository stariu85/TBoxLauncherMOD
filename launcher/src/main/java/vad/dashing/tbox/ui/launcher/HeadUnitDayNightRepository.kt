package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Build
import android.provider.Settings
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

    fun usesAdayoKeys(canMode: HeadUnitCanMode): Boolean {
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
     * Determines whether system theme is dark.
     * Checks DAY_NIGHT_STATUS first (2 = dark, 1 = light).
     * If missing, falls back to checking night_mode_auto (2 = dark, 0 = light).
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
        return null
    }

    /**
     * Sets system theme to dark or light manually.
     * Dark -> NIGHT_MODE_DARK_MANUAL (2)
     * Light -> NIGHT_MODE_LIGHT_MANUAL (0)
     */
    fun setSystemDark(context: Context, dark: Boolean): Boolean {
        val mode = if (dark) {
            HeadUnitDayNightMapping.NIGHT_MODE_DARK_MANUAL
        } else {
            HeadUnitDayNightMapping.NIGHT_MODE_LIGHT_MANUAL
        }
        return setSystemNightModeAuto(context, mode)
    }

    /**
     * Sets system theme to AUTO (NIGHT_MODE_AUTO = 1).
     */
    fun setSystemAuto(context: Context): Boolean {
        return setSystemNightModeAuto(context, HeadUnitDayNightMapping.NIGHT_MODE_AUTO)
    }
}
