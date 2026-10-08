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

    var darkTheme by mutableStateOf(true)
        private set

    fun init(context: Context) {
        darkTheme = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true)
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        if (darkTheme == enabled) return
        darkTheme = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()
    }
}
