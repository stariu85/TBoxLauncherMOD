package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.toOwnedImageBitmap
import java.io.File

@Composable
fun LauncherWallpaperDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onTemporarilyHide: (() -> Unit)? = null,
    onRestoreDialog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var darkUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperDarkUri(context))
    }
    var lightUri by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperLightUri(context))
    }
    var scaleMode by remember(visible) {
        mutableStateOf(LauncherAppConfigStore.wallpaperScaleMode(context))
    }
    var targetThemeForPicker by remember { mutableStateOf<Boolean?>(null) } // true = dark, false = light

    val systemImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val targetDark = targetThemeForPicker
        if (uri != null && targetDark != null) {
            val localPath = saveWallpaperImageToInternalStorage(context, uri, isDark = targetDark)
            val savedPath = localPath ?: uri.toString()
            if (targetDark) {
                darkUri = savedPath
                LauncherAppConfigStore.setWallpaperDarkUri(context, savedPath)
            } else {
                lightUri = savedPath
                LauncherAppConfigStore.setWallpaperLightUri(context, savedPath)
            }
        }
        targetThemeForPicker = null
        onRestoreDialog?.invoke()
    }

    if (!visible) return

    LauncherDarkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.launcher_wallpaper_dialog_title),
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Dark theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_dark_title),
                    currentUri = darkUri,
                    selectButtonLabel = stringResource(R.string.launcher_wallpaper_select_dark),
                    onPickSystem = {
                        targetThemeForPicker = true
                        onTemporarilyHide?.invoke()
                        systemImagePicker.launch("image/*")
                    },
                    onClear = {
                        if (darkUri.isNotBlank() && !darkUri.startsWith("content://")) {
                            runCatching { File(darkUri).delete() }
                        }
                        darkUri = ""
                        LauncherAppConfigStore.setWallpaperDarkUri(context, "")
                    },
                )

                // Light theme wallpaper selection
                WallpaperThemeSection(
                    title = stringResource(R.string.launcher_wallpaper_light_title),
                    currentUri = lightUri,
                    selectButtonLabel = stringResource(R.string.launcher_wallpaper_select_light),
                    onPickSystem = {
                        targetThemeForPicker = false
                        onTemporarilyHide?.invoke()
                        systemImagePicker.launch("image/*")
                    },
                    onClear = {
                        if (lightUri.isNotBlank() && !lightUri.startsWith("content://")) {
                            runCatching { File(lightUri).delete() }
                        }
                        lightUri = ""
                        LauncherAppConfigStore.setWallpaperLightUri(context, "")
                    },
                )

                // Scale mode selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_wallpaper_scale_title),
                        color = LauncherColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val options = listOf(
                        WallpaperScaleMode.FILL_WIDTH to stringResource(R.string.launcher_wallpaper_scale_width),
                        WallpaperScaleMode.FILL_HEIGHT to stringResource(R.string.launcher_wallpaper_scale_height),
                        WallpaperScaleMode.FILL_BOUNDS to stringResource(R.string.launcher_wallpaper_scale_fill),
                    )
                    options.forEach { (mode, label) ->
                        val selected = scaleMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) LauncherColors.AccentCyan.copy(alpha = 0.15f)
                                    else LauncherColors.CardDark,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) LauncherColors.AccentCyan else LauncherColors.SettingsBorder,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    scaleMode = mode
                                    LauncherAppConfigStore.setWallpaperScaleMode(context, mode)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) LauncherColors.AccentCyan else LauncherColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    scaleMode = mode
                                    LauncherAppConfigStore.setWallpaperScaleMode(context, mode)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LauncherColors.AccentCyan,
                                    unselectedColor = LauncherColors.TextMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = LauncherColors.AccentCyan,
                )
            }
        },
    )
}

@Composable
private fun WallpaperThemeSection(
    title: String,
    currentUri: String,
    selectButtonLabel: String,
    onPickSystem: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (currentUri.isNotBlank()) {
            val fileName = uriToDisplayFileName(currentUri)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.CardDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName,
                    color = LauncherColors.AccentCyan,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.launcher_wallpaper_clear),
                    color = LauncherColors.WarningRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(start = 8.dp),
                )
            }
        } else {
            Text(
                text = "Обои по умолчанию",
                color = LauncherColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        TextButton(
            onClick = onPickSystem,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LauncherColors.CardDarkElevated)
                .border(1.dp, LauncherColors.SettingsBorder, RoundedCornerShape(8.dp)),
        ) {
            Text(
                text = selectButtonLabel,
                color = LauncherColors.AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun uriToDisplayFileName(uriStr: String): String {
    if (uriStr.isBlank()) return ""
    return runCatching {
        if (uriStr.startsWith("content://")) {
            Uri.parse(uriStr).lastPathSegment?.substringAfterLast('/') ?: uriStr
        } else {
            File(uriStr).name
        }
    }.getOrDefault(uriStr)
}

internal fun saveWallpaperImageToInternalStorage(context: Context, uri: Uri, isDark: Boolean): String? {
    return runCatching {
        val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val fileName = if (isDark) "wallpaper_dark.jpg" else "wallpaper_light.jpg"
        val targetFile = File(wallpaperDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        if (targetFile.exists() && targetFile.length() > 0L) {
            targetFile.absolutePath
        } else null
    }.getOrNull()
}

internal fun loadWallpaperImageBitmap(context: Context, uriOrPath: String, isDark: Boolean): ImageBitmap? {
    if (uriOrPath.isBlank()) {
        val defaultRes = if (isDark) R.drawable.default_wallpaper_dark else R.drawable.default_wallpaper_light
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, defaultRes, options)
            val (w, h) = options.outWidth to options.outHeight
            if (w > 0 && h > 0) {
                val maxDim = 1920
                var sample = 1
                while (w / sample > maxDim || h / sample > maxDim) {
                    sample *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                BitmapFactory.decodeResource(context.resources, defaultRes, decodeOptions)?.toOwnedImageBitmap()
            } else null
        }.getOrNull()
    }

    return runCatching {
        val file = if (uriOrPath.startsWith("content://")) {
            val uri = Uri.parse(uriOrPath)
            saveWallpaperImageToInternalStorage(context, uri, isDark)?.let { File(it) }
        } else {
            File(uriOrPath)
        }
        if (file == null || !file.isFile || file.length() <= 0L) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val (w, h) = options.outWidth to options.outHeight
        if (w > 0 && h > 0) {
            val maxDim = 1920
            var sample = 1
            while (w / sample > maxDim || h / sample > maxDim) {
                sample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.toOwnedImageBitmap()
        } else null
    }.getOrNull()
}
