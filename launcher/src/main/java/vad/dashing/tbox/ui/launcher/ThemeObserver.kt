package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Observes system day/night theme settings changes via ContentObserver and updates
 * [LauncherThemeState] when followSystem (autoThemeSystemEnabled) is true.
 */
object ThemeObserver {
    private var registered = false
    private var appContext: Context? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val context = appContext ?: return
            if (LauncherThemeState.autoThemeSystemEnabled) {
                val isDark = HeadUnitDayNightRepository.isSystemDark(context)
                if (isDark != null) {
                    LauncherThemeState.setDarkThemeFromSystem(context, isDark)
                }
            }
        }
    }

    fun start(context: Context) {
        if (registered) return
        val app = context.applicationContext
        appContext = app
        val resolver = app.contentResolver

        runCatching {
            // Observe Settings.System "DAY_NIGHT_STATUS"
            val statusUri = Settings.System.getUriFor(HeadUnitDayNightMapping.DAY_NIGHT_STATUS_KEY)
            resolver.registerContentObserver(statusUri, false, observer)

            // Observe Settings.Global "com.mb.provider.night_mode_auto"
            val mbUri = Settings.Global.getUriFor(HeadUnitDayNightMapping.MB_NIGHT_MODE_AUTO_KEY)
            resolver.registerContentObserver(mbUri, false, observer)

            // Observe Settings.Global "com.adayo.provider.night_mode_auto"
            val adayoUri = Settings.Global.getUriFor(HeadUnitDayNightMapping.ADAYO_NIGHT_MODE_AUTO_KEY)
            resolver.registerContentObserver(adayoUri, false, observer)

            registered = true
        }

        if (LauncherThemeState.autoThemeSystemEnabled) {
            syncFromSystem(app)
        }
    }

    fun stop() {
        if (!registered) return
        val app = appContext ?: return
        runCatching {
            app.contentResolver.unregisterContentObserver(observer)
        }
        registered = false
        appContext = null
    }

    fun syncFromSystem(context: Context) {
        val isDark = HeadUnitDayNightRepository.isSystemDark(context)
        if (isDark != null) {
            LauncherThemeState.setDarkThemeFromSystem(context, isDark)
        }
    }
}
