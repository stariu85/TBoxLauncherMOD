package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * Super lightweight, instant black activity launched when 360 Camera (AVM) starts.
 *
 * Placed on top of the Activity task stack, forcing Android's ActivityManager to transition
 * background applications (e.g. Yandex Music, Navigation) into `onStop()`.
 * This hides their `Surface` and completely stops their RenderThread/GPU processing,
 * freeing 100% GPU for the 360 Camera stream.
 */
@Suppress("DEPRECATION")
class LauncherAvmBlankActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        overridePendingTransition(0, 0)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        val bgColor = getThemeBackgroundColor(this)
        val bgView = View(this).apply {
            setBackgroundColor(bgColor)
        }
        setContentView(bgView)
        Log.i(TAG, "LauncherAvmBlankActivity created and active (background apps paused)")
    }

    override fun onResume() {
        super.onResume()
        overridePendingTransition(0, 0)
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "LauncherAvmBlankActivity destroyed")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "LauncherAvmBlankAct"

        internal fun getThemeBackgroundColor(context: Context): Int {
            val isDark = runCatching {
                val config = context.resources.configuration
                val systemDark = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (LauncherThemeState.autoThemeSystemEnabled) systemDark else LauncherThemeState.darkTheme
            }.getOrDefault(LauncherThemeState.darkTheme)

            return if (isDark) {
                Color.parseColor("#1A1D24")
            } else {
                Color.parseColor("#E5E7EB")
            }
        }

        @Volatile
        private var instance: LauncherAvmBlankActivity? = null

        fun isShowing(): Boolean = instance != null

        fun show(context: Context) {
            if (isShowing()) return
            val intent = Intent(context, LauncherAvmBlankActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            runCatching {
                context.startActivity(intent)
                Log.i(TAG, "Starting LauncherAvmBlankActivity to pause foreign app render")
            }.onFailure { e ->
                Log.e(TAG, "Failed to start LauncherAvmBlankActivity", e)
            }
        }

        fun hide() {
            val act = instance ?: return
            instance = null
            runCatching {
                act.finish()
                act.overridePendingTransition(0, 0)
                Log.i(TAG, "Hiding LauncherAvmBlankActivity")
            }.onFailure { e ->
                Log.e(TAG, "Failed to finish LauncherAvmBlankActivity", e)
            }
        }
    }
}
