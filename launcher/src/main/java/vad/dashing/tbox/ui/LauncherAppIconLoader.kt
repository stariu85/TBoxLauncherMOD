package vad.dashing.tbox.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.zip.ZipFile

/**
 * Icon resolution for the HU app drawer:
 * 1. OEM themed `app_ic_*` from LauncherWT / WT theme (what stock Launcher3 shows)
 * 2. PackageManager / adaptive drawables (reject green-robot defaults)
 * 3. PNG from the app APK zip (only if not the Studio robot template)
 * 4. Letter tile
 *
 * Important: many MB_* packages ship a *wrong* shared `ic_launcher.png` (e.g. 360AVM
 * has a car/settings glyph). Prefer [OemThemeAppIcons] over APK PNG.
 */
internal object LauncherAppIconLoader {
    private const val TAG = "LauncherIcons"

    /** Used by [OemThemeAppIcons] — skips robot/transparency rejection that theme art never needs. */
    fun rasterizeForTheme(drawable: Drawable?, sizePx: Int): ImageBitmap? = rasterize(drawable, sizePx)

    private val apkIconEntryNames = listOf(
        "res/mipmap-xxxhdpi-v4/ic_launcher.png",
        "res/mipmap-xxxhdpi/ic_launcher.png",
        "res/mipmap-xxhdpi-v4/ic_launcher.png",
        "res/mipmap-xxhdpi/ic_launcher.png",
        "res/mipmap-xhdpi-v4/ic_launcher.png",
        "res/mipmap-xhdpi/ic_launcher.png",
        "res/mipmap-hdpi-v4/ic_launcher.png",
        "res/mipmap-hdpi/ic_launcher.png",
        "res/mipmap-mdpi-v4/ic_launcher.png",
        "res/mipmap-mdpi/ic_launcher.png",
        "res/mipmap-xxxhdpi-v4/ic_launcher_round.png",
        "res/mipmap-xxhdpi-v4/ic_launcher_round.png",
        "res/mipmap-xhdpi-v4/ic_launcher_round.png",
        "res/drawable-xxxhdpi-v4/ic_launcher.png",
        "res/drawable-xxhdpi-v4/ic_launcher.png",
    )

    fun densityCandidates(context: Context): IntArray {
        val device = context.resources.displayMetrics.densityDpi
        return intArrayOf(
            device,
            DisplayMetrics.DENSITY_XXXHIGH,
            DisplayMetrics.DENSITY_XXHIGH,
            DisplayMetrics.DENSITY_XHIGH,
            0,
        ).distinct().toIntArray()
    }

    fun fromLauncherActivity(
        context: Context,
        info: LauncherActivityInfo,
        sizePx: Int,
    ): ImageBitmap {
        val pm = context.packageManager
        val densities = densityCandidates(context)
        val pkg = info.componentName.packageName
        val label = info.label?.toString().orEmpty().ifBlank { pkg }
        OemThemeAppIcons.load(context, pkg, sizePx)?.let { return it }
        for (dpi in densities) {
            rasterizeReal(pm, runCatching { info.getBadgedIcon(dpi) }.getOrNull(), sizePx)?.let { return it }
            rasterizeReal(pm, runCatching { info.getIcon(dpi) }.getOrNull(), sizePx)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rasterizeReal(pm, runCatching { info.activityInfo.loadIcon(pm) }.getOrNull(), sizePx)?.let { return it }
            rasterizeReal(pm, runCatching { info.activityInfo.loadUnbadgedIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        }
        fromApplicationInfo(pm, info.applicationInfo, sizePx, densities)?.let { return it }
        fromPackageResources(pm, pkg, sizePx, densities)?.let { return it }
        fromApkPngFallback(pm, pkg, sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { pm.getActivityIcon(info.componentName) }.getOrNull(), sizePx)?.let { return it }
        Log.w(TAG, "letter fallback for ${info.componentName.flattenToShortString()}")
        return letterIcon(label, sizePx)
    }

    fun fromResolveInfo(
        context: Context,
        ri: ResolveInfo,
        sizePx: Int,
    ): ImageBitmap {
        val pm = context.packageManager
        val densities = densityCandidates(context)
        val pkg = ri.activityInfo.packageName
        val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
        OemThemeAppIcons.load(context, pkg, sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { ri.loadIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { ri.activityInfo.loadIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { ri.activityInfo.loadUnbadgedIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        fromApplicationInfo(pm, ri.activityInfo.applicationInfo, sizePx, densities)?.let { return it }
        fromPackageResources(pm, pkg, sizePx, densities)?.let { return it }
        fromApkPngFallback(pm, pkg, sizePx)?.let { return it }
        val component = ComponentName(pkg, ri.activityInfo.name)
        rasterizeReal(pm, runCatching { pm.getActivityIcon(component) }.getOrNull(), sizePx)?.let { return it }
        return letterIcon(label, sizePx)
    }

    fun fromPackage(
        context: Context,
        packageName: String,
        sizePx: Int,
    ): ImageBitmap {
        val pm = context.packageManager
        val densities = densityCandidates(context)
        val ai = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val label = ai?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            .orEmpty().ifBlank { packageName }
        OemThemeAppIcons.load(context, packageName, sizePx)?.let { return it }
        if (ai != null) {
            fromApplicationInfo(pm, ai, sizePx, densities)?.let { return it }
        }
        fromPackageResources(pm, packageName, sizePx, densities)?.let { return it }
        fromApkPngFallback(pm, packageName, sizePx)?.let { return it }
        return rasterizeReal(pm, runCatching { pm.getApplicationIcon(packageName) }.getOrNull(), sizePx)
            ?: letterIcon(label, sizePx)
    }

    /** Rasterize only if the drawable is not the generic Android default icon. */
    private fun rasterizeReal(pm: PackageManager, drawable: Drawable?, sizePx: Int): ImageBitmap? {
        if (drawable == null || isDefaultIcon(drawable, pm)) return null
        return rasterize(drawable, sizePx)
    }

    private fun isDefaultIcon(drawable: Drawable, pm: PackageManager): Boolean {
        val def = pm.defaultActivityIcon
        if (drawable === def) return true
        val a = drawable.constantState
        val b = def.constantState
        return a != null && b != null && a == b
    }

    /**
     * Read legacy PNG launcher icons from the installed APK zip. Bypasses
     * mipmap-anydpi-v26 adaptive XML that Resources resolves first.
     * Skips the Android Studio green-robot template many OEM apps ship as-is.
     */
    private fun fromApkPngFallback(
        pm: PackageManager,
        packageName: String,
        sizePx: Int,
    ): ImageBitmap? {
        val ai = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val apkPaths = listOfNotNull(ai.sourceDir, ai.publicSourceDir).distinct()
        for (apkPath in apkPaths) {
            val bitmap = runCatching { decodePngIconFromApk(apkPath, sizePx) }.getOrNull()
            if (bitmap != null) {
                Log.d(TAG, "apk-png ok $packageName ← $apkPath")
                return bitmap
            }
        }
        return null
    }

    private fun decodePngIconFromApk(apkPath: String, sizePx: Int): ImageBitmap? {
        ZipFile(apkPath).use { zip ->
            for (name in apkIconEntryNames) {
                val entry = zip.getEntry(name) ?: continue
                acceptDecodedPng(zip, entry, sizePx)?.let { return it }
            }
            // Last resort: any mipmap/*/ic_launcher.png present in the zip.
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!name.startsWith("res/mipmap-") || !name.endsWith("/ic_launcher.png")) continue
                if (name.contains("anydpi")) continue
                acceptDecodedPng(zip, entry, sizePx)?.let { return it }
            }
        }
        return null
    }

    private fun acceptDecodedPng(zip: ZipFile, entry: java.util.zip.ZipEntry, sizePx: Int): ImageBitmap? {
        zip.getInputStream(entry).use { input ->
            val decoded = BitmapFactory.decodeStream(input) ?: return null
            val scaled = if (decoded.width == sizePx && decoded.height == sizePx) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
            if (isMostlyTransparent(scaled) || looksLikeDefaultAndroidRobot(scaled)) {
                if (scaled !== decoded) scaled.recycle() else decoded.recycle()
                return null
            }
            return scaled.asImageBitmap()
        }
    }

    /**
     * Android Studio default launcher art: mint/seafoam green (~#3DDC84) grid with a
     * white robot-head silhouette. Many MB_* packages never replaced this asset.
     */
    private fun looksLikeDefaultAndroidRobot(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return false
        var opaque = 0
        var mint = 0
        var white = 0
        val grid = 7
        val x0 = (w * 0.18f).toInt()
        val y0 = (h * 0.18f).toInt()
        val x1 = w - 1 - x0
        val y1 = h - 1 - y0
        for (iy in 0 until grid) {
            for (ix in 0 until grid) {
                val x = x0 + (ix * (x1 - x0)) / (grid - 1)
                val y = y0 + (iy * (y1 - y0)) / (grid - 1)
                val p = bitmap.getPixel(x, y)
                val a = android.graphics.Color.alpha(p)
                if (a < 180) continue
                opaque++
                val r = android.graphics.Color.red(p)
                val g = android.graphics.Color.green(p)
                val b = android.graphics.Color.blue(p)
                if (g > 130 && g > r + 20 && g > b + 10 && r in 10..210 && b in 30..210) {
                    mint++
                } else if (r > 210 && g > 210 && b > 210) {
                    white++
                }
            }
        }
        if (opaque < 20) return false
        // Template is mostly mint green with a modest white head (~10–25% of samples).
        val mintRatio = mint.toFloat() / opaque
        val whiteRatio = white.toFloat() / opaque
        return mintRatio >= 0.45f && whiteRatio in 0.06f..0.35f
    }

    /** Soft letter tile when the package has no real launcher artwork. */
    private fun letterIcon(label: String, sizePx: Int): ImageBitmap {
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: label.firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"
        val hue = ((label.hashCode() ushr 1) % 360).toFloat()
        val bg = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.42f, 0.28f))
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = sizePx * 0.22f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, fill)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = sizePx * 0.48f
        }
        val y = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, sizePx / 2f, y, textPaint)
        return bmp.asImageBitmap()
    }

    private fun fromApplicationInfo(
        pm: PackageManager,
        ai: ApplicationInfo,
        sizePx: Int,
        densities: IntArray,
    ): ImageBitmap? {
        rasterizeReal(pm, runCatching { ai.loadIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { ai.loadUnbadgedIcon(pm) }.getOrNull(), sizePx)?.let { return it }
        rasterizeReal(pm, runCatching { pm.getApplicationIcon(ai) }.getOrNull(), sizePx)?.let { return it }
        val resIds = buildList {
            if (ai.icon != 0) add(ai.icon)
            val roundId = runCatching {
                ApplicationInfo::class.java.getField("roundIconResource").getInt(ai)
            }.getOrDefault(0)
            if (roundId != 0) add(roundId)
        }
        val res = runCatching { pm.getResourcesForApplication(ai) }.getOrNull() ?: return null
        for (id in resIds) {
            for (dpi in densities) {
                val drawable = runCatching {
                    @Suppress("DEPRECATION")
                    if (dpi > 0) res.getDrawableForDensity(id, dpi) else res.getDrawable(id)
                }.getOrNull()
                // Skip adaptive XML — PNG fallback handles those packages.
                if (drawable is AdaptiveIconDrawable) {
                    continue
                }
                rasterize(drawable, sizePx)?.let { return it }
            }
        }
        return null
    }

    private fun fromPackageResources(
        pm: PackageManager,
        packageName: String,
        sizePx: Int,
        densities: IntArray,
    ): ImageBitmap? {
        val res = runCatching { pm.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val names = listOf(
            "ic_launcher" to "mipmap",
            "ic_launcher_round" to "mipmap",
            "ic_launcher" to "drawable",
            "ic_launcher_foreground" to "drawable",
            "app_icon" to "mipmap",
            "app_icon" to "drawable",
        )
        for ((name, type) in names) {
            val id = runCatching { res.getIdentifier(name, type, packageName) }.getOrNull() ?: 0
            if (id == 0) continue
            for (dpi in densities) {
                val drawable = runCatching {
                    @Suppress("DEPRECATION")
                    if (dpi > 0) res.getDrawableForDensity(id, dpi) else res.getDrawable(id)
                }.getOrNull()
                if (drawable is AdaptiveIconDrawable) {
                    continue
                }
                rasterize(drawable, sizePx)?.let { return it }
            }
        }
        return null
    }

    private fun rasterize(drawable: Drawable?, sizePx: Int): ImageBitmap? {
        if (drawable == null || sizePx <= 0) return null
        return runCatching {
            val bmp = drawableToSoftwareBitmap(drawable.mutate(), sizePx) ?: return@runCatching null
            if (isMostlyTransparent(bmp) || looksLikeDefaultAndroidRobot(bmp)) return@runCatching null
            bmp.asImageBitmap()
        }.getOrNull()
    }

    private fun drawableToSoftwareBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
        if (drawable is BitmapDrawable) {
            val src = drawable.bitmap ?: return null
            val soft = if (src.config == Bitmap.Config.HARDWARE) {
                src.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else {
                src
            }
            return if (soft.width == sizePx && soft.height == sizePx) {
                soft
            } else {
                Bitmap.createScaledBitmap(soft, sizePx, sizePx, true)
            }
        }
        if (drawable is AdaptiveIconDrawable) {
            adaptiveToBitmap(drawable, sizePx)?.let { return it }
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun adaptiveToBitmap(icon: AdaptiveIconDrawable, sizePx: Int): Bitmap? {
        val full = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        if (!isMostlyTransparent(full)) return full

        full.eraseColor(android.graphics.Color.TRANSPARENT)
        val bg = icon.background
        val fg = icon.foreground
        bg?.setBounds(0, 0, sizePx, sizePx)
        bg?.draw(canvas)
        fg?.setBounds(0, 0, sizePx, sizePx)
        fg?.draw(canvas)
        return if (isMostlyTransparent(full)) null else full
    }

    /** Sample the inner half — adaptive icons are transparent near the edges. */
    private fun isMostlyTransparent(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        var opaque = 0
        val samples = 6
        val x0 = w / 4
        val y0 = h / 4
        val xSpan = (w / 2).coerceAtLeast(1)
        val ySpan = (h / 2).coerceAtLeast(1)
        for (iy in 0 until samples) {
            for (ix in 0 until samples) {
                val x = x0 + (ix * (xSpan - 1)) / (samples - 1).coerceAtLeast(1)
                val y = y0 + (iy * (ySpan - 1)) / (samples - 1).coerceAtLeast(1)
                val alpha = android.graphics.Color.alpha(bitmap.getPixel(x, y))
                if (alpha > 16) opaque++
            }
        }
        return opaque < 3
    }
}
