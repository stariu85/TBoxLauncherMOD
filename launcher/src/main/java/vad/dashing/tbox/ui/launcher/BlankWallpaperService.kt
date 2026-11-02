package vad.dashing.tbox.ui.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import vad.dashing.tbox.R

/**
 * System Live Wallpaper service providing a pure solid black background.
 * Setting this component as the active live wallpaper in Android settings
 * avoids boot delays on head units when System UI is hidden or disabled.
 */
class BlankWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return BlankWallpaperEngine()
    }

    private inner class BlankWallpaperEngine : Engine() {
        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                drawBlack()
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            drawBlack()
        }

        private fun drawBlack() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.drawColor(Color.BLACK)
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing black wallpaper canvas", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unlocking canvas", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BlankWallpaperService"

        /**
         * Sets static solid black wallpaper to system WallpaperManager.
         */
        fun setSystemBlackWallpaper(context: Context): Boolean {
            return try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val metrics = context.resources.displayMetrics
                val width = if (metrics.widthPixels > 0) metrics.widthPixels else 1920
                val height = if (metrics.heightPixels > 0) metrics.heightPixels else 1080
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.BLACK)
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                )
                bitmap.recycle()
                Toast.makeText(
                    context,
                    context.getString(R.string.launcher_system_wallpaper_set_success),
                    Toast.LENGTH_SHORT,
                ).show()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set system wallpaper", e)
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.launcher_system_wallpaper_set_error,
                        e.localizedMessage ?: e.message ?: "",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                false
            }
        }

        /**
         * Opens Android Live Wallpaper settings to let the user select/activate [BlankWallpaperService].
         * First closes any active settings overlay window, then launches the target settings in embedded mode.
         */
        fun openSystemWallpaperSettings(context: Context) {
            closeVehicleSettingsOverlay()
            LauncherAboutOverlayWindow.hide()

            val wallpaperComponent = ComponentName(context, BlankWallpaperService::class.java)

            val candidateIntents = listOf(
                // 1. Standard Live Wallpaper change intent targeting our service
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        wallpaperComponent,
                    )
                },
                // 2. Explicit Live Wallpaper Picker activity (com.android.wallpaper.livepicker)
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    setClassName(
                        "com.android.wallpaper.livepicker",
                        "com.android.wallpaper.livepicker.LiveWallpaperActivity",
                    )
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        wallpaperComponent,
                    )
                },
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    setClassName(
                        "com.android.wallpaper.livepicker",
                        "com.android.wallpaper.livepicker.LiveWallpaperChangeAndPreviewActivity",
                    )
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        wallpaperComponent,
                    )
                },
                // 3. Generic set wallpaper intent
                Intent(Intent.ACTION_SET_WALLPAPER),
                // 4. Android Settings -> Display
                Intent(Settings.ACTION_DISPLAY_SETTINGS),
                // 5. General Android Settings
                Intent(Settings.ACTION_SETTINGS),
            )

            for (intent in candidateIntents) {
                if (tryLaunchSettingsIntentInEmbeddedMode(context, intent)) {
                    Log.i(TAG, "Successfully launched wallpaper/display settings intent in embedded mode: ${intent.action}")
                    return
                }
            }

            // Fallback: try opening AC settings
            val acSettings = context.packageManager.getLaunchIntentForPackage("com.mengbo.acsettings")
            if (acSettings != null && tryLaunchSettingsIntentInEmbeddedMode(context, acSettings)) {
                return
            }

            Toast.makeText(
                context,
                context.getString(R.string.launcher_system_wallpaper_open_failed),
                Toast.LENGTH_LONG,
            ).show()
        }

        private fun tryLaunchSettingsIntentInEmbeddedMode(context: Context, intent: Intent): Boolean {
            val bounds = LauncherEmbeddedBoundsState.embeddedBounds()
            val resolved = intent.resolveActivity(context.packageManager)
            if (bounds != null && resolved != null) {
                val targeted = Intent(intent).apply { component = resolved }
                if (tryLaunchIntentInBounds(context, resolved.packageName, targeted, bounds)) {
                    return true
                }
            }

            // Fallback 1: try freeform launch helper
            if (tryLaunchSettingsIntentInFreeform(context, intent)) return true

            // Fallback 2: standard startActivity
            return runCatching {
                val target = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(target)
                true
            }.getOrDefault(false)
        }
    }
}
