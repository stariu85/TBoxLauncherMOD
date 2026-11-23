package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.app.UiModeManager
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
            // Only refocus if a Yandex navigation app is ACTUALLY currently active on screen
            val activePkg = LauncherActiveAppTracker.activeAppPackage()
            if (activePkg.isNullOrBlank() || !isYandexPackage(activePkg)) {
                return@runCatching
            }

            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val taskId = LauncherActiveAppTracker.activeTaskId()?.takeIf { it > 0 }
                ?: findTaskIdForPackage(am, activePkg)

            if (taskId != null && taskId > 0) {
                Log.w("LauncherThemeState", "Refocusing active Yandex task=$taskId pkg=$activePkg on theme change")
                moveTaskToFrontId(appCtx, taskId)
                LauncherAmStackShell.focusTask(taskId)
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
    private const val KEY_WINTER_THEME = "winter_theme_enabled"
    private const val KEY_WINTER_TEMP_ZERO = "winter_theme_temp_zero_enabled"
    private const val KEY_RAIN_THEME = "rain_theme_enabled"
    private const val KEY_AUTO_WEATHER = "auto_weather_rain_snow_enabled"

    var darkTheme by mutableStateOf(true)
        private set

    var autoThemeSystemEnabled by mutableStateOf(false)
        private set

    var autoThemeSolarEnabled by mutableStateOf(false)
        private set

    var themeButtonVisible by mutableStateOf(false)
        private set

    var winterThemeEnabled by mutableStateOf(false)
        private set

    var winterThemeTempBelowZeroEnabled by mutableStateOf(true)
        private set

    var rainThemeEnabled by mutableStateOf(false)
        private set

    var autoWeatherEnabled by mutableStateOf(false)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkTheme = prefs.getBoolean(KEY_DARK, true)
        autoThemeSystemEnabled = prefs.getBoolean(KEY_AUTO_SYSTEM, false)
        autoThemeSolarEnabled = prefs.getBoolean(KEY_AUTO_SOLAR, false)
        themeButtonVisible = prefs.getBoolean(KEY_THEME_BUTTON_VISIBLE, false)
        winterThemeEnabled = prefs.getBoolean(KEY_WINTER_THEME, false)
        winterThemeTempBelowZeroEnabled = prefs.getBoolean(KEY_WINTER_TEMP_ZERO, true)
        rainThemeEnabled = prefs.getBoolean(KEY_RAIN_THEME, false)
        autoWeatherEnabled = prefs.getBoolean(KEY_AUTO_WEATHER, false)

        LauncherAutoWeatherManager.start()

        if (autoThemeSystemEnabled && autoThemeSolarEnabled) {
            autoThemeSolarEnabled = false
        }

        ThemeObserver.start(context)

        if (autoThemeSystemEnabled) {
            val systemDark = HeadUnitDayNightRepository.isSystemDark(context)
            if (systemDark != null) {
                darkTheme = systemDark
                HeadUnitDayNightRepository.setAndroidNightMode(
                    context,
                    if (systemDark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
                )
            }
        } else if (autoThemeSolarEnabled) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val isDaytime = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                val targetDark = !isDaytime
                if (darkTheme != targetDark) {
                    darkTheme = targetDark
                    HeadUnitDayNightRepository.setSystemDark(context, darkTheme)
                }
            }
        }
    }

    /**
     * Synchronizes current launcher theme state to the head unit system.
     * Called during Stage 2 Warmup when the system is ready to receive broadcasts.
     * Ensures that if manual light or dark mode is selected, the system UI
     * matches the launcher state.
     */
    fun syncThemeToSystem(context: Context) {
        if (autoThemeSystemEnabled) {
            ThemeObserver.syncFromSystem(context)
        } else if (autoThemeSolarEnabled) {
            val coords = resolveBestGpsCoordinates(context)
            if (coords != null) {
                val (lat, lon) = coords
                val isDaytime = SolarTwilightCalculator.isDaytime(latitude = lat, longitude = lon)
                val targetDark = !isDaytime
                if (darkTheme != targetDark) {
                    darkTheme = targetDark
                    HeadUnitDayNightRepository.setSystemDark(context, darkTheme)
                }
            }
        } else {
            val systemDark = HeadUnitDayNightRepository.isSystemDark(context)
            if (systemDark != darkTheme) {
                HeadUnitDayNightRepository.setSystemDark(context, darkTheme)
            }
        }
    }

    /**
     * Updates darkTheme from launcher UI or manual user toggle.
     * Also updates head unit system theme.
     */
    fun setDarkTheme(context: Context, enabled: Boolean) {
        if (darkTheme == enabled) return
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
        HeadUnitDayNightRepository.setAndroidNightMode(
            context,
            if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
        )
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
                setDarkTheme(context, !isDaytime)
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SOLAR, autoThemeSolarEnabled)
            .putBoolean(KEY_AUTO_SYSTEM, autoThemeSystemEnabled)
            .apply()
    }

    fun setWinterThemeEnabled(context: Context, enabled: Boolean) {
        if (winterThemeEnabled == enabled) return
        winterThemeEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WINTER_THEME, enabled).apply()
    }

    fun setWinterThemeTempBelowZeroEnabled(context: Context, enabled: Boolean) {
        if (winterThemeTempBelowZeroEnabled == enabled) return
        winterThemeTempBelowZeroEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WINTER_TEMP_ZERO, enabled).apply()
    }

    fun setRainThemeEnabled(context: Context, enabled: Boolean) {
        if (rainThemeEnabled == enabled) return
        rainThemeEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RAIN_THEME, enabled).apply()
    }

    fun setAutoWeatherEnabled(context: Context, enabled: Boolean) {
        if (autoWeatherEnabled == enabled) return
        autoWeatherEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_WEATHER, enabled).apply()
    }
}
