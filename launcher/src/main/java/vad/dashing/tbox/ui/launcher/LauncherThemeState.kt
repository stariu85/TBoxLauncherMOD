package vad.dashing.tbox.ui.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Runtime light/dark theme for the launcher left panel. Persisted in its own prefs file;
 * Compose recomposition is driven by the observable [darkTheme] flag read via
 * [LauncherColors] getters.
 */
object LauncherThemeState {
    private const val PREFS = "tbox_launcher_theme"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_AUTO_HEADLIGHTS = "auto_theme_headlights_enabled"
    private const val KEY_AUTO_SOLAR = "auto_theme_solar_enabled"

    var darkTheme by mutableStateOf(true)
        private set

    var autoThemeHeadlightsEnabled by mutableStateOf(false)
        private set

    var autoThemeSolarEnabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkTheme = prefs.getBoolean(KEY_DARK, true)
        autoThemeHeadlightsEnabled = prefs.getBoolean(KEY_AUTO_HEADLIGHTS, false)
        autoThemeSolarEnabled = prefs.getBoolean(KEY_AUTO_SOLAR, false)
        if (autoThemeHeadlightsEnabled && autoThemeSolarEnabled) {
            autoThemeSolarEnabled = false
        }
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        if (darkTheme == enabled) return
        darkTheme = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()
    }

    fun setAutoThemeHeadlightsEnabled(context: Context, enabled: Boolean) {
        if (autoThemeHeadlightsEnabled == enabled) return
        autoThemeHeadlightsEnabled = enabled
        if (enabled && autoThemeSolarEnabled) {
            autoThemeSolarEnabled = false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_HEADLIGHTS, autoThemeHeadlightsEnabled)
            .putBoolean(KEY_AUTO_SOLAR, autoThemeSolarEnabled)
            .apply()
    }

    fun setAutoThemeSolarEnabled(context: Context, enabled: Boolean) {
        if (autoThemeSolarEnabled == enabled) return
        autoThemeSolarEnabled = enabled
        if (enabled && autoThemeHeadlightsEnabled) {
            autoThemeHeadlightsEnabled = false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SOLAR, autoThemeSolarEnabled)
            .putBoolean(KEY_AUTO_HEADLIGHTS, autoThemeHeadlightsEnabled)
            .apply()
    }
}
