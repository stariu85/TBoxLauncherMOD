package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val YANDEX_NAVIGATION_PACKAGES = setOf(
    "ru.yandex.yandexnavi",
    "ru.yandex.yandexmaps",
    "ru.yandex.mobile.navigator",
    "ru.yandex.yandexnavi.sandbox"
)

private fun isYandexPackage(pkg: String): Boolean {
    return pkg in YANDEX_NAVIGATION_PACKAGES ||
            pkg.startsWith("ru.yandex.yandexnavi") ||
            pkg.startsWith("ru.yandex.yandexmaps")
}

internal fun refocusYandexAppIfActive(context: Context) {
    val appCtx = context.applicationContext
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // 1. Check currently tracked active app first
            val activePkg = LauncherActiveAppTracker.activeAppPackage()
            var targetPkg: String? = if (!activePkg.isNullOrBlank() && isYandexPackage(activePkg)) {
                activePkg
            } else null

            // 2. If activePkg is not Yandex, search running stacks/tasks for Yandex Navigation/Maps
            if (targetPkg == null) {
                targetPkg = YANDEX_NAVIGATION_PACKAGES.firstOrNull { pkg ->
                    findTaskIdForPackage(am, pkg) != null
                }
            }

            if (targetPkg != null) {
                var taskId: Int? = if (targetPkg == activePkg) {
                    LauncherActiveAppTracker.activeTaskId()?.takeIf { it > 0 }
                } else null

                if (taskId == null) {
                    taskId = findTaskIdForPackage(am, targetPkg)
                }

                if (taskId != null && taskId > 0) {
                    Log.w("LauncherThemeState", "Refocusing Yandex task=$taskId pkg=$targetPkg on theme change")
                    moveTaskToFrontId(appCtx, taskId)
                    LauncherAmStackShell.focusTask(taskId)
                }
            }
        }.onFailure {
            Log.w("LauncherThemeState", "Failed to refocus Yandex on theme change", it)
        }
    }, 400L)
}

/**
 * Runtime light/dark theme for the launcher UI. Persisted in prefs;
 * Compose recomposition is driven by the observable [darkTheme] flag read via
 * [LauncherColors] getters.
 */
object LauncherThemeState {
    private const val PREFS = "tbox_launcher_theme"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_AUTO_SYSTEM = "auto_theme_system_enabled"
    private const val KEY_AUTO_SOLAR = "auto_theme_solar_enabled"
    private const val KEY_THEME_BUTTON_VISIBLE = "theme_button_visible"

    var darkTheme by mutableStateOf(true)
        private set

    var autoThemeSystemEnabled by mutableStateOf(true)
        private set

    var autoThemeSolarEnabled by mutableStateOf(false)
        private set

    var themeButtonVisible by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkTheme = prefs.getBoolean(KEY_DARK, true)
        autoThemeSystemEnabled = prefs.getBoolean(KEY_AUTO_SYSTEM, true)
        autoThemeSolarEnabled = prefs.getBoolean(KEY_AUTO_SOLAR, false)
        themeButtonVisible = prefs.getBoolean(KEY_THEME_BUTTON_VISIBLE, true)

        if (autoThemeSystemEnabled && autoThemeSolarEnabled) {
            autoThemeSolarEnabled = false
        }

        ThemeObserver.start(context)

        if (autoThemeSystemEnabled) {
            val systemDark = HeadUnitDayNightRepository.isSystemDark(context)
            if (systemDark != null) {
                darkTheme = systemDark
            }
        } else if (autoThemeSolarEnabled) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val isDaytime = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                darkTheme = !isDaytime
                HeadUnitDayNightRepository.setSystemDark(context, darkTheme)
            }
        }
    }

    /**
     * Updates darkTheme from launcher UI or manual user toggle.
     * Also updates head unit system theme.
     */
    fun setDarkTheme(context: Context, enabled: Boolean) {
        if (darkTheme == enabled) {
            HeadUnitDayNightRepository.setSystemDark(context, enabled)
            refocusYandexAppIfActive(context)
            return
        }
        darkTheme = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()
        HeadUnitDayNightRepository.setSystemDark(context, enabled)
        refocusYandexAppIfActive(context)
    }

    /**
     * Internal update from [ThemeObserver] when system theme changes.
     * Updates [darkTheme] without re-writing back to system settings.
     */
    fun setDarkThemeFromSystem(context: Context, enabled: Boolean) {
        if (darkTheme == enabled) return
        darkTheme = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()
        refocusYandexAppIfActive(context)
    }

    fun setThemeButtonVisible(context: Context, visible: Boolean) {
        if (themeButtonVisible == visible) return
        themeButtonVisible = visible
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_THEME_BUTTON_VISIBLE, visible).apply()
    }

    fun setAutoThemeSystemEnabled(context: Context, enabled: Boolean) {
        if (autoThemeSystemEnabled == enabled) return
        autoThemeSystemEnabled = enabled
        if (enabled && autoThemeSolarEnabled) {
            autoThemeSolarEnabled = false
        }
        if (enabled) {
            HeadUnitDayNightRepository.setSystemAuto(context)
            ThemeObserver.syncFromSystem(context)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SYSTEM, autoThemeSystemEnabled)
            .putBoolean(KEY_AUTO_SOLAR, autoThemeSolarEnabled)
            .apply()
    }

    fun setAutoThemeSolarEnabled(context: Context, enabled: Boolean) {
        if (autoThemeSolarEnabled == enabled) return
        autoThemeSolarEnabled = enabled
        if (enabled && autoThemeSystemEnabled) {
            autoThemeSystemEnabled = false
        }
        if (enabled) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val isDaytime = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                darkTheme = !isDaytime
                HeadUnitDayNightRepository.setSystemDark(context, darkTheme)
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SOLAR, autoThemeSolarEnabled)
            .putBoolean(KEY_AUTO_SYSTEM, autoThemeSystemEnabled)
            .putBoolean(KEY_DARK, darkTheme)
            .apply()
    }
}
